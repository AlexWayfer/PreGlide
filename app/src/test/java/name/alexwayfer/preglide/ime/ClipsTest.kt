package name.alexwayfer.preglide.ime

import android.app.Application
import android.net.Uri
import name.alexwayfer.preglide.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ClipsTest {
    private val image = Clip.Image(Uri.parse("content://media/external/images/media/7"), "image/png", copiedAt = 0)

    @Test
    fun aClipIsFreshForAFewMinutesAfterTheCopy() {
        assertTrue(isFresh(copiedAt = 1_000, now = 1_000))
        assertTrue(isFresh(copiedAt = 1_000, now = 999 + CLIP_FRESH_MILLIS))
        // Stale as the time left runs out, so the strip lets it go then.
        assertFalse(isFresh(copiedAt = 1_000, now = 1_000 + CLIP_FRESH_MILLIS))
        // Copied "later" than now, as after the phone's clock was set back: its age is unknown.
        assertFalse(isFresh(copiedAt = 1_000, now = 999))
    }

    @Test
    fun theTimeLeftRunsDownToZero() {
        assertEquals(CLIP_FRESH_MILLIS - 500, freshLeft(copiedAt = 1_000, now = 1_500))
        assertEquals(0L, freshLeft(copiedAt = 1_000, now = 2_000 + CLIP_FRESH_MILLIS))
    }

    @Test
    fun theChipShowsATextOnOneLine() {
        assertEquals("Konbini Cleanup: * те же ассеты", chipText("  Konbini Cleanup:\n* те же\tассеты \n"))
        assertEquals(200, chipText("а".repeat(10_000)).length)
    }

    @Test
    fun aSensitiveTextDoesNotShowItself() {
        assertEquals(HIDDEN_TEXT, chipTextOf(Clip.Text("hunter2", sensitive = true, copiedAt = 0)))
        assertEquals("hunter2", chipTextOf(Clip.Text("hunter2", sensitive = false, copiedAt = 0)))
        assertNull(chipTextOf(image))
    }

    @Test
    fun aFreshScreenshotIsCalledSoAndACopiedOneAnImage() {
        assertEquals(R.string.clip_screenshot, imageLabel(image.copy(screenshot = true)))
        assertEquals(R.string.clip_image, imageLabel(image))
    }

    @Test
    fun anImageGoesOnlyIntoAFieldThatTakesItsType() {
        assertTrue(takesClip(image, listOf("image/*")))
        assertTrue(takesClip(image, listOf("image/gif", "image/png")))
        assertFalse(takesClip(image, listOf("image/gif")))
        assertFalse(takesClip(image, emptyList()))
        assertTrue(takesClip(Clip.Text("текст", sensitive = false, copiedAt = 0), emptyList()))
    }

    @Test
    fun theStripOffersAnImageOnlyWhereTheFieldTakesIt() {
        val state = KeyboardState()
        state.clip = OfferedClip(image)
        assertEquals(StripContent.Empty, stripContent(state))

        state.fieldContentTypes = listOf("image/*")
        assertEquals(StripContent.Clip(OfferedClip(image)), stripContent(state))
    }

    @Test
    fun aClipGoesBeforeTheWordsToBeginAFieldWithButNotBeforeOtherWords() {
        val clip = OfferedClip(Clip.Text("текст", sensitive = false, copiedAt = 0))
        val state = KeyboardState()
        state.clip = clip
        state.suggestions = listOf("Привет")
        assertEquals(StripContent.Suggestions, stripContent(state))

        state.fieldStart = true
        assertEquals(StripContent.Clip(clip), stripContent(state))
    }
}
