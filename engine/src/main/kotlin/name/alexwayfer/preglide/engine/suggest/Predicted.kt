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
): List<Scored> = predictedScores(model, next, SuggestionInput(typed, preferCapital = false), context, limit)

/** [predictedScores] with a case preference applied before limiting candidates. */
fun predictedScores(
    model: LanguageModel,
    next: NextTokens,
    input: SuggestionInput,
    context: List<String>,
    limit: Int,
): List<Scored> {
    val typed = input.typed
    val preferCapital = input.preferCapital
    val mix = NextMix(model, next, context)
    val wanted = limit + model.personal.removedCount
    val fromModel = likeliestWords(next, typed, wanted) { preferCapital && hasWordCapital(it, model.personal) }
        .map { (id, probability) ->
            Scored(next.prediction.spellings[id], perBillion(probability * mix.modelShare))
        }
    val fromUser = mix.after.filter { startsLikeTyped(it.spelling, typed) }.map {
        Scored(it.spelling, perBillion(mix.usersShare(it.spelling)))
    }
    // With nothing typed yet, the next word is the model's and the user's after the words before alone.
    if (typed.isEmpty()) return fromModel + fromUser
    val listed = fromModel + fromUser + unknownWords(model, mix, typed, wanted, preferCapital)
    return listed + learnedCompletions(model, mix, typed, listed)
}

/**
 * The words of [model]'s dictionary beyond the vocabulary of [mix]'s prediction that complete [typed], up to [limit]
 * most frequent: each takes its share of the unknown token's probability, scaled as the model's own words are.
 */
private fun unknownWords(
    model: LanguageModel,
    mix: NextMix,
    typed: String,
    limit: Int,
    preferCapital: Boolean,
): List<Scored> {
    if (!mix.hasUnknown) return emptyList()
    return model.dictionary.completeScored(typed, limit) { preferCapital && hasWordCapital(it, model.personal) }
        .filter { mix.isUnknown(it.word) }
        .map { Scored(it.word, perBillion(mix.unknownShare(it.score) * mix.modelShare)) }
}

private fun perBillion(probability: Double): Long = (probability * PER_BILLION).roundToLong()
