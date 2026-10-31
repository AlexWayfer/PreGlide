package name.alexwayfer.preglide.engine.edit

/**
 * Where each word before the end of [text] starts, the nearest first: the steps a swipe from backspace selects
 * back by. A word is what lies between spaces, its marks with it, and each step takes the spaces after its word
 * too, so "Привет. Как дела?" steps back to "дела?", "Как дела?" and the whole text. When [cut], [text] is the
 * end of a longer text, and its start is no word's start.
 */
fun wordStepsBefore(text: CharSequence, cut: Boolean): List<Int> {
    val steps = mutableListOf<Int>()
    var start = text.length
    while (start > 0) {
        while (start > 0 && text[start - 1].isWhitespace()) start--
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        steps += start
    }
    if (cut && steps.lastOrNull() == 0) steps.removeAt(steps.lastIndex)
    return steps
}

/**
 * How many words a finger that went [left] pixels to the left of where it came down on backspace selects: none
 * until it goes [first] pixels, then one more every [step] pixels. Going right counts as no way at all.
 */
fun wordsSwiped(left: Float, first: Float, step: Float): Int =
    if (left < first) 0 else 1 + ((left - first) / step).toInt()
