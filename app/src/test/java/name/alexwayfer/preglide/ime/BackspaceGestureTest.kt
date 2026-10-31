package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val KEY = "backspace"

@RunWith(RobolectricTestRunner::class)
// The plain application: the keyboard's own loads its models and settings, which a touch of a key does not need.
@Config(application = Application::class)
class BackspaceGestureTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /** Writes down what a touch of backspace does: chars deleted, and swipes' starts, selections and ends. */
    private class Field : Backspace {
        var deleted = 0
        val swipe = mutableListOf<String>()

        override fun canDelete() = true

        override fun startSwipe(): Int {
            swipe += "start"
            return 3
        }

        override fun select(words: Int) {
            swipe += "select $words"
        }

        override fun endSwipe(lifted: Boolean) {
            swipe += if (lifted) "lift" else "cancel"
        }
    }

    private val field = Field()

    @Before
    fun showBackspace() {
        // The clock moves only when a test moves it, so a finger stays down exactly as long as the test says.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val haptics = rememberKeyHaptics()
            Box(
                Modifier
                    .size(60.dp, 48.dp)
                    .testTag(KEY)
                    .pointerInput(Unit) {
                        awaitEachGesture { handleBackspace(awaitFirstDown(), haptics,{ field.deleted++ }, field) }
                    },
            )
        }
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        compose.onNodeWithTag(KEY).performTouchInput(block)
    }

    private fun hold(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds, ignoreFrameDuration = true)
    }

    /** Moves the finger [left] to the left, or to the right when negative. */
    private fun TouchInjectionScope.moveLeft(left: Dp) {
        moveBy(Offset(-left.toPx(), 0f))
    }

    @Test
    fun aTapDeletesACharAsTheFingerLifts() {
        touch { down(center) }
        assertEquals(0, field.deleted)

        touch { up() }
        assertEquals(1, field.deleted)
    }

    @Test
    fun aHeldKeyDeletesOnAndOnOnceItRepeats() {
        touch { down(center) }
        hold(399)
        assertEquals(0, field.deleted)

        hold(1)
        assertEquals(1, field.deleted)
        hold(100)
        assertEquals(3, field.deleted)

        touch { up() }
        hold(200)
        assertEquals(3, field.deleted)
    }

    @Test
    fun aHeldKeyKeepsDeletingWhenTheFingerSlidesOff() {
        touch { down(center) }
        hold(400)
        touch { moveLeft(100.dp) }
        hold(50)

        assertEquals(2, field.deleted)
        assertEquals(emptyList<String>(), field.swipe)
    }

    @Test
    fun aSwipeLeftSelectsWordsAndErasesThemAsItLifts() {
        touch {
            down(center)
            // Half the key away: a swipe, with no word yet.
            moveLeft(31.dp)
            moveLeft(1.dp)
            moveLeft(32.dp)
            up()
        }

        assertEquals(listOf("start", "select 1", "select 2", "lift"), field.swipe)
        assertEquals(0, field.deleted)
    }

    @Test
    fun comingBackSelectsFewerWords() {
        touch {
            down(center)
            moveLeft(31.dp)
            moveLeft(33.dp)
            moveLeft((-64).dp)
            up()
        }

        assertEquals(listOf("start", "select 2", "select 0", "lift"), field.swipe)
    }

    @Test
    fun aSwipeSelectsNoMoreWordsThanThereAre() {
        touch {
            down(center)
            moveLeft(31.dp)
            moveLeft(300.dp)
            up()
        }

        assertEquals(listOf("start", "select 3", "lift"), field.swipe)
    }

    @Test
    fun aCanceledSwipeErasesNothing() {
        touch {
            down(center)
            moveLeft(31.dp)
            moveLeft(1.dp)
            cancel()
        }

        assertEquals(listOf("start", "select 1", "cancel"), field.swipe)
    }
}
