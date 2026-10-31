package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.predict.Vocabulary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPOutputStream

class TrainingDataBuildTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val root: Path get() = folder.root.toPath()
    private val corpus = Corpus.Russian.copy(
        sources = listOf("https://example.com/data/web.parquet"),
        vocabularySize = 2,
        subtitles =
            Subtitles("https://example.com/ru.txt.gz", share = 0.25, frequencies = "https://example.com/ru.freq.gz"),
    )
    private val fetched = mutableListOf<String>()
    private val reports = mutableListOf<String>()

    // The vocabulary's ids: the fixed tokens, then мир and привет, the two most frequent words.
    private val first = Vocabulary.FIXED.size
    private val mir = first
    private val privet = first + 1

    private fun build(): TrainingDataBuild {
        Files.createDirectories(root.resolve("assets"))
        Files.writeString(root.resolve("assets").resolve("ru.tsv"), "кот\t5\nмир\t30\nпривет\t10\n")
        val web = writeFineWeb(root.resolve("web.parquet"), listOf(Page("1", "Привет, мир!"), Page("2", "Кот")))
        return TrainingDataBuild(
            cache = root.resolve("cache"),
            dictionaries = root.resolve("assets"),
            fetch = { url, target ->
                fetched += url
                if (url.endsWith(".txt.gz")) {
                    GZIPOutputStream(Files.newOutputStream(target)).bufferedWriter().use { it.write(SUBTITLES) }
                } else {
                    Files.copy(web, target, StandardCopyOption.REPLACE_EXISTING)
                }
            },
            report = { reports += it },
            stepDocuments = 1,
        )
    }

    private fun training(name: String): Path = root.resolve("cache").resolve("training").resolve("ru").resolve(name)

    private fun ids(file: Path): List<Int> {
        val bytes = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN)
        return List(bytes.remaining() / Int.SIZE_BYTES) { bytes.getInt() }
    }

    private fun sources(): List<List<String>> = Files.readAllLines(training("sources.tsv")).map { it.split('\t') }

    @Test
    fun `the vocabulary holds the fixed tokens and the most frequent words`() {
        build().run(corpus)

        assertEquals(Vocabulary.FIXED + listOf("мир", "привет"), Files.readAllLines(training("vocabulary.txt")))
    }

    @Test
    fun `each source text is written as ids, with its share`() {
        build().run(corpus)

        val (web, subtitles) = sources()
        assertEquals("0.7500", web[1])
        assertEquals("0.2500", subtitles[1])
        // Each document is a text of its own; the order of the documents does not matter.
        val comma = Vocabulary.FIXED.indexOf(",")
        val exclamation = Vocabulary.FIXED.indexOf("!")
        assertEquals(setOf(listOf(privet, comma, mir, exclamation), listOf(Vocabulary.UNKNOWN)), texts(web[0]))
        // So is a subtitle line.
        assertEquals(setOf(listOf(mir), listOf(privet, Vocabulary.NUMBER)), texts(subtitles[0]))
    }

    /** The texts of the token file [name], each without the start token that opens it. */
    private fun texts(name: String): Set<List<Int>> {
        val all = ids(training(name))
        val starts = all.indices.filter { all[it] == Vocabulary.START } + all.size
        assertEquals(0, starts.first())
        return starts.zipWithNext { start, end -> all.subList(start + 1, end) }.toSet()
    }

    @Test
    fun `subtitles sampled for training write every so many lines into a file of their own`() {
        val sampled = corpus.subtitles!!.copy(trainingEvery = 2)

        build().run(corpus.copy(subtitles = sampled))

        val (_, subtitles) = sources()
        assertTrue(subtitles[0], subtitles[0].endsWith("-every2.bin"))
        // Of мир and Привет 5, the first line alone.
        assertEquals(setOf(listOf(mir)), texts(subtitles[0]))
    }

    @Test
    fun `the build reports its steps, what it wrote, and how long it took`() {
        build().run(corpus)

        assertTrue(reports.toString(), reports.any { it.startsWith("data_web.parquet tokens: step 2 of 2 in ") })
        val written = Regex("data_web\\.parquet\\.tokens-\\w+\\.bin: 7 tokens, 14\\.29% unknown words")
        assertTrue(reports.toString(), reports.any { it.matches(written) })
        assertTrue(reports.last(), reports.last().startsWith("ru: training data built in "))
    }

    @Test
    fun `token files already written are kept`() {
        build().run(corpus)
        fetched.clear()

        build().run(corpus)

        assertEquals(emptyList<String>(), fetched)
        assertEquals(2, sources().size)
    }

    @Test
    fun `a corpus without subtitles gives all of the training to the web`() {
        build().run(corpus.copy(subtitles = null))

        assertEquals(listOf("1.0000"), sources().map { it[1] })
    }

    private companion object {
        const val SUBTITLES = "мир\nПривет 5\n"
    }
}
