package name.alexwayfer.preglide.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ui.theme.KeyFont
import name.alexwayfer.preglide.ui.theme.Swatch
import name.alexwayfer.preglide.ui.theme.WallpaperWatch
import name.alexwayfer.preglide.ui.theme.isPreGlideDark
import name.alexwayfer.preglide.ui.theme.keyFontFamily
import name.alexwayfer.preglide.ui.theme.presetPalettes
import name.alexwayfer.preglide.ui.theme.schemeSwatch
import name.alexwayfer.preglide.ui.theme.swatch
import name.alexwayfer.preglide.ui.theme.wallpaperPalettes

// Material's fade through, in ms: the old content fades out quickly, then the new one fades in.
private const val FADE_THROUGH_OUT = 90
private const val FADE_THROUGH_IN = 210

/** The swatch of the colors in use on their row, as large as the picture a list row leads with. */
private val EntrySwatchSize = 40.dp

/**
 * The keyboard's appearance: the rows to its colors and its font, each on a page of its own, which [onOpen] opens,
 * then whether the keys have backgrounds, how round their corners are, whether keys without them have dividers, and
 * whether the keyboard and the app animate.
 */
@Composable
fun AppearancePage(
    settings: KeyboardSettings,
    wallpaper: WallpaperWatch,
    onOpen: (SettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    TryScaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The colors' row reaches the screen edges, as list rows do; the rest keeps off them.
            Column(Modifier.padding(horizontal = 16.dp)) {
                PageHeader(stringResource(R.string.settings_appearance), onBack)
            }
            Column {
                ColorsEntry(settings, wallpaper) { onOpen(SettingsPage.Colors) }
                FontEntry(settings) { onOpen(SettingsPage.Font) }
            }
            KeyBackgrounds(settings)
            AnimationsSwitch(settings)
        }
    }
}

/** The switch of the animations of the keyboard and the app's screen. */
@Composable
private fun AnimationsSwitch(settings: KeyboardSettings) {
    val animations by settings.animations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SwitchEntry(
        icon = null,
        stringResource(R.string.appearance_animations),
        stringResource(R.string.appearance_animations_hint),
        checked = animations,
        onChange = { scope.launch { settings.setAnimations(it) } },
    )
}

/**
 * The switch of the keys' backgrounds, and under it what goes with the choice: how round their corners are, which
 * only the backgrounds show, or else the switch of the dividers between the keys.
 */
@Composable
private fun KeyBackgrounds(settings: KeyboardSettings) {
    val backgrounds by settings.keyBackgrounds.collectAsStateWithLifecycle()
    val dividers by settings.keyDividers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SwitchEntry(
        icon = null,
        stringResource(R.string.appearance_key_backgrounds),
        stringResource(R.string.appearance_key_backgrounds_hint),
        checked = backgrounds,
        onChange = { scope.launch { settings.setKeyBackgrounds(it) } },
    )
    // One in the place of the other, as Material's fade through: the old one fades out, then the new one in, while
    // the place takes the new one's height. Two places, one shrinking and one growing, would jump by the room
    // between them as each comes and goes.
    AnimatedContent(
        backgrounds,
        transitionSpec = {
            fadeIn(tween(FADE_THROUGH_IN, delayMillis = FADE_THROUGH_OUT)) togetherWith fadeOut(tween(FADE_THROUGH_OUT))
        },
        contentAlignment = Alignment.TopStart,
        label = "key backgrounds' setting",
    ) { shown ->
        if (shown) {
            KeyCornersSection(settings)
        } else {
            SwitchEntry(
                icon = null,
                stringResource(R.string.appearance_key_dividers),
                stringResource(R.string.appearance_key_dividers_hint),
                checked = dividers,
                onChange = { scope.launch { settings.setKeyDividers(it) } },
            )
        }
    }
}

/**
 * The row to the colors' page: the swatch of the palette in use, the one the system has while the keyboard takes the
 * system's colors, or of the colors themselves when they are none of those offered; and a hint of what is chosen.
 */
@Composable
private fun ColorsEntry(settings: KeyboardSettings, wallpaper: WallpaperWatch, onOpen: () -> Unit) {
    val chosen by settings.colors.palette.collectAsStateWithLifecycle()
    val mode by settings.colors.darkMode.collectAsStateWithLifecycle()
    val appColors by settings.colors.fromApps.collectAsStateWithLifecycle()
    val seeds by wallpaper.seeds.collectAsStateWithLifecycle()
    val wallpaperOptions = seeds?.let(::wallpaperPalettes).orEmpty()
    val inUse = chosen ?: rememberSystemPalette(wallpaperOptions + presetPalettes)
    val dark = isPreGlideDark()
    val scheme = MaterialTheme.colorScheme
    val swatch = remember(inUse, dark, scheme) { inUse?.let { swatch(it, dark) } ?: schemeSwatch(scheme) }
    val resources = LocalResources.current
    val hint = colorsHint(appColors, chosen, wallpaperOptions, mode) { id, args -> resourceText(resources, id, args) }
    PageEntry(leading = { EntrySwatch(swatch) }, stringResource(R.string.settings_colors), hint, onOpen)
}

/** The row to the font's page: a sample of the font in use, as large as the colors' swatch, and its name. */
@Composable
private fun FontEntry(settings: KeyboardSettings, onOpen: () -> Unit) {
    val font by settings.keyFont.collectAsStateWithLifecycle()
    PageEntry(leading = { EntryFontSample(font) }, stringResource(R.string.settings_font), fontTitle(font), onOpen)
}

/** Two letters in [font] on the font's row, which fade into another font's as the hint beside them does. */
@Composable
private fun EntryFontSample(font: KeyFont) {
    AnimatedContent(font, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "font sample") { shown ->
        Box(Modifier.size(EntrySwatchSize), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.font_entry_sample),
                fontFamily = remember(shown) { keyFontFamily(shown) },
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/**
 * The [swatch] on the colors' row. Its colors flow from the ones it had, as when the wallpaper's colors come in after
 * the page opens and name the system's palette.
 */
@Composable
private fun EntrySwatch(swatch: Swatch) {
    val top by animateColorAsState(swatch.top)
    val bottomLeft by animateColorAsState(swatch.bottomLeft)
    val bottomRight by animateColorAsState(swatch.bottomRight)
    Canvas(Modifier.size(EntrySwatchSize)) { drawSwatch(Swatch(top, bottomLeft, bottomRight)) }
}
