package name.alexwayfer.preglide.settings

import androidx.annotation.StringRes
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ime.GlideStrip
import name.alexwayfer.preglide.ime.SpaceGestures
import java.util.Locale

/**
 * The hint of a row to a page of switches: the names of the ones turned [on], joined in their order and starting with
 * a capital, or [none] when all are off.
 */
fun switchesHint(on: List<String>, none: String): String =
    if (on.isEmpty()) none else on.joinToString(", ").replaceFirstChar { it.titlecase(Locale.ROOT) }

/**
 * How the suggestions' row names what is on there: the [strip], the [prediction] model, [autocorrect], fresh
 * [screenshots].
 */
@StringRes
fun suggestionsOn(strip: Boolean, prediction: Boolean, autocorrect: Boolean, screenshots: Boolean): List<Int> =
    listOfNotNull(
        R.string.input_hint_strip.takeIf { strip },
        R.string.input_hint_prediction.takeIf { prediction },
        R.string.input_hint_autocorrect.takeIf { autocorrect },
        R.string.input_hint_screenshots.takeIf { screenshots },
    )

/**
 * Whether the prediction model does anything: it gives the next words of the [strip], and the context [glides] are
 * read with. Without either, its switch has nothing to change.
 */
fun predictionMatters(strip: Boolean, glides: Boolean): Boolean = strip || glides

/**
 * How the gestures' row names what is on there: [glide] typing and the [space] bar's gestures. The backspace after a
 * glide goes unnamed: it only tells how glide typing behaves.
 */
@StringRes
fun gesturesOn(glide: Boolean, space: SpaceGestures): List<Int> = listOfNotNull(
    R.string.input_hint_glide.takeIf { glide },
    when (space) {
        SpaceGestures.SwipeLanguage -> R.string.input_hint_space_language
        SpaceGestures.SwipeCursor -> R.string.input_hint_space_cursor
        SpaceGestures.Off -> null
    },
)

/** The name and the hint of what the [strip] offers after a glide, in its choice. */
fun glideStripText(strip: GlideStrip): Pair<Int, Int> = when (strip) {
    GlideStrip.GlideWords -> R.string.settings_glide_strip_glide_words to R.string.settings_glide_strip_glide_words_hint
    GlideStrip.NextWords -> R.string.settings_glide_strip_next_words to R.string.settings_glide_strip_next_words_hint
    GlideStrip.Auto -> R.string.settings_glide_strip_auto to R.string.settings_glide_strip_auto_hint
}

/** The name and the hint of the space bar's [gestures] in their choice. */
fun spaceGesturesText(gestures: SpaceGestures): Pair<Int, Int> = when (gestures) {
    SpaceGestures.SwipeLanguage -> R.string.settings_space_language to R.string.settings_space_language_hint
    SpaceGestures.SwipeCursor -> R.string.settings_space_cursor to R.string.settings_space_cursor_hint
    SpaceGestures.Off -> R.string.settings_space_off to R.string.settings_space_off_hint
}
