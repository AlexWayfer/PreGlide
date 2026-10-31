package name.alexwayfer.preglide.engine.suggest

/** What can stand between two words of one sentence: spaces, commas and the like, dashes, quotes, brackets. */
private const val IN_SENTENCE_GAP = " \t,;:—–-\"«»„“”()"

/**
 * The marks the next word goes a space after, as after a word: the ends of a sentence or a phrase, and closing
 * quotes and brackets.
 */
private const val MARKS_BEFORE_SPACE = ".,!?;:…»”)]}"

/**
 * Whether [textBefore], the text before the cursor, ends a sentence once a space follows it, as Android's
 * `TextUtils.getCapsMode` tells a sentence's start, which it does only with the space typed: past closing brackets
 * and quotes, a ".", "?" or "!", but no period of an abbreviation such as "т.е.".
 */
fun endsSentenceBeforeSpace(textBefore: CharSequence): Boolean {
    var end = textBefore.length
    while (end > 0 && closesQuoteOrBracket(textBefore[end - 1])) end--
    val mark = textBefore.getOrNull(end - 1)
    return when (mark) {
        '?', '!' -> true
        '.' -> textBefore.subSequence(0, end - 1).takeLastWhile(Char::isLetter).let { word ->
            textBefore.getOrNull(end - 2 - word.length) != '.'
        }
        else -> false
    }
}

/** A straight quote or a closing bracket, which Android steps back over to the mark before them. */
private fun closesQuoteOrBracket(char: Char): Boolean =
    char == '"' || char == '\'' || Character.getType(char) == Character.END_PUNCTUATION.toInt()

/** Whether [textBefore], the text before the cursor, ends with a mark the next word goes a space after. */
fun endsWithMarkBeforeSpace(textBefore: CharSequence): Boolean =
    textBefore.lastOrNull()?.let { it in MARKS_BEFORE_SPACE } == true

/**
 * The word before the one being typed in [textBefore], in the same sentence, or null when there is none: at the
 * start of the text or of a sentence or line, or after a number.
 */
fun previousWord(textBefore: CharSequence): String? = previousWords(textBefore, 1).firstOrNull()

/**
 * Up to [count] words before the one being typed in [textBefore], in the same sentence, the nearest last: they stop
 * at the start of the text or of a sentence or line, and at a number or its ending (see [previousWord],
 * [isNumberEnding]).
 */
fun previousWords(textBefore: CharSequence, count: Int): List<String> {
    val words = ArrayList<String>(count)
    var end = textBefore.length - currentWord(textBefore).length
    while (words.size < count) {
        while (end > 0 && textBefore[end - 1] in IN_SENTENCE_GAP) end--
        val upToWord = textBefore.subSequence(0, end)
        val word = currentWord(upToWord)
        // A number ends the sentence's words, and so does its ending, as "го" of "2-го".
        if (word.isEmpty() || isNumberEnding(upToWord)) break
        words.add(word)
        end -= word.length
    }
    return words.asReversed()
}

/**
 * What the word being typed in [textBefore] comes after, as it is learned and suggested: [AFTER_NUMBER] right after
 * a number and a hyphen, as "разовое" of "2-разовое"; otherwise up to [count] of the [previousWords].
 */
fun wordContext(textBefore: CharSequence, count: Int): List<String> =
    if (isNumberEnding(textBefore)) listOf(AFTER_NUMBER) else previousWords(textBefore, count)

/**
 * Whether the word being typed at the end of [textBefore], which starts at [wordStart] in the field, is the first of
 * the field's text: [textBefore] reaches back to the field's start, and only blanks come before the word.
 */
fun beginsField(textBefore: CharSequence, wordStart: Int): Boolean {
    val before = textBefore.length - currentWord(textBefore).length
    return before == wordStart && textBefore.subSequence(0, before).isBlank()
}

/**
 * Whether the field is empty, by [textBefore] and [textAfter] the cursor, as at the start of a message: nothing
 * after the cursor, nothing but blanks before it.
 */
fun isEmptyField(textBefore: CharSequence, textAfter: CharSequence): Boolean =
    textBefore.isBlank() && textAfter.isEmpty()

/**
 * The word after the one the cursor is in, in [textAfter], the text after the cursor, in the same sentence; or
 * null when there is none: at the end of the text or of a sentence or line, or before a number.
 */
fun nextWord(textAfter: CharSequence): String? {
    var start = wordRest(textAfter).length
    while (start < textAfter.length && textAfter[start] in IN_SENTENCE_GAP) start++
    return wordRest(textAfter.subSequence(start, textAfter.length)).ifEmpty { null }
}
