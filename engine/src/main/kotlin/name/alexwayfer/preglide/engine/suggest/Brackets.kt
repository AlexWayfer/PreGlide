package name.alexwayfer.preglide.engine.suggest

/** Closing brackets that go right after a word when they close one opened before. */
const val CLOSING_BRACKETS = ")]"

/** The opening bracket each of [CLOSING_BRACKETS] pairs with. */
private val openingOf = mapOf(')' to '(', ']' to '[')

/** Chars that make a closing bracket after them a smiley's mouth, as in `:)`, not the end of a bracket. */
private const val SMILEY_EYES = ":;="

/**
 * Whether [text], the text before the cursor, has a bracket that [closing] pairs with open on its last line.
 * A run of opening brackets opens as many where something but a space or a closing bracket follows it; a run
 * of closing ones closes as many where it follows anything but a space, an opening bracket or a smiley's eyes.
 * So brackets standing apart, as a Twitch emote like `))` or `((`, and a smiley like `:)` do neither.
 */
fun bracketIsOpen(closing: Char, text: CharSequence): Boolean {
    val opening = openingOf.getValue(closing)
    val line = lastLine(text)
    var depth = 0
    var index = 0
    while (index < line.length) {
        val end = runEnd(line, index)
        val before = line.getOrNull(index - 1)
        val after = line.getOrNull(end)
        val run = end - index
        when {
            line[index] == opening && opensAfterRun(after, closing) -> depth += run
            line[index] == closing && closesAfter(before, opening) -> depth = maxOf(0, depth - run)
        }
        index = end
    }
    return depth > 0
}

/** Where the run of the char at [start] in [line] ends. */
private fun runEnd(line: CharSequence, start: Int): Int {
    var end = start + 1
    while (end < line.length && line[end] == line[start]) end++
    return end
}

private fun opensAfterRun(after: Char?, closing: Char): Boolean =
    after != null && !after.isWhitespace() && after != closing

private fun closesAfter(before: Char?, opening: Char): Boolean =
    before != null && !before.isWhitespace() && before != opening && before !in SMILEY_EYES
