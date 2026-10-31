package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.edit.wordStepsBefore

/** How many chars before the cursor a swipe from backspace reads, to select words back by. */
private const val SWIPE_TEXT = 4096

/**
 * What backspace asks of the field besides its key events: whether a press deletes anything now, and the words a
 * swipe from it selects back from the cursor, to erase them as the finger lifts.
 */
interface Backspace {
    /** Whether a press deletes anything now. */
    fun canDelete(): Boolean

    /** A swipe starts. Returns how many words before the cursor it can select. */
    fun startSwipe(): Int

    /** Selects the [words] nearest the cursor; none puts the selection back as it was. */
    fun select(words: Int)

    /** The swipe ends: the words selected are erased when the finger [lifted], or else the selection goes back. */
    fun endSwipe(lifted: Boolean)
}

/** Up to some chars [before] the selection, which runs from [start] to [end] in the field's whole text. */
data class FieldSelection(val before: CharSequence, val start: Int, val end: Int)

/** What [WordErasing] reads and changes in the field. */
interface WordField {
    /** Whether a backspace deletes anything now. */
    fun canDelete(): Boolean

    /** Up to [length] chars before the selection, and where it is; null when the field cannot tell. */
    fun selection(length: Int): FieldSelection?

    /** Selects from [start] to [end] in the field's whole text. */
    fun select(start: Int, end: Int)

    /** Erases the selection. */
    fun eraseSelection()
}

/**
 * Backspace in [field]: a swipe from it selects the words before the cursor, or before the selection there was,
 * one at a time, and erases them as the finger lifts. [beforeSelecting] runs as the first word is selected,
 * [afterErasing] once they are erased, told whether the first of them began with a capital (see
 * [erasesWordCapital]), and [afterSelecting] as a swipe that selected ends, erased or not.
 */
class WordErasing(
    private val field: WordField,
    private val beforeSelecting: () -> Unit,
    private val afterErasing: (capital: Boolean) -> Unit,
    private val afterSelecting: () -> Unit = {},
) : Backspace {
    /** Where the selection was as the swipe started; null with no swipe on, or in a field that cannot tell. */
    private var from: FieldSelection? = null

    /** Where the word of each step starts in [from]'s text, the nearest first. */
    private var steps = emptyList<Int>()
    private var selected = 0

    /** Whether the swipe selected a word at any time, even if it came back to none. */
    private var selecting = false

    override fun canDelete(): Boolean = field.canDelete()

    override fun startSwipe(): Int {
        val around = field.selection(SWIPE_TEXT)
        from = around
        // Text before what the field gave is cut off, and its first word may go on before it.
        steps = around?.let { wordStepsBefore(it.before, cut = it.start > it.before.length) }.orEmpty()
        selected = 0
        return steps.size
    }

    override fun select(words: Int) {
        val around = from ?: return
        val now = words.coerceIn(0, steps.size)
        if (now == selected) return
        if (!selecting) {
            selecting = true
            beforeSelecting()
        }
        selected = now
        field.select(selectionStart(around, now), around.end)
    }

    /** Where the selection of [words] words before [around] starts in the field's whole text. */
    private fun selectionStart(around: FieldSelection, words: Int): Int =
        if (words == 0) around.start else around.start - around.before.length + steps[words - 1]

    override fun endSwipe(lifted: Boolean) {
        val around = from ?: return
        from = null
        val words = selected
        selected = 0
        when {
            // Coming back to none put the selection back already.
            words == 0 -> Unit
            lifted -> {
                field.eraseSelection()
                afterErasing(erasesWordCapital(around.before, steps[words - 1]))
            }
            else -> field.select(around.start, around.end)
        }
        if (selecting) {
            selecting = false
            afterSelecting()
        }
    }
}
