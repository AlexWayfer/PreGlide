package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.predict.Vocabulary
import org.junit.Assert.assertEquals
import org.junit.Test

class CapitalWordsTest {
    private val dictionary = Dictionary.parse(
        sequenceOf("как\t100", "кажется\t90", "катить\t80", "Катя\t10", "Катерина\t5"),
    )
    private val model = LanguageModel(dictionary)
    private val vocabulary = Vocabulary(listOf("как", "кажется", "катить", "катя", "катерина"))
    private val prediction = predictionOver(dictionary, vocabulary, 4) { FloatArray(0) }
    private val next = NextTokens(
        prediction,
        FloatArray(vocabulary.size) { id ->
            if (id < Vocabulary.FIXED.size) 0f else 1f / (id + 1)
        },
    )

    @Test
    fun capitalInsideASentenceFindsNamesBeyondTheOrdinaryLimit() {
        assertEquals(listOf("Катя", "Катерина", "Как"), suggestions("Это К", model, 3))
        assertEquals(listOf("Катя", "Катерина", "Катить"), suggestions("Это Кат", model, 3))
        assertEquals(listOf("как", "кажется", "катить"), suggestions("Это к", model, 3))
    }

    @Test
    fun predictionAlsoFindsNamesBeforeItsCandidatesAreLimited() {
        assertEquals(listOf("Катя", "Катерина", "Как"), suggestions("Это К", model, 3, next))
        assertEquals(listOf("Катя"), suggestions("Это Кат", model, 1, next))
    }

    @Test
    fun sentenceStartsAndCapsKeepTheirUsualOrder() {
        for (text in listOf("К", "  К", "«К", "Это. К", "Это! «К", "Это\nК")) {
            assertEquals(text, "Как", suggestions(text, model, 3).first())
        }
        assertEquals("КАК", suggestions("Это КА", model, 3).first())
    }

    @Test
    fun capitalAfterANumberOrAnOpeningBracketStillPrefersNames() {
        for (text in listOf("Это 2 К", "Это (К", "Это: «К", "Это, К")) {
            assertEquals(text, "Катя", suggestions(text, model, 1).first())
        }
    }

    @Test
    fun learnedSpellingDecidesWhetherAWordIsANameAndRemovedNamesStayOut() {
        model.personal.learn("Капа", previous = null, time = 1)
        model.personal.remove("Катя", time = 2)
        assertEquals(listOf("Капа", "Катерина", "Как"), suggestions("Это К", model, 3))
        val spellings = LanguageModel(Dictionary.parse(sequenceOf("Катя\t10", "Катерина\t5")))
        spellings.personal.learn("катя", previous = null, time = 3)
        assertEquals(listOf("Катерина", "Катя"), suggestions("Это Кат", spellings, 2))
    }

    @Test
    fun namesOutsideThePredictionVocabularyAreAlsoPreferred() {
        val smallVocabulary = Vocabulary(listOf("как", "кажется", "катить"))
        val smallPrediction = predictionOver(dictionary, smallVocabulary, 4) { FloatArray(0) }
        val unknown = NextTokens(smallPrediction, FloatArray(smallVocabulary.size) { 0.1f })

        assertEquals(listOf("Катя"), suggestions("Это К", model, 1, unknown))
    }
}
