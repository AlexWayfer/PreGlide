package name.alexwayfer.preglide.settings

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The stops between a slider's ends for [stops], as Material's slider counts its steps. */
fun sliderSteps(stops: IntProgression): Int = stops.count() - 2

/** The one of [stops] nearest to [value], as a slider gives it or as the store holds it. */
fun nearestStop(stops: IntProgression, value: Float): Int = stops.minBy { abs(it - value) }

/**
 * A setting picked from [stops], [stored] so far: a slider with a stop for each, and the value, as [describe] gives
 * it, beside the [title]. Each new stop is felt as a tick, or as [feel] makes it felt, and goes to [onPick] at once,
 * so the keyboard has it when it opens next.
 */
@Composable
fun StepSliderSection(
    title: String,
    stops: IntProgression,
    stored: StateFlow<Int>,
    describe: @Composable (Int) -> String,
    onPick: suspend (Int) -> Unit,
    feel: ((Int) -> Unit)? = null,
) {
    // The slider follows the finger from its own value rather than wait for the store: one page alone changes each
    // setting, and the store is read before the app's screen draws.
    var picked by remember { mutableIntStateOf(stored.value) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // Android's haptic for a slider's points, from Android 14; before it, a clock's tick, as light.
    val stopHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.SEGMENT_TICK
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    val value = describe(picked)
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = picked.toFloat(),
            onValueChange = { moved ->
                val stop = nearestStop(stops, moved)
                if (stop != picked) {
                    picked = stop
                    if (feel != null) feel(stop) else view.performHapticFeedback(stopHaptic)
                    scope.launch { onPick(stop) }
                }
            },
            valueRange = stops.first.toFloat()..stops.last.toFloat(),
            steps = sliderSteps(stops),
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = value
            },
        )
    }
}
