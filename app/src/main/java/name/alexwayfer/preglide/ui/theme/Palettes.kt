package name.alexwayfer.preglide.ui.theme

/** A style of Material You palette built from one color, as Android's wallpaper and style picker offers them. */
enum class PaletteStyle {
    TonalSpot,
    Neutral,
    Vibrant,
    Expressive,
    Monochrome,

    /** Kept near its seed's own colorfulness, for a color taken from content: the app the keyboard types in. */
    Content,
}

/**
 * Where a palette's color comes from: the wallpaper, which a new one may take away, the app's own set, or the app
 * the keyboard types in.
 */
enum class PaletteSource {
    Wallpaper,
    Preset,
    App,
}

/** The keyboard's own colors: the Material You palette of [style] built from [seed], an ARGB color. */
data class Palette(val seed: Int, val style: PaletteStyle, val source: PaletteSource) {
    /** The palette as the settings keep it, read back by [parse]. */
    fun encode(): String = "${source.name}$SEPARATOR${style.name}$SEPARATOR${Integer.toHexString(seed)}"

    companion object {
        private const val SEPARATOR = ':'
        private const val HEX = 16

        /** The source, the style and the seed. */
        private const val PARTS = 3

        /** The palette [encode] wrote, or null for text it did not write, as from a later version of the app. */
        fun parse(text: String): Palette? {
            val parts = text.split(SEPARATOR).takeIf { it.size == PARTS }.orEmpty()
            val source = PaletteSource.entries.firstOrNull { it.name == parts.getOrNull(0) }
            val style = PaletteStyle.entries.firstOrNull { it.name == parts.getOrNull(1) }
            val seed = parts.getOrNull(2)?.toLongOrNull(HEX)?.toInt()
            return if (source != null && style != null && seed != null) {
                Palette(seed, style, source)
            } else {
                null
            }
        }
    }
}

/** At most this many colors of a wallpaper are offered, as Android's picker does. */
private const val WALLPAPER_SEEDS = 4

/** The styles Android's picker offers for each color of the wallpaper, in its order. */
private val wallpaperStyles =
    listOf(PaletteStyle.TonalSpot, PaletteStyle.Neutral, PaletteStyle.Vibrant, PaletteStyle.Expressive)

/** The palettes of the wallpaper's [seeds], as Android's picker lists them: each color in each style. */
fun wallpaperPalettes(seeds: List<Int>): List<Palette> = seeds.take(WALLPAPER_SEEDS).flatMap { seed ->
    wallpaperStyles.map { Palette(seed, it, PaletteSource.Wallpaper) }
}

// The colors a Pixel's picker offers besides the wallpaper's, in its order. Each is the calm palette of a hue; these
// are its swatches on a dark screen, which give back the same palettes as seeds.
private const val BLUE = 0xFFB6C6ED
private const val PINK = 0xFFF0B7CE
private const val CORAL = 0xFFF9B6AD
private const val PEACH = 0xFFF6B997
private const val GOLD = 0xFFE1C387
private const val OLIVE = 0xFFC6CB91
private const val GREEN = 0xFFABD0AA
private const val TEAL = 0xFF9CD1C6
private const val SKY_BLUE = 0xFF9FCEDE
private const val PERIWINKLE = 0xFFB8C6EE
private const val LAVENDER = 0xFFC5C2EE
private const val LILAC = 0xFFD2BEEA

/** Monochrome takes no hue from its seed; AOSP gives it this one. */
private const val MONOCHROME = 0xFFFFFF00

/** The palettes not from the wallpaper, as a Pixel's picker lists them: monochrome, then calm ones of each hue. */
val presetPalettes: List<Palette> =
    listOf(Palette(MONOCHROME.toInt(), PaletteStyle.Monochrome, PaletteSource.Preset)) +
        listOf(BLUE, PINK, CORAL, PEACH, GOLD, OLIVE, GREEN, TEAL, SKY_BLUE, PERIWINKLE, LAVENDER, LILAC)
            .map { Palette(it.toInt(), PaletteStyle.TonalSpot, PaletteSource.Preset) }

/**
 * Whether [chosen] must give way to the system's colors: a palette of an earlier wallpaper whose color the current
 * one, of [seeds], lacks. Android resets its own choice the same way. While the wallpaper's colors are unknown, it
 * stays.
 */
fun outlivedWallpaper(chosen: Palette?, seeds: List<Int>?): Boolean =
    chosen?.source == PaletteSource.Wallpaper && seeds != null && chosen.seed !in seeds

/**
 * The palette the keyboard takes when the user turns the system's colors off: the [system]'s own, if one of the
 * offered matches it, so the colors stay as they were; else the wallpaper's first, if known.
 */
fun paletteAfterSystem(seeds: List<Int>?, system: Palette?): Palette =
    system ?: seeds?.let(::wallpaperPalettes)?.firstOrNull() ?: presetPalettes.first()
