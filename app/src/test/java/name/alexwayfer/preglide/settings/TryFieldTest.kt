package name.alexwayfer.preglide.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.ime.ComposeHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TryFieldTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var shown by mutableStateOf(false)

    /** What the field asks of the keyboard, in order. */
    private val asked = mutableListOf<String>()

    private val keyboard = object : SoftwareKeyboardController {
        override fun show() {
            asked += SHOW
        }

        override fun hide() {
            asked += HIDE
        }
    }

    @Test
    fun theFieldAsksForTheKeyboardAsItComesOut() {
        show()

        shown = true
        compose.waitForIdle()

        compose.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(SHOW, asked.last())
    }

    @Test
    fun theKeyboardGoesAsTheFieldStartsToLeave() {
        show()
        shown = true
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        shown = false
        intoTheMotion()

        assertEquals(HIDE, asked.last())
    }

    @Test
    fun theFieldAsksForTheKeyboardAgainWhenItComesBackOnItsWayOut() {
        show()
        shown = true
        compose.waitForIdle()

        // The button is tapped again while the field still slides away: the same field comes back.
        compose.mainClock.autoAdvance = false
        shown = false
        intoTheMotion()
        compose.onNode(hasSetTextAction()).assertExists()
        assertEquals(HIDE, asked.last())
        shown = true
        intoTheMotion()

        assertEquals(SHOW, asked.last())
    }

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                Box(Modifier.size(400.dp, 200.dp)) {
                    TryField(shown, Modifier) {}
                }
            }
        }
        compose.waitForIdle()
    }

    /** A few frames into the field's motion: the frame that recomposes, the effects it launches, then the motion. */
    private fun intoTheMotion() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAMES_INTO_THE_EXIT * FRAME_MILLIS)
    }

    private companion object {
        const val SHOW = "show"
        const val HIDE = "hide"
        const val FRAMES_INTO_THE_EXIT = 3
        const val FRAME_MILLIS = 16L
    }
}
