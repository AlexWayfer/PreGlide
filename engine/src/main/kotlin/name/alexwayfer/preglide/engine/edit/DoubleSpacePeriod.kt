package name.alexwayfer.preglide.engine.edit

/** Closing marks can stand between the word and its sentence's end. */
private const val CLOSING_MARKS = ")]}'\"»”’"

/** Two consecutive space taps at an unchanged cursor, and the period a backspace can undo immediately afterward. */
class DoubleSpacePeriod {
    private var first: String? = null
    private var undoTail: String? = null

    /** Whether this space replaces the previous one with a period and a space. */
    fun space(before: String): Boolean {
        val previous = first
        forget()
        val replaces = previous != null && before == previous
        if (replaces) undoTail = before.dropLast(1) + ". "
        return replaces
    }

    /** A space key was handled, including a redundant tap after a suggestion's automatic space. */
    fun spaced(before: String) {
        first = before.takeIf(::canEndSentence)
    }

    /** Another key breaks the pair; only backspace keeps the last period available to undo. */
    fun otherKey(backspace: Boolean) {
        first = null
        if (!backspace) undoTail = null
    }

    /** Whether backspace restores the two spaces; a changed field leaves the period alone. */
    fun undo(before: String): Boolean {
        val tail = undoTail
        forget()
        return tail != null && tail == before
    }

    /** A cursor move, selection, another field or a disabled setting cancels the pair and its undo. */
    fun forget() {
        first = null
        undoTail = null
    }
}

/** A word or number followed by exactly one space; indentation and punctuation stay as typed. */
private fun canEndSentence(before: String): Boolean {
    if (!before.endsWith(' ')) return false
    val word = before.dropLast(1).trimEnd { it in CLOSING_MARKS }
    return word.isNotEmpty() && Character.isLetterOrDigit(word.codePointBefore(word.length))
}
