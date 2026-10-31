package name.alexwayfer.preglide.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ui.theme.DarkMode
import name.alexwayfer.preglide.ui.theme.Palette
import name.alexwayfer.preglide.ui.theme.Swatch
import name.alexwayfer.preglide.ui.theme.WallpaperWatch
import name.alexwayfer.preglide.ui.theme.isPreGlideDark
import name.alexwayfer.preglide.ui.theme.keyColors
import name.alexwayfer.preglide.ui.theme.paletteAfterSystem
import name.alexwayfer.preglide.ui.theme.paletteKeyColors
import name.alexwayfer.preglide.ui.theme.presetPalettes
import name.alexwayfer.preglide.ui.theme.rememberSystemContrast
import name.alexwayfer.preglide.ui.theme.swatch
import name.alexwayfer.preglide.ui.theme.systemColorScheme
import name.alexwayfer.preglide.ui.theme.systemPalette
import name.alexwayfer.preglide.ui.theme.wallpaperPalettes

private val SwatchSize = 56.dp
private val RingWidth = 2.dp

/** The room between a chosen swatch and its ring. */
private val RingGap = 4.dp

private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f

/**
 * The keyboard's colors: light or dark, whether it takes the color of the app it opens in, then the system's colors,
 * or a palette of the wallpaper's colors or of
 * Android's own set, shown as Android's picker shows them. The app takes them too, so a choice shows at once. While
 * the keyboard takes the system's colors, the palette the system has is marked, if it is one of those offered.
 */
@Composable
fun ColorsPage(settings: KeyboardSettings, wallpaper: WallpaperWatch, onBack: () -> Unit) {
    val chosen by settings.colors.palette.collectAsStateWithLifecycle()
    val seeds by wallpaper.seeds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val choose: (Palette?) -> Unit = { palette -> scope.launch { settings.colors.setPalette(palette) } }
    val wallpaperOptions = seeds?.let(::wallpaperPalettes).orEmpty()
    val system = rememberSystemPalette(wallpaperOptions + presetPalettes)
    val marked = chosen ?: system

    TryScaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The switch's row reaches the screen edges, as list rows do; the rest keeps off them.
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader(stringResource(R.string.settings_colors), onBack)
                DarkModeChoice(settings)
            }
            AppColorsSwitch(settings)
            SwitchEntry(
                icon = null,
                stringResource(R.string.colors_system),
                stringResource(R.string.colors_system_hint),
                checked = chosen == null,
                onChange = { on -> choose(if (on) null else paletteAfterSystem(seeds, system)) },
            )
            AnimatedVisibility(
                wallpaperOptions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PaletteSection(
                    stringResource(R.string.colors_wallpaper),
                    stringResource(R.string.colors_wallpaper_hint),
                ) {
                    Swatches(wallpaperOptions, marked, choose)
                }
            }
            PaletteSection(stringResource(R.string.colors_other), hint = null) {
                Swatches(presetPalettes, marked, choose)
            }
        }
    }
}

/** Light or dark: as the system is, or always one way; three segments, the current one checked. */
@Composable
private fun DarkModeChoice(settings: KeyboardSettings) {
    val mode by settings.colors.darkMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        DarkMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == mode,
                onClick = { scope.launch { settings.colors.setDarkMode(option) } },
                shape = SegmentedButtonDefaults.itemShape(index, DarkMode.entries.size),
            ) {
                Text(stringResource(darkModeName(option)))
            }
        }
    }
}

/** Whether the keyboard takes the color of the app it opens in; the colors under it are for the other apps. */
@Composable
private fun AppColorsSwitch(settings: KeyboardSettings) {
    val on by settings.colors.fromApps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SwitchEntry(
        icon = null,
        stringResource(R.string.colors_app),
        stringResource(R.string.colors_app_hint),
        checked = on,
        onChange = { scope.launch { settings.colors.setFromApps(it) } },
    )
}

/**
 * The palette of [options] the system's colors are built from, or null for none: see [systemPalette]. The system's
 * colors change with a change of configuration.
 */
@Composable
internal fun rememberSystemPalette(options: List<Palette>): Palette? {
    val context = LocalContext.current
    val dark = isPreGlideDark()
    val contrast = rememberSystemContrast()
    return remember(options, dark, contrast, LocalConfiguration.current) {
        systemPalette(systemColorScheme(context, dark).keyColors(), options) { paletteKeyColors(it, dark, contrast) }
    }
}

/** A [title]d group of palettes to choose from, the [swatches], with a [hint] under the title, if any. */
@Composable
private fun PaletteSection(title: String, hint: String?, swatches: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        swatches()
    }
}

/** The swatches of [palettes], the [marked] one shrunk into a ring. */
@Composable
private fun Swatches(palettes: List<Palette>, marked: Palette?, choose: (Palette) -> Unit) {
    val resources = LocalResources.current
    val text = { id: Int, args: List<Any> -> resourceText(resources, id, args) }
    val dark = isPreGlideDark()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        palettes.forEach { palette ->
            val swatch = remember(palette, dark) { swatch(palette, dark) }
            SwatchButton(swatch, palette == marked, paletteLabel(palettes, palette, text)) { choose(palette) }
        }
    }
}

/** A palette's [swatch] that a tap chooses. A [selected] one shrinks into a ring, which fades in around it. */
@Composable
private fun SwatchButton(swatch: Swatch, selected: Boolean, label: String, onClick: () -> Unit) {
    val selection by animateFloatAsState(if (selected) 1f else 0f)
    val ring = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
    ) {
        val ringWidth = RingWidth.toPx()
        drawCircle(ring, radius = size.minDimension / 2 - ringWidth / 2, alpha = selection, style = Stroke(ringWidth))
        inset((ringWidth + RingGap.toPx()) * selection) { drawSwatch(swatch) }
    }
}

/** A palette's [swatch], as Android's picker draws it, as a circle over the area: the primary color over the others. */
internal fun DrawScope.drawSwatch(swatch: Swatch) {
    // Each part over a whole circle rather than beside the others: the smoothed edges of two parts side by side let
    // the background through as a thin line, which shows where the parts are of one color.
    drawCircle(swatch.bottomRight)
    drawArc(swatch.bottomLeft, startAngle = QUARTER_TURN, sweepAngle = QUARTER_TURN, useCenter = true)
    drawArc(swatch.top, startAngle = HALF_TURN, sweepAngle = HALF_TURN, useCenter = true)
}
