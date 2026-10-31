package name.alexwayfer.preglide.diagnostics

/** The journal keeps this many distinct problems; a repeat of one only raises its count. */
internal const val DIAGNOSTIC_JOURNAL_LIMIT = 20

/** A problem the app caught and went on from, a crash, or a freeze (Android's "app not responding"). */
internal enum class DiagnosticKind { Failure, Crash, Anr }

/**
 * One problem the developer should hear about. [source] is the log tag or the thread, [detail] the exception and
 * its top frame, [message] what went wrong or the stack, and [recentLog] the app's log lines just before the latest
 * time it happened. Never what the user typed: the keyboard sees passwords and private messages.
 */
internal data class DiagnosticEvent(
    val kind: DiagnosticKind,
    val source: String,
    val detail: String,
    val message: String = "",
    val firstAtMillis: Long,
    val lastAtMillis: Long = firstAtMillis,
    val count: Int = 1,
    val recentLog: List<String> = emptyList(),
) {
    fun sameProblem(other: DiagnosticEvent): Boolean =
        kind == other.kind && source == other.source && detail == other.detail
}

/** Adds [event] to the journal: a repeat updates the earlier entry, and the oldest entries fall off. */
internal fun recordDiagnosticEvent(events: List<DiagnosticEvent>, event: DiagnosticEvent): List<DiagnosticEvent> {
    val earlier = events.firstOrNull { it.sameProblem(event) }
    val merged = earlier?.copy(
        message = event.message,
        lastAtMillis = event.lastAtMillis,
        count = earlier.count + 1,
        recentLog = event.recentLog,
    ) ?: event
    return (events.filterNot { it.sameProblem(event) } + merged)
        .sortedByDescending { it.lastAtMillis }
        .take(DIAGNOSTIC_JOURNAL_LIMIT)
}

/**
 * A notice names a problem once; its repeats only raise the count in the report, until a report goes out at
 * [sentAtMillis]. After that a repeat is news again.
 */
internal fun diagnosticNoticeDue(events: List<DiagnosticEvent>, event: DiagnosticEvent, sentAtMillis: Long): Boolean =
    events.none { it.sameProblem(event) && it.lastAtMillis > sentAtMillis }

/**
 * What the next report holds: the problems that happened since the last report at [sentAtMillis], repeats of sent
 * ones included. Android does not tell whether the report really went out, so with nothing new it holds the whole
 * journal again.
 */
internal fun diagnosticEventsToSend(events: List<DiagnosticEvent>, sentAtMillis: Long): List<DiagnosticEvent> =
    events.filter { it.lastAtMillis > sentAtMillis }.ifEmpty { events }

/** Crashes and freezes are found on the next start; these are the ones no notice has named yet. */
internal fun unseenExitEvents(events: List<DiagnosticEvent>, noticedAtMillis: Long): List<DiagnosticEvent> =
    events.filter {
        (it.kind == DiagnosticKind.Crash || it.kind == DiagnosticKind.Anr) && it.lastAtMillis > noticedAtMillis
    }

/** Keeps the main thread of an ANR trace, or nothing without one; the other threads only make the report long. */
internal fun anrMainThread(trace: Sequence<String>): String =
    trace.dropWhile { !it.startsWith("\"main\"") }
        .takeWhile { it.isNotBlank() }
        .joinToString("\n")

/** The first frame of a thread dump, which names where the thread was stuck. */
internal fun topStackFrame(thread: String): String? =
    thread.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("at ") }?.removePrefix("at ")

/** A crash on [thread]: the exception and its top frame name it, and the whole stack goes with it. */
internal fun crashEvent(thread: Thread, error: Throwable, atMillis: Long): DiagnosticEvent = DiagnosticEvent(
    kind = DiagnosticKind.Crash,
    source = thread.name,
    detail = errorDetail(error),
    message = error.stackTraceToString(),
    firstAtMillis = atMillis,
)

/** A problem the app caught at [source] and went on from: [what] it was doing, and the stack. */
internal fun failureEvent(source: String, what: String, error: Throwable, atMillis: Long): DiagnosticEvent =
    DiagnosticEvent(
        kind = DiagnosticKind.Failure,
        source = source,
        detail = errorDetail(error),
        message = what + "\n" + error.stackTraceToString(),
        firstAtMillis = atMillis,
    )

/** The exception's class and its top frame: what tells two problems apart. */
private fun errorDetail(error: Throwable): String {
    val frame = error.stackTrace.firstOrNull()?.toString()
    return listOfNotNull(error.javaClass.name, frame?.let { "at $it" }).joinToString(" ")
}
