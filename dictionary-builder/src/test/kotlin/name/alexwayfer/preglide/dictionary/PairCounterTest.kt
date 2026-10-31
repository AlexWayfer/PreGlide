package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

class PairCounterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val previous = listOf("как", "у", "привет")
    private val next = listOf("дела", "у", "тебя", "как")

    private fun <T> withCounter(
        stepDocuments: Int = STEP_DOCUMENTS,
        stepLines: Int = STEP_LINES,
        use: (PairCounter) -> T,
    ): T =
        DuckDb.open(folder.root.toPath()).use { db ->
            PairCounter(db, stepDocuments, stepLines).run {
                useWords(previous, next, folder.root.toPath().resolve("words"))
                use(this)
            }
        }

    private fun counted(pages: List<Page>, stepDocuments: Int = STEP_DOCUMENTS): PairCounts {
        val text = writeFineWeb(folder.root.toPath().resolve("pages.parquet"), pages)
        val pairs = folder.root.toPath().resolve("pages.pairs.parquet")
        return withCounter(stepDocuments) { counter ->
            counter.count(text, pairs) {}
            counter.pairs(listOf(pairs))
        }
    }

    @Test
    fun `pairs are neighbor words of the dictionary within a sentence, lowercase`() {
        val counts = counted(List(3) { Page("$it", "Как дела? Как дела у тебя. Привет. Как") })

        assertEquals(
            PairCounts(listOf(WordPair("как", "дела", 6), WordPair("у", "тебя", 3)), total = 9),
            counts,
        )
    }

    @Test
    fun `a document counts a pair at most five times`() {
        val counts = counted(listOf(Page("1", "как дела ".repeat(100)), Page("2", "как дела")))

        assertEquals(listOf(WordPair("как", "дела", 6)), counts.pairs)
    }

    @Test
    fun `pairs seen too rarely are left out of the pairs but not of the total`() {
        val counts = counted(listOf(Page("1", "как дела, у тебя")))

        assertEquals(PairCounts(emptyList(), total = 2), counts)
    }

    @Test
    fun `steps add up to the same pairs`() {
        val pages = List(5) { Page("$it", "как дела") }

        assertEquals(counted(pages), counted(pages, stepDocuments = 2))
    }

    @Test
    fun `subtitle lines count pairs line by line, quotes and all`() {
        val text = folder.root.toPath().resolve("subtitles.txt.gz")
        GZIPOutputStream(Files.newOutputStream(text)).bufferedWriter().use {
            // A line with a tab does not fit the one column and is skipped.
            it.write("Как дела?\n\"Ну\" как дела у тебя\nКак дела\n- Как\nдела\n\tКак дела\n")
        }
        val pairs = folder.root.toPath().resolve("subtitles.pairs.parquet")

        val counts = withCounter { counter ->
            counter.countLines(text, pairs)
            counter.pairs(listOf(pairs))
        }

        assertEquals(PairCounts(listOf(WordPair("как", "дела", 3)), total = 4), counts)
        assertFalse(Files.exists(Path.of("$pairs.part")))
    }

    @Test
    fun `subtitle lines count pairs in steps to the same pairs`() {
        val text = folder.root.toPath().resolve("stepped.txt.gz")
        GZIPOutputStream(Files.newOutputStream(text)).bufferedWriter().use {
            it.write("как дела\nКак дела у тебя\nну как дела\nпривет\nкак дела\n")
        }
        val whole = folder.root.toPath().resolve("whole.pairs.parquet")
        val stepped = folder.root.toPath().resolve("stepped.pairs.parquet")

        val counts = listOf(whole to STEP_LINES, stepped to 2).map { (pairs, stepLines) ->
            withCounter(stepLines = stepLines) { counter ->
                counter.countLines(text, pairs)
                counter.pairs(listOf(pairs))
            }
        }

        assertEquals(counts[0], counts[1])
        // Of the pairs of the words, как дела come 4 times and у тебя once, too rarely to keep.
        assertEquals(PairCounts(listOf(WordPair("как", "дела", 4)), total = 5), counts[1])
    }
}
