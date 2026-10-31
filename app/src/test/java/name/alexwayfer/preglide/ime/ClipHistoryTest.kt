package name.alexwayfer.preglide.ime

import android.app.Application
import android.net.Uri
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import name.alexwayfer.preglide.testDispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ClipHistoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val app = RuntimeEnvironment.getApplication()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = ClipCipher { key }
    private val scope = CoroutineScope(Dispatchers.Main)
    private val keyboard = KeyboardState()
    private val files by lazy { File(folder.root, "clipboard") }
    private var now = 10 * HISTORY_KEEP_MILLIS
    private var unlocked = true
    private val history by lazy { newHistory(keyboard) }

    @After
    fun stop() {
        scope.cancel()
    }

    @Test
    fun aCopyGoesFirst() {
        history.saw(text("раз", now - 1))
        history.saw(text("два", now))
        settle()

        assertEquals(listOf("два", "раз"), shownTexts(keyboard))
    }

    @Test
    fun aPasswordIsNotKept() {
        history.saw(Clip.Text("hunter2", sensitive = true, copiedAt = now))
        settle()

        assertEquals(emptyList<String>(), shownTexts(keyboard))
    }

    @Test
    fun theSameCopyReadAgainIsKeptOnce() {
        history.saw(text("раз", now))
        history.saw(text("раз", now))
        settle()

        assertEquals(listOf("раз"), shownTexts(keyboard))
    }

    @Test
    fun aRemovedCopyDoesNotComeBackFromTheClipboard() {
        val copy = text("раз", now)
        history.saw(copy)
        settle()

        history.remove(keyboard.history.single().clip)
        settle()
        // A field starting reads the clipboard anew, where the copy still is; so does a keyboard started anew.
        history.saw(copy)
        newHistory(KeyboardState()).saw(copy)
        settle()

        assertEquals(emptyList<String>(), shownTexts(keyboard))
    }

    @Test
    fun theHistoryOutlivesTheKeyboardEncrypted() {
        history.saw(text("Konbini Cleanup", now))
        settle()

        val next = KeyboardState()
        newHistory(next).refresh()
        settle()

        assertEquals(listOf("Konbini Cleanup"), shownTexts(next))
        assertFalse(String(File(files, "history").readBytes(), Charsets.ISO_8859_1).contains("Konbini"))
    }

    @Test
    fun copiesGrowOldAndLeave() {
        history.saw(text("раз", now))
        settle()

        now += HISTORY_KEEP_MILLIS
        history.refresh()
        settle()

        assertEquals(emptyList<String>(), shownTexts(keyboard))
    }

    @Test
    fun clearingEmptiesIt() {
        history.saw(text("раз", now - 1))
        history.saw(text("два", now))
        history.clear()
        settle()

        assertEquals(emptyList<String>(), shownTexts(keyboard))
    }

    @Test
    fun anImageIsKeptAsAnEncryptedCopyOfItsOwn() {
        val source = folder.newFile("shot.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        history.saw(Clip.Image(Uri.fromFile(source), "image/png", copiedAt = now))
        settle()
        // The app that copied it takes access back.
        source.delete()

        val saved = keyboard.history.single().clip as SavedClip.Image
        assertEquals(SavedClip.Image(savedImageName(now), "image/png", now), saved)
        assertArrayEquals(byteArrayOf(1, 2, 3), history.open(saved).use { it.readBytes() })
        assertFalse(File(files, saved.file).readBytes().contentEquals(byteArrayOf(1, 2, 3)))

        history.remove(saved)
        settle()
        assertFalse(File(files, saved.file).exists())
    }

    @Test
    fun anImageThatCannotBeReadIsNotKept() {
        history.saw(Clip.Image(Uri.fromFile(File(folder.root, "gone.png")), "image/png", copiedAt = now))
        settle()

        assertEquals(emptyList<HistoryItem>(), keyboard.history)
    }

    @Test
    fun anUnreadableHistoryStartsAnew() {
        files.mkdirs()
        File(files, "history").writeBytes(byteArrayOf(12, 1, 2, 3))
        File(files, "image-1").writeBytes(byteArrayOf(1))

        history.saw(text("раз", now))
        settle()

        assertEquals(listOf("раз"), shownTexts(keyboard))
        assertFalse(File(files, "image-1").exists())
    }

    @Test
    fun imagesTheHistoryDoesNotListAreDropped() {
        history.saw(text("раз", now))
        settle()
        File(files, "image-1").writeBytes(byteArrayOf(1))

        newHistory(KeyboardState()).refresh()
        settle()

        assertFalse(File(files, "image-1").exists())
        assertTrue(File(files, "history").exists())
    }

    @Test
    fun nothingIsKeptBeforeTheFirstUnlock() {
        unlocked = false
        history.saw(text("раз", now))
        history.refresh()
        settle()

        assertEquals(emptyList<HistoryItem>(), keyboard.history)
        assertFalse(files.exists())
    }

    private fun newHistory(keyboard: KeyboardState) = ClipHistory(
        keyboard,
        HistoryFiles(files, cipher, app.contentResolver),
        KeyboardWork(scope, testDispatchers.now, available = { unlocked }, clock = { now }),
        previewSize = 96,
    )

    private fun text(text: String, copiedAt: Long) = Clip.Text(text, sensitive = false, copiedAt = copiedAt)

    private fun shownTexts(keyboard: KeyboardState): List<String> =
        keyboard.history.map { (it.clip as SavedClip.Text).text }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
