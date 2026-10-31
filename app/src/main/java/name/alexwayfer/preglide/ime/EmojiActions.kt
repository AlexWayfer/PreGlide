package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page

/**
 * Whether a long press on a key with [action] on [page] opens the emoji panel: the comma's, on every page but a
 * keypad, whose field takes digits; and none with the [emojiKey], which opens it instead while the comma shows no
 * smile to tell of its long press.
 */
fun opensEmoji(action: KeyAction, page: Page, emojiKey: Boolean = false): Boolean =
    !emojiKey && !page.keypad && action is KeyAction.Letter && action.char == ','

/** What [EmojiActions] asks of the rest of the keyboard. */
interface EmojiOutput {
    /** Reads the panel's emoji, their names and the history, if not read yet. */
    fun load()

    /** Keeps [EmojiState.history] for the next time. */
    fun save()

    /** Types [emoji] into the field. */
    fun type(emoji: String)

    /** The word an emoji typed now goes right after, to pair them, or null (see [emojiPairWord]). */
    fun wordBefore(): String?

    /** Types [emoji], chosen in the strip, after the word typed (see [EmojiChoice]). */
    fun typeAfterWord(emoji: String)

    /** The keys show again for the field, as after the panel closed. */
    fun backToKeys()
}

/**
 * What the emoji key, the comma's long press, the emoji panel, its strip and its search do: open and close the panel
 * in place of the keys, type an emoji through [output], and while the search shows, take the keys' presses for its
 * words. [emojiKey] tells whether the user has the emoji key, which takes the comma's long press over.
 */
class EmojiActions(
    private val keyboard: KeyboardState,
    private val output: EmojiOutput,
    private val emojiKey: () -> Boolean = { false },
) {
    private val emoji get() = keyboard.emoji

    /** Shows the panel in place of the keys, on the recent emoji, or on the smileys while there are none. */
    fun open() {
        keyboard.removing = null
        output.load()
        emoji.tab = openingTab(emoji.history)
        emoji.openings++
        emoji.shownRecent = emoji.history.recent
        emoji.query = null
        emoji.open = true
    }

    /** Shows the keys again, for the field. */
    fun close() {
        emoji.open = false
        emoji.query = null
        output.backToKeys()
    }

    /**
     * Types [cell]'s emoji, or with [tone], its form of that tone, which the next tap on it types too. Right after a
     * word, it is offered for that word later.
     */
    fun type(cell: EmojiCell, tone: Int? = null) {
        val form = tone?.let(cell.emoji::toned) ?: cell.form
        val word = output.wordBefore()
        output.type(form)
        emoji.history = emoji.history.typed(form, cell.emoji, tone, after = word)
        output.save()
    }

    /** Types [form], an emoji the strip offered for the word typed, after that word, and pairs them once more. */
    fun chose(form: String) {
        val word = output.wordBefore()
        output.typeAfterWord(form)
        emoji.history = emoji.history.typed(form, emoji.palette?.emojiOf(form), after = word)
        output.save()
    }

    /** Shows the search over the letters, with no word typed yet and no capital. */
    fun startSearch() {
        emoji.shownRecent = emoji.history.recent
        emoji.query = ""
        keyboard.page = Page.Letters
        keyboard.shift = ShiftState.Off
    }

    /** Shows the panel again, the search's words dropped. */
    fun endSearch() {
        emoji.query = null
    }

    /**
     * A key's [action], by a [longPress] or not, before it types into the field: true when the panel took it, as the
     * emoji key or, without it, the comma's long press opening the panel, or any key while the search shows, but a
     * long press on the space bar.
     */
    fun key(action: KeyAction, longPress: Boolean): Boolean = when {
        emoji.searching -> searchKey(action, longPress)
        action == KeyAction.Emoji || longPress && opensEmoji(action, keyboard.page, emojiKey()) -> {
            open()
            true
        }
        else -> false
    }

    private fun searchKey(action: KeyAction, longPress: Boolean): Boolean {
        val query = emoji.query.orEmpty()
        when (action) {
            is KeyAction.Letter -> {
                emoji.query = query + keyboard.typed(action.char)
                if (keyboard.shift == ShiftState.Once) keyboard.shift = ShiftState.Off
            }
            // Its long press picks another keyboard, as it does over the field.
            KeyAction.Space -> if (longPress) return false else emoji.query = "$query "
            KeyAction.Backspace -> emoji.query = query.dropLast(1)
            // Done searching: back to typing in the field.
            KeyAction.Enter -> close()
            KeyAction.Shift -> keyboard.shift = keyboard.shift.next()
            is KeyAction.ShowPage -> keyboard.page = action.page
            // The search shows over the keys as a part of the panel: its key goes back to the panel, as ← does.
            KeyAction.Emoji -> endSearch()
            KeyAction.Gap -> Unit
        }
        return true
    }

    private var backCloses = false

    /** The back key went down: true when it goes back within the panel rather than closing the keyboard. */
    fun backDown(): Boolean {
        backCloses = emoji.open
        return backCloses
    }

    /** The back key came up: true when it went back, from the search to the panel, or from the panel to the keys. */
    fun backUp(): Boolean {
        if (!backCloses) return false
        backCloses = false
        if (emoji.searching) endSearch() else close()
        return true
    }
}

/** While the search shows, backspace erases its words, and a swipe from it selects nothing in the field. */
class QueryBackspace(private val emoji: EmojiState) : Backspace {
    override fun canDelete(): Boolean = !emoji.query.isNullOrEmpty()

    override fun startSwipe(): Int = 0

    override fun select(words: Int) = Unit

    override fun endSwipe(lifted: Boolean) = Unit
}
