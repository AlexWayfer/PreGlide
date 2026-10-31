package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningTest {
    private val model = LanguageModel(Dictionary.parse(sequenceOf("привет\t10", "Москва\t5", "NASA\t1")))

    @Test
    fun explicitlyKeptCaseOverridesTheDictionaryAndEarlierLearning() {
        model.personal.learn("привет", previous = null, time = 1)

        assertEquals("ПРИВЕТ", learnedSpelling("ПРИВЕТ", sentenceStart = false, model, kept = true))
        assertEquals("Привет", learnedSpelling("Привет", sentenceStart = false, model, kept = true))
        assertEquals("ООО", learnedSpelling("ООО", sentenceStart = true, model, kept = true))
        assertEquals("привет", learnedSpelling("Привет", sentenceStart = true, model, kept = true))
        assertEquals("кринж", learnedSpelling("Кринж", sentenceStart = true, model, kept = true))
    }

    @Test
    fun aLearnedDictionaryWordKeepsTheUsersSpelling() {
        model.personal.learn("nasa", previous = null, time = 1)

        assertEquals("nasa", learnedSpelling("Nasa", sentenceStart = true, model))
        assertEquals("nasa", learnedSpelling("nasa", sentenceStart = false, model))
        assertEquals("nasa", learnedSpelling("NASA", sentenceStart = false, model))
    }

    @Test
    fun `a single letter is learnable only as a dictionary word`() {
        val letters = LanguageModel(Dictionary.parse(sequenceOf("и\t20", "я\t10", "кот\t5")))

        assertTrue(isLearnable("и", letters))
        assertTrue(isLearnable("Я", letters))
        assertFalse(isLearnable("т", letters))
        assertFalse(isLearnable("D", letters))
        assertTrue(isLearnable("кот", letters))
        assertTrue(isLearnable("ок", letters))
    }

    @Test
    fun `a dictionary word keeps the dictionary's spelling`() {
        assertEquals("привет", learnedSpelling("ПРИВЕТ", sentenceStart = false, model))
        assertEquals("привет", learnedSpelling("Привет", sentenceStart = true, model))
        assertEquals("Москва", learnedSpelling("москва", sentenceStart = false, model))
    }

    @Test
    fun `a word typed without its yo takes the dictionary's yo, but a typed yo stays`() {
        // In search order, as a dictionary file keeps them.
        val yo = LanguageModel(Dictionary.parse(sequenceOf("все\t20", "всё\t10", "ещё\t3", "колесики\t5")))

        assertEquals("ещё", learnedSpelling("еще", sentenceStart = false, yo))
        assertEquals("ещё", learnedSpelling("Еще", sentenceStart = true, yo))
        assertEquals("все", learnedSpelling("все", sentenceStart = false, yo))
        assertEquals("колёсики", learnedSpelling("колёсики", sentenceStart = false, yo))
    }

    @Test
    fun `a new word within a sentence keeps its case`() {
        assertEquals("Вася", learnedSpelling("Вася", sentenceStart = false, model))
        assertEquals("пиздец", learnedSpelling("пиздец", sentenceStart = false, model))
    }

    @Test
    fun `a new word at a sentence start loses the capital, unless all in caps`() {
        assertEquals("пиздец", learnedSpelling("Пиздец", sentenceStart = true, model))
        assertEquals("ЛОЛ", learnedSpelling("ЛОЛ", sentenceStart = true, model))
    }

    @Test
    fun `at a sentence start a word learned before keeps its spelling`() {
        model.personal.learn("Вася", previous = null, time = 1)

        assertEquals("Вася", learnedSpelling("Вася", sentenceStart = true, model))
        assertEquals("вася", learnedSpelling("вася", sentenceStart = false, model))
    }

    @Test
    fun `a word learned before keeps its spelling when typed in caps`() {
        model.personal.learn("кринж", previous = null, time = 1)
        model.personal.learn("TDLib", previous = null, time = 1)

        assertEquals("кринж", learnedSpelling("КРИНЖ", sentenceStart = false, model))
        assertEquals("TDLib", learnedSpelling("TDLIB", sentenceStart = false, model))
        assertEquals("ООО", learnedSpelling("ООО", sentenceStart = false, model))
    }
}
