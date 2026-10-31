package name.alexwayfer.preglide.engine.suggest

import java.io.Reader

/** A word to suggest and how strongly: its frequency per billion, the user's own use added. */
data class Scored(val word: String, val score: Long)

/**
 * The words that most often come after a word, the most frequent first, for suggesting the next word before the
 * user types it. A word is looked up by its [foldedKey], so "Как" and "как" share their next words.
 */
class NextWords private constructor(private val followers: Map<String, Followers>) {
    /** A word's next words and their frequencies, in arrays: a list of objects would take several times the memory. */
    private class Followers(val words: Array<String>, val frequencies: LongArray)

    val size: Int get() = followers.size

    /** The words that come after [previous], the most frequent first; empty for a word with none known. */
    fun after(previous: String): List<String> = followers[foldedKey(previous)]?.words?.toList().orEmpty()

    /** [after] with each word's frequency, per billion pairs. */
    fun scoredAfter(previous: String): List<Scored> {
        val found = followers[foldedKey(previous)] ?: return emptyList()
        return found.words.indices.map { Scored(found.words[it], found.frequencies[it]) }
    }

    companion object {
        val None = NextWords(emptyMap())

        /**
         * Next words from the lines of a next words file: a word, the word after it and how often the two come
         * together, separated by tabs, each word's next words the most frequent first. The dictionary builder
         * writes the next word as its line in the dictionary file, which takes no search to find; a word spelled
         * out is searched for. A next word missing from [dictionary] is skipped; the others are the dictionary's
         * own copies, so each word is in memory once.
         */
        fun parse(lines: Sequence<String>, dictionary: Dictionary): NextWords {
            val found = LinkedHashMap<String, MutableList<Scored>>()
            // A word's next words come on lines one after another: its key is made once for them all. The lines
            // are many, so they are cut by hand, with no list of fields made for each.
            var previous = ""
            var key = ""
            for (line in lines) {
                if (line.isBlank()) continue
                val first = line.indexOf('\t')
                val second = line.indexOf('\t', first + 1)
                require(first in 1..<second && line.indexOf('\t', second + 1) < 0) {
                    "A next words line needs a word, the next word and a count: $line"
                }
                if (first != previous.length || !line.startsWith(previous)) {
                    previous = line.substring(0, first)
                    key = foldedKey(previous)
                }
                // A lookup per line is cheap: the key's string keeps its hash once made.
                val list = found.getOrPut(key) { ArrayList() }
                val next = nextWord(line, first + 1, second, dictionary)
                if (next != null && list.none { it.word == next }) {
                    list.add(Scored(next, line.substring(second + 1).trim().toLong()))
                }
            }
            val followers = found.filterValues { it.isNotEmpty() }.mapValues { (_, next) ->
                Followers(Array(next.size) { next[it].word }, LongArray(next.size) { next[it].score })
            }
            return NextWords(followers)
        }

        fun read(reader: Reader, dictionary: Dictionary): NextWords =
            reader.buffered().useLines { parse(it, dictionary) }

        /** The next word between [start] and [end] of [line]: a line of [dictionary]'s file, or a word to find. */
        private fun nextWord(line: String, start: Int, end: Int, dictionary: Dictionary): String? {
            var number = 0
            for (index in start until end) {
                val digit = line[index] - '0'
                if (digit !in 0..DIGIT_MAX) return dictionary.find(line.substring(start, end))
                number = number * BASE + digit
            }
            return dictionary.atLine(number)
        }

        private const val BASE = 10
        private const val DIGIT_MAX = 9
    }
}

/**
 * A language's words, the words that come after them, and what the user's typing taught: all that suggestions
 * need, but for the prediction model, which loads apart and only when the user wants it.
 */
class LanguageModel(
    val dictionary: Dictionary,
    val nextWords: NextWords = NextWords.None,
    val personal: PersonalWords = PersonalWords(),
)
