package name.alexwayfer.preglide.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.core.content.IntentCompat
import java.io.File
import java.io.IOException
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.diagnostics.AppLog

/** Android still validates the signature and asks the user to approve the install. */
internal fun commitUpdate(context: Context, file: File) {
    val manager = context.packageManager
    val own = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val apk = manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
    if (apk == null || !updatePackageMatches(updatePackage(own), updatePackage(apk))) {
        throw IOException("The update package or signing certificate does not match")
    }
    val installer = manager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
        setSize(file.length())
    }
    val id = installer.createSession(params)
    var committed = false
    try {
        installer.openSession(id).use { session ->
            session.openWrite("update.apk", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val status = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
            committed = true
        }
    } finally {
        if (!committed) installer.abandonSession(id)
    }
}

private fun updatePackage(info: PackageInfo): UpdatePackage = UpdatePackage(
    info.packageName, info.longVersionCode,
    info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray().toHexString() }.toSet(),
)

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            AppLog.i("AppUpdate", "install status $status")
            (context.applicationContext as PreGlideApplication).updateInstall.onStatus(
                success = status == PackageInstaller.STATUS_SUCCESS,
                canceled = status == PackageInstaller.STATUS_FAILURE_ABORTED,
            )
        }
    }
}
