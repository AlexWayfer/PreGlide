package name.alexwayfer.preglide.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import name.alexwayfer.preglide.R
import java.util.Locale

/** The name of [locale]'s language in that language, as the system's list of languages has it: "Русский". */
fun languageName(locale: Locale): String =
    locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }

/**
 * The first page's row of the app's language, with the language it shows now; it opens Android's screen to pick
 * another. That screen exists from Android 13 on: on older versions the row is left out, and the app follows the
 * system.
 */
@Composable
fun AppLanguageEntry() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    ActionEntry(
        R.drawable.ic_language,
        stringResource(R.string.settings_app_language),
        languageName(LocalConfiguration.current.locales[0]),
    ) { openAppLanguageSettings(context) }
}
