package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import name.alexwayfer.preglide.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ClipRowTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val pasted = mutableListOf<Clip>()
    private val opened = mutableListOf<String>()

    @Test
    fun aShortClipsChipIsAsWideAsItsWords() {
        show("да")

        val width = compose.onNodeWithTag(CLIP_CHIP).getUnclippedBoundsInRoot().width
        assertTrue("$width", width < 100.dp)
    }

    @Test
    fun aLongClipsChipStopsAtItsWidth() {
        show("Konbini Cleanup: те же ассеты, что и в других играх. ".repeat(20))

        // Robolectric's font is too narrow for the chip to fill its width; it stays within it, not the row's.
        val width = compose.onNodeWithTag(CLIP_CHIP).getUnclippedBoundsInRoot().width
        assertTrue("$width", width > 150.dp && width <= ClipChipWidth)
    }

    @Test
    fun aTapPastesTheClip() {
        val clip = show("да")

        compose.onNodeWithTag(CLIP_CHIP).performClick()

        assertEquals(listOf(clip), pasted)
    }

    @Test
    fun theButtonsAtTheEndsOpenTheClipboardAndTheSettings() {
        show("да")

        val app = RuntimeEnvironment.getApplication()
        compose.onNodeWithContentDescription(app.getString(R.string.clipboard)).performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.keyboard_settings)).performClick()

        assertEquals(listOf("clipboard", "settings"), opened)
    }

    @Test
    fun aRowWithNothingOfferedShowsTheGlideMark() {
        show(offered = null)

        compose.onNodeWithTag(GLIDE_MARK).assertExists()
        compose.onNodeWithTag(CLIP_CHIP).assertDoesNotExist()
    }

    @Test
    fun aClipTakesTheGlideMarksPlace() {
        show("да")

        compose.onNodeWithTag(GLIDE_MARK).assertDoesNotExist()
    }

    @Test
    fun theGlideMarkComesBackAsTheClipGoes() {
        var offered by mutableStateOf<OfferedClip?>(OfferedClip(Clip.Text("да", sensitive = false, copiedAt = 0)))
        show { offered }

        offered = null

        compose.onNodeWithTag(GLIDE_MARK).assertExists()
        compose.onNodeWithTag(CLIP_CHIP).assertDoesNotExist()
    }

    private fun show(text: String): Clip {
        val clip = Clip.Text(text, sensitive = false, copiedAt = 0)
        show(OfferedClip(clip))
        return clip
    }

    private fun show(offered: OfferedClip?) = show { offered }

    private fun show(offered: () -> OfferedClip?) {
        compose.setContent {
            Box(Modifier.size(400.dp, 40.dp)) {
                ClipRow(
                    offered(),
                    KeyboardShowing(),
                    onOpen = { opened += "clipboard" },
                    onPaste = { pasted += it },
                    onSettings = { opened += "settings" },
                )
            }
        }
    }
}
