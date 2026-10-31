package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.layout.size
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

private const val CELL = "cell"

@RunWith(RobolectricTestRunner::class)
// The plain application: the keyboard's own loads its models and settings, which a key's touch does not need.
@Config(application = Application::class)
class KeyCellTouchTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /** What backspace does in the field, which no letter's touch asks. */
    private class NoBackspace : Backspace {
        override fun canDelete() = false

        override fun startSwipe() = 0

        override fun select(words: Int) = Unit

        override fun endSwipe(lifted: Boolean) = Unit
    }

    @Test
    fun aTouchInTheMarginAroundAKeyStillTypesIt() {
        val typed = mutableListOf<KeyAction>()
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
                Key(KeyAction.Letter('к')),
                KeyboardState(),
                look,
                { action, _, _ -> typed += action },
                NoBackspace(),
                glide = null,
                Modifier.size(60.dp, 48.dp).testTag(CELL),
            )
        }

        // The cell's corner, outside the key's inset look.
        compose.onNodeWithTag(CELL).performTouchInput {
            down(Offset(1f, 1f))
            up()
        }

        assertEquals(listOf<KeyAction>(KeyAction.Letter('к')), typed)
    }
}
