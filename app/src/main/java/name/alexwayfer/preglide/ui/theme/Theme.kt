package name.alexwayfer.preglide.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.settings.ThemeSettings

/** How long the colors take to flow into newly chosen ones. */
private const val COLOR_CHANGE_MILLIS = 400

/**
 * A keyboard sits under every other app, so it follows the wallpaper colors (Material You) where Android has them,
 * unless the user chose its own palette. A change of colors flows rather than jumps.
 *
 * Until the settings are read, a screen that [waitsForSettings] shows nothing, so it opens in the user's colors
 * rather than flowing into them from the system's; the keyboard shows the system's meanwhile, rather than nothing.
 * The keyboard takes the colors of the app it types in, from its [appSeed], where the user asked for them.
 */
@Composable
fun PreGlideTheme(waitsForSettings: Boolean = false, appSeed: Int? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val theme = rememberThemeSettings()
    if (theme == null && waitsForSettings) return
    val palette = shownPalette(appSeed, theme?.colors)
    val dark = isPreGlideDark()
    val contrast = rememberSystemContrast()
    val target = palette?.let { remember(it, dark, contrast) { paletteColorScheme(it, dark, contrast) } }
        ?: systemColorScheme(context, dark)
    MaterialTheme(colorScheme = flowingColorScheme(target), content = content)
}

/** The theme's settings as read, or null before the store is read. */
@Composable
private fun rememberThemeSettings(): ThemeSettings? {
    val settings = (LocalContext.current.applicationContext as PreGlideApplication).settings
    return settings.colors.theme.collectAsState().value
}

/** Whether the keyboard and the app are dark: as the system is, or as the user picked. */
@Composable
fun isPreGlideDark(): Boolean =
    (rememberThemeSettings()?.darkMode ?: DarkMode.System).isDark(isSystemInDarkTheme())

/** The system's colors: Material You's from Android 12, Material's baseline before it. */
internal fun systemColorScheme(context: Context, dark: Boolean): ColorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    dark -> darkColorScheme()
    else -> lightColorScheme()
}

/**
 * The system's contrast setting, from Android 14, which builds its own palettes with it: 0 for the standard one. A
 * change of it comes as a change of configuration, as the system's colors change with it.
 */
@Composable
internal fun rememberSystemContrast(): Double {
    val context = LocalContext.current
    return remember(LocalConfiguration.current) { systemContrast(context) }
}

private fun systemContrast(context: Context): Double =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.getSystemService(UiModeManager::class.java).contrast.toDouble()
    } else {
        0.0
    }

/** [target], reached by blending each color from the ones shown, so a new palette flows in. */
@Composable
private fun flowingColorScheme(target: ColorScheme): ColorScheme {
    val targetRoles = target.roles()
    var shown by remember { mutableStateOf(targetRoles) }
    LaunchedEffect(targetRoles) {
        val start: List<Color> = shown
        if (start == targetRoles) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(COLOR_CHANGE_MILLIS)) { fraction, _ ->
            shown = blend(start, targetRoles, fraction)
        }
        // Blending converts each color through Oklab, so the last frame may miss the target by a rounding.
        shown = targetRoles
    }
    return remember(shown) { colorSchemeOf(shown) }
}
