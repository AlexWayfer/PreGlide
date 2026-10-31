package name.alexwayfer.preglide.ime

import android.text.InputType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.Vocabulary
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.predictionOver
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class GlideContextTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** A field that holds its text, with the cursor at its end. */
    private class Field(var text: String) : SuggestionField {
        override val inputType = InputType.TYPE_CLASS_TEXT
        override val imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = ""

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
        }
    }

    private val now = testDispatchers.now

    // п, р and о are neighbors in a row: про glides as по does, and по, more frequent, wins without the context.
    private val dictionary = Dictionary.parse(sequenceOf("по\t100", "про\t50"))
    private val vocabulary = Vocabulary(listOf("по", "про"))
    private val pro = Vocabulary.FIXED.size + 1
    private val predictions = PredictionModels(CoroutineScope(now), now) { _, over ->
        predictionOver(over, vocabulary, 4) { FloatArray(vocabulary.size) { token -> if (token == pro) 9f else 0f } }
    }

    /** Runs of the prediction model, done when [runPredictions] runs them, as a background thread does later. */
    private val runs = ArrayDeque<Runnable>()
    private val later = Executor { runs.addLast(it) }.asCoroutineDispatcher()

    private fun runPredictions() {
        while (runs.isNotEmpty()) runs.removeFirst().run()
    }

    private val field = Field("Думаю ")
    private val keyboard = KeyboardState()
    private val models = LanguageModels(CoroutineScope(now), now) { LanguageModel(dictionary) }
    private val sources = WordSources(models, prediction = {
        ContextPrediction(predictions, { true }, CoroutineScope(now), later, it, timed = {})
    })
    private val suggestions = WordSuggestions(keyboard, field, KeyboardWork(CoroutineScope(now), now), sources)
    private val personal by lazy {
        PersonalData(PersonalStore { File(folder.root, "personal") }, CoroutineScope(now), now)
    }

    // Lazy: the user's words live in the folder, which the rule makes only once the test starts.
    private val typing by lazy {
        GlideTyping(
            keyboard,
            suggestions,
            learning(),
            GlideContexts(CoroutineScope(now), now),
            GlideOptions(erasesWord = { true }, bringsBackWords = { true }),
            GlideHooks(log = {}),
        )
    }

    private fun learning() =
        Learning(field, { Language.Russian }, { suggestions.model(it) }, { null }, personal, clock = { 0 })
    @Test
    fun aGlideBegunBeforeThePredictionCameReadsWithItOnceItCame() {
        // The first update loads the prediction model; the strip waits for its answer.
        suggestions.update()
        runPredictions()
        field.text = "Думаю, "
        suggestions.update()

        typing.preview(glideThrough("по"))
        runPredictions()
        typing.glided(glideThrough("по"))

        assertEquals("Думаю, про", field.text)
    }
}
