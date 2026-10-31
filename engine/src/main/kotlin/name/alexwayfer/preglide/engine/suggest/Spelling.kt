package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.Language
import java.util.Locale

/**
 * How many times the user has to type a word the dictionary lacks before it counts as spelled right: the keyboard
 * learns every word typed, and a word typed once may be a slip of the finger.
 */
const val TRUSTED_USES = 2

/** The longest word that gets corrections only one edit away: two edits make a short word into too many others. */
private const val SHORT_WORD = 4

private const val TYPOGRAPHIC_APOSTROPHE = '’'

/** Whether the word counts as spelled right: typed [TRUSTED_USES] times, or kept as typed on purpose once. */
val Learned.trusted: Boolean get() = count >= TRUSTED_USES || kept > 0

/**
 * Whether [word] is spelled right in [language]: [dictionary] has it, [isOwn] tells it is the user's own, or it is
 * made of such words joined by hyphens, as "онлайн-магазин". A word in another script than the language's, or with
 * a digit, is not judged, and counts as spelled right.
 */
fun isSpelledRight(word: String, language: Language, dictionary: Dictionary, isOwn: (String) -> Boolean): Boolean {
    val spelling = word.replace(TYPOGRAPHIC_APOSTROPHE, '\'')
    return when {
        !judges(spelling, language) || dictionary.knows(spelling) || isOwn(spelling) -> true
        '-' in spelling -> spelling.split('-').all { part ->
            part.isNotEmpty() && isSpelledRight(part, language, dictionary, isOwn)
        }
        else -> false
    }
}

/** Whether a spell checker of [language] judges [word]: one in its script, with no digit. */
private fun judges(word: String, language: Language): Boolean =
    word.none { it.isDigit() } && word.any { Character.UnicodeScript.of(it.code) == language.script }

/**
 * Whether this model knows [word] as spelled right in [language], by its dictionary and the user's trusted words:
 * see [isSpelledRight]. The words of the system's personal dictionary are the spell checker's alone.
 */
fun LanguageModel.knows(word: String, language: Language): Boolean =
    isSpelledRight(word, language, dictionary) { personal.learned(it)?.trusted == true }

/**
 * What a spell checker knows of [language]: the words of [dictionary], the user's [learned] words that are
 * [trusted], and [userWords], which the user added to the system's own dictionary. [removed] holds the words the
 * user asked never to be suggested, in lowercase. It keeps its own copies, so another thread can use it while the
 * keyboard learns on.
 */
class Spelling(
    private val language: Language,
    private val dictionary: Dictionary,
    learned: List<Learned>,
    private val removed: Set<String>,
    userWords: Set<String>,
) {
    private val trusted = learned.filter { it.trusted }
    private val known = (trusted.map { it.spelling } + userWords).map { it.lowercase(Locale.ROOT) }.toSet()

    /** The user's own words as corrections, scored as [suggestions] scores them. */
    private val own = trusted.map { Scored(it.spelling, it.count * WORD_USE) } + userWords.map { Scored(it, WORD_USE) }

    /**
     * Up to [limit] corrections of [word], the nearest and then the most frequent first, cased like [word]; or null
     * when [word] is spelled right. A word in another script than the language's, or with a digit, is not judged.
     */
    fun check(word: String, limit: Int): List<String>? {
        val spelling = word.replace(TYPOGRAPHIC_APOSTROPHE, '\'')
        val right = isSpelledRight(spelling, language, dictionary) { it.lowercase(Locale.ROOT) in known }
        return if (right) null else corrections(spelling, limit)
    }

    private fun corrections(word: String, limit: Int): List<String> {
        val maxDistance = if (foldedKey(word).length <= SHORT_WORD) 1 else 2
        val ownNear = own.map { Near(it.word, editDistance(it.word, word), it.score) }
        return (dictionary.near(word, maxDistance) + ownNear.filter { it.distance <= maxDistance }).asSequence()
            .filterNot { it.word.lowercase(Locale.ROOT) in removed }
            .sortedWith(compareBy<Near> { it.distance }.thenByDescending { it.frequency })
            .map { casedLike(it.word, word) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(limit)
            .toList()
    }
}

/** What [LanguageModel] knows of the spelling of [language], with the user's [userWords]: see [Spelling]. */
fun LanguageModel.spelling(language: Language, userWords: Set<String>): Spelling =
    Spelling(language, dictionary, personal.all(), personal.removedWords, userWords)
