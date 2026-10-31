package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Test

class LearnedNextTest {
    private val next = LearnedNext(limit = 10)

    private fun countAfterPairs() = (0 until 20).sumOf { next.after(listOf("а$it", "б$it"), length = 2).size }

    @Test
    fun `past the limit the next words typed fewest times go, the oldest first, down to nine tenths`() {
        for (index in 0 until 10) next.learn("слово", listOf("а$index", "б$index"), time = index.toLong())
        // Typed twice, the first is kept, although it is the oldest.
        next.learn("слово", listOf("а0", "б0"), time = 0)

        next.learn("слово", listOf("а10", "б10"), time = 10)

        assertEquals(9, countAfterPairs())
        assertEquals(listOf(Learned("слово", 2, 0)), next.after(listOf("а0", "б0"), 2))
        assertEquals(emptyList<Learned>(), next.after(listOf("а1", "б1"), 2))
        assertEquals(emptyList<Learned>(), next.after(listOf("а2", "б2"), 2))
        assertEquals(listOf(Learned("слово", 1, 10)), next.after(listOf("а10", "б10"), 2))
    }

    @Test
    fun `next words after a single word are not limited`() {
        for (index in 0 until 20) next.learn("слово$index", listOf("б0"), time = index.toLong())

        assertEquals(20, next.after(listOf("б0"), 1).size)
    }

    @Test
    fun `the count follows unlearning and forgetting, so pruning comes only past the limit`() {
        for (index in 0 until 10) next.learn("слово", listOf("а$index", "б$index"), time = index.toLong())
        next.unlearn("слово", listOf("а0", "б0"))
        next.forget("другое")

        next.learn("слово", listOf("а10", "б10"), time = 10)

        assertEquals(10, countAfterPairs())
    }

    @Test
    fun `a context read from a file past the limit is pruned too, and one too long is skipped`() {
        for (index in 0 until 11) next.put("а$index б$index", Learned("слово", 1, index.toLong()))
        next.put("а б в г", Learned("слово", 1, 1))

        assertEquals(9, countAfterPairs())
        assertEquals(emptyList<Learned>(), next.after(listOf("а", "б", "в", "г"), 3))
    }
}
