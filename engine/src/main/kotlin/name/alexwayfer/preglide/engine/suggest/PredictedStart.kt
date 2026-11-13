package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.predict.Prediction
import name.alexwayfer.preglide.engine.predict.Vocabulary

/**
 * Up to [limit] ids of the prediction's words that start with [typed], with ё where it has ё (see [keepsTypedYo]),
 * any for an empty [typed], with their probabilities in [next], the likeliest first. The fixed tokens are no words
 * to suggest. Only the words with that start are looked at, as they lie next to each other in the search order.
 * [preferred] words come first, before limiting the candidates.
 */
fun likeliestWords(
    next: NextTokens,
    typed: String,
    limit: Int,
    preferred: (String) -> Boolean = { false },
): List<Pair<Int, Float>> {
    val best = ArrayList<Int>(limit + 1)
    val prediction = next.prediction
    val orderByScore = compareByDescending<Int> { preferred(prediction.spellings[it]) }
        .thenByDescending { next.probabilities[it] }
    val order = prediction.searchOrder
    var position = firstStartingWith(prediction, typed)
    while (position < order.size && startsWithFolded(wordOf(prediction, order[position]), typed)) {
        if (keepsTypedYo(wordOf(prediction, order[position]), typed)) {
            insertByScore(best, order[position], orderByScore, limit)
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

/** Puts [id] into [best], kept in [order] and at most [limit] long. */
private fun insertByScore(best: MutableList<Int>, id: Int, order: Comparator<Int>, limit: Int) {
    var position = best.size
    while (position > 0 && order.compare(id, best[position - 1]) < 0) position--
    if (position < limit) {
        best.add(position, id)
        if (best.size > limit) best.removeAt(best.lastIndex)
    }
}
