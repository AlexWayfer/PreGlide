package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.suggest.compareFolded
import name.alexwayfer.preglide.engine.suggest.isDictionaryGarbage
import java.util.Locale
import kotlin.math.roundToLong

/**
 * A word as the text spells it, case included. [occurrences] counts at most [PER_DOCUMENT_CAP] per document;
 * [midSentence] counts those not at the start of a sentence, where a capital letter says the word is a name.
 * A text that does not tell where sentences start, as a frequency list, gives no [midSentence] count.
 */
data class Form(val text: String, val occurrences: Long, val midSentence: Long = 0)

/** A dictionary word: the spelling to suggest and its counts over all its spellings. */
data class Word(val spelling: String, val occurrences: Long)

/**
 * How many times one document counts a word. A spam page repeats its keyword thousands of times; a real word
 * comes up a few times at most, so the cap leaves real words alone and puts spam in its place.
 */
const val PER_DOCUMENT_CAP = 5

/**
 * The share of a word's occurrences in lowercase that makes it a lowercase word. Within a sentence a common word
 * is in lowercase almost always; a name rarely is, though SEO text writes "купить диван москва".
 */
private const val LOWERCASE_SHARE = 0.3

/** Words longer than this are run-together text or chemistry, not something to type. */
private const val MAX_LENGTH = 32

/** The forms counted in one text, and all occurrences of the language's words there, the rare ones included. */
data class Counts(val forms: List<Form>, val total: Long)

/** Blended frequencies count occurrences per billion words, so texts of any size mix by their shares alone. */
const val PER_BILLION = 1_000_000_000L

/**
 * Mixes the counts of several texts, each with its share: a form's frequency is its weighted share of each text.
 * The [Form.midSentence] counts add up as they are: they only compare the spellings of one word with each other.
 */
fun blend(sources: List<Pair<Counts, Double>>): List<Form> {
    val frequencies = HashMap<String, Double>()
    val midSentences = HashMap<String, Long>()
    for ((counts, share) in sources) {
        if (counts.total == 0L) continue
        for ((text, occurrences, midSentence) in counts.forms) {
            frequencies.merge(text, share * PER_BILLION * occurrences / counts.total, Double::plus)
            midSentences.merge(text, midSentence, Long::plus)
        }
    }
    return frequencies
        .map { (text, frequency) -> Form(text, frequency.roundToLong(), midSentences.getValue(text)) }
        .filter { it.occurrences > 0 }
}

/**
 * The words of [corpus] in a subtitle frequency list: a line per token, its count and the token after a space.
 * Punctuation tokens and words of other languages are left out of the total too.
 */
fun subtitleCounts(lines: Sequence<String>, corpus: Corpus): Counts {
    val word = Regex(corpus.wordPattern)
    val forms = lines.mapNotNull { line ->
        val trimmed = line.trim()
        val space = trimmed.indexOf(' ')
        if (space < 0) return@mapNotNull null
        val text = trimmed.substring(space + 1).replace('’', '\'')
        if (word.matches(text)) Form(text, trimmed.substring(0, space).toLong()) else null
    }.toList()
    return Counts(forms, forms.sumOf { it.occurrences })
}

/** Merges the spellings of each word that differ in case only, into one word under its usual spelling. */
fun mergeCase(forms: List<Form>): List<Word> =
    forms.groupBy { it.text.lowercase(Locale.ROOT) }.values.map { spellings ->
        Word(spelling(spellings), spellings.sumOf { it.occurrences })
    }

/**
 * The spelling to suggest among those of one word: lowercase when the word comes in lowercase often enough,
 * otherwise the most frequent one, as "Москва" or "iPhone". Counted within sentences when the text tells where
 * they start: a sentence capitalizes "Привет" as well as "Москва". A word known only from frequency lists is
 * counted everywhere.
 */
fun spelling(spellings: List<Form>): String {
    val votes: (Form) -> Long = if (spellings.any { it.midSentence > 0 }) Form::midSentence else Form::occurrences
    val lowercase = spellings.firstOrNull { it.text == it.text.lowercase(Locale.ROOT) }
    val total = spellings.sumOf(votes)
    return if (lowercase != null && votes(lowercase) >= total * LOWERCASE_SHARE) {
        lowercase.text
    } else {
        spellings.minWith(compareByDescending(votes).thenBy { it.text }).text
    }
}

/** Whether [corpus] keeps [word]: not corrupted, too long, or a single letter other than a real word. */
fun keeps(word: Word, corpus: Corpus): Boolean =
    !isDictionaryGarbage(word.spelling) && word.spelling.length <= MAX_LENGTH &&
        (word.spelling.length > 1 || word.spelling.lowercase(Locale.ROOT) in corpus.singleLetterWords)

/**
 * The words of [corpus]'s dictionary from all [forms] counted for it, the most frequent first: [Corpus.size] of
 * them, then the rarer words down to the floor of its [Corpus.spelling] that are among [spelled], the forms that
 * spelling dictionary gives (see [wordForms]).
 */
fun dictionary(forms: List<Form>, corpus: Corpus, spelled: Set<String> = emptySet()): List<Word> {
    val words = mergeCase(forms)
        .filter { keeps(it, corpus) }
        .sortedWith(compareByDescending<Word> { it.occurrences }.thenBy { it.spelling })
    val floor = corpus.spelling?.floor ?: return words.take(corpus.size)
    return words.take(corpus.size) +
        words.drop(corpus.size).takeWhile { it.occurrences >= floor }.filter { formKey(it.spelling) in spelled }
}

/** The share of all [total] word occurrences in the text that [words] cover. */
fun coverage(words: List<Word>, total: Long): Double =
    if (total == 0L) 0.0 else words.sumOf { it.occurrences }.toDouble() / total

/**
 * [words], the most frequent first, in the order the keyboard searches them: the order the dictionary file keeps,
 * so the keyboard has nothing to sort as it loads. Words a search sees as one, as все and всё, keep their order.
 */
fun searchOrder(words: List<Word>): List<Word> = words.sortedWith { a, b -> compareFolded(a.spelling, b.spelling) }

/** The dictionary file: a line per word, in [searchOrder], its spelling and its count separated by a tab. */
fun dictionaryText(words: List<Word>): String =
    searchOrder(words).joinToString("") { "${it.spelling}\t${it.occurrences}\n" }
