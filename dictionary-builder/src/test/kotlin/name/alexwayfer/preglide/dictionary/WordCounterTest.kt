package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.zip.GZIPOutputStream

class WordCounterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var files = 0

    private fun <T> withCounter(
        stepDocuments: Int = STEP_DOCUMENTS,
        stepLines: Int = STEP_LINES,
        use: (WordCounter) -> T,
    ): T = DuckDb.open(folder.root.toPath()).use { use(WordCounter(it, stepDocuments, stepLines)) }

    private fun subtitles(name: String, text: String): Path =
        folder.root.toPath().resolve(name).also { file ->
            GZIPOutputStream(Files.newOutputStream(file)).bufferedWriter().use { it.write(text) }
        }

    private fun counted(vararg pages: Page): Path {
        val text = writeFineWeb(folder.root.toPath().resolve("text-${files++}.parquet"), pages.toList())
        val counts = folder.root.toPath().resolve("${text.fileName}.counts.parquet")
        withCounter { it.count(text, counts) {} }
        return counts
    }

    @Test
    fun `counting keeps case, hyphens and apostrophes, and splits on everything else`() {
        val counts = counted(Page("1", "Кто-то сказал: «Привет, мир!» привет... Don’t 42 stop—now"))

        assertEquals(
            listOf(
                "Don't true",
                "now false",
                "stop false",
                "Кто-то true",
                "Привет true",
                "мир false",
                "привет true",
                "сказал false",
            ).map { "$it 1 1" },
            readCounts(counts),
        )
    }

    @Test
    fun `sentence starts are told apart from words within a sentence`() {
        val counts = counted(Page("1", "Он пришёл. Привет! «Пока» — сказал Иван.\n— Ага, ну (Вот) и\r\nВсё"))

        assertEquals(
            listOf(
                "Ага true",
                "Вот false",
                "Всё true",
                "Иван false",
                "Он true",
                "Пока true",
                "Привет true",
                "и false",
                "ну false",
                "пришёл false",
                "сказал false",
            ),
            readCounts(counts).map { it.substringBeforeLast(" ").substringBeforeLast(" ") },
        )
    }

    @Test
    fun `a document counts a word at most five times`() {
        val counts = counted(Page("1", "купить ".repeat(1000)), Page("2", "купить купить"))

        assertEquals(listOf("купить false 6 2", "купить true 2 2"), readCounts(counts))
    }

    @Test
    fun `subtitle lines count every word, a line a sentence, apostrophes made one`() {
        // A line with a tab does not fit the one column and is skipped.
        val lines = "Don''t stop\nI’m sure you don't\nstop stop stop stop stop stop\n\tstop\n"
        val text = subtitles("subtitles.txt.gz", lines)
        val counts = folder.root.toPath().resolve("subtitles.counts.parquet")

        withCounter { it.countLines(text, counts) }

        assertEquals(
            listOf(
                "Don't true 1 1",
                "I'm true 1 1",
                "don't false 1 1",
                "stop false 6 6",
                "stop true 1 1",
                "sure false 1 1",
                "you false 1 1",
            ),
            readCounts(counts),
        )
        assertFalse(Files.exists(counts.resolveSibling("${counts.fileName}.part")))
    }

    @Test
    fun `subtitle lines count in steps to the same counts, reporting each`() {
        val text = subtitles("stepped.txt.gz", "Hello there\nhello world\nWorld hello\nthere\nhello\n")
        val whole = folder.root.toPath().resolve("whole.counts.parquet")
        val stepped = folder.root.toPath().resolve("stepped.counts.parquet")
        val progress = mutableListOf<CountProgress>()

        withCounter { it.countLines(text, whole) }
        withCounter(stepLines = 2) { counter -> counter.countLines(text, stepped) { progress += it } }

        assertEquals(readCounts(whole), readCounts(stepped))
        val steps = progress.filterIsInstance<CountProgress.Step>()
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), steps.map { it.done to it.steps })
    }

    @Test
    fun `a finished count leaves no partial file`() {
        val counts = counted(Page("1", "слово"))

        assertFalse(Files.exists(counts.resolveSibling("${counts.fileName}.part")))
        assertFalse(Files.exists(counts.resolveSibling("${counts.fileName}.steps")))
    }

    @Test
    fun `a count goes in steps and reports each`() {
        val pages = List(5) { Page("$it", "слово номер $it") }
        val text = writeFineWeb(folder.root.toPath().resolve("steps.parquet"), pages)
        val counts = folder.root.toPath().resolve("steps.counts.parquet")
        val progress = mutableListOf<CountProgress>()

        withCounter(stepDocuments = 2) { counter -> counter.count(text, counts) { progress += it } }

        val steps = progress.filterIsInstance<CountProgress.Step>()
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), steps.map { it.done to it.steps })
        assertTrue(steps.all { it.took != null })
        assertTrue(progress.last() is CountProgress.Merged)
        assertEquals(listOf("номер false 5 5", "слово true 5 5"), readCounts(counts))
    }

    @Test
    fun `a stopped count goes on from the steps it finished`() {
        val text = writeFineWeb(folder.root.toPath().resolve("resumed.parquet"), List(4) { Page("$it", "слово") })
        val counts = folder.root.toPath().resolve("resumed.counts.parquet")
        val steps = Files.createDirectories(counts.resolveSibling("${counts.fileName}.steps"))
        // The first step was counted before the stop; its counts say "раньше" so that the test can tell.
        DriverManager.getConnection("jdbc:duckdb:").use { connection ->
            connection.createStatement().use {
                it.execute(
                    "COPY (SELECT 'раньше' AS form, true AS initial, 2::BIGINT AS occurrences, " +
                        "2::BIGINT AS documents) TO ${literal(steps.resolve("0.parquet"))} (FORMAT parquet)",
                )
            }
        }

        val progress = mutableListOf<CountProgress>()

        withCounter(stepDocuments = 2) { counter -> counter.count(text, counts) { progress += it } }

        assertEquals(listOf("раньше true 2 2", "слово true 2 2"), readCounts(counts))
        // The step counted before reports no time of its own.
        assertEquals(null, (progress.first() as CountProgress.Step).took)
    }

    @Test
    fun `dumps count the documents of each crawl, oldest first`() {
        val text = writeFineWeb(
            folder.root.toPath().resolve("dumps.parquet"),
            listOf(
                Page("1", "a", "CC-MAIN-2024-10"),
                Page("2", "b", "CC-MAIN-2013-20"),
                Page("3", "c", "CC-MAIN-2024-10"),
            ),
        )

        val dumps = withCounter { it.dumps(text) }

        assertEquals(listOf("CC-MAIN-2013-20" to 1L, "CC-MAIN-2024-10" to 2L), dumps.toList())
    }

    @Test
    fun `forms are the language's words in enough documents, summed over files`() {
        val first = counted(*Array(MIN_DOCUMENTS - 1) { Page("a$it", "слово Слово word редкое") })
        val second = counted(Page("b", "Это слово"), Page("c", "кто-то"))

        val forms = withCounter { it.forms(listOf(first, second), Corpus.Russian) }

        assertEquals(setOf(Form("слово", MIN_DOCUMENTS.toLong(), midSentence = 1)), forms.toSet())
    }

    @Test
    fun `the total counts every word of the language, rare ones included`() {
        val counts = counted(Page("1", "слово слово word редкое"))

        val total = withCounter { it.total(listOf(counts), Corpus.Russian) }

        assertEquals(3L, total)
    }

    @Test
    fun `the total of no words is zero`() {
        val counts = counted(Page("1", "only English here"))

        assertEquals(0L, withCounter { it.total(listOf(counts), Corpus.Russian) })
    }

    @Test
    fun `a literal escapes its quotes`() {
        assertEquals("'don''t'", literal("don't"))
    }
}
