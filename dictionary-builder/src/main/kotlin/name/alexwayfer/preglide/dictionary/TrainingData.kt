package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.predict.Vocabulary
import java.util.Locale

/** The words of a dictionary file, as [dictionaryText] writes it: a line per word, its spelling and frequency. */
fun dictionaryWords(lines: Sequence<String>): List<Word> =
    lines.filter { it.isNotBlank() }.map { line ->
        val tab = line.indexOf('\t')
        require(tab > 0) { "A dictionary line needs a word and its frequency: $line" }
        Word(line.substring(0, tab), line.substring(tab + 1).trim().toLong())
    }.toList()

/** The vocabulary of [corpus]'s prediction model: the [Corpus.vocabularySize] most frequent [words], lowercase. */
fun vocabularyOf(words: List<Word>, corpus: Corpus): Vocabulary =
    Vocabulary(
        words.sortedWith(compareByDescending<Word> { it.occurrences }.thenBy { it.spelling })
            .take(corpus.vocabularySize)
            .map { it.spelling.lowercase(Locale.ROOT) },
    )

/** The list of the training data's files: a line per file, its name and its share, separated by a tab. */
fun sourcesText(sources: List<TokenSource>): String =
    sources.joinToString("") { "${it.file}\t${String.format(Locale.ROOT, "%.4f", it.share)}\n" }

/** What writing a file of [written] token ids, [unknown] of them unknown words, came to. */
fun tokensSummary(name: String, written: Long, unknown: Long): String {
    val percent = if (written == 0L) 0.0 else unknown * PERCENT / written
    return String.format(Locale.ROOT, "%s: %,d tokens, %.2f%% unknown words", name, written, percent)
}

private const val PERCENT = 100.0
