package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingTest {
    private val russian = KeyboardLayout.of(Language.Russian)
    private val english = KeyboardLayout.of(Language.English)

    private fun typing(typed: String, vararg points: KeyPoint?, rowHeight: Float = DEFAULT_ROW_HEIGHT) =
        Typing(typed, Touches(russian, points.toList(), rowHeight))

    @Test
    fun `a tap read as its own char costs nothing`() {
        assertEquals(0f, typing("е").costsOf('е')[0])
    }

    @Test
    fun `a tap in a key's middle costs about 3 read as its neighbor's char`() {
        // A key width away, with a finger spread of 0.4: 1 / (2 · 0.4²).
        assertEquals(3.125f, typing("е").costsOf('к')[0], DELTA)
        assertEquals(3.125f, typing("е").costsOf('н')[0], DELTA)
    }

    @Test
    fun `a tap on the edge between two keys costs next to nothing read as the other`() {
        val edge = russian.places.getValue('е').pointAt(0f, 0.5f)

        assertEquals(0.1f, typing("е", edge).costsOf('к')[0], DELTA)
        // The farther neighbor got farther still: 1.5² less 0.5², over 2 · 0.4².
        assertEquals(6.25f, typing("е", edge).costsOf('н')[0], DELTA)
    }

    @Test
    fun `a far key spelled alike or a char with no key costs as a mistake`() {
        assertEquals(5.5f, typing("е").costsOf('и')[0], DELTA)
        assertEquals(5.5f, typing("о").costsOf('а')[0], DELTA)
        assertEquals(5.5f, Typing("i", Touches(english, emptyList())).costsOf('a')[0], DELTA)
        assertEquals(5.5f, typing("е").costsOf('-')[0], DELTA)
        assertEquals(5.5f, typing("-").costsOf('е')[0], DELTA)
    }

    @Test
    fun `a far key spelled unlike costs all its distance, more than any word may`() {
        // й lies four keys left of е, в two left and a row down.
        assertEquals(50f, typing("е").costsOf('й')[0], DELTA)
        assertEquals(18.625f, typing("е").costsOf('в')[0], DELTA)
    }

    @Test
    fun `a row away weighs as tall as the rows are`() {
        // а is a key width left and a row down from е.
        assertEquals(9.25f, typing("е").costsOf('а')[0], DELTA)
        assertEquals(3.90625f, typing("е", rowHeight = 0.5f).costsOf('а')[0], DELTA)
    }

    @Test
    fun `a word with the typed start costs nothing`() {
        assertEquals(0f, typing("прив").costOf("привет"))
        assertEquals(0f, typing("ПРИВЕТ").costOf("привет"))
        assertEquals(0f, typing("еще").costOf("ещё"))
    }

    @Test
    fun `a slip to a key nearby costs what the tap does`() {
        // м is the neighbor of и on the bottom row, whose keys are narrower.
        assertEquals(2.79f, typing("прмвет").costOf("привет")!!, 0.01f)
    }

    @Test
    fun `a missed char, an extra tap and a swap each cost their own`() {
        assertEquals(4.5f, typing("приет").costOf("привет")!!, DELTA)
        assertEquals(4.5f, typing("привеет").costOf("привет")!!, DELTA)
        assertEquals(4f, typing("пирвет").costOf("привет")!!, DELTA)
    }

    @Test
    fun `a word that costs too much is none the user meant`() {
        assertEquals(null, typing("абв").costOf("где"))
        assertEquals(null, typing("жжж").costOf("привет"))
    }

    @Test
    fun `a tap next to the one before or after it, or the same key again, reads as an extra one`() {
        // ы lies next to в before it, р next to п after it.
        assertEquals(4.5f, typing("привыет").costOf("привет")!!, DELTA)
        assertEquals(4.5f, typing("рпривет").costOf("привет")!!, DELTA)
        assertEquals(4.5f, typing("приввет").costOf("привет")!!, DELTA)
        // The last tap, brushed after в.
        assertEquals(4.5f, typing("привы").costOf("привет")!!, DELTA)
    }

    @Test
    fun `a tap far from the keys around it is no extra one`() {
        // ж lies far from в and е, у from н.
        assertEquals(null, typing("привжет").costOf("привет"))
        assertEquals(null, typing("привж").costOf("привет"))
        assertEquals(null, typing("уник").costOf("никто"))
    }

    @Test
    fun `a tap of a char with no key may be an extra one`() {
        assertEquals(4.5f, typing("при-вет").costOf("привет")!!, DELTA)
    }

    @Test
    fun `a short word allows only slips, a longer one a mistake too`() {
        assertEquals(3.5f, typing("пр").maxCost)
        assertEquals(3.5f, typing("при").maxCost)
        assertEquals(6.5f, typing("прив").maxCost)
        assertEquals(8f, typing("приве").maxCost)
    }

    @Test
    fun `an apostrophe takes no tap, and the points stay with their chars`() {
        val edge = english.places.getValue('e').pointAt(1f, 0.5f)
        val typing = Typing("w'e", Touches(english, listOf(null, null, edge)))

        assertEquals("we", typing.chars)
        // Read with its own point, the tap on e's right edge is a likely r.
        assertEquals(0.1f, typing.costsOf('r')[1], DELTA)
    }

    @Test
    fun `a word read whole counts each char past the taps as missed`() {
        assertEquals(0f, typing("привет").wholeCostOf("привет"))
        assertEquals(4.5f, typing("приве").wholeCostOf("привет")!!, DELTA)
        // Two chars missed are more than four taps may cost, though the start costs nothing.
        assertEquals(null, typing("прив").wholeCostOf("привет"))
        assertEquals(0f, typing("прив").costOf("привет"))
    }

    @Test
    fun `a word read whole is cut short once it costs too much`() {
        assertEquals(null, typing("приве").wholeCostOf("жжжжжжжж"))
        assertEquals(4.5f, typing("приве").wholeCostOf("привет")!!, DELTA)
    }

    @Test
    fun `the costs of one word do not carry over to the next`() {
        val typing = typing("приет")

        assertEquals(null, typing.costOf("абвгд"))
        assertEquals(4.5f, typing.costOf("привет")!!, DELTA)
        assertEquals(4.5f, typing.costOf("привет")!!, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
