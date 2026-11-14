package name.alexwayfer.preglide.engine.suggest

import java.nio.charset.Charset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryGarbageTest {
    private val evidence = checkNotNull(javaClass.getResource("/dictionary-garbage.tsv")).readText()
        .lineSequence().filter(String::isNotEmpty).map { it.split('\t') }.toList()

    @Test
    fun everyRejectedSpellingHasReversibleEncodingEvidence() {
        val windows = Charset.forName("windows-1251")
        val koi = Charset.forName("KOI8-R")
        for ((corrupted, original) in evidence) {
            val recovered = listOf(
                String(corrupted.toByteArray(windows), koi),
                String(corrupted.toByteArray(koi), windows),
            )
            assertTrue(corrupted, recovered.any { it.equals(original, ignoreCase = true) })
            assertTrue(corrupted, isDictionaryGarbage(corrupted.lowercase(Locale.ROOT)))
            assertFalse(original, isDictionaryGarbage(original))
        }
        assertEquals(evidence.size, evidence.map { it.first().lowercase(Locale.ROOT) }.toSet().size)
    }

    @Test
    fun ordinaryWordsAndAbbreviationsAreNotRejected() {
        for (word in listOf("", "hello", "пьёт", "объект", "Ын", "э-э", "бдсм", "ВГТРК", "МБОУ", "ЛПНП")) {
            assertFalse(word, isDictionaryGarbage(word))
        }
    }
}
