package name.alexwayfer.preglide.dictionary

import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.time.measureTime

/** How many of the most frequent and of the rarest kept words the summary shows, to judge the list by eye. */
private const val SAMPLE = 20

/** How many of the most frequent words the summary shows the next words of. */
private const val SAMPLE_PREVIOUS = 8
private const val PERCENT = 100.0

/** How many hex digits of a word list's hash name the pairs counted for it. */
private const val WORDS_KEY_LENGTH = 12

/** Names the counts' format: a change to how words are counted renames it, so old counts are not read. */
private const val COUNTS_VERSION = "counts-by-sentence"

/** [COUNTS_VERSION] for the counts of a subtitle text, a line a sentence. */
private const val LINE_COUNTS_VERSION = "counts-by-line"

/**
 * Builds dictionaries: downloads each source into [cache] with [fetch], counts its words and pairs of words
 * there, and writes each dictionary and its next words into [output]. Downloads and counts stay in [cache], so
 * the next build with other rules for picking words only reads the counts. A count goes in steps of
 * [stepDocuments] documents.
 */
class DictionaryBuild(
    private val cache: Path,
    private val output: Path,
    private val fetch: (url: String, target: Path) -> Unit,
    private val report: (String) -> Unit,
    private val stepDocuments: Int = STEP_DOCUMENTS,
) {
    fun run(corpus: Corpus) {
        Files.createDirectories(cache)
        Files.createDirectories(output)
        val took = measureTime {
            DuckDb.open(cache).use { db ->
                val yo = yoOf(corpus)
                val words = buildDictionary(corpus, WordCounter(db, stepDocuments), yo)
                buildNextWords(corpus, words, PairCounter(db, stepDocuments), yo)
            }
        }
        report("${corpus.code}: built in ${duration(took)}")
    }

    /**
     * Writes [corpus]'s dictionary, with ё put back by [yo], and returns its words as the text spells them: the
     * pairs are counted for those, and the counts stay good when the ё list changes.
     */
    private fun buildDictionary(corpus: Corpus, counter: WordCounter, yo: Map<String, String>): List<Word> {
        val counts = corpus.sources.map { countsOf(it, counter) }
        val web = Counts(counter.forms(counts, corpus), counter.total(counts, corpus))
        val subtitles = corpus.subtitles?.let { subtitlesOf(it, corpus, counter) }
        val words = dictionary(blend(listOfNotNull(web to webShare(corpus), subtitles)), corpus, spelledOf(corpus))
        val spelled = withYo(words, yo)
        Files.writeString(output.resolve("${corpus.code}.tsv"), dictionaryText(spelled))
        report(summary(corpus, spelled))
        if (corpus.spelling != null) {
            val below = (words.size - corpus.size).coerceAtLeast(0)
            report("${corpus.code}: $below words below the cut let in by the spelling dictionary")
        }
        if (yo.isNotEmpty()) report("${corpus.code}: ё put back in ${yoCount(words, yo)} words")
        return words
    }

    /** The forms [corpus]'s spelling dictionary gives, or none when it has none. */
    private fun spelledOf(corpus: Corpus): Set<String> {
        val spelling = corpus.spelling ?: return emptySet()
        val (affixes, stems) = listOf(spelling.affixes, spelling.stems).map { url ->
            cache.resolve(cacheName(url)).also { fetch(url, it) }
        }
        return Files.newBufferedReader(affixes).useLines { affixLines ->
            Files.newBufferedReader(stems).useLines { wordForms(affixLines, it) }
        }
    }

    /** The words that always take ё, from [corpus]'s ё list, or none when it has no list. */
    private fun yoOf(corpus: Corpus): Map<String, String> {
        val url = corpus.yoList ?: return emptyMap()
        val list = cache.resolve(cacheName(url))
        fetch(url, list)
        return Files.newBufferedReader(list).useLines(::yoWords)
    }

    /** The counts of [subtitles]' words with their share: from their frequency list, or counted in their text. */
    private fun subtitlesOf(subtitles: Subtitles, corpus: Corpus, counter: WordCounter): Pair<Counts, Double> {
        val frequencies = subtitles.frequencies
        val counts = if (frequencies == null) {
            subtitleTextCounts(subtitles, corpus, counter)
        } else {
            val list = cache.resolve(cacheName(frequencies))
            fetch(frequencies, list)
            GZIPInputStream(Files.newInputStream(list)).bufferedReader().useLines { subtitleCounts(it, corpus) }
        }
        return counts to subtitles.share
    }

    /** The words of [subtitles]' text, counted once into the cache and read from there afterward. */
    private fun subtitleTextCounts(subtitles: Subtitles, corpus: Corpus, counter: WordCounter): Counts {
        val text = cache.resolve(cacheName(subtitles.text))
        val counts = cache.resolve("${text.fileName}.$LINE_COUNTS_VERSION.parquet")
        if (!Files.exists(counts)) {
            fetch(subtitles.text, text)
            report("${text.fileName}: counting words")
            val took = measureTime {
                counter.countLines(text, counts) { report(progressLine("${text.fileName} words", it)) }
            }
            report("${text.fileName}: words counted in ${duration(took)}")
        }
        return Counts(counter.forms(listOf(counts), corpus), counter.total(listOf(counts), corpus))
    }

    private fun countsOf(url: String, counter: WordCounter): Path {
        val text = cache.resolve(cacheName(url))
        val counts = cache.resolve("${text.fileName}.$COUNTS_VERSION.parquet")
        if (!Files.exists(counts)) {
            fetch(url, text)
            report("${text.fileName}: documents by dump ${counter.dumps(text)}")
            counter.count(text, counts) { report(progressLine("${text.fileName} words", it)) }
        }
        return counts
    }

    /**
     * Counts the pairs of [words], the dictionary just built, in the same texts and writes their next words.
     * The pairs counted depend on the words, so their files are named after the words' hash.
     */
    private fun buildNextWords(corpus: Corpus, words: List<Word>, counter: PairCounter, yo: Map<String, String>) {
        val previous = words.take(PREDICTED_AFTER).map { it.spelling.lowercase(Locale.ROOT) }
        val next = words.map { it.spelling.lowercase(Locale.ROOT) }
        val key = wordsKey(previous, next)
        counter.useWords(previous, next, cache.resolve("words"))
        val web = counter.pairs(corpus.sources.map { webPairsOf(it, key, counter) }) to webShare(corpus)
        val subtitles = corpus.subtitles?.let { counter.pairs(listOf(subtitlePairsOf(it, key, counter))) to it.share }
        val result = nextWords(listOfNotNull(web, subtitles), words, yo)
        Files.writeString(output.resolve("${corpus.code}.next.tsv"), nextWordsText(result, withYo(words, yo)))
        report(nextWordsSummary(corpus, result))
    }

    private fun webPairsOf(url: String, key: String, counter: PairCounter): Path {
        val text = cache.resolve(cacheName(url))
        val pairs = cache.resolve("${text.fileName}.pairs-$key.parquet")
        if (!Files.exists(pairs)) {
            fetch(url, text)
            counter.count(text, pairs) { report(progressLine("${text.fileName} pairs", it)) }
        }
        return pairs
    }

    private fun subtitlePairsOf(subtitles: Subtitles, key: String, counter: PairCounter): Path {
        val text = cache.resolve(cacheName(subtitles.text))
        val pairs = cache.resolve("${text.fileName}.pairs-$key.parquet")
        if (!Files.exists(pairs)) {
            fetch(subtitles.text, text)
            report("${text.fileName}: counting pairs")
            val took = measureTime {
                counter.countLines(text, pairs) { report(progressLine("${text.fileName} pairs", it)) }
            }
            report("${text.fileName}: pairs counted in ${duration(took)}")
        }
        return pairs
    }

    companion object {
        /** A build in the project at [root]: its cache in the tool's folder, its dictionaries in the app's assets. */
        fun inProject(root: Path, client: HttpClient, report: (String) -> Unit) = DictionaryBuild(
            cache = root.resolve(Path.of("dictionary-builder", "cache")),
            output = root.resolve(Path.of("app", "src", "main", "assets", "dictionaries")),
            fetch = Downloader(client, report)::fetch,
            report = report,
        )
    }
}

