package name.alexwayfer.preglide.ime

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import name.alexwayfer.preglide.R

/** The test tag of the glide mark. */
const val GLIDE_MARK = "glideMark"

/** Pale enough not to pass for a suggestion or a button. */
private const val MARK_ALPHA = 0.3f

/** About as long as a glide across the keys takes. */
const val MARK_DRAW_MILLIS = 700

/**
 * The glide of the app's logo, pale, for a strip with nothing to show. As the keyboard shows, it draws in from left
 * to right, as a finger would glide it, speeding up and slowing down as a glide does; once the user typed anything,
 * it shows whole, as the strip fades it in between words (see [KeyboardShowing]). With animations turned off in the
 * system it shows whole at once: Compose scales the draw's duration by the system's animator duration scale.
 */
@Composable
fun GlideMark(showing: KeyboardShowing, modifier: Modifier = Modifier) {
    val drawn = rememberDrawnIn(showing)
    Image(
        painterResource(R.drawable.ic_glide_mark),
        contentDescription = null,
        modifier
            .testTag(GLIDE_MARK)
            // Read in the draw phase, so the draw-in redraws the mark without recomposing it.
            .drawWithContent {
                clipRect(right = revealedWidth(size.width, drawn.value)) { this@drawWithContent.drawContent() }
            },
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MARK_ALPHA)),
    )
}

/**
 * How much of the mark is drawn, from 0 to 1: drawn in when it shows before the user typed anything since the
 * keyboard showed, and anew each time the keyboard shows again, as the keyboard keeps its views while it hides and
 * the mark stays composed; whole otherwise.
 */
@Composable
internal fun rememberDrawnIn(showing: KeyboardShowing): State<Float> {
    val drawn = remember { Animatable(if (showing.drawsIn) 0f else 1f) }
    LaunchedEffect(showing.shows) {
        if (showing.drawsIn) {
            drawn.snapTo(0f)
            drawn.animateTo(1f, tween(MARK_DRAW_MILLIS, easing = FastOutSlowInEasing))
        } else {
            drawn.snapTo(1f)
        }
    }
    return drawn.asState()
}

/**
 * When the keyboard showed for a field, as the glide mark draws in then: how many times it did, [shows], and whether
 * the user typed anything since. It shows for a field as the field starts, and anew for the same field after it hid.
 */
class KeyboardShowing {
    var shows by mutableIntStateOf(0)
        private set

    /** Whether the mark draws in, as before anything was typed since the keyboard showed. */
    var drawsIn by mutableStateOf(true)
        private set

    /** The keyboard shows for a field. */
    fun shown() {
        drawsIn = true
        shows++
    }

    /** The user typed: a key, a word chosen or glided. */
    fun typed() {
        drawsIn = false
    }
}

/** How much of a mark [width] wide shows when the part [drawn] of it, from 0 to 1, is drawn in from its left edge. */
fun revealedWidth(width: Float, drawn: Float): Float = width * drawn.coerceIn(0f, 1f)
