package name.alexwayfer.preglide.diagnostics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import java.io.File

/** The build and the device a report comes from: nothing of the user's own. */
internal data class DiagnosticDevice(
    val appVersion: String,
    val androidRelease: String,
    val sdk: Int,
    val manufacturer: String,
    val model: String,
    /** The system's languages in order, as language tags. */
    val languages: List<String>,
    val debug: Boolean,
    /** When this APK was installed or last updated: tells which deploy of a debug build a report came from. */
    val installedAtMillis: Long?,
)

/** The build and the device a report comes from, as this device tells them. */
internal fun diagnosticDevice(context: Context): DiagnosticDevice {
    val info = packageInfo(context)
    return DiagnosticDevice(
        appVersion = diagnosticBuild(info),
        androidRelease = Build.VERSION.RELEASE,
        sdk = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        // Without the regional preferences, such as the first day of the week, that ride along as extensions.
        languages = Resources.getSystem().configuration.locales.let { locales ->
            (0 until locales.size()).map { locales[it].stripExtensions().toLanguageTag() }.distinct()
        },
        debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        installedAtMillis = diagnosticInstalledAt(
            packageUpdatedMillis = info?.lastUpdateTime,
            // Android Studio deploys a changed debug build into this overlay without reinstalling the APK.
            studioOverlayMillis = File(context.codeCacheDir, ".overlay/id").lastModified(),
        ),
    )
}

/** The app's version name and code, as "0.1.0 (1)". */
internal fun diagnosticBuild(context: Context): String = diagnosticBuild(packageInfo(context))

private fun diagnosticBuild(info: PackageInfo?): String =
    info?.let { "${it.versionName} (${it.longVersionCode})" } ?: "unknown"

private fun packageInfo(context: Context): PackageInfo? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
} catch (_: PackageManager.NameNotFoundException) {
    null
}
