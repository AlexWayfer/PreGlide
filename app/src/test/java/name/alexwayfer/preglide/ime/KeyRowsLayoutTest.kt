package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The plain application: the keyboard's own loads its models and settings, which laying out keys does not need.
@Config(application = Application::class)
class KeyRowsLayoutTest {
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
    fun theRowsLieOneUnderAnotherEvenInAParentThatStacksItsChildren() {
        val look = KeyLook(
            hints = false,
            backgrounds = 1f,
            dividers = 0f,
            cornerRadius = 8.dp,
            height = 48.dp,
            stripHeight = 40.dp,
        )
        compose.setContent {
            Box {
                KeyRows(
                    KeyboardLayout.of(Language.Russian, Page.Letters),
                    KeyboardState(),
                    look,
                    { _, _, _ -> },
                    NoBackspace(),
                    space = null,
                ) { null }
            }
        }

        val top = compose.onNodeWithText("й").getUnclippedBoundsInRoot().top
        val third = compose.onNodeWithText("я").getUnclippedBoundsInRoot().top

        assertEquals(look.height * 2, third - top)
    }

    @Test
    fun theDigitRowLiesARowAboveTheLettersAndTypesItsDigit() {
        val look = KeyLook(
            hints = false,
            backgrounds = 1f,
            dividers = 0f,
            cornerRadius = 8.dp,
            height = 48.dp,
            stripHeight = 40.dp,
            digitRow = 1f,
        )
        val typed = mutableListOf<KeyAction>()
        compose.setContent {
            KeyRows(
                KeyboardLayout.of(Language.Russian, Page.Letters),
                KeyboardState(),
                look,
                { action, _, _ -> typed += action },
                NoBackspace(),
                space = null,
            ) { null }
        }

        // The first "1" is the digit row's key; the second, й's hidden hint.
        val digitKey = compose.onAllNodesWithText("1").onFirst()
        val digit = digitKey.getUnclippedBoundsInRoot().top
        val letter = compose.onNodeWithText("й").getUnclippedBoundsInRoot().top
        digitKey.performClick()

        assertEquals(look.height, letter - digit)
        assertEquals(listOf<KeyAction>(KeyAction.Letter('1')), typed)
    }

    @Test
    fun theEmojiKeyLiesLeftOfTheCommaAndOpensTheEmoji() {
        val look = KeyLook(
            hints = false,
            backgrounds = 1f,
            dividers = 0f,
            cornerRadius = 8.dp,
            height = 48.dp,
            stripHeight = 40.dp,
            emojiKey = 1f,
        )
        val typed = mutableListOf<KeyAction>()
        compose.setContent {
            KeyRows(
                KeyboardLayout.of(Language.English, Page.Letters),
                KeyboardState(),
                look,
                { action, _, _ -> typed += action },
                NoBackspace(),
                space = null,
            ) { null }
        }

        val emojiKey = compose.onNodeWithContentDescription("Emoji")
        val comma = compose.onNodeWithText(",").getUnclippedBoundsInRoot()
        val key = emojiKey.getUnclippedBoundsInRoot()
        emojiKey.performClick()

        // On the comma's row: the icon and the comma's text are of other heights, but overlap.
        assertTrue(key.top < comma.bottom && comma.top < key.bottom)
        assertTrue(key.right <= comma.left)
        assertEquals(listOf<KeyAction>(KeyAction.Emoji), typed)
    }
}
