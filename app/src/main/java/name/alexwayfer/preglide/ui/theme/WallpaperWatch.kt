package name.alexwayfer.preglide.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The seed colors of the home screen's wallpaper, as Android's picker offers them ([wallpaperSeeds]): null until
 * read, and while Android does not know the wallpaper's colors yet. Follows each new wallpaper.
 */
class WallpaperWatch(context: Context, scope: CoroutineScope, io: CoroutineDispatcher) {
    private val manager = WallpaperManager.getInstance(context)

    // One read at a time, in order: an older wallpaper's colors must not land after a newer one's.
    private val reads = io.limitedParallelism(1)
    private val current = MutableStateFlow<List<Int>?>(null)
    val seeds: StateFlow<List<Int>?> = current.asStateFlow()

    init {
        // Android asks to read them off the main thread: it may count them first.
        scope.launch(reads) { current.value = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let(::seedsOf) }
        manager.addOnColorsChangedListener(
            { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    scope.launch(reads) { current.value = colors?.let(::seedsOf) }
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    private fun seedsOf(colors: WallpaperColors): List<Int> = wallpaperSeeds(colorData(colors))

    /**
     * Every color Android counted in the wallpaper, read from the Parcel the colors write themselves to: only the
     * main ones have a public getter. Where the Parcel is not as expected, the main ones alone, as a live wallpaper
     * gives.
     */
    private fun colorData(colors: WallpaperColors): WallpaperColorData {
        val parcel = Parcel.obtain()
        try {
            colors.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            readWallpaperColors(parcel.dataAvail() / Int.SIZE_BYTES, parcel::readInt)?.let { return it }
        } finally {
            parcel.recycle()
        }
        val main = listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor).map { it.toArgb() }
        return WallpaperColorData(main, emptyMap())
    }
}
