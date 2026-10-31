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

private const val KEY = "backspace"

@RunWith(RobolectricTestRunner::class)
// The plain application: the keyboard's own loads its models and settings, which a key's touch does not need.
@Config(application = Application::class)
class BackspaceChangeTouchTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /** A backspace that counts the swipes it was asked to start, under its [name]. */
    private class CountingBackspace(val name: String, private val swipes: MutableList<String>) : Backspace {
        override fun canDelete() = true

        override fun startSwipe(): Int {
            swipes += name
            return 1
        }

        override fun select(words: Int) = Unit

        override fun endSwipe(lifted: Boolean) = Unit
    }

    @Test
    fun theVeryFirstSwipeAfterBackspaceChangesGoesToTheNewOne() {
        val swipes = mutableListOf<String>()
        // As the emoji search's backspace gives way to the field's when the search closes.
        var backspace: Backspace by mutableStateOf(CountingBackspace("query", swipes))
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
                Key(KeyAction.Backspace),
                KeyboardState(),
                look,
                { _, _, _ -> },
                backspace,
                glide = null,
                Modifier.size(60.dp, 48.dp).testTag(KEY),
            )
        }
        val swipe = {
            compose.onNodeWithTag(KEY).performTouchInput {
                down(center)
                moveBy(Offset(-100.dp.toPx(), 0f))
                up()
            }
        }
        // A touch before the change, so the key waits for the next one with the old backspace already set.
        swipe()
        swipes.clear()

        compose.runOnIdle { backspace = CountingBackspace("field", swipes) }
        swipe()

        assertEquals(listOf("field"), swipes)
    }
}
