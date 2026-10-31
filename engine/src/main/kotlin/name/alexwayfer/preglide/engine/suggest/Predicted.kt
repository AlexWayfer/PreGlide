package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.predict.Prediction
import name.alexwayfer.preglide.engine.predict.Vocabulary
import kotlin.math.roundToLong

/**
 * How many of the user's own uses the model's prediction weighs as, after a word: the user's next words after it
 * and the model's prediction mix as counts do, the model's spread over [PREDICTION_WEIGHT] uses. A pair the user
 * typed once gets a ninth at least, which usually puts it among the suggestions without pushing the model's strong
 * words aside; a few more uses put it first. The more the user typed after that word, the less the model counts
 * there. After two words, their next words mix with that mix the same way (see [NextMix]): a phrase typed once
 * gives its next word about a fifth, its pair's ninth included.
 */
const val PREDICTION_WEIGHT = 8.0

internal const val PER_BILLION = 1e9

/** [textBefore], the text before the cursor, without the word being typed: what the prediction model reads. */
fun textBeforeWord(textBefore: CharSequence): CharSequence =
    textBefore.subSequence(0, textBefore.length - currentWord(textBefore).length)

/**
 * The [Prediction] of a model of [vocabulary] that reads [context] tokens, over [dictionary]: each of its words is
 * spelled as the dictionary spells it, and its unknown token stands for the dictionary's words beyond it.
 */
fun predictionOver(
    dictionary: Dictionary,
    vocabulary: Vocabulary,
    context: Int,
    logits: (IntArray) -> FloatArray,
): Prediction {
    val spellings = Array(vocabulary.size) { id ->
        vocabulary.word(id)?.let { dictionary.spellingOf(it) ?: it }.orEmpty()
    }
    val known = vocabulary.words.indices.sumOf { dictionary.frequency(spellings[Vocabulary.FIXED.size + it]) }
    val unknown = (dictionary.totalFrequency - known).coerceAtLeast(0)
    return Prediction(vocabulary, context, spellings, unknown, searchOrderOf(vocabulary), logits)
}

/** The ids of [vocabulary]'s words in the order a search by a typed start sees them (see [compareFolded]). */
fun searchOrderOf(vocabulary: Vocabulary): IntArray {
    val words = vocabulary.words
    return words.indices.sortedWith { a, b -> compareFolded(words[a], words[b]) }
        .map { Vocabulary.FIXED.size + it }
        .toIntArray()
}

/**
 * The words to suggest that start with [typed], strongest first, from [next], what the prediction model gives for
 * the text before it: the model's likeliest words, mixed with the user's next words after [context], the words
 * before it, the nearest last, in [model] (see [PREDICTION_WEIGHT]). The dictionary's words beyond the model's
 * vocabulary share the probability of its unknown token by their frequency, and the words the user typed add to
 * their score as they do to a completion. [limit] is how many of the model's words to take; removed words are left
 * out.
 */
fun predictedWords(
    model: LanguageModel,
    next: NextTokens,
    typed: String,
    context: List<String>,
    limit: Int,
): List<String> = strongestFirst(predictedScores(model, next, typed, context, limit), model.personal)

/** The words of [predictedWords] with their scores, before they are merged and sorted. */
fun predictedScores(
    model: LanguageModel,
    next: NextTokens,
    typed: String,
    context: List<String>,
    limit: Int,
): List<Scored> {
    val mix = NextMix(model, next, context)
    val wanted = limit + model.personal.removedCount
    val fromModel = likeliestWords(next, typed, wanted).map { (id, probability) ->
        Scored(next.prediction.spellings[id], perBillion(probability * mix.modelShare))
    }
    val fromUser = mix.after.filter { startsLikeTyped(it.spelling, typed) }.map {
        Scored(it.spelling, perBillion(mix.usersShare(it.spelling)))
    }
    // With nothing typed yet, the next word is the model's and the user's after the words before alone.
    if (typed.isEmpty()) return fromModel + fromUser
    val listed = fromModel + fromUser + unknownWords(model.dictionary, mix, typed, wanted)
    return listed + learnedCompletions(model, mix, typed, listed)
}

/**
 * The words of [dictionary] beyond the vocabulary of [mix]'s prediction that complete [typed], up to [limit] of the
 * most frequent: each takes its share of the unknown token's probability, scaled as the model's own words are.
 */
private fun unknownWords(dictionary: Dictionary, mix: NextMix, typed: String, limit: Int): List<Scored> {
    if (!mix.hasUnknown) return emptyList()
    return dictionary.completeScored(typed, limit)
        .filter { mix.isUnknown(it.word) }
        .map { Scored(it.word, perBillion(mix.unknownShare(it.score) * mix.modelShare)) }
}

/**
 * Up to [limit] ids of the prediction's words that start with [typed], with ё where it has ё (see [keepsTypedYo]),
 * any for an empty [typed], with their probabilities in [next], the likeliest first. The fixed tokens are no words
 * to suggest. Only the words with
 * that start are looked at, as they lie next to each other in the search order: a letter typed costs a look at a
 * few of the vocabulary's words, not at all of them.
 */
fun likeliestWords(next: NextTokens, typed: String, limit: Int): List<Pair<Int, Float>> {
    val best = ArrayList<Int>(limit + 1)
    val prediction = next.prediction
    val order = prediction.searchOrder
    var position = firstStartingWith(prediction, typed)
    while (position < order.size && startsWithFolded(wordOf(prediction, order[position]), typed)) {
        if (keepsTypedYo(wordOf(prediction, order[position]), typed)) {
            insertByScore(best, order[position], next.probabilities, limit)
        }
        position++
    }
    return best.map { it to next.probabilities[it] }
}

/** The first place in [prediction]'s search order whose word comes at or after [typed] as a search sees them. */
private fun firstStartingWith(prediction: Prediction, typed: String): Int {
    val order = prediction.searchOrder
    var low = 0
    var high = order.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (compareFolded(wordOf(prediction, order[middle]), typed) < 0) low = middle + 1 else high = middle
    }
    return low
}

private fun wordOf(prediction: Prediction, id: Int): String = prediction.vocabulary.words[id - Vocabulary.FIXED.size]

/** Puts [id] into [best], kept sorted by [scores] and at most [limit] long. */
private fun insertByScore(best: MutableList<Int>, id: Int, scores: FloatArray, limit: Int) {
    var position = best.size
    while (position > 0 && scores[best[position - 1]] < scores[id]) position--
    if (position < limit) {
        best.add(position, id)
        if (best.size > limit) best.removeAt(best.lastIndex)
    }
}

private fun perBillion(probability: Double): Long = (probability * PER_BILLION).roundToLong()
