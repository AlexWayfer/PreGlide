package name.alexwayfer.preglide.update

import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface UpdateBackend {
    suspend fun latest(): UpdateResult
    suspend fun download(release: UpdateRelease, target: File, progress: (Float) -> Unit): Boolean
}

internal data class UpdateEnvironment(
    val folder: File,
    val installed: UpdateInstallation,
    val clock: () -> Long,
    val notice: (UpdateRelease?) -> Unit,
    val enabled: Boolean = true,
    val problem: (UpdateFailure) -> Unit = { },
)

/** Checks and downloads share state, while their blocking work uses the injected context. */
internal class Updates(
    private val backend: UpdateBackend,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val environment: UpdateEnvironment,
) {
    private val shown = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = shown
    private val lock = Any()
    private var checking: Job? = null
    private var downloading: Job? = null
    private var lastRead: Long? = null
    private var offered: UpdateRelease? = null
    private var notifyRunning = false

    fun check(notify: Boolean = false, force: Boolean = true): Job = synchronized(lock) {
        checking?.takeIf { it.isActive }?.let {
            notifyRunning = notifyRunning || notify
            return it
        }
        if (!force && !updateCheckDue(lastRead, environment.clock())) return scope.launch { }
        notifyRunning = notify
        lastRead = null
        shown.update { it.copy(checking = true) }
        scope.launch(io) { read() }.also { checking = it }
    }

    /** A crash or freeze may already have a fix in the newest release, as in CustomTV. */
    fun afterProblem(): Job = synchronized(lock) {
        if (updateCheckDue(lastRead, environment.clock())) check(notify = true, force = false)
        else {
            offered?.let(environment.notice)
            scope.launch { }
        }
    }

    /** A changed login invalidates checks, downloaded offers, and notifications from the previous account. */
    fun reset() = synchronized(lock) {
        checking?.cancel()
        downloading?.cancel()
        offered = null
        lastRead = null
        notifyRunning = false
        shown.value = UpdateState()
        environment.notice(null)
    }

    private suspend fun read() {
        val context = kotlin.coroutines.coroutineContext
        val result = if (!environment.enabled) UpdateResult.Debug else try {
            backend.latest()
        } catch (_: IOException) {
            UpdateResult.Failed(UpdateFailure.NoAnswer)
        }
        val release = (result as? UpdateResult.Read)?.release?.takeIf {
            updateIsNewer(it, environment.installed)
        }
        val cached = release?.let { target(it).takeIf { file -> checkedUpdate(file, release) } }
        // Hashing a large APK stays off the main thread and outside the lock used by UI actions.
        synchronized(lock) {
            context.ensureActive()
            apply(result, cached)
        }
    }

    private fun apply(result: UpdateResult, cached: File?) {
        if (result == UpdateResult.Failed(UpdateFailure.InvalidResponse)) {
            environment.problem(UpdateFailure.InvalidResponse)
        }
        if (result is UpdateResult.Read) {
            lastRead = environment.clock()
            val next = result.release?.takeIf { updateIsNewer(it, environment.installed) }
            if (offered != next) {
                downloading?.cancel()
                shown.update { it.copy(download = UpdateDownload.Idle) }
            }
            offered = next
            if (downloading?.isCompleted != false) prepareFiles(cached)
            if (offered == null || notifyRunning) environment.notice(offered)
        } else if (result == UpdateResult.SignIn || result == UpdateResult.NoAccess) {
            downloading?.cancel()
            offered = null
            shown.update { it.copy(download = UpdateDownload.Idle) }
            environment.notice(null)
        }
        shown.update { it.copy(checking = false, result = result) }
        notifyRunning = false
    }

    private fun target(release: UpdateRelease): File = environment.folder.resolve(
        "${environment.installed.edition}-${release.id}-${release.version}.apk",
    )

    private fun prepareFiles(cached: File?) {
        environment.folder.mkdirs()
        val release = offered
        val target = release?.let(::target)
        environment.folder.listFiles().orEmpty().filter { it != target }.forEach { it.delete() }
        val ready = if (release != null && cached != null) {
            UpdateDownload.Ready(release.id, cached)
        } else UpdateDownload.Idle
        shown.update { it.copy(download = ready) }
    }

    fun download(release: UpdateRelease): Job? = synchronized(lock) {
        if (!environment.enabled || offered != release || downloading?.isCompleted == false) return null
        shown.update { it.copy(download = UpdateDownload.Running(release.id, 0f)) }
        scope.launch(io) { fetch(release) }.also { downloading = it }
    }

    private suspend fun fetch(release: UpdateRelease) {
        val context = kotlin.coroutines.coroutineContext
        environment.folder.mkdirs()
        val target = target(release)
        val result = try {
            val intact = backend.download(release, target) { fraction ->
                synchronized(lock) {
                    context.ensureActive()
                    shown.update { it.copy(download = UpdateDownload.Running(release.id, fraction)) }
                }
            }
            if (intact) UpdateDownload.Ready(release.id, target)
            else UpdateDownload.Failed(release.id, UpdateFailure.Damaged)
        } catch (_: IOException) {
            UpdateDownload.Failed(release.id, UpdateFailure.Download)
        }
        synchronized(lock) {
            context.ensureActive()
            if (offered == release) {
                shown.update { it.copy(download = result) }
                if (result == UpdateDownload.Failed(release.id, UpdateFailure.Damaged)) {
                    environment.problem(UpdateFailure.Damaged)
                }
            } else target.delete()
        }
    }
}
