package name.alexwayfer.preglide.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal fun diagnosticReport(
    device: DiagnosticDevice,
    events: List<DiagnosticEvent>,
    zone: ZoneId,
    appLog: List<String> = emptyList(),
    // Premium's Telegram account, so the developer can answer; a report goes to the developer only.
    telegramAccount: String? = null,
): String = buildString {
    appendLine("PreGlide problem report")
    appendLine("App: ${diagnosticApp(device)}")
    device.installedAtMillis?.let { appendLine("Installed: ${diagnosticTime(it, zone)}") }
    appendLine("Android: ${device.androidRelease} (API ${device.sdk}), ${device.manufacturer} ${device.model}")
    appendLine("Languages: ${device.languages.joinToString(", ").ifEmpty { "-" }}")
    telegramAccount?.let { appendLine("Telegram: $it") }
    events.forEach { appendEvent(it, zone) }
    if (appLog.isNotEmpty()) {
        appendLine()
        appendLine("App log:")
        appLog.forEach { appendLine("  $it") }
    }
}.trimEnd() + "\n"

private fun StringBuilder.appendEvent(event: DiagnosticEvent, zone: ZoneId) {
    appendLine()
    appendLine(diagnosticHeadline(event))
    val last = diagnosticTime(event.lastAtMillis, zone)
    val since = diagnosticTime(event.firstAtMillis, zone)
    appendLine(if (event.count > 1) "$last, ${event.count} times since $since" else last)
    if (event.message.isNotBlank()) appendLine(event.message.trimEnd())
    if (event.recentLog.isNotEmpty()) {
        appendLine("Log before it:")
        event.recentLog.forEach { appendLine("  $it") }
    }
}

/** The persistent totals are included even when the report has no errors or rotating journal attached. */
internal fun glideStatisticsReport(lines: List<String>): String = if (lines.isEmpty()) {
    ""
} else {
    "\nGlide statistics (all recorded use):\n" + lines.joinToString("\n", postfix = "\n") { "  $it" }
}

private val fileTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

/**
 * The report file's name with the time it was made, so reports saved on a computer sort by time instead of piling
 * up as copies. The time is in UTC, marked with Z, since the name cannot tell the device's zone. Dashes stand for
 * colons, which Windows does not allow in a file name.
 */
internal fun diagnosticReportFileName(millis: Long): String =
    "preglide-report_${Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).format(fileTimeFormat)}Z.txt"

/**
 * When the running code arrived: the package's install or update, or a later deploy from Android Studio, which
 * changes a debug build without reinstalling it. [studioOverlayMillis] is 0 without such a deploy.
 */
internal fun diagnosticInstalledAt(packageUpdatedMillis: Long?, studioOverlayMillis: Long): Long? =
    listOfNotNull(packageUpdatedMillis, studioOverlayMillis.takeIf { it > 0 }).maxOrNull()

/** The build: its version, and a mark on a debug build. */
internal fun diagnosticApp(device: DiagnosticDevice): String =
    device.appVersion + if (device.debug) " debug" else ""

/**
 * The message the report file goes with: only the build and how many problems the file holds. The problems
 * themselves stay in the file, so the person who sends it does not read a list of exceptions.
 */
internal fun diagnosticShareText(device: DiagnosticDevice, problems: Int): String =
    "PreGlide ${diagnosticApp(device)}" + if (problems > 0) "\nProblems: $problems" else ""

/** One line naming the problem, used as the first line of an event and in the notice. */
internal fun diagnosticHeadline(event: DiagnosticEvent): String = when (event.kind) {
    DiagnosticKind.Failure -> "${event.source}: ${event.detail}"
    DiagnosticKind.Crash -> "Crash: ${event.detail}"
    DiagnosticKind.Anr -> "Freeze: ${event.detail}"
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

internal fun diagnosticTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS).atZone(zone).toOffsetDateTime().format(timeFormat)
