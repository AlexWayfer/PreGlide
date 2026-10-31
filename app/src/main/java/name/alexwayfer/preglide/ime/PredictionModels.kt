package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.Prediction
import name.alexwayfer.preglide.engine.suggest.Dictionary
import kotlin.coroutines.CoroutineContext

/**
 * The prediction models of the process, each loaded once with [readPrediction] in [loadContext], off the main
 * thread, and only when asked for: while the user keeps them off, none loads. A language with no model gets none;
 * its suggestions keep to the pairs. Calls come on the thread [scope] runs on.
 */
class PredictionModels(
    private val scope: CoroutineScope,
    private val loadContext: CoroutineContext,
    private val readPrediction: (Language, Dictionary) -> Prediction?,
) {
    private val predictions = HashMap<Language, Prediction>()
    private val loading = HashSet<Language>()

    /** Counts the models loaded, so the keyboard knows to suggest anew with a model it waited for. */
    val loaded: StateFlow<Int>
        field = MutableStateFlow(0)

    /** The model of [language] if it is loaded, or null. */
    operator fun get(language: Language): Prediction? = predictions[language]

    /** Loads the model of [language] over its [dictionary], unless it is loaded or loading. */
    fun load(language: Language, dictionary: Dictionary) {
        if (!loading.add(language)) return
        scope.launch {
            val prediction = withContext(loadContext) { readPrediction(language, dictionary) }
            if (prediction != null) {
                predictions[language] = prediction
                loaded.value++
            }
        }
    }
}
