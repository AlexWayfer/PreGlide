package name.alexwayfer.preglide.engine.suggest

/** The text to complete and whether its initial capital asks for a word with a capital of its own. */
class SuggestionInput(val text: CharSequence, val preferCapital: Boolean = prefersCapitalWord(text)) {
    val typed: String = currentWord(text)
}

/** Whether a single initial capital inside a sentence asks for a word that has a capital of its own. */
fun prefersCapitalWord(textBefore: CharSequence): Boolean {
    val typed = currentWord(textBefore)
    return typed.firstOrNull()?.isUpperCase() == true && !isInCaps(typed) &&
        !startsSentence(textBeforeWord(textBefore))
}

/** Whether a word after [textBefore] starts a sentence, past spaces and opening quotes or brackets. */
fun startsSentence(textBefore: CharSequence): Boolean {
    val before = textBefore.dropLastWhile { it == ' ' || it == '\t' || it in OPENING_MARKS }
    return before.isEmpty() || before.last() == '\n' || before.last() == '\r' || endsSentenceBeforeSpace(before)
}

private const val OPENING_MARKS = "\"'«„“([{"

/** The user's own spelling, when learned, decides whether a word has an initial capital. */
internal fun hasWordCapital(word: String, personal: PersonalWords): Boolean =
    (personal.learned(word)?.spelling ?: word).firstOrNull()?.isUpperCase() == true

/** Keeps the existing order within the words with capitals and within the rest. */
internal fun capitalWordsFirst(words: List<String>, typed: String, personal: PersonalWords): List<String> =
    words.sortedByDescending { hasWordCapital(it, personal) && startsLikeTyped(it, typed) }
