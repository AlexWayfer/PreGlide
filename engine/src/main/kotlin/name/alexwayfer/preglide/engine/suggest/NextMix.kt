package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.predict.Token
import name.alexwayfer.preglide.engine.predict.Vocabulary
import java.util.Locale

/**
 * The model's prediction [next] mixed with the user's next words after [context], the words before, the nearest
 * last, in [model], as counts are: see [PREDICTION_WEIGHT]. The user's next words after the last word mix with the
 * prediction so, those after the last two with that mix the same way, and so on up to [RANKED_CONTEXT] words: a
 * longer context the user typed before speaks first, and one never typed leaves the shorter one's mix as it is.
 */
internal class NextMix(model: LanguageModel, private val next: NextTokens, context: List<String>) {
    private val prediction = next.prediction

    /** The user's next words after the last word of the context, its last two, and so on: the shortest first. */
    private val levels = model.personal.rankedAfter(context).map(::UsersNext)

    /** The user's next words after the context, each once. */
    val after: List<Learned> = levels.flatMap { it.words }.distinctBy { keyOf(it.spelling) }

    /** What the model's prediction weighs in the mix: what each level leaves of the one below it, all together. */
    val modelShare = levels.fold(1.0) { share, level -> share * level.passed }

    /** Whether the model has some words beyond its vocabulary for its unknown token to stand for. */
    val hasUnknown: Boolean get() = prediction.unknownFrequency > 0

    /** The share of [word] the user's next words after the context give it. */
    fun usersShare(word: String): Double {
        val key = keyOf(word)
        return levels.fold(0.0) { shorter, level -> level.share(key) + level.passed * shorter }
    }

    /** Whether [word] is beyond the model's vocabulary. */
    fun isUnknown(word: String): Boolean = idOf(word) == Vocabulary.UNKNOWN

    /**
     * How likely [word], used [frequency] per billion words by the dictionary, is to come next: by the model, or as
     * one of the dictionary's words beyond its vocabulary, and by the user's own next words.
     */
    fun probability(word: String, frequency: Long): Double = fromModel(word, frequency) * modelShare + usersShare(word)

    /** What [probability] mixes for [word], used [frequency] per billion words, told apart. */
    fun parts(word: String, frequency: Long): ContextParts {
        val key = keyOf(word)
        val counts = levels.map { LevelCount(it.count(key), it.count) }
        return ContextParts(fromModel(word, frequency) * PER_BILLION, counts)
    }

    /** How likely the model alone makes [word], used [frequency] per billion words, before the user's shares. */
    private fun fromModel(word: String, frequency: Long): Double {
        val id = idOf(word)
        return if (id == Vocabulary.UNKNOWN) unknownShare(frequency) else next.probabilities[id].toDouble()
    }

    /**
     * What a word beyond the vocabulary, used [frequency] per billion words, takes of the unknown token's
     * probability: its share of the frequency of all such words.
     */
    fun unknownShare(frequency: Long): Double {
        if (!hasUnknown) return 0.0
        return next.probabilities[Vocabulary.UNKNOWN].toDouble() * frequency / prediction.unknownFrequency
    }

    private fun idOf(word: String): Int = prediction.vocabulary.id(Token.Word(word.lowercase(Locale.ROOT)))
}

/**
 * The user's next [words] after one context, as counts mix with what comes from below them: a word typed there
 * takes its count of the total and [PREDICTION_WEIGHT] more, and what comes from below gets the rest.
 */
private class UsersNext(val words: List<Learned>) {
    private val counts = words.associate { keyOf(it.spelling) to it.count }

    /** How many times the user typed any next word here. */
    val count = words.sumOf { it.count }
    private val total = count + PREDICTION_WEIGHT

    /** What is left to what comes from below. */
    val passed = PREDICTION_WEIGHT / total

    /** How many times the user typed the word of [key] here. */
    fun count(key: String): Int = counts[key] ?: 0

    /** The share the word of [key] takes here. */
    fun share(key: String): Double = count(key) / total
}

/**
 * What [NextMix.probability] mixes for one word: how likely the [model] alone makes it, per billion, and at each
 * context the user typed next words after, the shortest first, how often there (see [LevelCount]). For tuning the
 * mix offline.
 */
class ContextParts(val model: Double, val levels: List<LevelCount>)

/** How many times the user typed a word after one context, and any word after it in all. */
data class LevelCount(val count: Int, val total: Int)
