package name.alexwayfer.preglide.ime

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import name.alexwayfer.preglide.testDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ClipboardWatchTest {
    private val app = RuntimeEnvironment.getApplication()
    private val clipboard = app.getSystemService(ClipboardManager::class.java)
    private val keyboard = KeyboardState()
    private val scope = CoroutineScope(Dispatchers.Main)
    // Robolectric stamps a copy by the time since boot, where Android stamps it by System.currentTimeMillis: the
    // watch is told Robolectric's clock.
    private val watch = ClipboardWatch(
        keyboard,
        clipboard,
        app.contentResolver,
        KeyboardWork(scope, testDispatchers.now, clock = SystemClock::elapsedRealtime),
        Size(48, 48),
    )

    @After
    fun stop() {
        watch.stop()
        scope.cancel()
    }

    @Test
    fun aTextCopiedIsOffered() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "https://example.com"))
        watch.start()

        assertEquals(Clip.Text("https://example.com", sensitive = false, copiedAt = copiedAt()), keyboard.clip?.clip)
    }

    @Test
    fun aCopyWhileTheKeyboardIsOpenIsOfferedAtOnce() {
        watch.start()
        assertNull(keyboard.clip)

        clipboard.setPrimaryClip(ClipData.newPlainText("", "новое"))
        settle()

        assertEquals("новое", (keyboard.clip?.clip as Clip.Text).text)
    }

    @Test
    fun aPasswordCopiedIsMarkedSensitive() {
        val clip = ClipData.newPlainText("", "hunter2")
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        watch.start()

        assertTrue((keyboard.clip?.clip as Clip.Text).sensitive)
    }

    @Test
    fun aClipPastedFromTheStripIsNotOfferedAgain() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "раз"))
        watch.start()

        watch.pasted(keyboard.clip!!.clip)
        assertNull(keyboard.clip)

        // A field starting reads the clipboard anew, where the same clip still is.
        watch.check()
        assertNull(keyboard.clip)
    }

    @Test
    fun theLatestOfACopyAndAScreenshotIsOffered() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "раз"))
        watch.start()
        val copy = keyboard.clip!!.clip
        // Taken a second after the copy.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val shot = Clip.Image(Uri.parse("content://nobody/7"), "image/png", copy.copiedAt + 1000, screenshot = true)

        watch.screenshot(shot)
        settle()
        assertEquals(shot, keyboard.clip?.clip)

        // Pasted, it leaves the copy before it to offer.
        watch.pasted(shot)
        assertEquals(copy, keyboard.clip?.clip)
    }

    @Test
    fun aClipGrowsStaleAndLeavesTheStrip() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "раз"))
        watch.start()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(CLIP_FRESH_MILLIS + 1))

        assertNull(keyboard.clip)
    }

    @Test
    fun anEmptiedClipboardOffersNothing() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "раз"))
        watch.start()

        clipboard.clearPrimaryClip()
        watch.check()

        assertNull(keyboard.clip)
    }

    @Test
    fun anImageThatCannotBeReadIsOfferedWithoutAPreview() {
        val uri = Uri.parse("content://nobody/images/7")
        clipboard.setPrimaryClip(ClipData(ClipDescription("", arrayOf("image/jpeg")), ClipData.Item(uri)))
        watch.start()
        settle()

        val offered = keyboard.clip!!
        assertEquals(Clip.Image(uri, "image/jpeg", copiedAt()), offered.clip)
        assertNull(offered.preview)
    }

    private fun copiedAt(): Long = clipboard.primaryClipDescription!!.timestamp

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
