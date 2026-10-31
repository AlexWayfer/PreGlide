package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.glide.GlidedWord
import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.ContextParts
import name.alexwayfer.preglide.engine.suggest.LevelCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar

class GlideSlipsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val glide = Glide(listOf(KeyPoint(1f, 0.5f), KeyPoint(2f, 0.5f)), rowHeight = 1.5f)
    private val scores = listOf(
        GlidedWord("привет", 120_000, 3_456.7, 0, 1.234f),
        GlidedWord("приват", 900, 12.4, 2, 2.5f),
        GlidedWord("пират", 4_000, null, 0, 6f),
    )
    // The phone's own time, whatever its zone.
    private val at = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 16, 15, 30, 12) }.timeInMillis
    private val slip = GlideSlip(3, at, Language.Russian, "привет", "привет", scores.map { it.word }, scores, glide)

    @Test
    fun aSlipLineTellsWhenTheWordsTheirScoresAndTheGlide() {
        assertEquals(
            "glide=3 at=2026-10-16T15:30:12 language=ru typed=привет chose=приват offered=привет,приват,пират " +
                "scores=120000/3457/0/1.23;900/12/2/2.50;4000/-/0/6.00 rowHeight=1.500 trace=1.00,0.50;2.00,0.50",
            glideSlipLine(slip.swappedFor("приват")),
        )
    }

    @Test
    fun aScoreWithTheModelTellsWhatItsContextWasMixedFrom() {
        val parts = ContextParts(2_100.4, listOf(LevelCount(1, 5), LevelCount(0, 2)))
        val word = GlidedWord("привет", 120_000, 3_456.7, 0, 1.234f, contextParts = parts)

        assertEquals("120000/3457/0/1.23/2100/1:5+0:2", glidedScoreText(word))
        // A context the user typed nothing after has no levels.
        val alone = GlidedWord("привет", 120_000, 2_100.4, 0, 1.234f, contextParts = ContextParts(2_100.4, emptyList()))
        assertEquals("120000/2100/0/1.23/2100/", glidedScoreText(alone))
    }

    @Test
    fun aGlideErasedWholeChoseNothing() {
        assertTrue(" chose= offered=" in glideSlipLine(slip.erased()))
    }

    @Test
    fun slipsAreKeptALineEachUntilTheFileIsFull() {
        val file = File(folder.root, "slips.txt")
        val slips = GlideSlips(file, { it.run() }, maxBytes = 1)

        slips.record(slip)
        slips.record(slip)

        // The first line fills the file past its bound: the second is not kept.
        assertEquals(listOf(glideSlipLine(slip)), file.readLines())
    }
}
