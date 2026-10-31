package name.alexwayfer.preglide.engine.predict

import java.util.Locale

/**
 * A piece of text the prediction model reads. The training text and the text before the cursor are both cut with
 * [tokens], so the model sees the same pieces in both.
 */
sealed interface Token {
    /** A word: letters joined by hyphens or apostrophes, lowercase, with ’ written as '. */
    data class Word(val text: String) : Token

    /** A punctuation mark, one of [MARKS]. */
    data class Mark(val mark: Char) : Token {
        init {
            require(mark in MARKS) { "Not a mark the model knows: $mark" }
        }
    }

    /** A number: digits, and the points, commas and colons between them, as in 3.14, 1,000 or 12:30. */
    data object Number : Token

    /** A line break, which starts a new message or paragraph. */
    data object LineBreak : Token
}

/** The marks the model tells apart; [markOf] brings the others to these. */
const val MARKS = ".,!?:;—()\""

/** Chars that join the letters of one word, as in "кто-то" and "don't". */
private const val WORD_JOINERS = "-'’"

/** The apostrophes of [WORD_JOINERS]: a run of them joins as one, as OPUS's English subtitles write "you''re". */
private const val APOSTROPHES = "'’"

/** A run of apostrophes in a word, written as one. */
private val APOSTROPHE_RUN = Regex("['’]{2,}")

/** Chars that join the digits of one number. */
private const val NUMBER_JOINERS = ".,:"

/**
 * The mark of [MARKS] that [char] counts as, or null for a char that is no mark: an ellipsis is a point, a dash or
 * a hyphen outside a word a dash, brackets round, and every quote a straight double quote.
 */
fun markOf(char: Char): Char? =
    when (char) {
        '.', '…' -> '.'
        ',', '!', '?', ':', ';' -> char
        '—', '–', '-' -> '—'
        '(', '[' -> '('
        ')', ']' -> ')'
        '"', '«', '»', '„', '“', '”', '\'', '’' -> '"'
        else -> null
    }

/**
 * The tokens of [text]: its words, numbers, marks and line breaks. Spaces, emoji and other symbols are left out. A
 * mark repeated, as in "..." or "!!!", counts once, and so do blank lines.
 */
fun tokens(text: CharSequence): List<Token> {
    val found = ArrayList<Token>()
    var start = 0
    while (start < text.length) {
        val end = tokenEnd(text, start)
        tokenOf(text, start, end)?.let { addCollapsed(found, it) }
        start = end
    }
    return found
}

/** Where the token that starts at [start] of [text] ends: after its word or number, or after its one char. */
private fun tokenEnd(text: CharSequence, start: Int): Int =
    when {
        text[start].isLetter() -> runEnd(text, start, WORD_JOINERS, Char::isLetter)
        text[start].isDigit() -> runEnd(text, start, NUMBER_JOINERS, Char::isDigit)
        else -> start + 1
    }

/**
 * The end of the run of chars that are [part] from [start] of [text]: a char of [joiners] continues it only between
 * two of them.
 */
private fun runEnd(text: CharSequence, start: Int, joiners: String, part: (Char) -> Boolean): Int {
    var end = start
    while (end < text.length && (part(text[end]) || joinsAt(text, end, joiners, part))) end++
    return end
}

/**
 * Whether [text] has a char of [joiners] at [index] with a char that is [part] right after it, or after the run of
 * apostrophes it starts.
 */
private fun joinsAt(text: CharSequence, index: Int, joiners: String, part: (Char) -> Boolean): Boolean {
    if (text[index] !in joiners) return false
    var next = index + 1
    if (text[index] in APOSTROPHES) while (next < text.length && text[next] in APOSTROPHES) next++
    return next < text.length && part(text[next])
}

/** The token between [start] and [end] of [text], or null for a char the model does not read. */
private fun tokenOf(text: CharSequence, start: Int, end: Int): Token? =
    when {
        text[start].isLetter() -> Token.Word(wordText(text.substring(start, end)))
        text[start].isDigit() -> Token.Number
        text[start] == '\n' -> Token.LineBreak
        else -> markOf(text[start])?.let { Token.Mark(it) }
    }

/** The text of the word [word] as a [Token.Word] holds it: lowercase, with one ' for each run of apostrophes. */
private fun wordText(word: String): String = word.replace(APOSTROPHE_RUN, "'").replace('’', '\'').lowercase(Locale.ROOT)

/** Adds [token] to [found], unless it is a mark or a line break just like the one before it. */
private fun addCollapsed(found: MutableList<Token>, token: Token) {
    val repeated = (token is Token.Mark || token is Token.LineBreak) && token == found.lastOrNull()
    if (!repeated) found.add(token)
}
