package name.alexwayfer.preglide.ime

/**
 * Where the cursor goes after each of the keyboard's edits the field has not reported yet, in order. The field
 * reports edits later, and fast typing makes several before the first report, so each expected place follows on
 * from the one before, not from the cursor last reported. A report at an expected place is the keyboard's own
 * edit; anything else is a cursor move or an app's own change.
 */
class EditExpectations {
    private val places = ArrayDeque<Int>()

    /** Where the cursor is once the field reports every edit still expected; null when none is. */
    val cursor: Int? get() = places.lastOrNull()

    /**
     * The keyboard made [edit]; [selectionStart] and [selectionEnd] are the last reported selection, which the
     * edit replaces when nothing else is still expected.
     */
    fun expect(edit: SuggestionEdit, selectionStart: Int, selectionEnd: Int) {
        val last = places.lastOrNull()
        val place = if (last == null) {
            cursorAfterEdit(selectionStart, selectionEnd, edit.deleteBefore, edit.text.length)
        } else {
            cursorAfterEdit(last, last, edit.deleteBefore, edit.text.length)
        }
        places.addLast(place)
    }

    /**
     * Whether the field's report of the cursor at [cursor] is of the keyboard's edits. A field may report several
     * edits at once, at the last one's place, so the places before it are done too. A report anywhere else forgets
     * what was expected: the text is not what the keyboard thought.
     */
    fun reported(cursor: Int): Boolean {
        val index = places.indexOf(cursor)
        if (index < 0) {
            places.clear()
        } else {
            repeat(index + 1) { places.removeFirst() }
        }
        return index >= 0
    }

    fun clear() = places.clear()
}

/**
 * Where the cursor goes after the keyboard deletes [deleteBefore] chars before it, or the selection from
 * [selectionStart] to [selectionEnd] when there is one, and then types [inserted] chars.
 */
fun cursorAfterEdit(selectionStart: Int, selectionEnd: Int, deleteBefore: Int, inserted: Int): Int {
    val start = if (selectionStart != selectionEnd) selectionStart else maxOf(0, selectionStart - deleteBefore)
    return start + inserted
}
