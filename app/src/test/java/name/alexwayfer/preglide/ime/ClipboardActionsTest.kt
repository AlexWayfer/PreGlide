package name.alexwayfer.preglide.ime

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.inputmethod.InputConnectionWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import name.alexwayfer.preglide.testDispatchers
import org.junit.After
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
import java.time.Duration
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ClipboardActionsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val app = RuntimeEnvironment.getApplication()
    private val clipboard = app.getSystemService(ClipboardManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val keyboard = KeyboardState()
    private val field = Field()
    private var pastes = 0
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val history by lazy {
        ClipHistory(
            keyboard,
            HistoryFiles(File(folder.root, "clipboard"), ClipCipher { key }, app.contentResolver),
            // Robolectric stamps a copy by the time since boot.
            KeyboardWork(scope, testDispatchers.now, clock = SystemClock::elapsedRealtime),
            previewSize = 96,
        )
    }
    private val watch by lazy {
        ClipboardWatch(
            keyboard,
            clipboard,
            app.contentResolver,
            KeyboardWork(scope, testDispatchers.now, clock = SystemClock::elapsedRealtime),
            Size(48, 48),
            onCopy = history::saw,
        )
    }
    private val paste by lazy {
        ClipPaste(
            app.contentResolver,
            File(folder.root, "clips"),
            shared = { Uri.parse("content://shared/${it.name}") },
            connection = { field },
            KeyboardWork(scope, testDispatchers.now),
        )
    }
    private val actions by lazy { ClipboardActions(keyboard, watch, history, paste, beforePaste = { pastes++ }) }

    @After
    fun stop() {
        watch.stop()
        scope.cancel()
    }

    @Test
    fun thePanelOpensInPlaceOfTheKeysAndCloses() {
        keyboard.removing = "привет"

        actions.open()
        assertTrue(keyboard.clipboardOpen)
        assertNull(keyboard.removing)

        keyboard.clearing = true
        keyboard.removingClip = SavedClip.Text("раз", copiedAt = 0)
        actions.close()
        assertFalse(keyboard.clipboardOpen)
        assertFalse(keyboard.clearing)
        assertNull(keyboard.removingClip)
    }

    @Test
    fun theChipPastesAndLeaves() {
        copy("https://example.com")

        actions.paste(keyboard.clip!!.clip)

        assertEquals("https://example.com", field.text)
        assertEquals(1, pastes)
        assertNull(keyboard.clip)
    }

    @Test
    fun aCopyFromThePanelPastesAndTheKeysComeBack() {
        copy("раз")
        actions.open()
        settle()

        actions.paste(keyboard.history.single().clip)

        assertEquals("раз", field.text)
        assertEquals(1, pastes)
        assertFalse(keyboard.clipboardOpen)
        // The chip offered the same copy: it leaves too.
        assertNull(keyboard.clip)
    }

    @Test
    fun theHistoryLosesWhatIsRemovedAndCleared() {
        copy("раз")
        settle()
        keyboard.removingClip = keyboard.history.single().clip

        actions.remove(keyboard.history.single().clip)
        settle()
        assertEquals(emptyList<HistoryItem>(), keyboard.history)
        assertNull(keyboard.removingClip)

        copy("два")
        settle()
        assertEquals(1, keyboard.history.size)
        keyboard.clearing = true
        actions.clear()
        settle()
        assertEquals(emptyList<HistoryItem>(), keyboard.history)
        assertFalse(keyboard.clearing)
    }

    @Test
    fun aScreenshotIsKeptAndOfferedAsIfCopied() {
        val file = folder.newFile("Screenshot.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val shot = Clip.Image(Uri.fromFile(file), "image/png", SystemClock.elapsedRealtime(), screenshot = true)

        actions.screenshotTaken(shot)
        settle()

        assertEquals(shot, keyboard.clip?.clip)
        assertEquals(shot.copiedAt, keyboard.history.single().clip.copiedAt)
    }

    @Test
    fun backClosesThePanelRatherThanTheKeyboard() {
        assertFalse(actions.backDown())
        assertFalse(actions.backUp())

        actions.open()
        assertTrue(actions.backDown())
        assertTrue(keyboard.clipboardOpen)
        assertTrue(actions.backUp())
        assertFalse(keyboard.clipboardOpen)
    }

    private fun copy(text: String) {
        // Each copy a moment after the one before, as the history tells them apart by their time.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        clipboard.setPrimaryClip(ClipData.newPlainText("", text))
        watch.check()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** A field that keeps the text that goes into it. */
    private class Field : InputConnectionWrapper(null, false) {
        var text = ""

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            this.text += text
            return true
        }
    }
}
