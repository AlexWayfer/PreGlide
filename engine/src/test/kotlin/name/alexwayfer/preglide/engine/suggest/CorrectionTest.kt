package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.predict.Vocabulary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class CorrectionTest {
    private val russian = Touches(KeyboardLayout.of(Language.Russian), emptyList())
    private val dictionary = Dictionary.parse(sequenceOf("привет\t100000", "дела\t10", "ещё\t500"))

    private fun model(personal: PersonalWords = PersonalWords()) = LanguageModel(dictionary, personal = personal)

    @Test
    fun softSignInsteadOfTIsCorrectedWithTheAppDictionary() {
        val dictionary = File("../app/src/main/assets/dictionaries/ru.tsv").reader().use(Dictionary::read)
        val model = LanguageModel(dictionary)
        val words = suggestions("ьак", model, 3, touches = russian)

        assertEquals("так", correction("ьак", words, model, null, russian))
        val layout = russian.layout
        val touches = Touches(layout, "ьак".map { layout.places.getValue(it).center })
        val tappedWords = suggestions("ьак", model, 3, touches = touches)

        assertEquals("так", correction("ьак", tappedWords, model, null, touches))
    }

    @Test
    fun `a slip to a key nearby is fixed when the word meant is far likelier`() {
        // т is the neighbor of и: 100000 made less by a cost of 2.8 is some 6000, far above an unknown word's 50.
        assertEquals("привет", correction("Ну пртвет", listOf("привет"), model(), null, russian))
    }

    @Test
    fun `the correction is the strip's word, in the typed case`() {
        assertEquals("Привет", correction("Пртвет", listOf("Привет"), model(), null, russian))
    }

    @Test
    fun `a word meant that is not likely enough leaves the word as typed`() {
        val rare = LanguageModel(Dictionary.parse(sequenceOf("привет\t1000")))

        // 1000 made less by 2.8 is some 60: not five times an unknown word's 50.
        assertEquals(null, correction("пртвет", listOf("привет"), rare, null, russian))
    }

    @Test
    fun `a known word is changed only for one far likelier than it`() {
        val english = KeyboardLayout.of(Language.English)
        val model = LanguageModel(Dictionary.parse(sequenceOf("tip\t1000", "top\t100000")))
        val i = english.places.getValue('i')
        fun touches(across: Float) = Touches(english, listOf(null, i.pointAt(across, 0.5f), null))

        // In i's middle, top costs 3.1: 4400, below five times tip's 1000. On the edge toward o, nearly nothing.
        assertEquals(null, correction("tip", listOf("tip", "top"), model, null, touches(0.5f)))
        assertEquals("top", correction("tip", listOf("tip", "top"), model, null, touches(1f)))
    }

    @Test
    fun `a missed last char is filled in, but the start of a word is not a typo`() {
        assertEquals("привет", correction("приве", listOf("привет"), model(), null, russian))
        assertEquals(null, correction("прив", listOf("привет"), model(), null, russian))
    }

    @Test
    fun `nothing typed, a word in caps, a word the user kept, or one with a digit is left alone`() {
        val kept = PersonalWords().apply { learn("пртвет", null, 0, kept = true) }

        assertEquals(null, correction("Ну ", listOf("привет"), model(), null, russian))
        assertEquals(null, correction("ПРТВЕТ", listOf("ПРИВЕТ"), model(), null, russian))
        assertEquals(null, correction("пртвет", listOf("привет"), model(kept), null, russian))
        assertEquals(null, correction("пртвет2", listOf("привет"), model(), null, russian))
    }

    @Test
    fun `a word of two letters is left alone, however likely the word a slip away`() {
        val model = LanguageModel(Dictionary.parse(sequenceOf("за\t10000000", "зал\t10000000")))

        // щ is the neighbor of з.
        assertEquals(null, correction("ща", listOf("за"), model, null, russian))
        assertEquals("зал", correction("щал", listOf("зал"), model, null, russian))
    }

    @Test
    fun `the same word in another case or with yo is no correction`() {
        assertEquals(null, correction("еще", listOf("ещё"), model(), null, russian))
    }

    @Test
    fun `the user's uses make a word likelier, typed or meant`() {
        val learned = PersonalWords().apply { learn("пртвет", null, 0) }

        // One use of the typed word weighs 300000: more than привет made less by its cost.
        assertEquals(null, correction("пртвет", listOf("привет"), model(learned), null, russian))
    }

    @Test
    fun `with the prediction, the context makes a rare word likely enough`() {
        val vocabulary = Vocabulary(listOf("дела", "привет"))
        val prediction = predictionOver(dictionary, vocabulary, 4) { FloatArray(0) }
        val probabilities = FloatArray(vocabulary.size).apply { this[Vocabulary.FIXED.size] = 0.5f }

        // к is the neighbor of е. By its frequency, 10, дела would be no correction.
        assertEquals(null, correction("как дкла", listOf("дела"), model(), null, russian))
        assertEquals(
            "дела",
            correction("как дкла", listOf("дела"), model(), NextTokens(prediction, probabilities), russian),
        )
    }
}
