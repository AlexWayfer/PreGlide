package name.alexwayfer.preglide.spell

import name.alexwayfer.preglide.engine.layout.Language
import java.util.concurrent.atomic.AtomicInteger

/** A word of the system's personal dictionary, added for [locale], or for every language when it has none. */
data class UserWord(val word: String, val locale: String?)

/** The words of [entries] added for [language] or for every language. */
fun userWordsFor(language: Language, entries: List<UserWord>): Set<String> =
    entries.filter { it.locale.isNullOrEmpty() || Language.forTag(it.locale) == language }.mapTo(HashSet()) { it.word }

/**
 * The words the user added to the system's personal dictionary, as with "Add to dictionary" in a misspelled word's
 * menu. [read] reads them all when first asked, and again after a [changed]. Any thread may ask.
 */
class UserWords(private val read: () -> List<UserWord>) {
    private val version = AtomicInteger()

    @Volatile
    private var cached: Pair<Int, List<UserWord>>? = null

    fun of(language: Language): Set<String> {
        val current = version.get()
        // A change while reading leaves the words read stale: they are read again next time.
        val entries = cached?.takeIf { it.first == current }?.second ?: read().also { cached = current to it }
        return userWordsFor(language, entries)
    }

    /** The personal dictionary changed. */
    fun changed() {
        version.incrementAndGet()
    }
}
