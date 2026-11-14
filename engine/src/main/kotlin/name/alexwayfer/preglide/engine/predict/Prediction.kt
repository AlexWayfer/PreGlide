package name.alexwayfer.preglide.engine.predict

import kotlin.math.exp

/**
 * A language's next-word prediction model and what the keyboard needs around it: its [vocabulary], how many
 * tokens of [context] it reads, each of its words as the dictionary spells it ([spellings], by id, as "Москва"),
 * the frequency, per billion, of the dictionary's words beyond the vocabulary ([unknownFrequency]), which its
 * unknown token stands for, and the ids of its words in the order a search by a typed start sees them
 * ([searchOrder]), so the words with one start lie next to each other. [logits] runs the model on a context, as
 * [contextIds] makes it, and gives a score for each token of the vocabulary.
 */
class Prediction(
    val vocabulary: Vocabulary,
    val context: Int,
    val spellings: Array<String>,
    val unknownFrequency: Long,
    val searchOrder: IntArray,
    private val logits: (IntArray) -> FloatArray,
) {
    init {
        require(spellings.size == vocabulary.size) { "A spelling for each token of the vocabulary" }
        require(searchOrder.all { it in Vocabulary.FIXED.size until vocabulary.size }) {
            "Only word ids belong in the search order"
        }
    }

    /** The probability of each token of the vocabulary coming next after [contextIds]. */
    fun probabilities(contextIds: IntArray): FloatArray = softmax(logits(contextIds))

    /** The ids the model reads for [text], the text before the word being typed: see [contextIds]. */
    fun contextOf(text: CharSequence, wholeText: Boolean): IntArray = contextIds(text, wholeText, vocabulary, context)
}

/** How many of the likeliest next words [NextTokens.likeliest] holds. */
const val LIKELIEST_WORDS = 100

/** What [prediction] gives for one context: the [probabilities] of its tokens coming next. */
class NextTokens(val prediction: Prediction, val probabilities: FloatArray) {
    /**
     * The ids of the [LIKELIEST_WORDS] words likeliest to come next, the likeliest first, found once for the
     * context: each letter of the word typed in it looks at them again.
     */
    val likeliest: List<Int> by lazy {
        val best = ArrayList<Int>(LIKELIEST_WORDS + 1)
        for (id in prediction.searchOrder) {
            var position = best.size
            while (position > 0 && probabilities[best[position - 1]] < probabilities[id]) position--
            if (position < LIKELIEST_WORDS) {
                best.add(position, id)
                if (best.size > LIKELIEST_WORDS) best.removeAt(best.lastIndex)
            }
        }
        best
    }
}

/**
 * The [length] ids the model reads for [text], the text before the word being typed: the ids of its last tokens,
 * padded on the left. When [text] is the [wholeText] of the field, a start token goes before it, as before every
 * text the model learned from; otherwise it was cut off at its start, which may have cut a word in two, and its
 * first token is left out.
 */
fun contextIds(text: CharSequence, wholeText: Boolean, vocabulary: Vocabulary, length: Int): IntArray {
    val found = tokens(text).let { if (wholeText) it else it.drop(1) }
    val ids = (if (wholeText) listOf(Vocabulary.START) else emptyList()) + found.map(vocabulary::id)
    val last = ids.takeLast(length)
    return IntArray(length) { index -> last.getOrElse(index - (length - last.size)) { Vocabulary.PADDING } }
}

/** [logits] as probabilities that add up to 1, computed apart from the largest so no exp overflows. */
fun softmax(logits: FloatArray): FloatArray {
    val max = logits.maxOrNull() ?: return logits
    val exps = FloatArray(logits.size) { exp(logits[it] - max) }
    val sum = exps.sum()
    for (index in exps.indices) exps[index] /= sum
    return exps
}
