package name.alexwayfer.preglide.dictionary

import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Duration
import java.time.LocalTime

private const val CONNECT_TIMEOUT_SECONDS = 30L

/** Builds the dictionaries of the languages named in [args], such as `ru`, or of all languages for none. */
fun main(args: Array<String>) {
    // Each line with its time, to see in the log what ran when and what was slow.
    val build = DictionaryBuild.inProject(Path.of(""), httpClient()) { println(timestamped(it, LocalTime.now())) }
    corporaFor(args).forEach(build::run)
}

/** An HTTP client for Hugging Face, which answers a file request with a redirect to its file storage. */
fun httpClient(): HttpClient =
    HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()

/** The corpora of the language codes in [args], or all of them for none. */
fun corporaFor(args: Array<String>): List<Corpus> =
    if (args.isEmpty()) {
        Corpus.all
    } else {
        args.map { code ->
            requireNotNull(Corpus.all.firstOrNull { it.code == code }) {
                "Unknown language $code; known: ${Corpus.all.joinToString { it.code }}"
            }
        }
    }
