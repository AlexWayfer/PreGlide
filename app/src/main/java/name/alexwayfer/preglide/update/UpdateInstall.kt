package name.alexwayfer.preglide.update

import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal data class UpdateInstallState(val busy: Boolean = false, val failed: Boolean = false)

/** The copy into Android's installer is asynchronous; a failure can be retried and a cancel is not an error. */
internal class UpdateInstall(
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val commit: (File) -> Unit,
    private val problem: () -> Unit = { },
) {
    private val shown = MutableStateFlow(UpdateInstallState())
    val state: StateFlow<UpdateInstallState> = shown

    fun install(file: File) {
        if (!shown.compareAndSet(shown.value.copy(busy = false), UpdateInstallState(busy = true))) return
        scope.launch(io) {
            try {
                commit(file)
            } catch (_: IOException) {
                failed()
            } catch (_: SecurityException) {
                failed()
            } catch (_: IllegalArgumentException) {
                failed()
            }
        }
    }

    fun onStatus(success: Boolean, canceled: Boolean) {
        shown.value = UpdateInstallState(failed = !success && !canceled)
        if (shown.value.failed) problem()
    }

    private fun failed() {
        shown.value = UpdateInstallState(failed = true)
        problem()
    }
}