/** The share of the web text in [corpus]'s frequencies and training data: what its subtitles leave. */
fun webShare(corpus: Corpus): Double = 1.0 - (corpus.subtitles?.share ?: 0.0)

/** The file name a download from [url] gets in the cache: its whole path, so two sources never share one. */
fun cacheName(url: String): String = URI(url).path.removePrefix("/").replace('/', '_')

/** A short name for the word lists [previous] and [next] pairs are counted for: the same lists, the same name. */
fun wordsKey(previous: List<String>, next: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (word in previous + "" + next) digest.update("$word\n".toByteArray())
    return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }.take(WORDS_KEY_LENGTH)
}

/** What a build made of [corpus]: how much of the text [words] cover, and samples to check them by eye. */
fun summary(corpus: Corpus, words: List<Word>): String {
    val percent = String.format(Locale.ROOT, "%.2f", coverage(words, PER_BILLION) * PERCENT)
    return """
        |${corpus.code}: ${words.size} words, $percent% of all word occurrences in the text
        |Most frequent: ${words.take(SAMPLE).joinToString(" ") { it.spelling }}
        |Rarest kept: ${words.takeLast(SAMPLE).joinToString(" ") { it.spelling }}
    """.trimMargin()
}

/** What a build made of [corpus]'s next words: for how many words, and those of the most frequent ones. */
fun nextWordsSummary(corpus: Corpus, next: List<NextWord>): String {
    val byWord = next.groupBy { it.previous }
    val samples = byWord.entries.take(SAMPLE_PREVIOUS).joinToString("\n") { (word, after) ->
        "$word → ${after.joinToString(" ") { it.next }}"
    }
    return "${corpus.code}: next words for ${byWord.size} words\n$samples"
}
