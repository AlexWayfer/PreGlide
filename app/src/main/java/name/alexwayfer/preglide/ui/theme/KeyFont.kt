package name.alexwayfer.preglide.ui.theme

import androidx.annotation.FontRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import name.alexwayfer.preglide.R

/**
 * The font of the keyboard's keys and suggestions: the system's, or one the app carries, each with Cyrillic, as the
 * Russian letters need. [title] is its name, the same in every language; the system's, null, has a string of its
 * own. [regular] and [medium] are its files for the two weights the keyboard draws: the letters, and the hints and
 * the best suggestion a notch bolder. A [variable] font holds both in one file. A font without a medium face gives
 * its bold one, so the best suggestion still stands out.
 */
enum class KeyFont(
    val title: String?,
    @param:FontRes val regular: Int?,
    @param:FontRes val medium: Int? = regular,
    val variable: Boolean = true,
) {
    System(null, null),
    Comfortaa("Comfortaa", R.font.comfortaa),
    DejaVuSans("DejaVu Sans", R.font.dejavu_lgc_sans, R.font.dejavu_lgc_sans_bold, variable = false),
    FiraSans("Fira Sans", R.font.fira_sans_regular, R.font.fira_sans_medium, variable = false),
    GoogleSans("Google Sans", R.font.google_sans),
    IbmPlexSans("IBM Plex Sans", R.font.ibm_plex_sans),
    Inter("Inter", R.font.inter),
    Manrope("Manrope", R.font.manrope),
    Montserrat("Montserrat", R.font.montserrat),
    NotoSans("Noto Sans", R.font.noto_sans),
    Nunito("Nunito", R.font.nunito),
    OpenSans("Open Sans", R.font.open_sans),
    PtSans("PT Sans", R.font.pt_sans_regular, R.font.pt_sans_bold, variable = false),
    Rubik("Rubik", R.font.rubik),
    ;

    companion object {
        /** The font the settings keep by [name]; the system's for none, or for one a later version of the app has. */
        fun parse(name: String?): KeyFont = entries.firstOrNull { it.name == name } ?: System
    }
}

/** The family of [font]'s files, or null for the system's font, which text takes when it names none. */
fun keyFontFamily(font: KeyFont): FontFamily? {
    val regular = font.regular ?: return null
    val medium = font.medium ?: regular
    return FontFamily(face(regular, FontWeight.Normal, font.variable), face(medium, FontWeight.Medium, font.variable))
}

/** A face of [weight] from the file [resource]; a [variable] one is set to that weight, as it holds them all. */
private fun face(@FontRes resource: Int, weight: FontWeight, variable: Boolean): Font = if (variable) {
    Font(resource, weight, variationSettings = FontVariation.Settings(weight, FontStyle.Normal))
} else {
    Font(resource, weight)
}

/** These styles in [family], or as they are for null, the system's font. */
fun Typography.withFontFamily(family: FontFamily?): Typography {
    if (family == null) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}

/**
 * The keyboard's [content] in [font]: every text style of the theme takes it, so a text that names no style, as a
 * key's label, takes it too, and the app's own screens keep the system's.
 */
@Composable
fun KeyFontTheme(font: KeyFont, content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = remember(typography, font) { typography.withFontFamily(keyFontFamily(font)) },
        content = content,
    )
}
