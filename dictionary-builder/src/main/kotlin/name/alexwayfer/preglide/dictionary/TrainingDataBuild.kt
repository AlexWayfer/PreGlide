package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.predict.Vocabulary
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import kotlin.time.measureTime

/** A file of token ids in the training data, by its name, and the share of the training it makes up. */
data class TokenSource(val file: String, val share: Double)

/**
 * Builds the prediction model's training data from the texts the dictionaries are counted in: for each language,
 * into `training/<code>` of [cache], the model's vocabulary, taken from the dictionary file in [dictionaries], and
 * a file of token ids for each text, listed with its share in `sources.tsv`. Downloads stay in [cache], and a
 * token file stays until the vocabulary changes, since its name holds the vocabulary's hash. A FineWeb file is
 * read in steps of [stepDocuments] documents.
 */
class TrainingDataBuild(
    private val cache: Path,
    private val dictionaries: Path,
    private val fetch: (url: String, target: Path) -> Unit,
    private val report: (String) -> Unit,
    private val stepDocuments: Int = STEP_DOCUMENTS,
) {
    fun run(corpus: Corpus) {
        val took = measureTime {
            val words = Files.newBufferedReader(dictionaries.resolve("${corpus.code}.tsv")).useLines {
                dictionaryWords(it)
            }
            val vocabulary = vocabularyOf(words, corpus)
            val folder = Files.createDirectories(cache.resolve("training").resolve(corpus.code))
            Files.writeString(folder.resolve("vocabulary.txt"), vocabulary.text())
            val files = TokenFiles(folder, wordsKey(vocabulary.tokens, emptyList()), vocabulary)
            val webShareEach = webShare(corpus) / corpus.sources.size
            val web = corpus.sources.map { TokenSource(webTokensOf(it, files), webShareEach) }
            val subtitles = corpus.subtitles?.let { TokenSource(subtitleTokensOf(it, files), it.share) }
            Files.writeString(folder.resolve("sources.tsv"), sourcesText(web + listOfNotNull(subtitles)))
        }
        report("${corpus.code}: training data built in ${duration(took)}")
    }

    /** Where the token files of one vocabulary go: [folder], named with the vocabulary's [key]. */
    private class TokenFiles(val folder: Path, val key: String, val vocabulary: Vocabulary) {
        /** The token file of [text], or of its every [every]th line, named so: a sample is a file of its own. */
        fun of(text: Path, every: Int = 1): Path {
            val sample = if (every > 1) "-every$every" else ""
            return folder.resolve("${text.fileName}.tokens-$key$sample.bin")
        }
    }

    /** Writes the tokens of the FineWeb file at [url], a document a text, unless they are written already. */
    private fun webTokensOf(url: String, files: TokenFiles): String {
        val text = cache.resolve(cacheName(url))
        val tokens = files.of(text)
        if (!Files.exists(tokens)) {
            fetch(url, text)
            writeTokens(tokens) { writer ->
                DuckDb.open(cache).use { writeWebSteps(it, text, writer, files.vocabulary) }
            }
        }
        return tokens.fileName.toString()
    }

    /** Writes the tokens of the FineWeb file [text] with [writer], a step of documents at a time. */
    private fun writeWebSteps(db: DuckDb, text: Path, writer: TokenWriter, vocabulary: Vocabulary) {
        val steps = db.steps(text, stepDocuments)
        for (index in 0 until steps) {
            val first = index.toLong() * stepDocuments
            val took = measureTime {
                db.query("SELECT text FROM ${stepRows(text, first, stepDocuments)}") { getString(1) }
                    .forEach { writer.write(vocabulary.textIds(it)) }
            }
            report(progressLine("${text.fileName} tokens", CountProgress.Step(index + 1, steps, took)))
        }
    }

    /**
     * Writes the tokens of [subtitles], unless they are written already. A line is a text of its own: subtitles do
     * not tell where a film ends, and a short line that starts afresh is much like a message typed in a chat. Only
     * every [Subtitles.trainingEvery]th line is written.
     */
    private fun subtitleTokensOf(subtitles: Subtitles, files: TokenFiles): String {
        val text = cache.resolve(cacheName(subtitles.text))
        val every = subtitles.trainingEvery
        val tokens = files.of(text, every)
        if (!Files.exists(tokens)) {
            fetch(subtitles.text, text)
            writeTokens(tokens) { writer ->
                GZIPInputStream(Files.newInputStream(text)).bufferedReader().useLines { lines ->
                    lines.filterIndexed { index, _ -> index % every == 0 }
                        .forEach { writer.write(files.vocabulary.textIds(it)) }
                }
            }
        }
        return tokens.fileName.toString()
    }

    /**
     * Writes the token file [target] with [write], first under another name, so a stopped build leaves no file to
     * mistake for a finished one, and reports what was written.
     */
    private fun writeTokens(target: Path, write: (TokenWriter) -> Unit) {
        val partial = target.resolveSibling("${target.fileName}.part")
        val took = measureTime {
            TokenWriter(partial).use { writer ->
                write(writer)
                report(tokensSummary(target.fileName.toString(), writer.written, writer.unknown))
            }
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        report("${target.fileName}: written in ${duration(took)}")
    }

    companion object {
        /** A build in the project at [root]: its cache in the tool's folder, the dictionaries in the app's assets. */
        fun inProject(root: Path, client: HttpClient, report: (String) -> Unit) = TrainingDataBuild(
            cache = root.resolve(Path.of("dictionary-builder", "cache")),
            dictionaries = root.resolve(Path.of("app", "src", "main", "assets", "dictionaries")),
            fetch = Downloader(client, report)::fetch,
            report = report,
        )
    }
}
