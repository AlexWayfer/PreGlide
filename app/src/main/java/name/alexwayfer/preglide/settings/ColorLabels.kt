package name.alexwayfer.preglide.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ui.theme.DarkMode
import name.alexwayfer.preglide.ui.theme.Palette
import name.alexwayfer.preglide.ui.theme.PaletteSource
import name.alexwayfer.preglide.ui.theme.PaletteStyle
import name.alexwayfer.preglide.ui.theme.presetPalettes

/**
 * [id]'s text from [resources] with [args] in it, as the `text` that [paletteLabel] and [colorsHint] take: none, one
 * or two arguments, which needs no array spread into getString.
 */
fun resourceText(resources: Resources, @StringRes id: Int, args: List<Any>): String = when (args.size) {
    0 -> resources.getString(id)
    1 -> resources.getString(id, args[0])
    2 -> resources.getString(id, args[0], args[1])
    else -> error("A colors' text takes at most two arguments, not ${args.size}")
}

/**
 * What a screen reader says for [palette] among [palettes]: a wallpaper's by its color's number and its style, a
 * preset by its color's name. [text] reads a string resource with its arguments.
 */
fun paletteLabel(palettes: List<Palette>, palette: Palette, text: (Int, List<Any>) -> String): String {
    val style = styleName(palette.style)
    val name = presetName(palette)
    return when {
        name != null -> text(name, emptyList())
        palette.source == PaletteSource.Wallpaper && style != null ->
            text(R.string.colors_wallpaper_option, listOf(colorNumber(palettes, palette), text(style, emptyList())))
        else -> ""
    }
}

/**
 * The hint of what the colors' page has chosen: the [appColors], else the system's colors or [chosen]'s name, as
 * among the [wallpaper]'s palettes, then the light or dark [mode] unless it follows the system. A palette of a
 * wallpaper whose colors are not among those, as before they are known, goes by the wallpaper alone, without a
 * number. [text] reads a string resource with its arguments.
 */
fun colorsHint(
    appColors: Boolean,
    chosen: Palette?,
    wallpaper: List<Palette>,
    mode: DarkMode,
    text: (Int, List<Any>) -> String,
): String {
    val colors = when {
        appColors -> text(R.string.colors_app, emptyList())
        chosen == null -> text(R.string.colors_system, emptyList())
        chosen.source == PaletteSource.Wallpaper && chosen !in wallpaper -> text(R.string.colors_wallpaper, emptyList())
        else -> paletteLabel(wallpaper, chosen, text)
    }
    val modeName = modeHint(mode) ?: return colors
    return text(R.string.colors_hint_with_mode, listOf(colors, text(modeName, emptyList())))
}

/** How a hint names a light or dark [mode]; null for one that follows the system, which a hint leaves out. */
@StringRes
private fun modeHint(mode: DarkMode): Int? = when (mode) {
    DarkMode.System -> null
    DarkMode.Light -> R.string.dark_mode_hint_light
    DarkMode.Dark -> R.string.dark_mode_hint_dark
}

/** The name of a light or dark [mode] on its segment. */
@StringRes
fun darkModeName(mode: DarkMode): Int = when (mode) {
    DarkMode.System -> R.string.dark_mode_system
    DarkMode.Light -> R.string.dark_mode_light
    DarkMode.Dark -> R.string.dark_mode_dark
}

/**
 * The name of a wallpaper palette's [style]; null for monochrome, which only the presets have, and for content's,
 * which only an app's color takes.
 */
@StringRes
private fun styleName(style: PaletteStyle): Int? = when (style) {
    PaletteStyle.TonalSpot -> R.string.colors_style_tonal_spot
    PaletteStyle.Neutral -> R.string.colors_style_neutral
    PaletteStyle.Vibrant -> R.string.colors_style_vibrant
    PaletteStyle.Expressive -> R.string.colors_style_expressive
    PaletteStyle.Monochrome, PaletteStyle.Content -> null
}

/** The names of [presetPalettes], in their order. */
private val presetNames = listOf(
    R.string.colors_monochrome,
    R.string.colors_blue,
    R.string.colors_pink,
    R.string.colors_coral,
    R.string.colors_peach,
    R.string.colors_gold,
    R.string.colors_olive,
    R.string.colors_green,
    R.string.colors_teal,
    R.string.colors_sky_blue,
    R.string.colors_periwinkle,
    R.string.colors_lavender,
    R.string.colors_lilac,
)

@StringRes
private fun presetName(palette: Palette): Int? =
    presetPalettes.indexOf(palette).takeIf { it >= 0 }?.let(presetNames::getOrNull)

/** The number of [palette]'s color among the wallpaper's [palettes], from 1, as each color comes in several styles. */
private fun colorNumber(palettes: List<Palette>, palette: Palette): Int =
    palettes.map { it.seed }.distinct().indexOf(palette.seed) + 1
