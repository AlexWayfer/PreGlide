package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.suggest.currentWord

/**
 * What autocorrect changes in [field]: it puts its word in place of the word being typed, with [choose] when a space
 * ends the word, as a chosen suggestion goes in with the keyboard's [ownSpace] after it. A backspace right after
 * the space or the mark that ended the word puts the word back as typed, and that word is not corrected again
 * while the field is open, as the user meant it so.
 */
class Corrections(
    private val field: SuggestionField,
    private val ownSpace: OwnSpace,
    private val choose: (String) -> SuggestionEdit,
) {
    /** The word as typed and what autocorrect typed in its place, while nothing else was typed after it. */
    private var last: Pair<String, String>? = null

    /** The word the user put back as typed last, which autocorrect leaves alone. */
    private var kept: String? = null

    /**
     * Puts [word] in place of the word being typed as the user ends it with [ending]: with a space after it for a
     * space; alone for a mark, which the keyboard types next. Either way, a backspace right after the ending puts
     * the word back. Returns the edit made.
     */
    fun correct(word: String, ending: Char): SuggestionEdit {
        val typed = currentWord(textBeforeCursor())
        if (ending == ' ') return choose(word).also { last = typed to it.text }
        val edit = SuggestionEdit(typed.length, word)
        field.replace(edit.deleteBefore, edit.text)
        ownSpace.forget()
        // The mark is not in yet, but the backspace comes after it.
        last = typed to "$word$ending"
        return edit
    }

    /** Whether autocorrect leaves [typed] alone: the user put it back as typed. */
    fun keeps(typed: String): Boolean = kept?.equals(typed, ignoreCase = true) == true

    /**
     * Backspace right after autocorrect's change: puts the word back as typed, without the space or the mark that
     * ended it, and returns the edit made. Null when there is no such change, and the backspace deletes as usual;
     * either way, the change is past undoing from now on.
     */
    fun revert(): SuggestionEdit? {
        val change = last
        last = null
        // Reads the field only right after a change.
        val edit = change?.let { (typed, text) -> revertEdit(textBeforeCursor(), typed, text) }
        if (edit != null) {
            field.replace(edit.deleteBefore, edit.text)
            kept = edit.text
        }
        return edit
    }

    /** The user typed something else, or moved the cursor: the change stays. */
    fun forget() {
        last = null
    }

    /** A field starts or ends: nothing is left to undo, and no word is kept. */
    fun clear() {
        last = null
        kept = null
    }

    private fun textBeforeCursor(): CharSequence = field.textBeforeCursor(TEXT_BEFORE_CURSOR) ?: ""
}

/**
 * Undoing autocorrect's change of [typed] into [text] when the text before the cursor, [textBefore], ends with
 * [text]: [typed] goes back in its place. Null when the text has changed since, and nothing is undone.
 */
fun revertEdit(textBefore: CharSequence, typed: String, text: String): SuggestionEdit? =
    if (textBefore.endsWith(text)) SuggestionEdit(text.length, typed) else null
