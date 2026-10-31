package name.alexwayfer.preglide.settings

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import name.alexwayfer.preglide.R

/** The keyboard's own entry among the system's [keyboards], whether enabled or not: the one of [packageName]. */
internal fun ownKeyboard(keyboards: List<InputMethodInfo>, packageName: String): InputMethodInfo? =
    keyboards.firstOrNull { it.packageName == packageName }

/**
 * The names of the languages the keyboard types in, as the system's screen of its languages shows them: those the
 * user checked, or until then those the system picks from its own languages. Empty while the system lists none.
 */
internal fun keyboardLanguages(context: Context): List<String> {
    val manager = context.getSystemService(InputMethodManager::class.java)
    val keyboard = ownKeyboard(manager.inputMethodList, context.packageName) ?: return emptyList()
    val app = keyboard.serviceInfo.applicationInfo
    return manager.getEnabledInputMethodSubtypeList(keyboard, true)
        .map { it.getDisplayName(context, keyboard.packageName, app).toString() }
}

/** The row's hint of the keyboard's [languages] in their order, as Android's own keyboards put it; null for none. */
internal fun languagesHint(languages: List<String>): String? =
    languages.takeIf { it.isNotEmpty() }?.joinToString(", ")

/**
 * The first page's row of the keyboard's languages, with those it types in now; it opens the system's screen to
 * check others. That screen saves the checks only once it stops, after this screen has the focus back, so the
 * languages are read again when the system's setting of them changes. Its switch to follow the system's languages
 * saves at once, while this screen is in the background, and Android tells a background app of a changed setting
 * only 10 s later: so the languages are read again on the focus too.
 */
@Composable
fun KeyboardLanguagesEntry() {
    val context = LocalContext.current
    var languages by remember { mutableStateOf(keyboardLanguages(context)) }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (focused) languages = keyboardLanguages(context)
    }
    WatchSecureSettings(Settings.Secure.ENABLED_INPUT_METHODS) {
        languages = keyboardLanguages(context)
    }
    ActionEntry(
        R.drawable.ic_translate,
        stringResource(R.string.settings_keyboard_languages),
        languagesHint(languages) ?: stringResource(R.string.settings_keyboard_languages_hint),
    ) { openKeyboardLanguageSettings(context) }
}
