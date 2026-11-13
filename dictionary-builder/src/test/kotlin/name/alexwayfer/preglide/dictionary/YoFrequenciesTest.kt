package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YoFrequenciesTest {
    private val wheels = listOf(Word("колеса", 17_593), Word("колёса", 3658))
    private val usage = listOf(YoUsage("колеса", "колёса", plainUses = 294, yoUses = 441))

    @Test
    fun cleanUsesRedistributeThePairAndKeepOtherWords() {
        val words = wheels + Word("мир", 50_000)

        assertEquals(
            listOf(Word("мир", 50_000), Word("колёса", 12_171), Word("колеса", 9080)),
            rebalanceYo(words, usage),
        )
    }

    @Test
    fun missingSpellingsAreNotInventedOrReweighted() {
        val cases = listOf(emptyList(), wheels.take(1), wheels.takeLast(1), listOf(Word("мир", 100)))

        for (words in cases) assertEquals(words, rebalanceYo(words, usage))
        assertEquals(wheels, rebalanceYo(wheels, emptyList()))
    }

    @Test
    fun namesKeepTheirDictionaryCase() {
        val words = listOf(Word("Алена", 5700), Word("Алёна", 2018))
        val names = listOf(YoUsage("алена", "алёна", plainUses = 49, yoUses = 250))

        assertEquals(listOf(Word("Алёна", 5818), Word("Алена", 1900)), rebalanceYo(words, names))
    }

    @Test
    fun roundingNeverRemovesEitherSpelling() {
        val words = listOf(Word("все", 1), Word("всё", 1))
        val onlyPlain = listOf(YoUsage("все", "всё", plainUses = 1000, yoUses = 0))
        val onlyYo = listOf(YoUsage("все", "всё", plainUses = 0, yoUses = 1000))

        assertEquals(words, rebalanceYo(words, onlyPlain))
        assertEquals(words, rebalanceYo(words, onlyYo))
    }

    @Test
    fun usagesReadBothSpellingsAndTheirCounts() {
        assertEquals(usage, yoUsages(sequenceOf("колеса\tколёса\t294\t441")))
        assertEquals(emptyList<YoUsage>(), yoUsages(emptySequence()))
    }

    @Test
    fun invalidUsagesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { YoUsage("все", "всё", -1, 1) }
        assertThrows(IllegalArgumentException::class.java) { YoUsage("все", "всё", 1, -1) }
        assertThrows(IllegalArgumentException::class.java) { YoUsage("все", "всё", 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { YoUsage("всё", "всё", 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { YoUsage("все", "все", 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { YoUsage("все", "ещё", 1, 1) }
    }

    @Test
    fun bundledUsagesAreUniqueAndOnlySelectedPairsChange() {
        val uses = checkNotNull(javaClass.getResourceAsStream("/yo-uses.tsv"))
            .bufferedReader().useLines(::yoUsages)

        assertEquals(62, uses.size)
        assertEquals(uses.size, uses.map { it.plain }.distinct().size)
        assertEquals(usage.single(), uses.single { it.plain == "колеса" })
        assertEquals(listOf(Word("колёса", 12_171), Word("колеса", 9080)), rebalanceYo(wheels))
        val unchanged = listOf(Word("берег", 100), Word("берёг", 1))
        assertEquals(unchanged, rebalanceYo(unchanged))
        assertTrue(uses.none { it.yo == "ищёт" || it.yo == "тяжёло" })
    }
}
