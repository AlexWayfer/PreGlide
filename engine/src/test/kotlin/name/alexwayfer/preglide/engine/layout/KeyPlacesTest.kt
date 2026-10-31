package name.alexwayfer.preglide.engine.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPlacesTest {
    private val russian = KeyboardLayout.of(Language.Russian)
    private val english = KeyboardLayout.of(Language.English)

    @Test
    fun `each letter lies where its row puts it, in key widths`() {
        assertEquals(KeyPlace(0f, 1f, 0), russian.places['й'])
        assertEquals(KeyPlace(10f, 1f, 0), russian.places['х'])
        assertEquals(KeyPlace(0f, 1f, 1), russian.places['ф'])
        // After the shift key, the bottom row's letters are narrower.
        assertEquals(KeyPlace(1.25f, 8.5f / 9, 2), russian.places['я'])
        // The gap before a shorter row counts.
        assertEquals(KeyPlace(0.5f, 1f, 1), english.places['a'])
        assertEquals(KeyPlace(1.5f, 1f, 3), english.places[','])
    }

    @Test
    fun `only the keys that type a char have a place, yo being on e`() {
        assertEquals(null, russian.places['ё'])
        assertEquals(null, russian.places[' '])
        // 31 letters, the comma and the period.
        assertEquals(33, russian.places.size)
    }

    @Test
    fun `a glide finds a letter of a long press at the key it lives on`() {
        assertEquals(russian.places['ь'], russian.glidePlaces['ъ'])
        assertEquals(russian.places['е'], russian.glidePlaces['ё'])
        assertEquals(russian.places['к'], russian.glidePlaces['к'])
        // A digit hint is no letter to glide over.
        assertEquals(null, russian.glidePlaces['1'])
    }

    @Test
    fun `a point on a key goes across its width and down its row`() {
        val place = KeyPlace(2f, 2f, 1)

        assertEquals(KeyPoint(3f, 1.5f), place.center)
        assertEquals(KeyPoint(2f, 1f), place.pointAt(0f, 0f))
        assertEquals(KeyPoint(3.5f, 1.25f), place.pointAt(0.75f, 0.25f))
    }

    @Test
    fun `keys are next to each other when they share an edge`() {
        val e = russian.places.getValue('е')

        assertTrue(e.isNextTo(russian.places.getValue('к')))
        assertTrue(e.isNextTo(russian.places.getValue('н')))
        assertTrue(e.isNextTo(russian.places.getValue('п')))
        assertTrue(e.isNextTo(e))
        // а only touches е's corner, and в lies a key farther.
        assertFalse(e.isNextTo(russian.places.getValue('а')))
        assertFalse(e.isNextTo(russian.places.getValue('в')))
        // The narrower bottom keys lie under two keys each, and a row apart is no edge.
        assertTrue(russian.places.getValue('я').isNextTo(russian.places.getValue('ы')))
        assertTrue(russian.places.getValue('я').isNextTo(russian.places.getValue('в')))
        assertFalse(russian.places.getValue('я').isNextTo(russian.places.getValue('ц')))
        // A shorter row lies half a key over: a under q and w.
        assertTrue(english.places.getValue('a').isNextTo(english.places.getValue('q')))
        assertTrue(english.places.getValue('a').isNextTo(english.places.getValue('w')))
        assertFalse(english.places.getValue('a').isNextTo(english.places.getValue('e')))
    }

    @Test
    fun `a tap is where it lands across the key and down it`() {
        assertEquals(KeyTap(0.25f, 0.5f, 100, 140), keyTapAt(25f, 70f, 100, 140))
    }

    @Test
    fun `a tap past an edge counts at it, and one on a key with no size in its middle`() {
        assertEquals(KeyTap(0f, 1f, 100, 140), keyTapAt(-3f, 150f, 100, 140))
        assertEquals(KeyTap(0.5f, 0.5f, 0, 0), keyTapAt(10f, 10f, 0, 0))
    }

    @Test
    fun `a row is as tall as the key next to its width`() {
        assertEquals(1.4f, rowHeightOf(KeyTap(0.5f, 0.5f, 100, 140), KeyPlace(0f, 1f, 0)), DELTA)
        // A key twice as wide is two key widths across its pixels.
        assertEquals(1.4f, rowHeightOf(KeyTap(0.5f, 0.5f, 200, 140), KeyPlace(0f, 2f, 0)), DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
