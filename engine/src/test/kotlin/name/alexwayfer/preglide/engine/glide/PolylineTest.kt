package name.alexwayfer.preglide.engine.glide

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PolylineTest {
    private val corner = Polyline(floatArrayOf(0f, 3f, 3f), floatArrayOf(0f, 0f, 4f))

    @Test
    fun `its length runs along every point`() {
        assertEquals(7f, corner.length, DELTA)
        assertEquals(0f, Polyline(floatArrayOf(1f), floatArrayOf(2f)).length, DELTA)
    }

    @Test
    fun `points spread evenly keep the ends and go round the corner`() {
        val spread = corner.evenly(8)

        assertArrayEquals(floatArrayOf(0f, 1f, 2f, 3f, 3f, 3f, 3f, 3f), spread.xs, DELTA)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f, 1f, 2f, 3f, 4f), spread.ys, DELTA)
    }

    @Test
    fun `a line of no length spreads all its points on its one place`() {
        val spread = Polyline(floatArrayOf(1f, 1f), floatArrayOf(2f, 2f)).evenly(3)

        assertArrayEquals(floatArrayOf(1f, 1f, 1f), spread.xs, DELTA)
        assertArrayEquals(floatArrayOf(2f, 2f, 2f), spread.ys, DELTA)
        assertEquals(2, Polyline(floatArrayOf(5f), floatArrayOf(5f)).spaced(1f).size)
    }

    @Test
    fun `points spaced by a step land about that far apart`() {
        assertEquals(15, corner.spaced(0.5f).size)
        assertEquals(3, corner.spaced(4f).size)
    }

    @Test
    fun `distances are squared`() {
        assertEquals(25f, corner.squaredDistance(0, 3f, 4f), DELTA)
        assertEquals(16f, corner.squaredDistance(1, corner, 2), DELTA)
    }

    @Test
    fun `a line needs a point and as many x as y`() {
        assertThrows(IllegalArgumentException::class.java) { Polyline(floatArrayOf(), floatArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { Polyline(floatArrayOf(1f), floatArrayOf(1f, 2f)) }
        assertThrows(IllegalArgumentException::class.java) { corner.evenly(1) }
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
