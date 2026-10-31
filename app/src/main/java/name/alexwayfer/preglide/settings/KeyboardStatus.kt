package name.alexwayfer.preglide.settings

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.view.textservice.TextServicesManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.app.NotificationManagerCompat

/**
 * Whether the keyboard is [enabled] and [selected], whether its [spellChecker] checks the spelling (before
 * Android 12 that cannot be told, and reads as not), and whether the app may post [notices]: with the leave, and
 * not turned off in the app's notice settings.
 */
internal data class KeyboardStatus(
    val enabled: Boolean,
    val selected: Boolean,
    val spellChecker: Boolean,
    val notices: Boolean,
) {
    companion object {
        fun read(context: Context): KeyboardStatus {
            val packageName = context.packageName
            val enabled = context.getSystemService(InputMethodManager::class.java)
                .enabledInputMethodList.any { it.packageName == packageName }
            val selected = currentKeyboard(context)?.let(ComponentName::unflattenFromString)?.packageName == packageName
            val notices = NotificationManagerCompat.from(context).areNotificationsEnabled()
            return KeyboardStatus(enabled, selected, isSpellChecker(context), notices)
        }

        /** The keyboard the system has chosen, as its setting names it: any keyboard, this one or another. */
        fun currentKeyboard(context: Context): String? =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)

        private fun isSpellChecker(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
            val textServices = context.getSystemService(TextServicesManager::class.java)
            return textServices.isSpellCheckerEnabled &&
                textServices.currentSpellCheckerInfo?.packageName == context.packageName
        }
    }
}

/** Whether the [current] keyboard is another than the one [shown] last, when one was. */
internal fun keyboardSwitched(shown: String?, current: String?): Boolean = shown != null && current != shown

/**
 * The keyboard's status, read again whenever the screen gets the focus back: the system keyboard list, the keyboard
 * picker and the leave dialog take it while the user changes what the status shows. A keyboard switched from the
 * keyboard itself, as in the field to try it, takes no focus: the system settings of the current and the enabled
 * keyboards are watched for that, as Android's own input method service watches them.
 */
@Composable
internal fun rememberKeyboardStatus(): KeyboardStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(KeyboardStatus.read(context)) }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (focused) status = KeyboardStatus.read(context)
    }
    WatchSecureSettings(Settings.Secure.DEFAULT_INPUT_METHOD, Settings.Secure.ENABLED_INPUT_METHODS) {
        status = KeyboardStatus.read(context)
    }
    return status
}

/** Calls [onChange] on the main thread whenever one of the system's secure settings of [keys] changes. */
@Composable
internal fun WatchSecureSettings(vararg keys: String, onChange: () -> Unit) {
    val context = LocalContext.current
    val changed by rememberUpdatedState(onChange)
    DisposableEffect(context, *keys) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changed()
            }
        }
        for (key in keys) {
            context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
