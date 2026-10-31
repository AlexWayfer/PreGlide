package name.alexwayfer.preglide.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.PreGlideApplication
import java.io.IOException
import java.time.ZoneId
import name.alexwayfer.preglide.update.checksUpdateAfterProblem

/** The log tag of the diagnostics themselves, written straight to Android's log. */
internal const val DIAGNOSTICS_TAG = "Diagnostics"

/**
 * Keeps the problems a developer should hear about and offers the user to send them: crashes, freezes, and the
 * failures the app caught and went on from. Nothing leaves the device unless the user shares the report, and the
 * report never holds what the user typed.
 */
internal object Diagnostics {
    private const val NOTICED_AT = "noticed_at"
    private const val EXITS_READ_AT = "exits_read_at"
    private lateinit var application: Application

    /** Starts keeping the problems of [application], reading the ones the system kept on [io]. */
    fun start(application: Application, io: CoroutineDispatcher) {
        this.application = application
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Whatever goes wrong here, the crash itself must still reach the system handler.
            val event = crashEvent(thread, error, System.currentTimeMillis())
            runCatching { DiagnosticJournal.record(application, event) }
            previous?.uncaughtException(thread, error)
        }
        CoroutineScope(SupervisorJob() + io).launch {
            readFreezes()
            noticeUnseenExits()
        }
    }

    /** A failure at [source] the app caught and went on from: [what] it was doing when [error] came. */
    fun reportFailure(source: String, what: String, error: Throwable) {
        AppLog.e(source, what, error)
        if (!::application.isInitialized) return
        val event = failureEvent(source, what, error, System.currentTimeMillis())
        if (DiagnosticJournal.record(application, event)) notice(event)
    }

    /**
     * The report as plain text, read when the user chooses to send it. [withAppLog] adds the whole log file, for a
     * report sent from the app's screen about a problem the app did not notice.
     */
    suspend fun report(context: Context, withAppLog: Boolean): String = diagnosticReport(
        device = diagnosticDevice(context),
        events = DiagnosticJournal.eventsToSend(context),
        zone = ZoneId.systemDefault(),
        appLog = if (withAppLog) AppLog.fileLines() else emptyList(),
        telegramAccount = reportTelegramAccount(context),
    ) + glideStatisticsReport((context.applicationContext as PreGlideApplication).glideStatistics.lines())

    /** The message that goes with the report file. */
    fun shareText(context: Context): String =
        diagnosticShareText(diagnosticDevice(context), DiagnosticJournal.eventsToSend(context).size)

    /** Android keeps why the process ended; a freeze since the last start becomes an event. */
    private fun readFreezes() {
        val prefs = application.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
        val readAt = prefs.getLong(EXITS_READ_AT, -1L)
        val now = System.currentTimeMillis()
        // The first start only marks the time, so freezes from before this version are not reported.
        if (readAt >= 0) {
            application.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(application.packageName, 0, 0)
                .orEmpty()
                .filter { it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp > readAt }
                .sortedBy { it.timestamp }
                .forEach { DiagnosticJournal.record(application, freezeEvent(it)) }
        }
        prefs.edit { putLong(EXITS_READ_AT, now) }
    }

    private fun freezeEvent(exit: ApplicationExitInfo): DiagnosticEvent {
        val main = try {
            exit.traceInputStream?.bufferedReader()?.useLines(::anrMainThread).orEmpty()
        } catch (error: IOException) {
            Log.w(DIAGNOSTICS_TAG, "freeze trace failed ${error.javaClass.simpleName}")
            ""
        }
        Log.w(DIAGNOSTICS_TAG, "freeze found")
        return DiagnosticEvent(
            kind = DiagnosticKind.Anr,
            source = "main",
            detail = topStackFrame(main) ?: "unknown",
            message = listOfNotNull(exit.description, main.ifBlank { null }).joinToString("\n"),
            firstAtMillis = exit.timestamp,
        )
    }

    private fun noticeUnseenExits() {
        val noticedAt = application.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
            .getLong(NOTICED_AT, 0L)
        unseenExitEvents(DiagnosticJournal.read(application), noticedAt).firstOrNull()?.let(::notice)
    }

    private fun notice(event: DiagnosticEvent) {
        Log.w(DIAGNOSTICS_TAG, "problem notice ${event.kind}")
        application.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
            .edit { putLong(NOTICED_AT, System.currentTimeMillis()) }
        showDiagnosticNotification(
            application,
            DiagnosticJournal.eventsToSend(application).map(::diagnosticHeadline)
                .ifEmpty { listOf(diagnosticHeadline(event)) },
        )
        if (checksUpdateAfterProblem(event.source)) {
            // Application-owned services are first initialized on the main thread.
            Handler(Looper.getMainLooper()).post {
                (application as? PreGlideApplication)?.updates?.afterProblem()
            }
        }
    }
}
