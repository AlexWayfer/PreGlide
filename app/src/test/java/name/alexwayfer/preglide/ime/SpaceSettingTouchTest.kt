package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.Key
import name.alexwayfer.preglide.engine.layout.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val BAR = "space"

@RunWith(RobolectricTestRunner::class)
// The plain application: the keyboard's own loads its models and settings, which a key's touch does not need.
@Config(application = Application::class)
class SpaceSettingTouchTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private class NoBackspace : Backspace {
        override fun canDelete() = false

        override fun startSwipe() = 0

        override fun select(words: Int) = Unit

        override fun endSwipe(lifted: Boolean) = Unit
    }

    @Test
    fun theVeryFirstSwipeAfterTheSettingChangesDoesTheNewGesture() {
        val moves = mutableListOf<CursorMove>()
        val switched = mutableListOf<Boolean>()
        val cursor = Cursor { moves += it; true }
        val languages = LanguageSwitch { switched += it; true }
        var space by mutableStateOf(SpaceBar(SpaceGestures.SwipeCursor, cursor, languages))
        val look = KeyLook(
            hints = false,
            backgrounds = 1f,
            dividers = 0f,
            cornerRadius = 8.dp,
            height = 48.dp,
            stripHeight = 40.dp,
        )
        compose.setContent {
            KeyView(
                Key(KeyAction.Space),
                KeyboardState(),
                look,
                { _, _, _ -> },
                NoBackspace(),
                glide = null,
                Modifier.size(200.dp, 48.dp).testTag(BAR),
                space,
            )
        }
        val swipe = {
            compose.onNodeWithTag(BAR).performTouchInput {
                down(center)
                moveBy(Offset(30.dp.toPx(), 0f))
                up()
            }
        }
        // A touch before the change, so the bar waits for the next one with the old gestures already set.
        swipe()
        moves.clear()

        compose.runOnIdle { space = SpaceBar(SpaceGestures.SwipeLanguage, cursor, languages) }
        swipe()

        assertEquals(listOf(true), switched)
        assertEquals(emptyList<CursorMove>(), moves)
    }
}
