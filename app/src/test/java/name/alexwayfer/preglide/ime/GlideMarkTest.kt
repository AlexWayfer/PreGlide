package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GlideMarkTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val showing = KeyboardShowing()

    @Test
    fun theMarkShowsAsMuchOfItsWidthAsIsDrawn() {
        assertEquals(0f, revealedWidth(60f, 0f), 0f)
        assertEquals(15f, revealedWidth(60f, 0.25f), 0f)
        assertEquals(60f, revealedWidth(60f, 1f), 0f)
    }

    @Test
    fun theShownPartStaysWithinTheMark() {
        assertEquals(0f, revealedWidth(60f, -0.1f), 0f)
        assertEquals(60f, revealedWidth(60f, 1.1f), 0f)
    }

    @Test
    fun typingStopsTheDrawInTillTheKeyboardShowsAgain() {
        showing.shown()
        assertEquals(1, showing.shows)
        assertTrue(showing.drawsIn)

        showing.typed()
        assertFalse(showing.drawsIn)

        showing.shown()
        assertEquals(2, showing.shows)
        assertTrue(showing.drawsIn)
    }

    @Test
    fun theMarkDrawsInAsTheKeyboardShows() {
        showing.shown()
        val drawn = drawnIn()

        compose.mainClock.advanceTimeBy(MARK_DRAW_MILLIS / 2L)
        assertTrue("${drawn()}", drawn() > 0f && drawn() < 1f)
        compose.mainClock.advanceTimeBy(MARK_DRAW_MILLIS.toLong())
        assertEquals(1f, drawn(), 0f)
    }

    @Test
    fun theMarkShowsWholeOnceTheUserTyped() {
        showing.shown()
        showing.typed()

        val drawn = drawnIn()

        assertEquals(1f, drawn(), 0f)
    }

    @Test
    fun theMarkDrawsInAnewEachTimeTheKeyboardShowsAgain() {
        showing.shown()
        val drawn = drawnIn()
        compose.mainClock.advanceTimeBy(MARK_DRAW_MILLIS * 2L)
        showing.typed()

        showing.shown()
        // A write outside a composition reaches it once applied, which Android's frames do on their own.
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(MARK_DRAW_MILLIS / 2L)

        assertTrue("${drawn()}", drawn() < 1f)
        compose.mainClock.advanceTimeBy(MARK_DRAW_MILLIS.toLong())
        assertEquals(1f, drawn(), 0f)
    }

    /** Composes the mark's draw-in on a virtual clock and gives a way to read how much of it is drawn. */
    private fun drawnIn(): () -> Float {
        compose.mainClock.autoAdvance = false
        var drawn = { 0f }
        compose.setContent {
            val state = rememberDrawnIn(showing)
            drawn = { state.value }
        }
        compose.mainClock.advanceTimeByFrame()
        return { drawn() }
    }
}
