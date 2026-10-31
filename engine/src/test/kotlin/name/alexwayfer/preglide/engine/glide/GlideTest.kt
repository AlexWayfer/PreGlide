package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.predict.Vocabulary
import name.alexwayfer.preglide.engine.suggest.DEFAULT_ROW_HEIGHT
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.learn
import name.alexwayfer.preglide.engine.suggest.usesBoost
import name.alexwayfer.preglide.engine.suggest.predictionOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideTest {
    private val russian = KeyboardLayout.of(Language.Russian)
    private val dictionary = Dictionary.parse(
        sequenceOf("по\t100", "про\t50", "пол\t20", "кот\t50", "код\t60", "привет\t30", "пример\t40"),
    )

    @Test
    fun `a glide along a word's keys reads as that word first`() {
        assertEquals("привет", glidedWords(model(), russian, glideThrough("привет"), 3).first())
        assertEquals("пример", glidedWords(model(), russian, glideThrough("пример"), 3).first())
    }

    @Test
    fun `a glide's length weighs a row's way down by the row's height`() {
        val glide = Glide(listOf(KeyPoint(0f, 0f), KeyPoint(3f, 0f), KeyPoint(3f, 2f)), rowHeight = 2f)

        assertEquals(7f, glide.length, 1e-5f)
    }

    @Test
    fun `a word whose last key lies off the glide's end ranks below`() {
        // код is likelier, but its д lies two keys off where the glide lifts.
        assertEquals("кот", glidedWords(model(), russian, glideThrough("кот"), 3).first())
    }

    @Test
    fun `a finger that runs past the last key before it lifts still reads the word`() {
        val last = russian.places.getValue('т').center
        // Most of a row below т, past the letters, where a fast finger lifts.
        val glide = Glide(pointsThrough("кот") + KeyPoint(last.x + 0.3f, last.y + 0.9f))

        assertEquals("кот", glidedWords(model(), russian, glide, 3).first())
    }

    @Test
    fun `words of the same shape come by their frequency`() {
        // п, р and о are neighbors in a row: про glides as по does.
        val words = glidedWords(model(), russian, glideThrough("по"), 3)

        assertEquals(listOf("по", "про"), words.take(2))
    }

    @Test
    fun `what comes next decides between words of one shape`() {
        val vocabulary = Vocabulary(listOf("по", "про"))
        val prediction = predictionOver(dictionary, vocabulary, 4) { FloatArray(0) }
        val first = Vocabulary.FIXED.size
        val next = NextTokens(
            prediction,
            FloatArray(vocabulary.size).apply {
                this[first] = 0.05f
                this[first + 1] = 0.9f
            },
        )

        val words = glidedWords(model(), russian, glideThrough("по"), 2, WordsBefore(listOf("думаю"), next))

        assertEquals(listOf("про", "по"), words)

        val (pro, po) = glidedScores(model(), russian, glideThrough("по"), 2, WordsBefore(listOf("думаю"), next))
        assertTrue(pro.context!! > po.context!!)
        assertEquals(dictionary.frequency("по"), po.frequency)
        assertTrue(pro.score > po.score)
    }

    @Test
    fun `a glided word tells what it was scored by`() {
        val personal = PersonalWords().apply { repeat(2) { learn("по", null, 0) } }

        val po = glidedScores(model(personal), russian, glideThrough("по"), 3).first { it.word == "по" }

        assertNull(po.context)
        assertEquals(2, po.uses)
        assertEquals(dictionary.frequency("по"), po.frequency)
        assertEquals(glideScore(po.frequency * usesBoost(2), po.cost), po.score, 0.0)
    }

    @Test
    fun `a word typed often does not beat one the finger went far closer to`() {
        // As the user glided текст and got тест, typed four times: by the context alike, and the glide some 12
        // times closer to текст.
        val test = GlidedWord("тест", frequency = 38_842, context = 15_924.0, uses = 4, cost = 5.30f)
        val text = GlidedWord("текст", frequency = 51_204, context = 15_195.0, uses = 0, cost = 2.79f)

        assertTrue(text.score > test.score)
    }

    @Test
    fun `a glide is sure of its word only far ahead of the next`() {
        val best = GlidedWord("кот", frequency = 1_000, context = null, uses = 0, cost = 1f)

        // Four nats of cost more make a word e⁴, some 55 times, less likely.
        assertTrue(glideIsSure(listOf(best, best.copy(word = "код", cost = 5.01f))))
        assertFalse(glideIsSure(listOf(best, best.copy(word = "код", cost = 4.99f))))
    }

    @Test
    fun `a glide is not sure of its word while another it offers fits its shape far better`() {
        // As the user glided созреешь twice and got сможешь, by its context and uses alone.
        val best = GlidedWord("сможешь", frequency = 81_140, context = 14_300_045.0, uses = 3, cost = 18.60f)
        val shaped = GlidedWord("созреешь", frequency = 71, context = 10.0, uses = 0, cost = 8.76f)
        val other = GlidedWord("можешь", frequency = 391_933, context = 126_968.0, uses = 9, cost = 20.53f)

        assertFalse(glideIsSure(listOf(best, other, shaped)))
        // Within two nats of the shape, the scores alone decide.
        assertTrue(glideIsSure(listOf(best, other, shaped.copy(cost = 16.61f))))
    }

    @Test
    fun `a glide of one word is sure of it, and one of none is not`() {
        assertTrue(glideIsSure(listOf(GlidedWord("кот", frequency = 1_000, context = null, uses = 0, cost = 1f))))
        assertFalse(glideIsSure(emptyList()))
    }

    @Test
    fun `the user's own words join the dictionary's, and removed words stay out`() {
        val personal = PersonalWords().apply {
            learn("вот", null, 0)
            remove("кот", 0)
        }

        assertEquals("вот", glidedWords(model(personal), russian, glideThrough("вот"), 3).first())
        assertFalse("кот" in glidedWords(model(personal), russian, glideThrough("кот"), 3))
    }

    @Test
    fun `a learned word with a digit is no glide's`() {
        val personal = PersonalWords().apply { repeat(2) { learn("вот-3", null, 0) } }

        assertFalse("вот-3" in glidedWords(model(personal), russian, glideThrough("вот"), 3))
    }

    @Test
    fun `matching costs keys off the glide, turns no key explains, and the way run past the last key`() {
        val keys = GlideKeys(russian, glideThrough("вот"))
        val right = matchCost(keys, "вот")

        assertTrue("$right", right < 0.5f)
        // Backwards, it starts where the glide ends.
        val backwards = matchCost(keys, "тов")
        assertTrue("$backwards", backwards > MAX_MATCH_COST)
        // к lies a row above the glide.
        val above = matchCost(keys, "вкот")
        assertTrue("$above", above > right + 1f)
        // The turn at о down to т is left unexplained, and the finger ran on past о.
        val short = matchCost(keys, "во")
        assertTrue("$short", short > right + 1f)
        // A hyphen has no key and is glided past, as is a key twice in a row.
        assertEquals(right, matchCost(keys, "во-т"), DELTA)
        assertEquals(right, matchCost(keys, "воот"), DELTA)
        assertNull(keys.costOf("-"))
        assertEquals(0f, keys.costOf("вот")!!, 0.01f)
    }

    @Test
    fun `a start costs no more than any word that goes on from it`() {
        val rows = GlideRows(GlideKeys(russian, glideThrough("привет")))
        var row = rows.start('п')
        val bounds = mutableListOf(row.min())
        for (char in "ривет") {
            row = rows.follow(row, char)
            bounds += row.min()
        }

        assertEquals(bounds.sorted(), bounds)
        assertTrue(rows.whole(row) >= bounds.last())
        assertEquals(null, rows.of("-"))
    }

    @Test
    fun `the keys know where each char's key lies`() {
        val keys = GlideKeys(russian, glideThrough("вот"))

        assertTrue(keys.has('в'))
        assertFalse(keys.has('-'))
        assertFalse(keys.has('€'))
        assertEquals(2.5f, keys.x('в'), DELTA)
        assertEquals(1.5f * DEFAULT_ROW_HEIGHT, keys.y('в'), DELTA)
    }

    private fun matchCost(keys: GlideKeys, word: String): Float {
        val rows = GlideRows(keys)
        return rows.of(word)?.let(rows::whole) ?: UNREACHED
    }

    @Test
    fun `a word's line has a point for each key, once for a key twice in a row`() {
        val keys = GlideKeys(russian, glideThrough("вот"))

        assertEquals(2, keys.lineOf("поо")!!.size)
        assertEquals(3, keys.lineOf("П'о-т")!!.size)
        assertNull(keys.lineOf("-'"))
    }

    @Test
    fun `the shape costs by how far the glide strays from the word's line`() {
        val straight = Polyline(floatArrayOf(0f, 1f, 2f), floatArrayOf(0f, 0f, 0f))
        val lingering = Polyline(floatArrayOf(0f, 0f, 1f, 2f), floatArrayOf(0f, 0f, 0f, 0f))
        val above = Polyline(floatArrayOf(0f, 1f, 2f), floatArrayOf(1f, 1f, 1f))

        assertEquals(0f, warpedDistance(straight, lingering), DELTA)
        assertEquals(1f, warpedDistance(straight, above), DELTA)
        assertEquals(0f, shapeCost(straight.evenly(SHAPE_POINTS), straight), DELTA)
        // A key width off all along: 6 samples at a spread of 0.35.
        assertEquals(24.49f, shapeCost(straight.evenly(SHAPE_POINTS), above), 0.01f)
    }

    @Test
    fun `a long glide is followed by a bounded number of points`() {
        val points = List(200) { KeyPoint(if (it % 2 == 0) 0.5f else 10.5f, 1.5f) }

        // Rounding may add one.
        assertTrue(Glide(points).course.size in 40..41)
        assertEquals(2, Glide(listOf(KeyPoint(1f, 1f))).course.size)
        assertThrows(IllegalArgumentException::class.java) { Glide(emptyList()) }
    }

    @Test
    fun `a costly glide still ranks its words by score, not by the alphabet`() {
        // Rounded to whole counts, both would be 0.
        assertTrue(glideScore(741.0, 9.4f) > glideScore(41_671.0, 20f))
        assertTrue(glideScore(41_671.0, 20f) > 0.0)
    }

    @Test
    fun `nothing is looked for when nothing is wanted`() {
        assertEquals(emptyList<Any>(), dictionary.glided(GlideKeys(russian, glideThrough("по")), 0))
    }

    private fun model(personal: PersonalWords = PersonalWords()) = LanguageModel(dictionary, personal = personal)

    /** A glide along the middles of [word]'s keys, a few points between each two. */
    private fun glideThrough(word: String): Glide = Glide(pointsThrough(word))

    /** The points along the middles of [word]'s keys, a few between each two. */
    private fun pointsThrough(word: String): List<KeyPoint> {
        val centers = word.map { russian.places.getValue(it).center }
        val points = ArrayList<KeyPoint>()
        for (i in 0 until centers.lastIndex) {
            val from = centers[i]
            val to = centers[i + 1]
            for (step in 0 until STEPS) {
                val share = step.toFloat() / STEPS
                points += KeyPoint(from.x + (to.x - from.x) * share, from.y + (to.y - from.y) * share)
            }
        }
        points += centers.last()
        return points
    }

    private companion object {
        const val DELTA = 1e-4f
        const val STEPS = 5
    }
}
