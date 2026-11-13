package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticlesTest {
    @Test
    fun particlesCompleteRussianWordsInOrderAndRespectRemoval() {
        val model = LanguageModel(Dictionary.parse(sequenceOf("релиз-с\t20", "статус\t10")))
        assertEquals(listOf("релиз-то", "релиз-с"), suggestions("Ну релиз-", model, 2))
        assertEquals(listOf("РЕЛИЗ-ТО", "РЕЛИЗ-С"), suggestions("РЕЛИЗ-", model, 2))
        assertEquals(listOf("релиз-то"), suggestions("Ну релиз-т", model, 2))
        assertEquals(listOf("релиз-с", "релиз-статус"), suggestions("Ну релиз-с", model, 2))
        model.personal.remove("релиз-то", 1)
        assertEquals(listOf("релиз-с"), suggestions("Ну релиз-", model, 2))
        assertEquals(emptyList<String>(), suggestions("release-", model, 2))
    }

    @Test
    fun productiveParticlesAreNotLearnedEvenWhenInTheDictionary() {
        val model = LanguageModel(Dictionary.parse(sequenceOf("машины-то\t10")))
        for (word in listOf("релиз-то", "извольте-с", "какой-с", "МАШИНЫ-ТО", "ну-ка-то")) {
            assertFalse(word, isLearnable(word, model))
        }
        for (word in listOf("как-то", "какими-то", "чьё-то", "Почему-то", "когда-то", "кому-то",
            "такому-то", "столько-то", "которыми-то", "вообще-то", "наконец-то", "всего-то", "так-то",
            "как-либо", "как-нибудь", "онлайн-магазин")) {
            assertTrue(word, isLearnable(word, model))
        }
    }
}
