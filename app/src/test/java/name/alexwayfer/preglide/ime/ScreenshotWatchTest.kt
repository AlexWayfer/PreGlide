package name.alexwayfer.preglide.ime

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import name.alexwayfer.preglide.testDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScreenshotWatchTest {
    private val app = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val now = 1_800_000_000_000L
    private val taken = mutableListOf<Clip.Image>()
    private val watch = ScreenshotWatch(app.contentResolver, scope, testDispatchers.now, { taken += it }, { now })

    @Before
    fun startTheGallery() {
        Gallery.newest = null
        Robolectric.buildContentProvider(Gallery::class.java).create(MediaStore.AUTHORITY)
    }

    @After
    fun stop() {
        watch.watch(false)
        scope.cancel()
    }

    @Test
    fun aScreenshotTakenWhileWatchingIsOfferedAsIfCopied() {
        watch.watch(true)
        settle()
        assertEquals(emptyList<Clip.Image>(), taken)

        add(GalleryImage(7, "Pictures/Screenshots/", now / 1000, "image/png"))

        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 7)
        assertEquals(listOf(Clip.Image(uri, "image/png", now / 1000 * 1000, screenshot = true)), taken)
    }

    @Test
    fun aScreenshotTakenAMomentBeforeIsOfferedAsTheWatchStarts() {
        Gallery.newest = GalleryImage(7, "DCIM/Screenshots/", now / 1000 - 10, null)

        watch.watch(true)
        settle()

        assertEquals("image/png", taken.single().mimeType)
    }

    @Test
    fun eachScreenshotIsOfferedOnce() {
        watch.watch(true)
        add(GalleryImage(7, "Pictures/Screenshots/", now / 1000, "image/png"))
        add(GalleryImage(7, "Pictures/Screenshots/", now / 1000, "image/png"))

        assertEquals(1, taken.size)
    }

    @Test
    fun photosAndOldScreenshotsAreNotOffered() {
        watch.watch(true)
        add(GalleryImage(7, "DCIM/Camera/", now / 1000, "image/jpeg"))
        add(GalleryImage(8, "Pictures/Screenshots/", (now - CLIP_FRESH_MILLIS) / 1000 - 1, "image/png"))

        assertEquals(emptyList<Clip.Image>(), taken)
    }

    @Test
    fun nothingIsOfferedOnceTheWatchStops() {
        watch.watch(true)
        watch.watch(false)
        add(GalleryImage(7, "Pictures/Screenshots/", now / 1000, "image/png"))

        assertEquals(emptyList<Clip.Image>(), taken)
    }

    @Test
    fun aScreenshotIsToldByItsFolderAndAge() {
        assertTrue(isFreshScreenshot(GalleryImage(1, "Pictures/Screenshots/", now / 1000, null), now))
        assertTrue(isFreshScreenshot(GalleryImage(1, "DCIM/screenshots/", now / 1000, null), now))
        assertFalse(isFreshScreenshot(GalleryImage(1, null, now / 1000, null), now))
        assertFalse(isFreshScreenshot(GalleryImage(1, "Pictures/Screenshots/", 0, null), now))
    }

    private fun add(image: GalleryImage) {
        Gallery.newest = image
        app.contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
        settle()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The gallery, which tells its newest image alone. */
    class Gallery : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(projection).apply {
            newest?.let { addRow(arrayOf<Any?>(it.id, it.folder, it.addedSeconds, it.mimeType)) }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) =
            0

        companion object {
            var newest: GalleryImage? = null
        }
    }
}
