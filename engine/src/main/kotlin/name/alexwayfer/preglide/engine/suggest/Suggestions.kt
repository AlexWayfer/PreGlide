package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.predict.NextTokens
import java.util.Locale
import kotlin.math.roundToLong

/**
 * How much one use of a pair adds to its frequency, per billion pairs: about what the most frequent pairs have,
 * since the user's own phrases are the likeliest next words.
 */
const val PAIR_USE = 1_000_000L

/**
 * Up to [count] words to suggest from [model] for [textBefore], the text before the cursor, cased like the word
 * being typed. The words known to come after the previous word go first, those that fit what is typed of the
 * word; the most frequent completions of it fill the rest. Right after a space, with nothing typed yet, the
 * next words alone are suggested. What the user typed adds to the frequencies, and what they removed is left out.
 *
 * With [next], what the prediction model gives for the text before the word being typed, the prediction takes the
 * place of the pairs: see [predictedWords].
 *
 * With [touches], where the user touched the keys for the word, the words they may have meant by a slip or a
 * mistake join those with the typed start, each made less likely by what reading the taps so costs: see [Typing].
 *
 * A word a hyphen splits, as "потихоньку-п", gets the words of [hyphenSuggestions]; [next] is then the prediction
 * for its [HyphenSplit.spaced] text, as [predictedText] tells.
 */
fun suggestions(
    textBefore: CharSequence,
    model: LanguageModel,
    count: Int,
    next: NextTokens? = null,
    touches: Touches? = null,
): List<String> = suggestions(SuggestionInput(textBefore), model, count, next, touches)

/** [suggestions] with the case preference gathered by the keyboard before its one-letter shift turns off. */
fun suggestions(
    input: SuggestionInput,
    model: LanguageModel,
    count: Int,
    next: NextTokens? = null,
    touches: Touches? = null,
): List<String> {
    val textBefore = input.text
    hyphenSplit(textBefore)?.let { return hyphenSuggestions(input, it, model, count, next) }
    val typed = input.typed
    // Next words follow a space, or a mark a word goes a space after: one chosen there brings its space along.
    val predicting = typed.isNotEmpty() || textBefore.lastOrNull() == ' ' || endsWithMarkBeforeSpace(textBefore)
    val context = if (predicting) wordContext(textBefore, RANKED_CONTEXT) else emptyList()
    // A word with a digit, as "mp3", is typed off the letters' keys in part: no slip of them is read from it.
    val typing = touches?.takeIf { typed.isNotEmpty() && typed.none(Char::isDigit) }?.let { Typing(typed, it) }
    val words = when {
        !predicting -> emptyList()
        next != null -> strongestFirst(
            predictedScores(model, next, input, context, count) +
                typing?.let { meantPredicted(model, next, context, it) }.orEmpty(),
            model.personal,
        )
        else -> pairedWords(model, input, context, count, typing)
    }
    val ordered = if (input.preferCapital) capitalWordsFirst(words, typed, model.personal) else words
    return ordered
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(count)
        .map { casedLike(it, typed) }
}

/**
 * The words known to come after [context] that start with [input]'s typed word, then the most frequent
 * completions of it, with the words the user may have meant by [typing] among them.
 */
private fun pairedWords(
    model: LanguageModel,
    input: SuggestionInput,
    context: List<String>,
    count: Int,
    typing: Typing?,
): List<String> =
    nextWords(model, context).filter { startsLikeTyped(it, input.typed) } +
        completions(model, input.typed, count, typing, input.preferCapital)

/**
 * The words after [context], the dictionary's after its last word and the user's, the strongest first. Each use of
 * a word after the context's last word, or after its last two, adds [PAIR_USE]: a phrase the user typed counts
 * once for its pair and once more for the two words before.
 */
private fun nextWords(model: LanguageModel, context: List<String>): List<String> {
    val previous = context.lastOrNull() ?: return emptyList()
    val personal = model.personal.rankedAfter(context).flatten().map { Scored(it.spelling, it.count * PAIR_USE) }
    return strongestFirst(model.nextWords.scoredAfter(previous) + personal, model.personal)
}

