package name.alexwayfer.preglide.update

import java.io.File
import kotlin.time.Duration.Companion.minutes

data class UpdateRelease(
    val id: Long,
    val version: ReleaseVersion,
    val size: Long,
    val sha256: String?,
    val versionCode: Long? = null,
)

internal data class UpdateInstallation(val version: ReleaseVersion, val versionCode: Long, val edition: String)

internal enum class UpdateFailure { NoAnswer, Server, InvalidResponse, Download, Damaged }

internal sealed interface UpdateResult {
    data class Read(val release: UpdateRelease?) : UpdateResult
    data class Failed(val reason: UpdateFailure) : UpdateResult
    data object SignIn : UpdateResult
    data object NoAccess : UpdateResult
    data object Debug : UpdateResult
}

internal sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class Running(val id: Long, val fraction: Float) : UpdateDownload
    data class Ready(val id: Long, val file: File) : UpdateDownload
    data class Failed(val id: Long, val reason: UpdateFailure) : UpdateDownload
}

internal data class UpdateState(
    val checking: Boolean = false,
    val result: UpdateResult = UpdateResult.Read(null),
    val download: UpdateDownload = UpdateDownload.Idle,
)

internal fun updateIsNewer(release: UpdateRelease, installed: UpdateInstallation): Boolean =
    release.versionCode?.let { it > installed.versionCode } ?: (release.version > installed.version)

internal fun updateCheckDue(lastRead: Long?, now: Long): Boolean =
    lastRead == null || now < lastRead || now - lastRead >= 30.minutes.inWholeMilliseconds

/** An update error must not start another update check through the problem journal. */
internal fun checksUpdateAfterProblem(source: String): Boolean = source != "AppUpdate"

/** Only our application ID, a newer version code, and the same signing certificate set can replace this app. */
internal data class UpdatePackage(val name: String, val versionCode: Long, val certificates: Set<String>)

internal fun updatePackageMatches(installed: UpdatePackage, candidate: UpdatePackage): Boolean =
    installed.name == candidate.name && candidate.versionCode > installed.versionCode &&
        candidate.certificates.isNotEmpty() && candidate.certificates == installed.certificates
