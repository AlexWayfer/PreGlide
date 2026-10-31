package name.alexwayfer.preglide.dictionary

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

private const val HTTP_OK = 200
private const val HTTP_PARTIAL_CONTENT = 206
private const val PERCENT = 100

/** How often a download reports its progress, in percent. */
private const val REPORT_STEP = 10

/** Downloads FineWeb files, which take gigabytes: a download that breaks resumes where it stopped. */
class Downloader(private val client: HttpClient, private val report: (String) -> Unit) {
    /** Downloads [url] to [target], unless [target] is already there. */
    fun fetch(url: String, target: Path) {
        if (Files.exists(target)) return
        val partial = target.resolveSibling("${target.fileName}.part")
        val done = if (Files.exists(partial)) Files.size(partial) else 0L
        val request = HttpRequest.newBuilder(URI(url))
            .apply { if (done > 0) header("Range", "bytes=$done-") }
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            val status = response.statusCode()
            check(status == HTTP_OK || status == HTTP_PARTIAL_CONTENT) { "$url: HTTP $status" }
            // A server that ignores the range sends the whole file again.
            val resumed = status == HTTP_PARTIAL_CONTENT
            val start = if (resumed) done else 0L
            val length = response.headers().firstValueAsLong("Content-Length").orElse(-1)
            val total = if (length < 0) -1 else start + length
            val mode = if (resumed) StandardOpenOption.APPEND else StandardOpenOption.TRUNCATE_EXISTING
            Files.newOutputStream(partial, StandardOpenOption.CREATE, StandardOpenOption.WRITE, mode).use { out ->
                copy(body, out, start, total) { report("${target.fileName}: $it%") }
            }
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Copies [input] to [output], with [start] bytes of the [total] there already, and calls [progress] with the
 * percent done each time it passes another [REPORT_STEP]. An unknown [total] is negative and reports nothing.
 */
fun copy(input: InputStream, output: OutputStream, start: Long, total: Long, progress: (Int) -> Unit) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var done = start
    var reported = percentStep(done, total)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        done += read
        val step = percentStep(done, total)
        if (step > reported) {
            reported = step
            progress(step)
        }
    }
}

/** [done] of [total] in percent, rounded down to a [REPORT_STEP]; 0 for an unknown or empty [total]. */
fun percentStep(done: Long, total: Long): Int =
    if (total <= 0) 0 else (done * PERCENT / total).toInt() / REPORT_STEP * REPORT_STEP
