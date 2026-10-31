package name.alexwayfer.preglide.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import name.alexwayfer.preglide.R

/**
 * The radii, in dp, the user picks the keys' corners from: square to as round as a narrow key of the Russian
 * letters gets, nearly a circle.
 */
val KeyCornerRadii: IntProgression = 0..16 step 2

/** The radius the keys had before the user could pick one. */
const val DEFAULT_KEY_CORNER_RADIUS = 8

/** The radius of [KeyCornerRadii] nearest to [value], as a slider gives it or as the store holds it. */
fun nearestCornerRadius(value: Float): Int = nearestStop(KeyCornerRadii, value)

/** The radius the store's [stored] one gives: the default until the user picks one. */
fun keyCornerRadius(stored: Int?): Int = stored?.let { nearestCornerRadius(it.toFloat()) } ?: DEFAULT_KEY_CORNER_RADIUS

/**
 * How round the keys' corners are: a slider with a stop for each of [KeyCornerRadii] and the radius beside the
 * title.
 */
@Composable
fun KeyCornersSection(settings: KeyboardSettings) {
    StepSliderSection(
        stringResource(R.string.appearance_key_corners),
        KeyCornerRadii,
        settings.keyCornerRadius,
        describe = { stringResource(R.string.appearance_key_corners_value, it) },
        onPick = settings::setKeyCornerRadius,
    )
}
