package name.alexwayfer.preglide.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import name.alexwayfer.preglide.R

/** The heights of a row of keys, in dp, the user picks from: from a compact keyboard to a roomy one. */
val KeyHeights: IntProgression = 40..64 step 2

/** The height the keys had before the user could pick one. */
const val DEFAULT_KEY_HEIGHT = 52

/** The height of [KeyHeights] nearest to [value], as a slider gives it or as the store holds it. */
fun nearestKeyHeight(value: Float): Int = nearestStop(KeyHeights, value)

/** The height the store's [stored] one gives: the default until the user picks one. */
fun keyHeight(stored: Int?): Int = stored?.let { nearestKeyHeight(it.toFloat()) } ?: DEFAULT_KEY_HEIGHT

/** How tall the keys are: a slider with a stop for each of [KeyHeights] and the height beside the title. */
@Composable
fun KeyHeightSection(settings: KeyboardSettings) {
    StepSliderSection(
        stringResource(R.string.layout_key_height),
        KeyHeights,
        settings.keyHeight,
        describe = { stringResource(R.string.layout_key_height_value, it) },
        onPick = settings::setKeyHeight,
    )
}
