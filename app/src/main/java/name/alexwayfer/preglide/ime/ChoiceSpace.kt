package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.KeyAction

/** A word chosen before an existing closing mark leaves its space until the next letter is typed. */
class ChoiceSpace(private val field: SuggestionField) {
    private var chosenAt: Pair<String, String>? = null

    /** A choice left the cursor before a mark: keep that position, without putting a space before the mark. */
    fun chosen() {
        val before = field.textBeforeCursor(TEXT_BEFORE_CURSOR)?.toString().orEmpty()
        val after = field.textAfterCursor(WORD_AFTER_CURSOR)?.toString().orEmpty()
        chosenAt = if (before.lastOrNull()?.isLetter() == true &&
            after.firstOrNull()?.let { goesRightAfterWord(it, before) } == true
        ) {
            before to after
        } else {
            null
        }
    }

    /** Shift keeps the next letter pending; any other action except a char cancels it. */
    fun beforeKey(action: KeyAction) {
        if (action !is KeyAction.Letter && action != KeyAction.Shift) forget()
    }

    /** Consumes the pending space: only a letter at the unchanged position starts a new word. */
    fun take(char: Char): Boolean {
        val at = chosenAt
        forget()
        return at != null && char.isLetter() &&
            field.textBeforeCursor(TEXT_BEFORE_CURSOR)?.toString() == at.first &&
            field.textAfterCursor(WORD_AFTER_CURSOR)?.toString() == at.second
    }

    /** A cursor move, another edit or the field's end leaves no space pending. */
    fun forget() {
        chosenAt = null
    }
}
