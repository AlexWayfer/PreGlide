package name.alexwayfer.preglide.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How many of the latest log lines go with each problem. */
internal const val RECENT_LOG_LINES = 20

/** The latest log lines, dropping the oldest past [RECENT_LOG_LINES]. */
internal class RecentLog {
    private val lines = ArrayDeque<String>()

    fun add(line: String) = synchronized(lines) {
        lines.addLast(line)
        while (lines.size > RECENT_LOG_LINES) lines.removeFirst()
    }

    fun lines(): List<String> = synchronized(lines) { lines.toList() }
}

private val logTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

internal fun recentLogLine(atMillis: Long, zone: ZoneId, level: Char, tag: String, message: String): String =
    "${Instant.ofEpochMilli(atMillis).atZone(zone).format(logTimeFormat)} $level/$tag: $message"
