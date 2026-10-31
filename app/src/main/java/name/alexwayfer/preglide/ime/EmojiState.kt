package name.alexwayfer.preglide.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import name.alexwayfer.preglide.engine.emoji.Emoji
import name.alexwayfer.preglide.engine.emoji.EmojiGroup
import name.alexwayfer.preglide.engine.emoji.EmojiHistory
import name.alexwayfer.preglide.engine.emoji.EmojiNames
import name.alexwayfer.preglide.engine.emoji.EmojiPalette
import kotlin.math.abs

/** What the emoji panel shows, in place of the keys, and its search, over them. */
class EmojiState {
    /** Whether the panel or its search shows. */
    var open by mutableStateOf(false)

    /** The words searched for, typed on the keys; null while the panel shows rather than its search. */
    var query by mutableStateOf<String?>(null)

    /** The tab the panel opens on, and then the one it shows. */
    var tab by mutableStateOf<EmojiTab>(EmojiTab.Recent)

    /**
     * How many times the panel was opened: each opening starts on [tab] anew, even when the panel opens again while
     * it still fades out, which brings back the panel that was leaving, on the page it was on.
     */
    var openings by mutableIntStateOf(0)

    /** The emoji the phone can draw, by group; null until they are read. */
    var palette by mutableStateOf<EmojiPalette?>(null)

    /** The emoji's names, to search them by and for a screen reader; null until they are read. */
    var names by mutableStateOf<EmojiNames?>(null)

    /** The emoji the user typed lately, and the skin tones chosen. */
    var history by mutableStateOf(EmojiHistory())

    /**
     * The recent emoji in the order the panel or its search shows them: as they were when it opened, so the one a
     * tap typed does not jump to the front, putting another under the finger for the next tap.
     */
    var shownRecent by mutableStateOf(emptyList<String>())

    /** Whether the search shows over the keys, which type into it. */
    val searching: Boolean get() = open && query != null
}

/** A tab of the emoji panel: the recent emoji, or a group's. */
sealed interface EmojiTab {
    data object Recent : EmojiTab

    data class Group(val group: EmojiGroup) : EmojiTab
}

/** The panel's tabs in their order: the recent emoji, then the groups. */
val emojiTabs: List<EmojiTab> = listOf(EmojiTab.Recent) + EmojiGroup.entries.map(EmojiTab::Group)

/** The tab the panel opens on: the recent emoji, or the smileys while there are none. */
fun openingTab(history: EmojiHistory): EmojiTab =
    if (history.recent.isEmpty()) EmojiTab.Group(EmojiGroup.Smileys) else EmojiTab.Recent

/** An emoji's cell: the [form] a tap types, and the [emoji] it is a form of, whose tones a long press offers. */
data class EmojiCell(val form: String, val emoji: Emoji) {
    /** The tone the cell shows, which a long press offers over the cell: 0 for the base. */
    val tone: Int get() = emoji.forms.indexOf(form).coerceAtLeast(0)
}

/**
 * The cells of [tab] in [state]: a group's emoji, each in the tone chosen for it last, or the recent ones as typed,
 * in the order shown ([EmojiState.shownRecent]); a recent one the phone no longer draws is left out. None until the
 * emoji are read.
 */
fun cellsOf(tab: EmojiTab, state: EmojiState): List<EmojiCell> {
    val palette = state.palette ?: return emptyList()
    return when (tab) {
        EmojiTab.Recent -> state.shownRecent.mapNotNull { form -> palette.emojiOf(form)?.let { EmojiCell(form, it) } }
        is EmojiTab.Group -> palette.groups[tab.group].orEmpty().map { EmojiCell(state.history.shown(it), it) }
    }
}

/**
 * What tells [cell] of [tab] from the others as the tab changes: a recent emoji by its form, as two tones of one may
 * be recent, and a group's emoji by itself, so its cell stays as a long press changes its tone.
 */
fun cellKey(tab: EmojiTab, cell: EmojiCell): String = if (tab == EmojiTab.Recent) cell.form else cell.emoji.base

/** What the search for [query] finds, each in the tone chosen for it last; with no word yet, the recent emoji. */
fun searchResults(query: String, state: EmojiState): List<EmojiCell> {
    if (query.isBlank()) return cellsOf(EmojiTab.Recent, state)
    val found = state.names?.search(query).orEmpty().mapNotNull { state.palette?.emojiOf(it) }
    return found.map { EmojiCell(state.history.shown(it), it) }
}

/**
 * How much of the selected tab's look the tab at [index] has with the panel's pages at [position], fractional
 * while they move: 1 for the page shown, flowing to 0 for its neighbor as the finger pulls the pages.
 */
fun tabSelection(index: Int, position: Float): Float = (1 - abs(index - position)).coerceIn(0f, 1f)
