package name.alexwayfer.preglide.diagnostics

import java.io.File

/** Past this size the log file becomes the previous one and a new file starts. */
internal const val APP_LOG_FILE_MAX_BYTES = 256L * 1024

/**
 * The app's log lines on disk, so they outlive logcat. Read them with
 * `adb shell run-as <package> cat cache/logs/app-log.txt` (and `app-log.1.txt`).
 */
internal class AppLogFile(dir: File, private val maxBytes: Long = APP_LOG_FILE_MAX_BYTES) {
    private val current = File(dir, "app-log.txt")
    private val previous = File(dir, "app-log.1.txt")

    init {
        dir.mkdirs()
    }

    fun append(line: String) {
        if (current.length() >= maxBytes) {
            previous.delete()
            current.renameTo(previous)
        }
        current.appendText(line + "\n")
    }

    /** Every kept line, oldest first: the previous file, then the current one. */
    fun read(): List<String> = listOf(previous, current).filter { it.exists() }.flatMap { it.readLines() }
}
