package name.alexwayfer.preglide.ui.theme

/**
 * The colors Android found in a wallpaper: its [main] ones, and [population], every color it counted with how many
 * pixels have it. Android's picker builds its options from the population; a wallpaper that tells only its main
 * colors, as a live one, has none.
 */
data class WallpaperColorData(val main: List<Int>, val population: Map<Int, Int>)

/**
 * Reads [WallpaperColorData] as android.app.WallpaperColors writes itself to a Parcel: the main colors, then the
 * colors with their populations. Only the main colors have a public getter. Null when a count is more than the
 * [available] numbers left: the format is not the one this reads.
 */
fun readWallpaperColors(available: Int, readInt: () -> Int): WallpaperColorData? {
    var left = available
    fun count(perItem: Int): Int? = readInt().also { left-- }.takeIf { it >= 0 && it.toLong() * perItem <= left }

    val main = count(1)?.let { size -> List(size) { readInt() }.also { left -= size } }
    val population = main?.let { count(2) }?.let { size -> buildMap { repeat(size) { put(readInt(), readInt()) } } }
    return if (main != null && population != null) WallpaperColorData(main, population) else null
}
