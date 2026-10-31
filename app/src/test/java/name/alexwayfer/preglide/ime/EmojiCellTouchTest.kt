package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.emoji.Emoji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CELL = "cell"

/** How long a finger stays down for a long press, as the cells wait for it. */
private const val LONG_PRESS = 350L

/** Where the cell stands in the window: far enough from both edges for the popup of six tones to open over it. */
private const val CELL_LEFT = 500f
private const val WINDOW_WIDTH = 1200

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EmojiCellTouchTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /** What a touch typed: the cell and the tone chosen, if any. */
    private val typed = mutableListOf<Pair<EmojiCell, Int?>>()
    private val touch = KeyTouch()

    @Before
    fun stopTheClock() {
        compose.mainClock.autoAdvance = false
    }

    /** Shows [cell]. */
    private fun show(cell: EmojiCell) {
        compose.setContent {
            val haptics = rememberKeyHaptics()
            val bounds = with(LocalDensity.current) { Rect(CELL_LEFT, 0f, CELL_LEFT + 40.dp.toPx(), 40.dp.toPx()) }
            val parts = TouchParts(touch, { KeyGeometry(bounds, WINDOW_WIDTH, popupPadding = 0) }, haptics)
            val on = EmojiCellTouch(parts) { c, t -> typed += c to t }
            Box(
                Modifier
                    .size(40.dp)
                    .testTag(CELL)
                    .pointerInput(Unit) { awaitEachGesture { handleEmojiGesture(cell, on) } },
            )
        }
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        compose.onNodeWithTag(CELL).performTouchInput(block)
    }

    private fun holdForLongPress() {
        touch { down(center) }
        compose.mainClock.advanceTimeBy(LONG_PRESS, ignoreFrameDuration = true)
    }

    private val waving = EmojiCell("👋", wave)
    private val smiling = EmojiCell("😀", Emoji("😀"))

    @Test
    fun aTapTypesTheCellAsItShows() {
        show(waving)
        touch { down(center) }
        assertTrue(touch.pressed)
        touch { up() }

        assertEquals(listOf(waving to null), typed)
        assertFalse(touch.pressed)
    }

    @Test
    fun aLongPressOffersTheTonesWithTheOneTheCellShowsOverIt() {
        show(EmojiCell("👋🏽", wave))
        holdForLongPress()

        assertEquals(0, touch.popup?.columns?.get(3))
        assertEquals(3, touch.selected)
        assertEquals(emptyList<Pair<EmojiCell, Int?>>(), typed)
    }

    @Test
    fun theToneTheFingerLiftsOnIsTyped() {
        show(waving)
        holdForLongPress()
        // Two cells to the right of the base, which is over the cell: the second tone.
        touch { moveBy(Offset(width * 2f, 0f)) }
        assertEquals(2, touch.selected)
        touch { up() }

        assertEquals(listOf(waving to 2), typed)
        assertNull(touch.popup)
    }

    @Test
    fun aLongPressOnAnEmojiWithoutTonesTypesItAsItLifts() {
        show(smiling)
        holdForLongPress()
        assertNull(touch.popup)
        assertEquals(emptyList<Pair<EmojiCell, Int?>>(), typed)

        touch { up() }

        assertEquals(listOf(smiling to null), typed)
    }

    @Test
    fun aCanceledTouchTypesNothing() {
        show(waving)
        touch {
            down(center)
            cancel()
        }

        assertEquals(emptyList<Pair<EmojiCell, Int?>>(), typed)
        assertFalse(touch.pressed)
    }
}
