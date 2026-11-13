package name.alexwayfer.preglide.ime

import android.text.InputType
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.predict.Vocabulary
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.predictionOver
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class CapitalSuggestionsTest {
    private class Field(var text: String = "Это ") : SuggestionField {
        override var inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        override val imeOptions = 0
        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)
        override fun textAfterCursor(length: Int): CharSequence = ""
        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
        }
    }

    private val now = testDispatchers.now
    private val scope = CoroutineScope(now)
    private val dictionary = Dictionary.parse(
        sequenceOf("как\t100", "кажется\t90", "катить\t80", "Катя\t10", "по\t100", "Про\t50"),
    )
    private val models = LanguageModels(scope, now) { LanguageModel(dictionary) }
    private val vocabulary = Vocabulary(listOf("как", "кажется", "катить", "катя"))
    private val predictions = PredictionModels(scope, now) { _, over ->
        predictionOver(over, vocabulary, 4) { FloatArray(vocabulary.size) { id -> -id.toFloat() } }
    }
    private val field = Field()
    private val keyboard = KeyboardState()
    private val sources = WordSources(models, prediction = {
        ContextPrediction(predictions, { true }, scope, now, it, timed = {})
    })
    private val suggestions = WordSuggestions(keyboard, field, KeyboardWork(scope, now), sources)

    @Test
    fun shiftBeforeAWordFindsNamesBeyondTheThreeOrdinaryPredictions() {
        suggestions.update()
        assertEquals("как", keyboard.suggestions.first())
        keyboard.shift = ShiftState.Once
        suggestions.update()
        assertEquals("Катя", keyboard.suggestions.first())
        keyboard.shift = ShiftState.Off
        suggestions.update()
        assertEquals("как", keyboard.suggestions.first())
    }

    @Test
    fun sentenceShiftAndCapsLockDoNotPreferNames() {
        suggestions.update()
        keyboard.shift = ShiftState.Once
        for (text in listOf("", "Это. ", "Это! «", "Это\n")) {
            field.text = text
            suggestions.update()
            if (text.endsWith(' ')) assertEquals(text, "Как", keyboard.suggestions.first())
        }
        field.text = "Это "
        keyboard.shift = ShiftState.Locked
        suggestions.update()
        assertEquals("КАК", keyboard.suggestions.first())
    }

    @Test
    fun automaticWordCapitalsDoNotPreferNames() {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        keyboard.shift = ShiftState.Once
        suggestions.update()
        assertEquals("Как", keyboard.suggestions.first())
    }

    @Test
    fun glideReadingUsesTheShiftPreferenceBeforeChangingTheWordsCase() {
        val withoutPrediction = WordSuggestions(keyboard, field, KeyboardWork(scope, now), WordSources(models))
        withoutPrediction.update()
        keyboard.shift = ShiftState.Once
        val words = withoutPrediction.glides.reading()!!.words(glideThrough("по"))
        assertEquals("Про", words.first())
        field.text = "Это. "
        val ordinary = withoutPrediction.glides.reading()!!.words(glideThrough("по"))
        assertEquals("по", ordinary.first())
    }
}
