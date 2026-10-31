package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.layout.KeyPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GlideTraceTest {
    @Test
    fun `a glide's trace tells its points in rows and its row height`() {
        val glide = Glide(listOf(KeyPoint(1.234f, 0.5f), KeyPoint(3f, 2f)), rowHeight = 1.4f)

        assertEquals("rowHeight=1.400 trace=1.23,0.50;3.00,2.00", glideTrace(glide))
    }

    @Test
    fun `a glide read back from its trace is the same glide`() {
        val glide = Glide(listOf(KeyPoint(0.5f, 0.5f), KeyPoint(2.25f, 1.5f), KeyPoint(4f, 0.75f)), rowHeight = 1.2f)

        val read = glideOfTrace("time=1 word=x " + glideTrace(glide))

        assertEquals(glide.rowHeight, read.rowHeight)
        assertEquals(glideTrace(glide), glideTrace(read))
        assertEquals(glide.length, read.length, 1e-4f)
    }
}
