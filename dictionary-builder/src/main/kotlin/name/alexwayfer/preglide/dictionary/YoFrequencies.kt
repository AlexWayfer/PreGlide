package name.alexwayfer.preglide.dictionary

import java.util.Locale
import kotlin.math.roundToLong

/** How many corpus uses the dictionary's original split contributes beside the clean-text evidence. */
private const val YO_PRIOR_USES = 50.0

/** Two dictionary spellings and their uses in texts that consistently spell ё. Counts are capped per document. */
data class YoUsage(val plain: String, val yo: String, val plainUses: Long, val yoUses: Long) {
    init {
        require(plainUses >= 0 && yoUses >= 0 && plainUses + yoUses > 0)
        require('ё' !in plain && yo != plain && yo.replace('ё', 'е') == plain)
    }
}

/** The curated evidence: two spellings and their counts, tab-separated, without a header. */
fun yoUsages(lines: Sequence<String>): List<YoUsage> = lines.map { line ->
    val fields = line.split('\t')
    val (plain, yo) = fields
    val (plainUses, yoUses) = fields.drop(2)
    YoUsage(plain, yo, plainUses.toLong(), yoUses.toLong())
}.toList()

private object RussianYoUsage {
    val values: List<YoUsage> = checkNotNull(javaClass.getResourceAsStream("/yo-uses.tsv"))
        .bufferedReader().useLines(::yoUsages)
}

/**
 * Redistributes only the selected pairs' frequencies by [uses], preserving their totals, spellings and case.
 * The original split weighs as [YO_PRIOR_USES] uses; both spellings keep at least one. A missing counterpart is
 * not invented. The most frequent words come first, as before spelling restoration and dictionary serialization.
 */
fun rebalanceYo(words: List<Word>, uses: List<YoUsage> = RussianYoUsage.values): List<Word> {
    val bySpelling = words.associateBy { it.spelling.lowercase(Locale.ROOT) }
    val replacements = uses.flatMap { rebalancedPair(bySpelling, it) }.associateBy { it.spelling }
    return words.map { replacements[it.spelling] ?: it }
        .sortedWith(compareByDescending<Word> { it.occurrences }.thenBy { it.spelling })
}

private fun rebalancedPair(words: Map<String, Word>, usage: YoUsage): List<Word> {
    val withoutYo = words[usage.plain]
    val withYo = words[usage.yo]
    if (withoutYo == null || withYo == null) return emptyList()
    val total = withoutYo.occurrences + withYo.occurrences
    val share = (usage.yoUses + YO_PRIOR_USES * withYo.occurrences / total) /
        (usage.plainUses + usage.yoUses + YO_PRIOR_USES)
    val restored = (total * share).roundToLong().coerceIn(1, total - 1)
    return listOf(withoutYo.copy(occurrences = total - restored), withYo.copy(occurrences = restored))
}
