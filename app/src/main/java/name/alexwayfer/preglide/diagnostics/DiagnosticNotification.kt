package name.alexwayfer.preglide.diagnostics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import name.alexwayfer.preglide.R

private const val CHANNEL_ID = "problem_reports"
internal const val DIAGNOSTIC_NOTIFICATION_ID = 7107

/**
 * One notice for every problem the next report holds: a newer problem updates it with the count and every
 * headline, since Send report sends them all at once. Without the user's leave to notify, Android drops it; the
 * report still goes from the app's screen.
 */
internal fun showDiagnosticNotification(context: Context, headlines: List<String>) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.diagnostics_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
    val send = PendingIntent.getActivity(
        context,
        DIAGNOSTIC_NOTIFICATION_ID,
        diagnosticReportIntent(context, withAppLog = false),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val text = context.getString(R.string.diagnostics_text)
    manager.notify(
        DIAGNOSTIC_NOTIFICATION_ID,
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bug_report)
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.diagnostics_title, headlines.size, headlines.size),
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(text) + headlines).joinToString("\n")))
            .setContentIntent(send)
            .addAction(0, context.getString(R.string.diagnostics_send), send)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build(),
    )
}
