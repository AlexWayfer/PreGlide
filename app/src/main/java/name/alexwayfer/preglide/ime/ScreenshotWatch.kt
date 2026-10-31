package name.alexwayfer.preglide.ime

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** The gallery's images, all of them, where Android adds screenshots. */
private val IMAGES = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

private val COLUMNS = arrayOf(
    MediaStore.Images.Media._ID,
    MediaStore.Images.Media.RELATIVE_PATH,
    MediaStore.Images.Media.DATE_ADDED,
    MediaStore.Images.Media.MIME_TYPE,
)
private const val MILLIS_PER_SECOND = 1000L

/** The images read at a time: the newest alone, which a screenshot just taken is. */
private const val NEWEST_ONLY = 1

/** The type a screenshot is in when the gallery does not tell: PNG, as Android takes them. */
private const val SCREENSHOT_TYPE = "image/png"

/** The newest image of the gallery, as far as telling a fresh screenshot needs. */
data class GalleryImage(val id: Long, val folder: String?, val addedSeconds: Long, val mimeType: String?)

/**
 * Whether [image] is a screenshot taken a moment before [now]: in a folder of screenshots, as Android and phone makers
 * put them (`Pictures/Screenshots`, `DCIM/Screenshots`), and fresh as a clip is (see [isFresh]).
 */
fun isFreshScreenshot(image: GalleryImage, now: Long): Boolean =
    image.folder?.contains("Screenshots", ignoreCase = true) == true &&
        isFresh(image.addedSeconds * MILLIS_PER_SECOND, now)

/** [image], a fresh screenshot, as a clip to offer. */
fun screenshotClip(image: GalleryImage): Clip.Image = Clip.Image(
    ContentUris.withAppendedId(IMAGES, image.id),
    image.mimeType ?: SCREENSHOT_TYPE,
    copiedAt = image.addedSeconds * MILLIS_PER_SECOND,
    screenshot = true,
)

/**
 * Watches the gallery through [resolver] for a screenshot the user takes, and gives each one to [onScreenshot] once,
 * while it is fresh. The gallery is read in [loadContext], off the main thread; [scope] runs on the main thread.
 * It needs the leave to read all photos, which the user gives as they turn it on.
 */
class ScreenshotWatch(
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val loadContext: CoroutineContext,
    private val onScreenshot: (Clip.Image) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            look()
        }
    }
    private var watching = false

    /** The image given last, so the gallery's many changes give it once. */
    private var given = Long.MIN_VALUE

    /** Watches the gallery while [on]; turning it on looks at once, for a screenshot taken a moment ago. */
    fun watch(on: Boolean) {
        if (on == watching) return
        watching = on
        if (on) {
            resolver.registerContentObserver(IMAGES, true, observer)
            look()
        } else {
            resolver.unregisterContentObserver(observer)
        }
    }

    private fun look() {
        scope.launch {
            val image = withContext(loadContext) { newest() } ?: return@launch
            if (watching && image.id != given && isFreshScreenshot(image, now())) {
                given = image.id
                onScreenshot(screenshotClip(image))
            }
        }
    }

    /** The newest image in the gallery; null with none, or without the leave, as after the user took it back. */
    private fun newest(): GalleryImage? {
        val newestFirst = Bundle().apply {
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, NEWEST_ONLY)
        }
        return try {
            resolver.query(IMAGES, COLUMNS, newestFirst, null)?.use { rows ->
                rows.takeIf { it.moveToFirst() }?.let {
                    GalleryImage(
                        it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)),
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)),
                        it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)),
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)),
                    )
                }
            }
        } catch (_: SecurityException) {
            null
        }
    }
}
