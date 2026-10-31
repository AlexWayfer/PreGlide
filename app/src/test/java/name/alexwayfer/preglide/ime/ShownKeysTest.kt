package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val KEYS = "keys"

@RunWith(RobolectricTestRunner::class)
// The plain application: the keyboard's own loads its models and settings, which showing keys does not need.
@Config(application = Application::class)
class ShownKeysTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val state = KeyboardState()
    private val typed = mutableListOf<KeyAction>()
    private val look = KeyLook(
        hints = false,
        backgrounds = 1f,
        dividers = 0f,
        cornerRadius = 8.dp,
        height = 48.dp,
        stripHeight = 40.dp,
    )

    private class NoBackspace : Backspace {
        override fun canDelete() = false

        override fun startSwipe() = 0

        override fun select(words: Int) = Unit

        override fun endSwipe(lifted: Boolean) = Unit
    }

    @Before
    fun showKeys() {
        // The clock moves only when a test moves it, so a test can stop the keys halfway.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.width(360.dp).testTag(KEYS)) {
                val layout = KeyboardLayout.of(state.language, state.page)
                val onKey = { action: KeyAction, _: Boolean, _: KeyTap? -> typed += action }
                ShownKeys(layout, state, look, onKey, NoBackspace(), null) { _, _ -> null }
            }
        }
    }

    /** Switches the language as a swipe forward does, and stops the keys halfway through coming in. */
    private fun swipeToEnglishHalfway() {
        compose.runOnIdle {
            state.swipingLanguage = true
            state.language = Language.English
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(LANGUAGE_CHANGE_MS / 2L)
    }

    @Test
    fun theOldKeysLeaveWithTheirOwnLanguageWhileTheNewOnesComeIn() {
        swipeToEnglishHalfway()

        compose.onAllNodesWithText("й").assertCountEquals(1)
        compose.onAllNodesWithText("q").assertCountEquals(1)
        compose.onAllNodesWithText("Русский").assertCountEquals(1)
        compose.onAllNodesWithText("English").assertCountEquals(1)

        compose.mainClock.advanceTimeBy(LANGUAGE_CHANGE_MS.toLong())
        compose.onAllNodesWithText("й").assertCountEquals(0)
    }

    @Test
    fun aTapWhileTheKeysSlideTypesTheNewLanguage() {
        swipeToEnglishHalfway()
        compose.onNodeWithTag(KEYS).performTouchInput {
            // In the top row, where a key of each language lies.
            down(Offset(centerX, 24.dp.toPx()))
            up()
        }

        val char = (typed.single() as KeyAction.Letter).char
        assertTrue("$char", char in 'a'..'z')
    }

    @Test
    fun theKeysOfALanguageFromThePickerComeInToo() {
        compose.runOnIdle { state.language = Language.English }
        compose.mainClock.advanceTimeBy(LANGUAGE_CHANGE_MS * 2L)

        compose.onAllNodesWithText("q").assertCountEquals(1)
        compose.onAllNodesWithText("й").assertCountEquals(0)
        assertEquals(null, state.languageSwiped)
    }
}
