package name.alexwayfer.preglide.engine.suggest

/** Quotes that both open and close: a quotation's straight quote, and the backtick around a piece of code. */
const val STRAIGHT_QUOTES = "\"`"

/** What a quote opens right after, besides a space or the start of a line. */
private const val OPENS_AFTER = "([{«„“"

/**
 * Whether [text], the text before the cursor, has a quotation in [quote]s open on its last line: a phrase or
 * a few sentences may stand in it. A quote opens where something but a space or another quote follows it, at
 * the start of the line, after a space or an opening bracket; the next quote that follows anything but a space
 * closes it. Any other quote, as amid a word or in a run like a code fence's three backticks, does neither.
 */
fun quoteIsOpen(quote: Char, text: CharSequence): Boolean {
    val line = lastLine(text)
    var open = false
    for (index in line.indices) {
        if (line[index] != quote) continue
        val before = line.getOrNull(index - 1)
        if (open) {
            if (closesAfter(before, quote)) open = false
        } else {
            open = opensBetween(before, line.getOrNull(index + 1), quote)
        }
    }
    return open
}

/** The last line of [text]: a quotation or a bracket left open in an earlier one does not count. */
internal fun lastLine(text: CharSequence): CharSequence = text.subSequence(text.lastIndexOf('\n') + 1, text.length)

private fun opensBetween(before: Char?, after: Char?, quote: Char): Boolean =
    (before == null || before.isWhitespace() || before in OPENS_AFTER) &&
        after != null && !after.isWhitespace() && after != quote

private fun closesAfter(before: Char?, quote: Char): Boolean =
    before != null && !before.isWhitespace() && before != quote
