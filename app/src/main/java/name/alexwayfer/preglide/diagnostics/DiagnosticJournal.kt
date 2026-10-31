package name.alexwayfer.preglide.diagnostics

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.io.IOException

internal const val DIAGNOSTICS_PREFS = "diagnostics"
private const val SENT_AT = "sent_at"

/** The problems kept on the device, and when the last report with them went out. */
internal object DiagnosticJournal {
    private val lock = Any()

    /** Adds [event] to the journal; true when the journal did not hold this problem yet. */
    fun record(context: Context, event: DiagnosticEvent): Boolean = synchronized(lock) {
        val file = journal(context)
        val previous = read(context)
        // A freeze is found after a restart, when the lines before it went with the old process.
        val withLog = if (event.kind == DiagnosticKind.Anr) event else event.copy(recentLog = AppLog.snapshot())
        try {
            file.parentFile?.mkdirs()
            file.writeText(encodeDiagnosticEvents(recordDiagnosticEvent(previous, withLog)))
        } catch (error: IOException) {
            Log.w(DIAGNOSTICS_TAG, "journal write failed ${error.javaClass.simpleName}")
        }
        diagnosticNoticeDue(previous, event, sentAt(context))
    }

    fun read(context: Context): List<DiagnosticEvent> = synchronized(lock) {
        val file = journal(context)
        if (!file.exists()) return emptyList()
        try {
            decodeDiagnosticEvents(file.readText())
        } catch (error: IOException) {
            Log.w(DIAGNOSTICS_TAG, "journal read failed ${error.javaClass.simpleName}")
            emptyList()
        }
    }

    /** The problems the next report holds. */
    fun eventsToSend(context: Context): List<DiagnosticEvent> = diagnosticEventsToSend(read(context), sentAt(context))

    /** The report went to the share sheet: the next one starts after it, and a repeat is news again. */
    fun markSent(context: Context) {
        context.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
            .edit { putLong(SENT_AT, System.currentTimeMillis()) }
    }

    private fun sentAt(context: Context): Long =
        context.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE).getLong(SENT_AT, 0L)

    private fun journal(context: Context): File = File(context.filesDir, "diagnostics/journal.json")
}
