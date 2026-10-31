package name.alexwayfer.preglide.engine.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class YoSpellingsTest {
    // As the user glided всё and got все: the same keys, the context for все four times over, and всё his 17 to 1.
    private val vse = GlidedWord("все", frequency = 3_420_476, context = 68_242_061.0, uses = 1, cost = 5.40f)
    private val vsyo = GlidedWord("всё", frequency = 1_717_325, context = 16_703_364.0, uses = 17, cost = 5.40f)
    private val vas = GlidedWord("вас", frequency = 1_620_228, context = 21_640.0, uses = 0, cost = 5.81f)

    @Test
    fun `spellings by e and yo share the context's likelihood as the user writes them`() {
        assertTrue(vse.score > vsyo.score)

        val (splitVse, splitVsyo) = splitByUses(listOf(vse, vsyo))

        assertTrue(splitVsyo.score > splitVse.score)
        assertEquals(vse.likelihood + vsyo.likelihood, splitVse.likelihood + splitVsyo.likelihood, 1.0)
        // The uses of both make either likelier.
        assertEquals(18, splitVse.boostUses)
    }

    @Test
    fun `a word with no other spelling, or spellings the user never used, stays as it was`() {
        val unused = GlidedWord("всё", frequency = 1_717_325, context = 16_703_364.0, uses = 0, cost = 5.40f)
        val unusedVse = GlidedWord("все", frequency = 3_420_476, context = 68_242_061.0, uses = 0, cost = 5.40f)

        assertSame(vas, splitByUses(listOf(vas, vse, vsyo)).first())
        assertEquals(listOf(unusedVse, unused), splitByUses(listOf(unusedVse, unused)))
    }
}
