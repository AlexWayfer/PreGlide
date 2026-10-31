package name.alexwayfer.preglide.engine.predict

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PredictionTest {
    private val vocabulary = Vocabulary(listOf("привет", "как", "дела"))
    private val privet = Vocabulary.FIXED.size
    private val kak = privet + 1
    private val dela = privet + 2
    private val comma = vocabulary.id(Token.Mark(','))

    // The order a search sees matters not here: these tests ask for no typed start.
    private val wordIds = IntArray(vocabulary.words.size) { privet + it }

    @Test
    fun `a whole text starts with the start token, padded on the left`() {
        assertArrayEquals(
            intArrayOf(Vocabulary.PADDING, Vocabulary.PADDING, Vocabulary.START, privet, comma, kak),
            contextIds("Привет, как", wholeText = true, vocabulary, 6),
        )
    }

    @Test
    fun `an empty field is its start alone`() {
        assertArrayEquals(intArrayOf(Vocabulary.PADDING, Vocabulary.START), contextIds("", true, vocabulary, 2))
    }

    @Test
    fun `a long text keeps its last tokens`() {
        assertArrayEquals(intArrayOf(kak, dela), contextIds("Привет, как дела", wholeText = true, vocabulary, 2))
    }

    @Test
    fun `a text cut off at its start loses its first token, which may be half a word`() {
        assertArrayEquals(
            intArrayOf(Vocabulary.PADDING, comma, kak, dela),
            contextIds("вет, как дела", wholeText = false, vocabulary, 4),
        )
    }

    @Test
    fun `probabilities add up to one, the largest logit the likeliest`() {
        val probabilities = softmax(floatArrayOf(1000f, 999f, 0f))

        assertEquals(1f, probabilities.sum(), 1e-6f)
        assertEquals(probabilities.max(), probabilities[0])
        assertEquals(kotlin.math.E.toFloat(), probabilities[0] / probabilities[1], 1e-3f)
    }

    @Test
    fun `no logits are no probabilities`() {
        assertEquals(0, softmax(FloatArray(0)).size)
    }

    @Test
    fun `a prediction runs the model on the context of a text`() {
        var given: IntArray? = null
        val prediction = Prediction(vocabulary, 3, Array(vocabulary.size) { "" }, 0, wordIds) { ids ->
            given = ids
            FloatArray(vocabulary.size) { if (it == dela) 10f else 0f }
        }

        val probabilities = prediction.probabilities(prediction.contextOf("как", wholeText = true))

        assertArrayEquals(intArrayOf(Vocabulary.PADDING, Vocabulary.START, kak), given)
        assertEquals(dela, probabilities.indices.maxBy { probabilities[it] })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a prediction needs a spelling for each token`() {
        Prediction(vocabulary, 3, arrayOf("привет"), 0, wordIds) { FloatArray(0) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a prediction needs each word in the search order`() {
        Prediction(vocabulary, 3, Array(vocabulary.size) { "" }, 0, IntArray(0)) { FloatArray(0) }
    }
}
