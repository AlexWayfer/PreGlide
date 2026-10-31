package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class GlideTallyTest {
    @Test
    fun unknownFormatsAndUnboundedCollectionsAreRejected() {
        fun input(vararg values: Int): DataInputStream {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out -> values.forEach(out::writeInt) }
            return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        }
        assertThrows(IllegalArgumentException::class.java) { GlideTally.read(input(0)) }
        assertThrows(IllegalArgumentException::class.java) { GlideTally.read(input(1, -1)) }
        assertThrows(IllegalArgumentException::class.java) { GlideTally.read(input(1, Int.MAX_VALUE)) }
    }

    @Test
    fun savedTotalsResumeWithoutWordsOrLostPrecision() {
        val tally = GlideTally(every = 1)
        val path = glide('п', KeyPoint(0.123f, -0.2f), 'т', KeyPoint(-0.5f, 0.25f))
        tally.meant(Language.Russian, path, "Привет", rank = 1, cost = 2f)
        tally.meant(Language.Russian, path, "пират", rank = 3, cost = 4f)
        tally.erased(Language.English)
        val bytes = ByteArrayOutputStream().also { tally.write(DataOutputStream(it)) }.toByteArray()

        val restored = GlideTally.read(DataInputStream(ByteArrayInputStream(bytes)), every = 1)

        assertEquals(tally.lines(), restored.lines())
        assertEquals(tally.erased(Language.Russian), restored.erased(Language.Russian))
        assertFalse(bytes.toString(Charsets.UTF_8).contains("Привет"))
    }

    private val russian = KeyboardLayout.of(Language.Russian).glidePlaces

    /** A glide that comes down off [first]'s key middle by [start] and lifts off [last]'s by [lift]. */
    private fun glide(first: Char, start: KeyPoint, last: Char, lift: KeyPoint): Glide {
        val from = russian.getValue(first).center
        val to = russian.getValue(last).center
        return Glide(listOf(KeyPoint(from.x + start.x, from.y + start.y), KeyPoint(to.x + lift.x, to.y + lift.y)), 1.5f)
    }

    @Test
    fun `every few glides settled tell how they were settled and where they started and lifted by key`() {
        val tally = GlideTally(every = 3)
        val glide = glide('п', KeyPoint(0.1f, -0.2f), 'т', KeyPoint(-0.5f, 0.25f))

        assertEquals(emptyList<String>(), tally.meant(Language.Russian, glide, "Привет", rank = 1, cost = 2f))
        assertEquals(emptyList<String>(), tally.meant(Language.Russian, glide, "пират", rank = 3, cost = 4f))
        val lines = tally.erased(Language.Russian)

        assertEquals(
            listOf(
                "Glides ru so far: kept 1, swapped 1 (by rank 3: 1), erased 1, of 3; " +
                    "the word meant cost 3.00 on average",
                "Glide starts ru, count and mean offset across/down in keys/rows by key: п 2 +0.10/-0.20",
                "Glide lifts ru, count and mean offset across/down in keys/rows by key: т 2 -0.50/+0.25",
            ),
            lines,
        )
        assertFalse(lines.any { "привет" in it.lowercase() || "пират" in it })
    }

    @Test
    fun `each language is counted apart, and erased glides tell no keys`() {
        val tally = GlideTally(every = 1)
        tally.meant(Language.Russian, glide('п', KeyPoint(0f, 0f), 'т', KeyPoint(0f, 0f)), "пат", rank = 1, cost = 1f)

        assertEquals(listOf("Glides en so far: kept 0, swapped 0, erased 1, of 1"), tally.erased(Language.English))
    }

    @Test
    fun `a word's letters are found at their keys as a search folds them`() {
        val tally = GlideTally(every = 1)
        val glide = glide('е', KeyPoint(0f, 0f), 'ж', KeyPoint(0.3f, 0f))

        val lines = tally.meant(Language.Russian, glide, "Ёж", rank = 1, cost = 1f)

        assertEquals(
            listOf(
                "Glide starts ru, count and mean offset across/down in keys/rows by key: е 1 +0.00/+0.00",
                "Glide lifts ru, count and mean offset across/down in keys/rows by key: ж 1 +0.30/+0.00",
            ),
            lines.drop(1),
        )
    }
}
