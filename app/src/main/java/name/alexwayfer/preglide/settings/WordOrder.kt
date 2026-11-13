package name.alexwayfer.preglide.settings

import androidx.annotation.StringRes
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Learned
import name.alexwayfer.preglide.engine.suggest.compareFolded
import java.util.Locale

/** How the list of learned words is sorted, and the [label] of its choice. */
enum class WordOrder(@param:StringRes val label: Int) {
    /** The most typed first, and of those typed as often, the latest. */
    Frequency(R.string.learned_order_frequency),

    /** As a dictionary has them, with case and ё not counted. */
    Alphabet(R.string.learned_order_alphabet),
    Recent(R.string.learned_order_recent),
}

private val alphabetical = Comparator<Learned> { a, b -> compareFolded(a.spelling, b.spelling) }

private val mostTyped = compareByDescending<Learned> { it.count }.thenByDescending { it.lastUsed }.then(alphabetical)

/** The learned [words] in [order]. */
fun inOrder(words: List<Learned>, order: WordOrder): List<Learned> = when (order) {
    WordOrder.Frequency -> words.sortedWith(mostTyped)
    WordOrder.Alphabet -> words.sortedWith(alphabetical)
    WordOrder.Recent -> words.sortedWith(compareByDescending<Learned> { it.lastUsed }.then(alphabetical))
}

internal fun matchingWords(words: List<Learned>, query: String): List<Learned> {
    val term = query.trim()
    return if (term.isEmpty()) words else words.filter { it.spelling.contains(term, ignoreCase = true) }
}

/** What tells a learned word apart in the list: words are learned by their lowercase, so no two share it. */
fun listKey(word: Learned): String = word.spelling.lowercase(Locale.ROOT)

/** The name of [language] in itself, as its tab shows it: "Русский", "English". */
fun languageName(language: Language): String {
    val locale = Locale.forLanguageTag(language.tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}
