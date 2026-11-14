package name.alexwayfer.preglide.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.io.IOException
import kotlinx.coroutines.MainScope
import name.alexwayfer.preglide.BuildConfig
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.diagnostics.AppLog
import name.alexwayfer.preglide.diagnostics.Diagnostics
import name.alexwayfer.preglide.settings.SettingsActivity

internal const val OPEN_UPDATES = "open_updates"
private const val CHANNEL = "app_updates"
private const val NOTICE_ID = 7108

internal fun appUpdates(app: PreGlideApplication): Updates = Updates(
    editionUpdates(app), MainScope(), app.dispatchers.io,
    UpdateEnvironment(
        app.cacheDir.resolve("updates"),
        UpdateInstallation(
            checkNotNull(ReleaseVersion.parse(BuildConfig.VERSION_NAME)),
            BuildConfig.VERSION_CODE.toLong(), BuildConfig.FLAVOR,
        ),
        SystemClock::elapsedRealtime,
        { release -> updateNotification(app, release) },
        enabled = !BuildConfig.LOCAL_TRACING,
        problem = { updateProblem(it.name) },
    ),
)

internal fun updateHttp(): UpdateHttp = UpdateHttp { status ->
    AppLog.w("AppUpdate", "server answered HTTP $status")
    Diagnostics.reportFailure("AppUpdate", "Update request failed", IOException("Update HTTP $status"))
}

internal fun updateProblem(step: String) {
    Diagnostics.reportFailure("AppUpdate", "Update failed: $step", IOException("Update $step failed"))
}

/** A denied notification permission leaves the offer on the updates page. */
internal fun updateNotification(context: Context, release: UpdateRelease?) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (release == null) {
        manager.cancel(NOTICE_ID)
        return
    }
    manager.createNotificationChannel(NotificationChannel(
        CHANNEL, context.getString(R.string.updates_title), NotificationManager.IMPORTANCE_DEFAULT,
    ))
    val open = PendingIntent.getActivity(
        context, NOTICE_ID,
        Intent(context, SettingsActivity::class.java).putExtra(OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    manager.notify(NOTICE_ID, NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_sync)
        .setContentTitle(context.getString(R.string.updates_available, release.version.toString()))
        .setContentText(context.getString(R.string.updates_notification))
        .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build())
}
