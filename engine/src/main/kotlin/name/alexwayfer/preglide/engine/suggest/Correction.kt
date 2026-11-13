package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.predict.NextTokens
import kotlin.math.exp

/**
 * How likely, per billion words, a word that neither the dictionary nor the user knows is taken to be as typed:
 * about as likely as the dictionary's rarest words. A word the user may have meant must beat it to take its place.
 */
const val UNKNOWN_WORD_SCORE = 50.0

/**
 * How many times likelier than the word as typed a word must be to take its place. A wrong correction costs the
 * user more than a typo left in place, so the keyboard corrects only when it is fairly sure.
 */
const val CORRECTION_MARGIN = 5.0

/**
 * The fewest letters a word needs for autocorrect to change it: a shorter one is a slip away from too many others,
 * and slang is full of them, as "ща" is a slip away from "за".
 */
private const val FEWEST_CORRECTED_LETTERS = 3

/**
 * The word autocorrect puts in place of the word being typed at the end of [textBefore] as the user ends it, or
 * null to leave it as typed: the likeliest of [words], what the strip suggests for it, read as the whole word from
 * [touches], when it is [CORRECTION_MARGIN] times likelier than the word as typed. [next] is what the prediction
 * model gives for the text before the word, if anything; without it, a word's frequency tells how likely it is.
 */
fun correction(
    textBefore: CharSequence,
    words: List<String>,
    model: LanguageModel,
    next: NextTokens?,
    touches: Touches,
): String? {
    val typed = currentWord(textBefore)
    if (!isCorrectable(typed, model)) return null
    return secondCapitalSuggestion(typed, model) ?: likelyCorrection(textBefore, words, model, next, touches)
}

private fun likelyCorrection(
    textBefore: CharSequence,
    words: List<String>,
    model: LanguageModel,
    next: NextTokens?,
    touches: Touches,
): String? {
    val typed = currentWord(textBefore)
    val typing = Typing(typed, touches)
    val scorer = WordScorer(model, next, wordContext(textBefore, RANKED_CONTEXT))
    val asTyped = maxOf(scorer.score(typed), UNKNOWN_WORD_SCORE)
    return words.asSequence()
        .filterNot { foldsTheSame(it, typed) }
        .mapNotNull { word -> typing.wholeCostOf(word)?.let { word to scorer.score(word) * exp(-it.toDouble()) } }
        .maxByOrNull { it.second }
        ?.takeIf { it.second >= CORRECTION_MARGIN * asTyped }
        ?.first
}

/**
 * Whether autocorrect may change [typed]: a word of [FEWEST_CORRECTED_LETTERS] at least, not one in caps, as an
 * abbreviation is, nor one the user kept as typed before, which they meant so, nor one with a digit, as "mp3", which
 * no word of the dictionary is a slip of.
 */
private fun isCorrectable(typed: String, model: LanguageModel): Boolean {
    val letters = typed.filter { it.isLetter() }
    val inCaps = letters.all { it.isUpperCase() }
    val kept = (model.personal.learned(typed)?.kept ?: 0) > 0
    return letters.length >= FEWEST_CORRECTED_LETTERS && !inCaps && !kept && typed.none(Char::isDigit)
}

/**
 * How likely a word is to come next after [context], the words before it, per billion words, as the suggestions
 * score it: by the prediction [next] mixed with the user's next words, or by the dictionary's frequency without
 * it, and by the user's uses of the word.
 */
private class WordScorer(private val model: LanguageModel, next: NextTokens?, context: List<String>) {
    private val mix = next?.let { NextMix(model, it, context) }

    fun score(word: String): Double {
        // Typed at a sentence start or with е for ё, a word is still the dictionary's.
        val spelling = model.dictionary.spellingOf(word) ?: word
        val frequency = model.dictionary.frequency(spelling)
        val likely = mix?.let { it.probability(spelling, frequency) * PER_BILLION } ?: frequency.toDouble()
        return withUses(likely, frequency, model.personal.learned(word)?.count ?: 0)
    }
}
