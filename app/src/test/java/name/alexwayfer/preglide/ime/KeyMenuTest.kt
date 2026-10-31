package name.alexwayfer.preglide.ime

import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyMenuTest {
    @Test
    fun onlyTheSymbolsKeyOffersTheMenu() {
        assertTrue(offersMenu(KeyAction.ShowPage(Page.Symbols)))
        assertFalse(offersMenu(KeyAction.ShowPage(Page.Letters)))
        assertFalse(offersMenu(KeyAction.Letter('а')))
        assertFalse(offersMenu(KeyAction.Enter))
    }

    @Test
    fun theMenuOpensTheClipboardAndTheSettings() {
        val opened = mutableListOf<String>()
        val menu = checkNotNull(keysMenu(strip = false, searching = false, { opened += "clipboard" }) {
            opened += "settings"
        })

        assertEquals(listOf(KeyMenuItem.Clipboard, KeyMenuItem.Settings), menu.items)
        menu.choose(KeyMenuItem.Clipboard)
        menu.choose(KeyMenuItem.Settings)
        assertEquals(listOf("clipboard", "settings"), opened)
    }

    @Test
    fun theKeysHaveNoMenuWhileTheStripShowsItsButtonsOrTheEmojiSearchTypes() {
        assertNull(keysMenu(strip = true, searching = false, {}, {}))
        assertNull(keysMenu(strip = false, searching = true, {}, {}))
    }

    @Test
    fun aPanelMakesRoomForItsOwnRowWhileTheStripIsHidden() {
        assertEquals(160.dp, panelHeight(200.dp, 40.dp, ownRow = true))
        assertEquals(200.dp, panelHeight(200.dp, 40.dp, ownRow = false))
    }
}
