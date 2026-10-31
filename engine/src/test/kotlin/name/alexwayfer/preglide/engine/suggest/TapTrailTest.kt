package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class TapTrailTest {
    private val russian = KeyboardLayout.of(Language.Russian)
    private val middle = KeyTap(0.5f, 0.5f, 100, 140)

    private fun TapTrail.type(word: String, tap: KeyTap? = middle) {
        for (char in word) typed(char, char.lowercaseChar(), russian, tap)
    }

    private fun centerOf(char: Char): KeyPoint = russian.places.getValue(char).center

    @Test
    fun `each char of the word gets the point its key was tapped at`() {
        val trail = TapTrail()
        trail.typed('к', 'к', russian, KeyTap(0f, 1f, 100, 140))
        trail.type("от")

        assertEquals(
            listOf(russian.places.getValue('к').pointAt(0f, 1f), centerOf('о'), centerOf('т')),
            trail.pointsFor("кот"),
        )
    }

    @Test
    fun `the trail matches the word from its end, whatever the case`() {
        val trail = TapTrail().apply { type("кот") }

        assertEquals(listOf(centerOf('к'), centerOf('о'), centerOf('т')), trail.pointsFor("Кот"))
        // A char typed some other way, as pasted, has no point; nor has any before it.
        assertEquals(listOf(null, centerOf('к'), centerOf('о'), centerOf('т')), trail.pointsFor("скот"))
        assertEquals(listOf(null, null, centerOf('т')), trail.pointsFor("кит"))
        assertEquals(emptyList<KeyPoint?>(), trail.pointsFor(""))
    }

    @Test
    fun `a backspace takes back the char typed last`() {
        val trail = TapTrail().apply {
            type("кот")
            deleted()
        }

        assertEquals(listOf(centerOf('к'), centerOf('о')), trail.pointsFor("ко"))
        trail.deleted()
        trail.deleted()
        trail.deleted()
        assertEquals(listOf<KeyPoint?>(null), trail.pointsFor("к"))
    }

    @Test
    fun `a char from a popup or off the letters has no point`() {
        val trail = TapTrail()
        trail.typed('ё', 'ё', russian, null)
        trail.typed('-', '-', null, middle)

        assertEquals(listOf(null, null), trail.pointsFor("ё-"))
    }

    @Test
    fun `a tap tells how tall a row is`() {
        val trail = TapTrail()

        assertEquals(DEFAULT_ROW_HEIGHT, trail.rowHeight)
        trail.typed('к', 'к', russian, KeyTap(0.5f, 0.5f, 100, 160))
        assertEquals(1.6f, trail.rowHeight)
        // A tap on a key with no size tells nothing.
        trail.typed('о', 'о', russian, KeyTap(0.5f, 0.5f, 0, 0))
        assertEquals(1.6f, trail.rowHeight)
    }

    @Test
    fun `only the chars typed last are kept`() {
        val trail = TapTrail().apply { type("а".repeat(70)) }

        val points = trail.pointsFor("а".repeat(70))

        assertEquals(List(6) { null }, points.take(6))
        assertEquals(List(64) { centerOf('а') }, points.drop(6))
    }

    @Test
    fun `a cleared trail knows no points`() {
        val trail = TapTrail().apply {
            type("кот")
            clear()
        }

        assertEquals(listOf(null, null, null), trail.pointsFor("кот"))
    }
}
