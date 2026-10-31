package name.alexwayfer.preglide.ui.theme

import com.materialkolor.hct.Hct

/** Below this chroma an app's color is a gray, a white or a black: bars of no color of the app's own. */
private const val MIN_APP_CHROMA = 16.0

/**
 * The primary colors an app's theme keeps until the app sets its own: Android Studio's templates of 2015 and 2019,
 * then Material Components' light and dark ones, which its templates took from 2020, then Material 3's.
 */
private val libraryDefaults = listOf(
    0xFF3F51B5,
    0xFF008577,
    0xFF6200EE,
    0xFFBB86FC,
    0xFF6750A4,
    0xFFD0BCFF,
).map(Long::toInt).toSet()

/**
 * The color the keyboard takes from an app whose theme's primary color is [primary], or null for none: a gray tells
 * of no color of the app's own, and so does a color an app keeps until it sets its own, one of the libraries' or of
 * [platformDefaults], the primary colors of Android's own themes.
 */
fun appSeed(primary: Int?, platformDefaults: Collection<Int>): Int? = primary?.takeIf { color ->
    color !in libraryDefaults && color !in platformDefaults && Hct.fromInt(color).chroma >= MIN_APP_CHROMA
}

/** The palette of an app's [seed], kept near the app's own color: see [appSeed]. */
fun appPalette(seed: Int): Palette = Palette(seed, PaletteStyle.Content, PaletteSource.App)

/**
 * The palette the keyboard shows: the one of the app it types in, from its [appSeed], when there is one; else the
 * user's [chosen] one, null for the system's colors.
 */
fun shownPalette(appSeed: Int?, chosen: Palette?): Palette? = appSeed?.let(::appPalette) ?: chosen
