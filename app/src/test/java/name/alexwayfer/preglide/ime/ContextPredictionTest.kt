package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.Vocabulary
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.NextWords
import name.alexwayfer.preglide.engine.suggest.predictionOver
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class ContextPredictionTest {
    // Unconfined runs a load or a prediction at once, in the call that starts it.
    private val now = testDispatchers.now
    private val dictionary = Dictionary.parse(sequenceOf("дела\t50", "мир\t40", "привет\t20"))
    private val vocabulary = Vocabulary(listOf("привет", "мир", "дела"))
    private val dela = Vocabulary.FIXED.size + 2

    /** The contexts the model was run on. */
    private val runs = mutableListOf<List<Int>>()
    private val loads = mutableListOf<Language>()
    private var use = true
    private var ready = 0

    /** How long each run of the model took, as the prediction reported it. */
    private val times = mutableListOf<Long>()

    private val predictions = PredictionModels(CoroutineScope(now), now) { language, over ->
        loads += language
        if (language == Language.English) {
            null
        } else {
            predictionOver(over, vocabulary, 4) { ids ->
                runs += ids.toList()
                FloatArray(vocabulary.size) { if (it == dela) 5f else 0f }
            }
        }
    }

    private fun prediction(runContext: CoroutineContext = now) =
        ContextPrediction(
            predictions,
            { use },
            CoroutineScope(now),
            runContext,
            onReady = { ready++ },
            timed = { times += it },
        )

    @Test
    fun turnedOffItNeitherLoadsNorPredicts() {
        use = false

        assertSame(Next.None, prediction().nextFor(Language.Russian, dictionary, "Ну как ", wholeText = true))
        assertTrue(loads.isEmpty())
    }

    @Test
    fun theFirstAskLoadsTheModelAndThePairsSuggestMeanwhile() {
        val predicting = prediction()

        assertSame(Next.None, predicting.nextFor(Language.Russian, dictionary, "Ну как ", wholeText = true))
        assertEquals(listOf(Language.Russian), loads)
        val next = predicting.nextFor(Language.Russian, dictionary, "Ну как ", wholeText = true)

        assertTrue(next is Next.Ready)
        val probabilities = (next as Next.Ready).tokens.probabilities
        assertEquals(dela, probabilities.indices.maxBy { probabilities[it] })
    }

    @Test
    fun aLanguageWithoutAModelKeepsToThePairs() {
        val predicting = prediction()
        predicting.nextFor(Language.English, dictionary, "Hi ", wholeText = true)

        assertSame(Next.None, predicting.nextFor(Language.English, dictionary, "Hi ", wholeText = true))
    }

    @Test
    fun theLettersOfOneWordAskTheModelOnce() {
        val predicting = prediction()
        predicting.nextFor(Language.Russian, dictionary, "", wholeText = true)

        for (typed in listOf("Ну как ", "Ну как д", "Ну как де")) {
            predicting.nextFor(Language.Russian, dictionary, typed, wholeText = true)
        }

        // The context is the text before the word being typed: one run for the three. The first ask only loaded.
        assertEquals(listOf(listOf(Vocabulary.PADDING, Vocabulary.START, Vocabulary.UNKNOWN, Vocabulary.UNKNOWN)), runs)
    }

    @Test
    fun eachRunOfTheModelIsTimed() {
        val predicting = prediction()
        predicting.nextFor(Language.Russian, dictionary, "", wholeText = true)

        predicting.nextFor(Language.Russian, dictionary, "Ну как ", wholeText = true)
        predicting.nextFor(Language.Russian, dictionary, "Ну как де", wholeText = true)
        predicting.nextFor(Language.Russian, dictionary, "Ну как дела ", wholeText = true)

        // Two contexts, two runs: the letters of a word ask the model nothing new.
        assertEquals(runs.size, times.size)
        assertEquals(2, times.size)
        assertTrue(times.all { it >= 0 })
    }

    @Test
    fun theTimeLineTellsTheMillisecondsAlone() {
        assertEquals("Model ran in 2 ms", predictionTimeLine(2_700_000))
    }

    @Test
    fun aNewContextWaitsForTheModelAndCallsWhenItIsDone() {
        val queued = mutableListOf<Runnable>()
        val later = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                queued += block
            }
        }
        val predicting = prediction(later)
        predicting.nextFor(Language.Russian, dictionary, "", wholeText = true)
        // The model loaded, which calls too.
        assertEquals(1, ready)
        ready = 0

        assertSame(Next.Waiting, predicting.nextFor(Language.Russian, dictionary, "Привет ", wholeText = true))
        assertSame(Next.Waiting, predicting.nextFor(Language.Russian, dictionary, "Привет ", wholeText = true))
        queued.removeFirst().run()

        assertEquals(1, ready)
        assertTrue(predicting.nextFor(Language.Russian, dictionary, "Привет ", wholeText = true) is Next.Ready)
    }

    @Test
    fun aStaleAnswerDoesNotCall() {
        val queued = mutableListOf<Runnable>()
        val later = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                queued += block
            }
        }
        val predicting = prediction(later)
        predicting.nextFor(Language.Russian, dictionary, "", wholeText = true)
        ready = 0
        predicting.nextFor(Language.Russian, dictionary, "Привет ", wholeText = true)
        predicting.nextFor(Language.Russian, dictionary, "Привет, мир ", wholeText = true)

        queued.removeFirst().run()

        assertEquals(0, ready)
    }

    @Test
    fun theStripSuggestsThePredictionInsteadOfThePairs() {
        val keyboard = KeyboardState()
        val field = object : SuggestionField {
            override val inputType = android.text.InputType.TYPE_CLASS_TEXT
            override val imeOptions = 0

            override fun textBeforeCursor(length: Int): CharSequence = "Ну как "

            override fun textAfterCursor(length: Int): CharSequence = ""

            override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) = Unit
        }
        val models = LanguageModels(CoroutineScope(now), now) {
            LanguageModel(dictionary, NextWords.parse(sequenceOf("как\tпривет\t9"), dictionary))
        }
        val sources = WordSources(models, prediction = { onReady ->
            ContextPrediction(predictions, { use }, CoroutineScope(now), now, onReady, timed = {})
        })
        val suggestions = WordSuggestions(keyboard, field, KeyboardWork(CoroutineScope(now), now), sources)

        // The model loads on the first update, and the strip suggests anew with it as it is loaded.
        suggestions.update()
        assertEquals("дела", keyboard.suggestions.first())

        use = false
        suggestions.update()
        assertEquals(listOf("привет"), keyboard.suggestions)
    }
}
