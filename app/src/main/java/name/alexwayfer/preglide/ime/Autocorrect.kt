package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.KeyAction

/**
 * Autocorrect at the keys: before a space or a mark ends the word being typed, the word [suggestions] would
 * correct it to goes in its place, and [learning] learns it as a chosen suggestion; a backspace right after the
 * space or the mark puts the word back as typed, and [learning] keeps it so. A glided word is no slip of a tap,
 * and stays as it is.
 */
class Autocorrect(private val suggestions: WordSuggestions, private val learning: Learning) {
    /** Whether the key being typed comes right after a glided word. */
    private var afterGlide = false

    /**
     * Before the user's key with [action], [afterGlide] when it comes right after a glided word: anything but a
     * backspace leaves the last correction past undoing.
     */
    fun beforeKey(action: KeyAction, afterGlide: Boolean = false) {
        if (action != KeyAction.Backspace) suggestions.corrections.forget()
        this.afterGlide = afterGlide
    }

    /**
     * Before [char] is typed: when it ends the word being typed, autocorrect's word goes in its place first, with a
     * space after it for a space. Returns whether one did.
     */
    fun beforeTyping(char: Char): Boolean {
        val word = if (afterGlide) null else suggestions.correctionNow(char)
        if (word != null) {
            // Learned first: it reads the word the correction replaces.
            learning.chose(word)
            learning.expectEdit(suggestions.corrections.correct(word, char))
        }
        return word != null
    }

    /** A backspace: right after a correction's space or mark, the word goes back as typed. Returns whether it did. */
    fun revert(): Boolean {
        val edit = suggestions.corrections.revert()
        if (edit != null) learning.expectEdit(edit, reverted = true)
        return edit != null
    }
}