/**
 * Up to [count] completions of [typed], the dictionary's and the user's, and the words the user may have meant by
 * [typing], the strongest first.
 */
internal fun completions(
    model: LanguageModel,
    typed: String,
    count: Int,
    typing: Typing?,
    preferCapital: Boolean = false,
): List<String> {
    if (typed.isEmpty()) return emptyList()
    // Removed words take room among the dictionary's best; asking for more leaves enough after them.
    val dictionary = model.dictionary.completeScored(typed, count + model.personal.removedCount) {
        preferCapital && hasWordCapital(it, model.personal)
    }
    val known = dictionary.map { it.word.lowercase(Locale.ROOT) }.toSet()
    // A learned word beyond the dictionary's best brings its frequency along; one among them already has it.
    val personal = model.personal.completions(typed).map { learned ->
        val counted = learned.spelling.lowercase(Locale.ROOT) in known
        val frequency = model.dictionary.frequency(learned.spelling)
        val uses = usesAdd(frequency.toDouble(), frequency, learned.count)
        Scored(learned.spelling, ((if (counted) 0.0 else frequency.toDouble()) + uses).roundToLong())
    }
    val meant = typing?.let { meantCompletions(model, it) }.orEmpty()
    return strongestFirst(dictionary + personal + meant, model.personal)
}
/**
 * [candidates] by their summed score, the strongest first: the same word, whatever its case, counts once, in the
 * spelling of its last candidate, which is the user's own when they learned it. Removed words are left out.
 */
internal fun strongestFirst(candidates: List<Scored>, personal: PersonalWords): List<String> {
    val merged = LinkedHashMap<String, Scored>()
    for (candidate in candidates.filterNot { personal.isRemoved(it.word) }) {
        merged.merge(candidate.word.lowercase(Locale.ROOT), candidate) { before, now ->
            Scored(now.word, before.score + now.score)
        }
    }
    return merged.values.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.word }).map { it.word }
}

/**
 * The word being typed: the end of [textBefore], the text before the cursor, from its last run of letters, digits,
 * hyphens and apostrophes. Empty right after a space or a punctuation mark. It starts at a letter: a hyphen or a
 * quote before the first letter is a dash or a quotation mark, and digits there a number, as "2" of "2-го", not part
 * of the word; digits after a letter are, as of "топ-3" or "mp3".
 */
fun currentWord(textBefore: CharSequence): String {
    var start = textBefore.length
    while (start > 0 && isWordChar(textBefore[start - 1])) start--
    while (start < textBefore.length && !textBefore[start].isLetter()) start++
    return textBefore.substring(start)
}

/**
 * The rest of the word the cursor is in: the start of [textAfter], the text after the cursor, up to the first
 * char that is no letter, digit, hyphen or apostrophe. Empty when a space or a mark follows the cursor.
 */
fun wordRest(textAfter: CharSequence): String {
    // A hyphen right after the cursor is a dash: the word ended before it.
    if (textAfter.firstOrNull()?.isLetter() != true) return ""
    var end = 0
    while (end < textAfter.length && isWordChar(textAfter[end])) end++
    // A hyphen or an apostrophe ends a word unless a letter or a digit follows it.
    while (end > 0 && !textAfter[end - 1].isLetterOrDigit()) end--
    return textAfter.substring(0, end)
}

/**
 * Whether the word being typed at the end of [textBefore], if any yet, is the ending of a number written in digits,
 * as "го" of "2-го" or "х" of "90-х": a hyphen right after a digit comes before it.
 */
fun isNumberEnding(textBefore: CharSequence): Boolean {
    val start = textBefore.length - currentWord(textBefore).length
    return start >= 2 && textBefore[start - 1] == '-' && textBefore[start - 2].isDigit()
}

private fun isWordChar(char: Char): Boolean =char.isLetterOrDigit() || char == '-' || char == '\''
