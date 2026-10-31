package name.alexwayfer.preglide.engine.suggest

import java.util.Locale
import kotlin.math.roundToLong

/**
 * How much one use of a word beyond the dictionary by the user adds to its frequency, per billion words: about what
 * the 5,000th most frequent word has, so a word of the user's own, as a name, typed once already comes up among the
 * dictionary's. A word the dictionary has is made likelier by [usesBoost] instead.
 */
const val WORD_USE = 300_000L

/** How much each use of a word multiplies its likelihood by, past none: once 3 times, twice 5 times. */
private const val BOOST_PER_USE = 2.0

/**
 * The most the user's uses multiply a word's likelihood by, about 2.3 nats: a word typed a few times beats others
 * that fit as well, but not one the finger or the context points to far more. Adding [WORD_USE] for each use, as for
 * a word beyond the dictionary, outweighed both: a word typed four times beat one glided twelve times better.
 */
private const val MAX_USES_BOOST = 10.0

/** How much the user's [uses] of a word multiply its likelihood: see [MAX_USES_BOOST]. */
fun usesBoost(uses: Int): Double = minOf(1 + BOOST_PER_USE * uses, MAX_USES_BOOST)

/**
 * The [likelihood] of a word, per billion words, made likelier by the user's [uses] of it: multiplied by [usesBoost]
 * for a word the dictionary has, used [frequency] per billion words; with [WORD_USE] added for each use for a word
 * beyond it, which has little likelihood of its own to multiply.
 */
fun withUses(likelihood: Double, frequency: Long, uses: Int): Double =
    if (frequency > 0) likelihood * usesBoost(uses) else likelihood + uses.toDouble() * WORD_USE

/**
 * What the user's [uses] add to a word's [likelihood] (see [withUses]): for a word whose likelihood is counted apart,
 * as the strip sums a word's scores from the dictionary and from the user's words.
 */
fun usesAdd(likelihood: Double, frequency: Long, uses: Int): Double =
    withUses(likelihood, frequency, uses) - likelihood

/**
 * The words the user typed that complete [typed], each scored by what its uses add to how likely [mix] makes it
 * (see [withUses]), with that likelihood too where [listed], the words scored already, lack it: the strip sums the
 * scores of a word.
 */
internal fun learnedCompletions(
    model: LanguageModel,
    mix: NextMix,
    typed: String,
    listed: List<Scored>,
): List<Scored> {
    val scored = listed.groupingBy { it.word.lowercase(Locale.ROOT) }.fold(0L) { sum, word -> sum + word.score }
    return model.personal.completions(typed).map { learned ->
        val frequency = model.dictionary.frequency(learned.spelling)
        val likely = mix.probability(learned.spelling, frequency) * PER_BILLION
        val unlisted = (likely - (scored[learned.spelling.lowercase(Locale.ROOT)] ?: 0L)).coerceAtLeast(0.0)
        Scored(learned.spelling, (unlisted + usesAdd(likely, frequency, learned.count)).roundToLong())
    }
}
