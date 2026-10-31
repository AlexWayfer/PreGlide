package name.alexwayfer.preglide.ui.theme

import com.materialkolor.hct.Hct
import kotlin.math.abs
import kotlin.math.roundToInt

/** The blue Android builds its palette from when a wallpaper has no color to offer. */
internal const val GOOGLE_BLUE = 0xFF1B6EF3.toInt()

/** Colors grayer than this take no part, as they would turn loud once a palette raises their chroma. */
internal const val MIN_CHROMA = 5.0

private const val MAX_SEEDS = 4
private const val DEGREES = 360

/** The hues on each side of a color's that count as its own. */
private const val HUE_NEIGHBORHOOD = 15

/** A color whose hues cover less of the wallpaper than this does not lead a palette. */
private const val MIN_HUE_SHARE = 0.01

/** Seeds are at least this far apart in hue. */
private const val MIN_SEED_HUE_DISTANCE = 90

/** The chroma of a palette's primary colors: colors below it lose score, colors above gain. */
private const val TARGET_CHROMA = 48.0
private const val CHROMA_BELOW_WEIGHT = 0.1
private const val CHROMA_ABOVE_WEIGHT = 0.3
private const val SHARE_WEIGHT = 70.0

/**
 * The seed colors of a wallpaper, best first, as Android's picker chooses them (ColorScheme.getSeedColors in AOSP's
 * monet): colors that cover much of the wallpaper with their hue and are colorful score higher, and each seed
 * differs from the ones before it in hue. Never empty.
 */
fun wallpaperSeeds(colors: WallpaperColorData): List<Int> {
    val total = colors.population.values.sumOf { it.toDouble() }
    // A population of nothing means the colors were not counted, as for a live wallpaper: their order is the rank.
    if (total == 0.0) {
        return colors.main.distinct().filter { Hct.fromInt(it).chroma >= MIN_CHROMA }.ifEmpty { listOf(GOOGLE_BLUE) }
    }
    val hcts = colors.population.keys.associateWith(Hct::fromInt)
    val shares = hueShares(hcts, colors.population.mapValues { it.value / total })
    val hueShare = hcts.mapValues { (_, hct) -> shareAround(shares, hct.hue.roundToInt()) }
    val ranked = hcts.keys
        .filter { hcts.getValue(it).chroma >= MIN_CHROMA && hueShare.getValue(it) > MIN_HUE_SHARE }
        .sortedByDescending { score(hcts.getValue(it), hueShare.getValue(it)) }
    return spreadByHue(ranked, hcts).ifEmpty { listOf(GOOGLE_BLUE) }
}

/** How much of the wallpaper each whole hue covers, gray colors left out. */
private fun hueShares(hcts: Map<Int, Hct>, shares: Map<Int, Double>): DoubleArray {
    val byHue = DoubleArray(DEGREES)
    for ((color, share) in shares) {
        val hct = hcts.getValue(color)
        if (hct.chroma > MIN_CHROMA) byHue[hct.hue.roundToInt() % DEGREES] += share
    }
    return byHue
}

/** How much of the wallpaper the hues around [hue] cover. */
private fun shareAround(byHue: DoubleArray, hue: Int): Double =
    (hue - HUE_NEIGHBORHOOD..hue + HUE_NEIGHBORHOOD).sumOf { byHue[it.mod(DEGREES)] }

private fun score(hct: Hct, hueShare: Double): Double {
    val chromaWeight = if (hct.chroma < TARGET_CHROMA) CHROMA_BELOW_WEIGHT else CHROMA_ABOVE_WEIGHT
    return chromaWeight * (hct.chroma - TARGET_CHROMA) + SHARE_WEIGHT * hueShare
}

/**
 * The best [ranked] colors, each far in hue from the ones taken before it. Android's loop would narrow the distance
 * while it finds no color, which never happens once there is one to rank, so the widest distance is the only one.
 */
private fun spreadByHue(ranked: List<Int>, hcts: Map<Int, Hct>): List<Int> {
    val seeds = mutableListOf<Int>()
    for (color in ranked) {
        val hue = hcts.getValue(color).hue
        if (seeds.none { hueDistance(hcts.getValue(it).hue, hue) < MIN_SEED_HUE_DISTANCE }) seeds += color
        if (seeds.size >= MAX_SEEDS) break
    }
    return seeds
}

private fun hueDistance(a: Double, b: Double): Double = abs(a - b).let { if (it > DEGREES / 2) DEGREES - it else it }
