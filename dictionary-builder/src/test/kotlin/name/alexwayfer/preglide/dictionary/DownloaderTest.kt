package name.alexwayfer.preglide.dictionary

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.file.Files

class DownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file = ByteArray(1000) { it.toByte() }
    private val ranges = mutableListOf<String?>()
    private var honorsRange = true
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/file.parquet", ::serve)
        createContext("/missing.parquet") { it.sendResponseHeaders(404, -1); it.close() }
        start()
    }
    private val reports = mutableListOf<String>()
    private val downloader = Downloader(HttpClient.newHttpClient()) { reports += it }
    private val target get() = folder.root.toPath().resolve("file.parquet")
    private val partial get() = folder.root.toPath().resolve("file.parquet.part")

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun serve(exchange: HttpExchange) {
        val range = exchange.requestHeaders.getFirst("Range")
        ranges += range
        val start = range?.takeIf { honorsRange }?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()
        val body = file.copyOfRange(start ?: 0, file.size)
        exchange.sendResponseHeaders(if (start != null) 206 else 200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    @After
    fun stopServer() = server.stop(0)

    @Test
    fun `a download writes the whole file and reports its progress`() {
        downloader.fetch(url("/file.parquet"), target)

        assertArrayEquals(file, Files.readAllBytes(target))
        assertFalse(Files.exists(partial))
        assertEquals(listOf<String?>(null), ranges)
        assertEquals("file.parquet: 100%", reports.last())
    }

    @Test
    fun `a broken download resumes where it stopped`() {
        Files.write(partial, file.copyOfRange(0, 400))

        downloader.fetch(url("/file.parquet"), target)

        assertArrayEquals(file, Files.readAllBytes(target))
        assertEquals(listOf<String?>("bytes=400-"), ranges)
    }

    @Test
    fun `a server that ignores the range sends the file again from the start`() {
        Files.write(partial, file.copyOfRange(0, 400))
        honorsRange = false

        downloader.fetch(url("/file.parquet"), target)

        assertArrayEquals(file, Files.readAllBytes(target))
    }

    @Test
    fun `a download of unknown size is written whole`() {
        server.createContext("/chunked.parquet") { exchange ->
            // Length 0 makes the server send the body in chunks, without a Content-Length.
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(file) }
        }

        downloader.fetch(url("/chunked.parquet"), target)

        assertArrayEquals(file, Files.readAllBytes(target))
        assertEquals(emptyList<String>(), reports)
    }

    @Test
    fun `a file already there is not downloaded again`() {
        Files.write(target, byteArrayOf(1))

        downloader.fetch(url("/file.parquet"), target)

        assertEquals(emptyList<String?>(), ranges)
    }

    @Test
    fun `an HTTP error fails the download and leaves no file`() {
        assertThrows(IllegalStateException::class.java) { downloader.fetch(url("/missing.parquet"), target) }
        assertFalse(Files.exists(target))
    }

    @Test
    fun `progress is reported once per step`() {
        val steps = mutableListOf<Int>()

        // The network hands the body over in pieces: here ten bytes at a time.
        val pieces = object : FilterInputStream(ByteArrayInputStream(ByteArray(50))) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 10))
        }

        copy(pieces, ByteArrayOutputStream(), start = 50, total = 100) { steps += it }

        assertEquals(listOf(60, 70, 80, 90, 100), steps)
    }

    @Test
    fun `an unknown size reports no progress`() {
        val steps = mutableListOf<Int>()

        copy(ByteArrayInputStream(ByteArray(50)), ByteArrayOutputStream(), start = 0, total = -1) { steps += it }

        assertEquals(emptyList<Int>(), steps)
    }

    @Test
    fun `progress rounds down to a step`() {
        assertEquals(30, percentStep(399, 1000))
        assertEquals(0, percentStep(5, 0))
    }
}
