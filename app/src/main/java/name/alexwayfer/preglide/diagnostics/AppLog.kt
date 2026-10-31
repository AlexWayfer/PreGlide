package name.alexwayfer.preglide.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Writes to logcat like [Log] and keeps the latest lines in memory and in a file, so a problem report shows what
 * the app did just before the problem. The app logs only what it does, never what the user types, nor words, nor
 * secrets, so the lines can go into a report as they are.
 */
internal object AppLog {
    private val recent = RecentLog()

    @Volatile
    private var file: AppLogFile? = null
    private val fileWriter = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AppLogFile").apply { isDaemon = true }
    }

    /** Also writes every line to a file in the cache, starting with a mark for this process naming its [build]. */
    fun start(context: Context, build: String) {
        val logFile = AppLogFile(File(context.cacheDir, "logs"))
        file = logFile
        val now = System.currentTimeMillis()
        val mark = recentLogLine(now, ZoneId.systemDefault(), 'I', "AppLog", "Process started $build")
        fileWriter.execute { appendQuietly(logFile, mark) }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        remember('I', tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        remember('W', tag, message)
    }

    fun e(tag: String, message: String, error: Throwable) {
        Log.e(tag, message, error)
        remember('E', tag, "$message: ${error.javaClass.simpleName}")
    }

    /** The latest lines, oldest first. */
    fun snapshot(): List<String> = recent.lines()

    /** Every line the log file keeps, oldest first, read after the lines still waiting to be written. */
    fun fileLines(): List<String> {
        val logFile = file ?: return emptyList()
        return try {
            fileWriter.submit<List<String>> { logFile.read() }.get()
        } catch (error: ExecutionException) {
            Log.w("AppLog", "log file read failed ${error.cause?.javaClass?.simpleName}")
            emptyList()
        }
    }

    private fun remember(level: Char, tag: String, message: String) {
        val line = recentLogLine(System.currentTimeMillis(), ZoneId.systemDefault(), level, tag, message)
        recent.add(line)
        val logFile = file ?: return
        fileWriter.execute { appendQuietly(logFile, line) }
    }

    private fun appendQuietly(logFile: AppLogFile, line: String) {
        try {
            logFile.append(line)
        } catch (_: IOException) {
            // The line stays in the recent lines; the log has nowhere to report its own failure.
        }
    }
}
