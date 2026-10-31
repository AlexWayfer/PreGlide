package name.alexwayfer.preglide.dictionary

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.file.Files

class EmojiBuildTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val englishAnnotations = """
        <ldml><annotations>
            <annotation cp="😀">face | grin</annotation>
            <annotation cp="😀" type="tts">grinning face</annotation>
        </annotations></ldml>
    """.trimIndent()

    private val files = mapOf(
        "/emoji-test.txt" to emojiTestSample,
        "/ru.xml" to russianAnnotations,
        "/en.xml" to englishAnnotations,
    )
    private val requests = mutableListOf<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests += path
            val body = files.getValue(path).toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
    private val base get() = "http://127.0.0.1:${server.address.port}"
    private val root get() = folder.root.toPath()
    private val reports = mutableListOf<String>()

    private fun build() = EmojiBuild(
        root.resolve("cache"),
        root.resolve("assets"),
        EmojiSources("$base/emoji-test.txt") { "$base/$it.xml" },
        Downloader(HttpClient.newHttpClient()) {},
    ) { reports += it }

    @After
    fun stopServer() = server.stop(0)

    @Test
    fun theBuildWritesThePaletteAndTheNamesOfEachLanguage() {
        build().run()

        val palette = Files.readString(root.resolve("assets/emoji.txt"))
        assertTrue(palette.startsWith("=Smileys\n😀\n☺️\n=People\n👋 👋🏻 👋🏼 👋🏽 👋🏾 👋🏿\n"))
        val russian = Files.readString(root.resolve("assets/ru.tsv"))
        assertTrue(russian.startsWith("😀\tшироко улыбается\tлицо|радость|смех\n"))
        assertTrue(Files.readString(root.resolve("assets/en.tsv")).startsWith("😀\tgrinning face\tface|grin\n"))
        assertEquals(
            listOf("Emoji: 8, 2 with skin tones", "Names in ru: 5 of 8", "Names in en: 8 of 8"),
            reports,
        )
    }

    @Test
    fun aSecondBuildReadsWhatTheFirstDownloaded() {
        build().run()
        build().run()

        assertEquals(listOf("/emoji-test.txt", "/ru.xml", "/en.xml"), requests)
    }

    @Test
    fun theEmojiBuildTakesNoArguments() {
        assertThrows(IllegalArgumentException::class.java) { EmojiMain.main(arrayOf("ru")) }
    }
}
