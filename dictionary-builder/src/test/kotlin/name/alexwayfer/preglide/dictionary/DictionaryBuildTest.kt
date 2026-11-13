package name.alexwayfer.preglide.dictionary

import com.sun.net.httpserver.HttpServer
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.NextWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPOutputStream

class DictionaryBuildTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val corpus = Corpus.Russian.copy(
        sources = listOf("https://example.com/data/one.parquet", "https://example.com/data/two.parquet"),
        size = 2,
        subtitles = null,
        yoList = null,
        spelling = null,
    )
    private val subtitles =
        Subtitles("https://example.com/ru.txt.gz", share = 0.5, frequencies = "https://example.com/ru.freq.gz")
    private val fetched = mutableListOf<String>()
    private val reports = mutableListOf<String>()

    private fun asset(name: String): String = Files.readString(folder.root.toPath().resolve("assets").resolve(name))

    private fun writeGzip(target: Path, text: String) {
        GZIPOutputStream(Files.newOutputStream(target)).bufferedWriter().use { it.write(text) }
    }

    private fun build(page: String = "Привет привет мир мир мир кот"): DictionaryBuild {
        val pages = List(MIN_DOCUMENTS) { Page("$it", page) }
        val text = writeFineWeb(folder.root.toPath().resolve("source.parquet"), pages)
        return DictionaryBuild(
            cache = folder.root.toPath().resolve("cache"),
            output = folder.root.toPath().resolve("assets"),
            // Like the real download, a file already there is not fetched again.
            fetch = fetch@{ url: String, target: Path ->
                if (Files.exists(target)) return@fetch
                fetched += url
                when {
                    url.endsWith(".freq.gz") -> writeGzip(target, " 100 привет\n 50 Пока\n 30 .\n 20 hello\n")
                    url.endsWith("lines.txt.gz") -> writeGzip(target, "привет мир\n".repeat(5) + "мир мир\n".repeat(5))
                    url.endsWith(".txt.gz") -> writeGzip(target, "Привет мир\nпривет мир\nпривет мир\n")
                    url.endsWith("safe.txt") -> Files.writeString(target, "ещё\nёлк(а|и)\n")
                    url.endsWith(".aff") -> Files.writeString(target, "SFX A Y 1\nSFX A 0 а [^аеиоуыэюя]\n")
                    url.endsWith(".dic") -> Files.writeString(target, "1\nкот/A\n")
                    else -> Files.copy(text, target, StandardCopyOption.REPLACE_EXISTING)
                }
            },
            report = { reports += it },
        )
    }

    @Test
    fun `a build writes the most frequent words of every source, per billion`() {
        build().run(corpus)

        // Of 60 words, мир takes 30, привет 10 and Привет 10, which a sentence start capitalized.
        assertEquals("мир\t500000000\nпривет\t333333334\n", asset("ru.tsv"))
        assertEquals(corpus.sources, fetched)
        assertTrue(reports.toString(), reports.any { it.startsWith("ru: 2 words, 83.33% of all word occurrences") })
    }

    @Test
    fun `a build writes the next words of its words, in dictionary order`() {
        build().run(corpus)

        // Of 40 pairs in the sentences, привет мир are 10; the rest repeat a word and suggest nothing.
        // The next word is "мир", on line 0 of the dictionary file.
        assertEquals("привет\t0\t250000000\n", asset("ru.next.tsv"))
        assertTrue(reports.toString(), reports.any { it.startsWith("ru: next words for 1 words\nпривет → мир") })
        assertTrue(reports.last(), reports.last().startsWith("ru: built in "))
        assertTrue(reports.toString(), reports.any { it.startsWith("data_one.parquet pairs: steps summed in ") })
    }

    @Test
    fun `subtitles mix in by their share`() {
        build().run(corpus.copy(subtitles = subtitles))

        // привет: a half of 20 of 60 web words and a half of 100 of 150 subtitle words.
        assertEquals("мир\t250000000\nпривет\t500000000\n", asset("ru.tsv"))
        // привет мир: a half of 10 of 40 web pairs and a half of all 3 subtitle pairs.
        assertEquals("привет\t0\t625000000\n", asset("ru.next.tsv"))
        assertEquals(corpus.sources + subtitles.frequencies + subtitles.text, fetched)
    }

    @Test
    fun `subtitles without a frequency list count their words in their text`() {
        val lines = Subtitles("https://example.com/en.lines.txt.gz", share = 0.5)

        build().run(corpus.copy(subtitles = lines))

        // мир: a half of 30 of 60 web words and a half of 15 of 20 subtitle words. привет: a half of 10 of 60 as
        // Привет, and a half of 10 of 60 and of 5 of 20 as привет, each spelling rounded on its own.
        assertEquals("мир\t625000000\nпривет\t291666666\n", asset("ru.tsv"))
        assertEquals(corpus.sources + lines.text, fetched)
        assertTrue(reports.toString(), reports.any { it.startsWith("en.lines.txt.gz: words counted in") })
    }

    @Test
    fun `yo goes back into the words that always take it, and their next words`() {
        build("еще еще ещё мир мир мир").run(corpus.copy(size = 3, yoList = "https://example.com/safe.txt"))

        // еще and ещё are one word, with both counts: 2 and 1 of 6 words.
        assertEquals("ещё\t500000000\nмир\t500000000\n", asset("ru.tsv"))
        assertTrue(asset("ru.next.tsv"), asset("ru.next.tsv").startsWith("еще\t1\t"))
        assertTrue(reports.toString(), reports.any { it == "ru: ё put back in 1 words" })
    }

    @Test
    fun correctedYoFrequenciesKeepNextWordReferences() {
        build("мир колеса колеса колёса мир мир мир").run(corpus.copy(size = 3))
        val dictionary = Dictionary.read(asset("ru.tsv").reader())
        val next = NextWords.read(asset("ru.next.tsv").reader(), dictionary)

        assertEquals(listOf("колёса", "колеса"), dictionary.complete("колеса", 2))
        assertEquals(428_571_429L, dictionary.frequency("колеса") + dictionary.frequency("колёса"))
        assertEquals(listOf("колеса"), next.after("мир"))
        assertEquals(listOf("мир"), next.after("колёса"))
    }

    @Test
    fun `a spelling dictionary lets in the rarer words it spells, down to its floor`() {
        val spelling = SpellingDictionary("https://example.com/ru.aff", "https://example.com/ru.dic", floor = 1)

        build("мир мир мир привет привет кота кит").run(corpus.copy(size = 1, spelling = spelling))

        // кота is a form of кот/A; привет and кит, as frequent, are not in the spelling dictionary.
        assertEquals("кота\t142857143\nмир\t428571429\n", asset("ru.tsv"))
        assertEquals(corpus.sources + spelling.affixes + spelling.stems, fetched)
        val letIn = "ru: 1 words below the cut let in by the spelling dictionary"
        assertTrue(reports.toString(), reports.any { it == letIn })
    }

    @Test
    fun `a second build reads the counts it made before`() {
        build().run(corpus)
        fetched.clear()

        build().run(corpus)

        assertEquals(emptyList<String>(), fetched)
    }

    @Test
    fun `a project build downloads through redirects and writes into the app's assets`() {
        val text = writeFineWeb(
            folder.root.toPath().resolve("served.parquet"),
            List(MIN_DOCUMENTS) { Page("$it", "привет мир") },
        )
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/resolve/text.parquet") { exchange ->
                exchange.responseHeaders.add("Location", "/storage/text.parquet")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/storage/text.parquet") { exchange ->
                exchange.sendResponseHeaders(200, Files.size(text))
                exchange.responseBody.use { Files.copy(text, it) }
            }
            start()
        }
        val project = folder.root.toPath().resolve("project")
        val source = "http://127.0.0.1:${server.address.port}/resolve/text.parquet"

        try {
            val build = DictionaryBuild.inProject(project, httpClient()) { reports += it }
            build.run(corpus.copy(sources = listOf(source)))
        } finally {
            server.stop(0)
        }

        assertEquals(
            "мир\t500000000\nпривет\t500000000\n",
            Files.readString(project.resolve(Path.of("app", "src", "main", "assets", "dictionaries", "ru.tsv"))),
        )
        assertTrue(Files.isDirectory(project.resolve(Path.of("dictionary-builder", "cache"))))
    }

    @Test
    fun `pairs counted for other words get another name`() {
        val key = wordsKey(listOf("как"), listOf("дела", "ты"))

        assertEquals(12, key.length)
        assertEquals(key, wordsKey(listOf("как"), listOf("дела", "ты")))
        assertTrue(key != wordsKey(listOf("как", "дела"), listOf("ты")))
    }

    @Test
    fun `a cached download is named after its whole path`() {
        assertEquals(
            "datasets_HuggingFaceFW_fineweb-2_resolve_main_data_rus_Cyrl_000.parquet",
            cacheName("https://huggingface.co/datasets/HuggingFaceFW/fineweb-2/resolve/main/data/rus_Cyrl/000.parquet"),
        )
    }

    @Test
    fun `the summary shows the coverage and both ends of the list`() {
        val words = listOf(Word("и", 600_000_000), Word("в", 300_000_000), Word("кот", 50_000_000))

        assertEquals(
            """
            ru: 3 words, 95.00% of all word occurrences in the text
            Most frequent: и в кот
            Rarest kept: и в кот
            """.trimIndent(),
            summary(Corpus.Russian, words),
        )
    }
}
