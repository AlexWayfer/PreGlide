package name.alexwayfer.preglide.dictionary

import java.util.Locale

/*
 * Web text often writes е for ё: "еще" comes up twice as often as "ещё". A list of the words that always take ё
 * puts it back in the dictionary, so the keyboard suggests "ещё". Words where both spellings are words, as "все"
 * and "всё", are not on that list and stay as the text has them.
 */

/** A list line's ending options: "колёс(|ам|ами|ах)" is "колёс", "колёсам" and so on. */
private val endings = Regex("""\(([^)]*)\)$""")

/**
 * The words of a ё list, as eyo's "safe" dictionary writes them: a line per word stem, with its endings in
 * brackets, and a comment after "#". Keyed by the word with е for ё, lowercase, as a text that drops ё has it.
 */
fun yoWords(lines: Sequence<String>): Map<String, String> {
    val words = HashMap<String, String>()
    for (line in lines) {
        val entry = line.substringBefore('#').trim().lowercase(Locale.ROOT)
        if ('ё' !in entry) continue
        val options = endings.find(entry)
        val forms = if (options == null) {
            listOf(entry)
        } else {
            val stem = entry.substring(0, options.range.first)
            options.groupValues[1].split('|').map { stem + it }
        }
        for (form in forms) words[form.replace('ё', 'е')] = form
    }
    return words
}

/** [spelling] with ё where [yo], a ё list, has it for the word, the case of each letter kept. */
fun withYo(spelling: String, yo: Map<String, String>): String {
    val word = yo[spelling.lowercase(Locale.ROOT).replace('ё', 'е')] ?: return spelling
    return String(CharArray(spelling.length) { i ->
        when {
            word[i] != 'ё' -> spelling[i]
            spelling[i].isUpperCase() -> 'Ё'
            else -> 'ё'
        }
    })
}

/** How many of [words] [yo] puts ё back in. */
fun yoCount(words: List<Word>, yo: Map<String, String>): Int = words.count { withYo(it.spelling, yo) != it.spelling }

/**
 * [words] with ё put back by [yo]: two spellings of a word, as "еще" and "ещё", become one, with both their
 * counts; the most frequent first, as a dictionary keeps them.
 */
fun withYo(words: List<Word>, yo: Map<String, String>): List<Word> =
    words.groupBy { withYo(it.spelling, yo).lowercase(Locale.ROOT) }.values
        .map { spellings ->
            val usual = spellings.maxBy { it.occurrences }
            Word(withYo(usual.spelling, yo), spellings.sumOf { it.occurrences })
        }
        .sortedWith(compareByDescending<Word> { it.occurrences }.thenBy { it.spelling })
