package name.alexwayfer.preglide.ime

import android.app.Application
import android.content.pm.PackageManager.GET_META_DATA
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.testDispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ClipPasteTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val app = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val copies by lazy { File(folder.root, "clips") }
    private var field: Field? = Field()
    private var now = 1_000_000L
    private val paste by lazy {
        ClipPaste(
            app.contentResolver,
            copies,
            shared = { Uri.parse("content://shared/${it.name}") },
            connection = { field },
            KeyboardWork(scope, testDispatchers.now, clock = { now }),
        )
    }

    @After
    fun stop() {
        scope.cancel()
    }

    @Test
    fun aTextGoesInAsTyped() {
        paste.paste(Clip.Text("https://example.com", sensitive = false, copiedAt = 0))

        assertEquals("https://example.com", field!!.text)
    }

    @Test
    fun anImageGoesInAsAContentCopyTheKeyboardShares() {
        paste.paste(Clip.Image(image(), "image/png", copiedAt = 0))
        settle()

        val content = field!!.content!!
        assertEquals(Uri.parse("content://shared/clip-1000000.png"), content.contentUri)
        assertEquals("image/png", content.description.getMimeType(0))
        assertEquals(InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, field!!.contentFlags)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(copies, "clip-1000000.png").readBytes())
    }

    @Test
    fun copiesAreKeptForADay() {
        paste.paste(Clip.Image(image(), "image/png", copiedAt = 0))
        settle()
        val first = File(copies, "clip-1000000.png")
        first.setLastModified(now)

        now += PASTED_IMAGE_KEEP_MILLIS
        paste.paste(Clip.Image(image(), "image/png", copiedAt = 1))
        settle()
        assertTrue(first.exists())

        now += 1
        paste.paste(Clip.Image(image(), "image/png", copiedAt = 2))
        settle()
        assertFalse(first.exists())
    }

    @Test
    fun aCopyFromTheHistoryGoesInAsTheClipboardsWould() {
        paste.paste(SavedClip.Text("раз", copiedAt = 0)) { error("A text is not opened") }
        assertEquals("раз", field!!.text)

        paste.paste(SavedClip.Image("image-0", "image/png", copiedAt = 0)) { byteArrayOf(4, 5).inputStream() }
        settle()
        assertArrayEquals(byteArrayOf(4, 5), File(copies, "clip-1000000.png").readBytes())
        assertEquals("image/png", field!!.content!!.description.getMimeType(0))
    }

    @Test
    fun anImageThatCannotBeReadDoesNotGoIn() {
        paste.paste(Clip.Image(Uri.fromFile(File(folder.root, "gone.png")), "image/png", copiedAt = 0))
        settle()

        assertNull(field!!.content)
        assertEquals(emptyList<String>(), copies.list().orEmpty().toList())
    }

    @Test
    fun anImageDoesNotGoIntoAnotherFieldOpenedWhileItWasCopied() {
        val first = field!!
        val pasteOff = ClipPaste(
            app.contentResolver,
            copies,
            shared = { Uri.parse("content://shared/${it.name}") },
            connection = { field },
            // The copy waits for the main thread, by which time the user moved on.
            KeyboardWork(scope, Dispatchers.Main),
        )
        pasteOff.paste(Clip.Image(image(), "image/png", copiedAt = 0))
        field = Field()
        settle()

        assertNull(first.content)
        assertNull(field!!.content)
    }

    @Test
    fun theKeyboardSharesItsCopiesThroughItsOwnProvider() {
        // FileProvider.getUriForFile, static, finds the shared folders in the manifest's meta-data alone. On Windows
        // it cannot be run here: it matches the folders by paths with slashes.
        val provider = app.packageManager.resolveContentProvider("${app.packageName}.clips", GET_META_DATA)!!

        assertEquals(ClipFileProvider::class.java.name, provider.name)
        assertEquals(R.xml.clip_paths, provider.metaData.getInt("android.support.FILE_PROVIDER_PATHS"))
    }

    @Test
    fun withNoFieldNothingIsPasted() {
        field = null

        paste.paste(Clip.Text("текст", sensitive = false, copiedAt = 0))
    }

    private fun image(): Uri {
        val file = folder.newFile()
        file.writeBytes(byteArrayOf(1, 2, 3))
        return Uri.fromFile(file)
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** A field that keeps what goes into it. */
    private class Field : InputConnectionWrapper(null, false) {
        var text = ""
        var content: InputContentInfo? = null
        var contentFlags = 0

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            this.text += text
            return true
        }

        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean {
            content = inputContentInfo
            contentFlags = flags
            return true
        }
    }
}
