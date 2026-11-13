package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecondCapitalTest {
    private val model = LanguageModel(Dictionary.parse(sequenceOf("всё\t5", "привет\t10")))

    @Test
    fun onlyTheSecondLetterMayChangeInAFinishedKnownWord() {
        model.personal.learn("Wokege", null, 1)
        assertEquals("Wokege", secondCapitalSuggestion("WOkege", model))
        assertEquals("Привет", secondCapitalSuggestion("ПРивет", model))
        for (word in listOf("WOKEGE", "WoKege", "WOke", "WOkeGe", "Wokege", "WO", "ВСе", "wOkege")) {
            assertNull(word, secondCapitalSuggestion(word, model))
        }
    }

    @Test
    fun aKnownMixedCaseSpellingOrRemovedWordStaysAsTyped() {
        model.personal.learn("TDLib", null, 1)
        assertNull(secondCapitalSuggestion("TDLib", model))
        model.personal.remove("привет", 1)
        assertNull(secondCapitalSuggestion("ПРивет", model))
    }
}
