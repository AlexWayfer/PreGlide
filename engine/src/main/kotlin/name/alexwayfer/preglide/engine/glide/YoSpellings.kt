package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.suggest.foldedKey

/**
 * How many of the user's uses the context's own split between spellings by е and ё weighs as (see [splitByUses]):
 * a word or two the user typed already lean it, and many decide it.
 */
private const val CONTEXT_USES = 2.0

/**
 * [words] with the likelihood of each group of them spelled alike but for е and ё, as все and всё, split between
 * them as the user writes them: by their uses, the context's own split weighing as [CONTEXT_USES] uses, and made
 * likelier by the uses of all of them. The texts the prediction learned from mostly write е for both, so the е
 * spelling got the likelihood of the other as well, and a user who writes всё seventeen times to все once still got
 * все. A group the user used none of stays as it was, as does a word with no other spelling among [words].
 */
internal fun splitByUses(words: List<GlidedWord>): List<GlidedWord> {
    val groups = words.groupBy { foldedKey(it.word) }
    return words.map { word ->
        val group = groups.getValue(foldedKey(word.word))
        val uses = group.sumOf { it.uses }
        val total = group.sumOf { it.likelihood }
        if (group.size < 2 || uses == 0 || total <= 0.0) {
            word
        } else {
            val share = (word.uses + CONTEXT_USES * word.likelihood / total) / (uses + CONTEXT_USES)
            word.copy(likelihood = total * share, boostUses = uses)
        }
    }
}
