package name.alexwayfer.preglide.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.R

/** Android 14's leave to read the photos the user picked alone; the constant is Android 14's too. */
private const val READ_SELECTED_PHOTOS = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

/** Android 13's leave to read all photos, asked for only there and later; the constant is Android 13's too. */
private const val READ_ALL_PHOTOS = "android.permission.READ_MEDIA_IMAGES"

/** How much of the photos the app may read. */
enum class PhotoAccess {
    /** All of them, new screenshots too. */
    All,

    /** Only those the user picked, as Android 14 offers: a new screenshot is not among them. */
    Selected,

    None,
}

/** The leave that reads all photos on Android [sdk]: the media one from Android 13, the files one before. */
fun allPhotosPermission(sdk: Int): String =
    if (sdk >= Build.VERSION_CODES.TIRAMISU) READ_ALL_PHOTOS else Manifest.permission.READ_EXTERNAL_STORAGE

/** The leaves to ask for on Android [sdk]: from Android 14, the one for picked photos too, which it may give alone. */
fun photoPermissions(sdk: Int): Array<String> =
    if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) arrayOf(allPhotosPermission(sdk), READ_SELECTED_PHOTOS)
    else arrayOf(allPhotosPermission(sdk))

/** How much of the photos the app may read on Android [sdk], with the leaves [granted]. */
fun photoAccess(sdk: Int, granted: (String) -> Boolean): PhotoAccess = when {
    granted(allPhotosPermission(sdk)) -> PhotoAccess.All
    sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(READ_SELECTED_PHOTOS) -> PhotoAccess.Selected
    else -> PhotoAccess.None
}

/** How much of the photos the app may read now. */
fun photoAccess(context: Context): PhotoAccess = photoAccess(Build.VERSION.SDK_INT) {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

/** Whether fresh screenshots are offered: the user [wants] them, and the app may read [access] enough to see them. */
fun offersScreenshots(wants: Boolean, access: PhotoAccess): Boolean = wants && access == PhotoAccess.All

/** The hint under the screenshots' switch: why only some photos are not enough, when that is the [access] given. */
@StringRes
fun screenshotsHint(access: PhotoAccess): Int =
    if (access == PhotoAccess.Selected) R.string.settings_screenshots_selected else R.string.settings_screenshots_hint

/** Whether fresh screenshots are offered, and how to turn them on, which asks for the leave, or off. */
class ScreenshotSwitch(val on: Boolean, val access: PhotoAccess, val turnOn: () -> Unit, val turnOff: () -> Unit)

/**
 * The switch of fresh screenshots in [settings], for the setup and the suggestions' page alike, and for the hint of
 * the row to it. Turning it on asks for the leave to read all photos, under the reason the page gives; once Android
 * stops asking after declines, it opens the app's settings instead. The access is read again whenever the screen gets
 * the focus back, as the user may change it in the system's settings.
 */
@Composable
fun rememberScreenshotSwitch(settings: KeyboardSettings): ScreenshotSwitch {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val wants by settings.screenshots.collectAsStateWithLifecycle()
    var access by remember { mutableStateOf(photoAccess(context)) }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (focused) access = photoAccess(context)
    }
    val permission = allPhotosPermission(Build.VERSION.SDK_INT)
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = photoAccess(context)
        val rationaleAfter = activity?.shouldShowRequestPermissionRationale(permission) == true
        when {
            access == PhotoAccess.All -> scope.launch { settings.setScreenshots(true) }
            // Some photos are a leave all the same: asking again offers all of them.
            opensSettingsAfterAsking(access != PhotoAccess.None, rationaleBefore, rationaleAfter) ->
                openAppSettings(context)
        }
    }
    return ScreenshotSwitch(
        on = offersScreenshots(wants, access),
        access = access,
        turnOn = {
            if (access == PhotoAccess.All) {
                scope.launch { settings.setScreenshots(true) }
            } else {
                rationaleBefore = activity?.shouldShowRequestPermissionRationale(permission) == true
                launcher.launch(photoPermissions(Build.VERSION.SDK_INT))
            }
        },
        turnOff = { scope.launch { settings.setScreenshots(false) } },
    )
}

/** The switch of fresh screenshots on the suggestions' page; turning it on asks for the leave it needs. */
@Composable
fun ScreenshotsEntry(screenshots: ScreenshotSwitch) {
    SwitchEntry(
        R.drawable.ic_image,
        stringResource(R.string.settings_screenshots),
        stringResource(screenshotsHint(screenshots.access)),
        checked = screenshots.on,
        onChange = { if (it) screenshots.turnOn() else screenshots.turnOff() },
    )
}

/** The app's page in the system's settings, where the user gives the leaves Android stopped asking for. */
private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
