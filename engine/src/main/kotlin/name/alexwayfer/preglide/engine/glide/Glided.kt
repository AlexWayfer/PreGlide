package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.suggest.ContextParts
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.Meant
import name.alexwayfer.preglide.engine.suggest.NextMix
import name.alexwayfer.preglide.engine.suggest.PER_BILLION
import name.alexwayfer.preglide.engine.suggest.isRemoved
import name.alexwayfer.preglide.engine.suggest.rankedAfter
import name.alexwayfer.preglide.engine.suggest.withUses
import java.util.Locale
import kotlin.math.exp

/**
 * How many of the dictionary's words a glide may mean are looked at, the likeliest by their frequency and cost:
 * enough that the context can still pick a rarer one, which the model's likeliest words add to besides. A search
 * for more goes deeper into the dictionary, and a long glide's for 30 took several times as long.
 */
internal const val GLIDED_WORDS = 16

/**
 * A word a glide may mean, in the dictionary's spelling, and what it was scored by: its [frequency] per billion
 * words, how likely it comes next by the context, per billion, null without the prediction model, how many [uses]
 * the user taught, and what reading the glide as it costs, in nats (see [shapeCost]).
 */
data class GlidedWord(
    val word: String,
    val frequency: Long,
    val context: Double?,
    val uses: Int,
    val cost: Float,
    /**
     * How likely the word is before the user's uses make it likelier, per billion words: by the context, or by its
     * frequency without it; split as the user writes them among its spellings by е and ё (see [splitByUses]).
     */
    val likelihood: Double = context ?: frequency.toDouble(),
    /** The uses that make it likelier: its own, or all its spellings' once they are split (see [splitByUses]). */
    val boostUses: Int = uses,
    /** What [context] was mixed from, null without the model: for the debug glide log. */
    val contextParts: ContextParts? = null,
) {
    /** How likely the word is meant, the user's uses counted as [withUses] counts them: see [glideScore]. */
    val score: Double = glideScore(withUses(likelihood, frequency, boostUses), cost)
}

/**
 * What comes before a glided word: the [words] before it, the nearest last, and [next], what the prediction model
 * gives for the text before it, null without the model.
 */
class WordsBefore(val words: List<String> = emptyList(), val next: NextTokens? = null)

/** The words of [glidedScores], alone. */
fun glidedWords(
    model: LanguageModel,
    layout: KeyboardLayout,
    glide: Glide,
    count: Int,
    before: WordsBefore = WordsBefore(),
): List<String> = glidedScores(model, layout, glide, count, before).map { it.word }

/**
 * Up to [count] words a [glide] over [layout]'s keys may mean, the likeliest first, with what each was scored by.
 * Each is made less likely by how far the glide strays from its keys (see [shapeCost]) and scored by its frequency
 * and the user's uses of it; with the prediction model's guess for the text [before] it, by how likely it comes next
 * after the words before it, as a word typed is. Besides the dictionary's likeliest, the model's likeliest words and
 * the user's own, learned or typed after those words, are looked at. Removed words are left out.
 */
fun glidedScores(
    model: LanguageModel,
    layout: KeyboardLayout,
    glide: Glide,
    count: Int,
    before: WordsBefore = WordsBefore(),
): List<GlidedWord> {
    val keys = GlideKeys(layout, glide)
    val next = before.next
    val context = before.words
    val mix = next?.let { NextMix(model, it, context) }
    val likely = next?.likeliest?.map { next.prediction.spellings[it] }.orEmpty() +
        model.personal.rankedAfter(context).flatten().map { it.spelling } +
        model.personal.all().map { it.spelling }
    val found = LinkedHashMap<String, Meant>()
    for (meant in model.dictionary.glided(keys, GLIDED_WORDS + model.personal.removedCount)) {
        found[keyOf(meant.word)] = meant
    }
    for (word in likely) {
        // The letters have no keys for digits: a word with one, as "топ-3", is no glide's.
        if (keyOf(word) !in found && word.none(Char::isDigit)) {
            keys.costOf(word)?.let { found[keyOf(word)] = Meant(word, it, model.dictionary.frequency(word)) }
        }
    }
    val scored = found.values.filterNot { model.personal.isRemoved(it.word) }.map { meant ->
        GlidedWord(
            meant.word,
            meant.frequency,
            mix?.let { it.probability(meant.word, meant.frequency) * PER_BILLION },
            model.personal.learned(meant.word)?.count ?: 0,
            meant.cost,
            contextParts = mix?.parts(meant.word, meant.frequency),
        )
    }
    return splitByUses(scored).sortedWith(compareByDescending<GlidedWord> { it.score }.thenBy { it.word }).take(count)
}

/**
 * [likelihood] made less by [cost] (see [shapeCost]), unrounded: a long glide's words cost so much that rounding
 * to whole counts, as typed words' scores are, left them all at 0, and sorted them by the alphabet.
 */
internal fun glideScore(likelihood: Double, cost: Float): Double = likelihood * exp(-cost.toDouble())

/**
 * How far ahead of the next word, in nats of score, a glide's best word must be for the glide to be sure of it: of
 * the user's 3488 Russian glides logged till 2026-10-25, 57% were that sure, and he swapped 1.2% of those for
 * another word the strip offered.
 */
const val SURE_GLIDE_MARGIN = 4.0

/**
 * How much better, in nats of shape cost, another word must fit a glide than its best word for the glide not to be
 * sure of it, whatever the scores: then the context or the user's uses alone put the best word ahead, as сможешь over
 * созреешь, glided twice. Of the user's sure glides logged till 2026-10-25, 5.5% had such a word, and 8 of the 24
 * swaps among the sure glides came from them.
 */
const val SURE_SHAPE_MARGIN = 2f

/**
 * Whether a glide is sure of the first of [words], its likeliest first (see [glidedScores]): it scores
 * [SURE_GLIDE_MARGIN] ahead of the second, or no other word came, and none of the others fits the glide's shape
 * [SURE_SHAPE_MARGIN] better.
 */
fun glideIsSure(words: List<GlidedWord>): Boolean {
    val best = words.firstOrNull() ?: return false
    val next = words.getOrNull(1)?.score ?: 0.0
    val betterShaped = words.drop(1).any { best.cost - it.cost >= SURE_SHAPE_MARGIN }
    return best.score > 0.0 && best.score >= next * exp(SURE_GLIDE_MARGIN) && !betterShaped
}

private fun keyOf(word: String): String = word.lowercase(Locale.ROOT)
