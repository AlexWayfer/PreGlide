package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredSpellingTest {
    private val model = LanguageModel(Dictionary.parse(sequenceOf("ИИ\t10", "привет\t5", "ещё\t3")))

    @Test
    fun abbreviationsUseTheirDictionaryCase() {
        assertEquals("ИИ", preferredSpelling("Ии", model))
        assertEquals("ИИ", preferredSpelling("ии", model))
        assertEquals("ИИ", preferredSpelling("ИИ", model))
    }

    @Test
    fun theUsersSpellingTakesPriorityOverTheDictionary() {
        model.personal.learn("иИ", previous = null, time = 1)

        assertEquals("иИ", preferredSpelling("ии", model))
        assertEquals("ИИ", preferredSpelling("Ии", model))
    }

    @Test
    fun capitalsAndUnknownWordsKeepTheirCase() {
        assertEquals("Привет", preferredSpelling("Привет", model))
        assertEquals("ПРИВЕТ", preferredSpelling("ПРИВЕТ", model))
        assertEquals("TDLib", preferredSpelling("TDLib", model))
        assertEquals("кринж", preferredSpelling("кринж", model))
    }

    @Test
    fun aTypedYoStaysAndTheDictionaryCanRestoreAnOmittedYo() {
        assertEquals("Ещё", preferredSpelling("Еще", model))
        assertEquals("колёсики", preferredSpelling("колёсики", model))
    }
}
