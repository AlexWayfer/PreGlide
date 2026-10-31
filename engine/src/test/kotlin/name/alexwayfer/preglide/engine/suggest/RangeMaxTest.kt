package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeMaxTest {
    private val values = floatArrayOf(3f, 1f, 4f, 1f, 5f, 9f, 2f, 6f, 5f)
    private val tree = RangeMax(values)

    @Test
    fun `the largest of a run is found wherever the run lies`() {
        for (from in values.indices) {
            for (to in from + 1..values.size) {
                assertEquals("$from until $to", values.copyOfRange(from, to).max(), tree.max(from, to))
            }
        }
    }

    @Test
    fun `each value is kept as it is`() {
        assertEquals(values.toList(), values.indices.map { tree[it] })
    }

    @Test
    fun `an empty run has no largest`() {
        assertEquals(Float.NEGATIVE_INFINITY, tree.max(3, 3))
        assertEquals(Float.NEGATIVE_INFINITY, RangeMax(FloatArray(0)).max(0, 0))
    }
}
