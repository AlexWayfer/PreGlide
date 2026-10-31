package name.alexwayfer.preglide.ime

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page

/** What the strip's buttons do, which the symbols' key offers by its long press while the strip is hidden. */
enum class KeyMenuItem(@DrawableRes val icon: Int, @StringRes val label: Int) {
    Clipboard(R.drawable.ic_content_paste, R.string.clipboard),
    Settings(R.drawable.ic_settings, R.string.keyboard_settings),
}

/** A key's long press menu: its [items], in the popup's order, and what [choose] does with the one lifted on. */
class KeyMenu(val items: List<KeyMenuItem>, val choose: (KeyMenuItem) -> Unit)

/**
 * The keys' menu, which opens the clipboard's panel by [openClipboard] and the keyboard's settings by [openSettings]:
 * only while the [strip] with their buttons is hidden, and not while the emoji search types on the keys, [searching].
 */
fun keysMenu(strip: Boolean, searching: Boolean, openClipboard: () -> Unit, openSettings: () -> Unit): KeyMenu? =
    if (strip || searching) {
        null
    } else {
        KeyMenu(KeyMenuItem.entries) { item ->
            when (item) {
                KeyMenuItem.Clipboard -> openClipboard()
                KeyMenuItem.Settings -> openSettings()
            }
        }
    }

/** Whether a key with [action] offers the keys' menu by its long press: the symbols' key, which has none of its own. */
fun offersMenu(action: KeyAction): Boolean = action == KeyAction.ShowPage(Page.Symbols)

/**
 * How tall a panel in place of the keys, [keysHeight] tall, is: as tall as they are, so the keyboard does not jump,
 * less the strip's [stripHeight] when the panel shows its [ownRow] of the strip, which is hidden above it.
 */
fun panelHeight(keysHeight: Dp, stripHeight: Dp, ownRow: Boolean): Dp =
    if (ownRow) keysHeight - stripHeight else keysHeight
