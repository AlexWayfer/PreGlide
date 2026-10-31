package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.predict.Prediction
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.diagnostics.AppLog
import name.alexwayfer.preglide.engine.suggest.textBeforeWord
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

private const val PREDICTION_TAG = "Prediction"

/** The log line of one run of the prediction model that took [nanos]: how long alone, nothing of the text. */
internal fun predictionTimeLine(nanos: Long): String = "Model ran in ${TimeUnit.NANOSECONDS.toMillis(nanos)} ms"

/** What [run] gives, telling [report] how many nanoseconds it took. */
internal inline fun <T> timedRun(report: (Long) -> Unit, run: () -> T): T {
    val start = System.nanoTime()
    val result = run()
    report(System.nanoTime() - start)
    return result
}

/** What the prediction model gives for the text before the cursor: none to give, on its way, or come. */
sealed interface Next {
    data object None : Next

    data object Waiting : Next

    class Ready(val tokens: NextTokens) : Next
}

/**
 * Asks the prediction models in [predictions], while [use] says to, what comes after the text before the cursor.
 * A context asked about before is answered at once, as while the letters of one word are typed; a new one runs in
 * [runContext], off the main thread, and calls [onReady] when done, as a model that loaded does. [scope] runs on
 * the thread that asks. [timed] gets how many nanoseconds each run of the model took; the app logs it, to tell
 * how fast a model runs on a phone.
 */
class ContextPrediction(
    private val predictions: PredictionModels,
    private val use: () -> Boolean,
    private val scope: CoroutineScope,
    private val runContext: CoroutineContext,
    private val onReady: () -> Unit,
    private val timed: (Long) -> Unit = { AppLog.i(PREDICTION_TAG, predictionTimeLine(it)) },
) {
    /** The context the model was last asked about, and its answer once it came. */
    private var asked: Asked? = null

    private class Asked(val prediction: Prediction, val context: IntArray) {
        var next: NextTokens? = null
    }

    init {
        // A model loaded later is one to suggest with at once; the count as it is now tells nothing new.
        scope.launch { predictions.loaded.drop(1).collect { onReady() } }
    }

    /**
     * What the model of [language] gives for [text], the text before the cursor, up to the word being typed; the
     * model loads over [dictionary] the first time. [wholeText] tells that [text] is all the field holds before the
     * cursor, not cut off at its start.
     */
    fun nextFor(language: Language, dictionary: Dictionary, text: CharSequence, wholeText: Boolean): Next {
        if (!use()) return Next.None
        val prediction = predictions[language]
        if (prediction == null) predictions.load(language, dictionary)
        return prediction?.let { answer(it, it.contextOf(textBeforeWord(text), wholeText)) } ?: Next.None
    }

    /** The answer to [context], given before or asked for now: on its way until it comes. */
    private fun answer(prediction: Prediction, context: IntArray): Next {
        val known = asked?.takeIf { it.prediction === prediction && it.context.contentEquals(context) }
        val asking = known ?: ask(prediction, context)
        return asking.next?.let { Next.Ready(it) } ?: Next.Waiting
    }

    /** Runs [prediction] on [context] off the main thread, calling [onReady] once done. */
    private fun ask(prediction: Prediction, context: IntArray): Asked {
        val asking = Asked(prediction, context)
        asked = asking
        scope.launch {
            val probabilities = withContext(runContext) { timedRun(timed) { prediction.probabilities(context) } }
            asking.next = NextTokens(prediction, probabilities)
            // A newer context asked meanwhile makes this answer stale: that one calls when it comes.
            if (asked === asking) onReady()
        }
        return asking
    }
}
