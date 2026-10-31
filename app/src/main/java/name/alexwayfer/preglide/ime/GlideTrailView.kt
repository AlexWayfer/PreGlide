package name.alexwayfer.preglide.ime

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** How wide the glide's trail is where the finger is; it narrows toward its tail. */
internal val TrailWidth = 6.dp

/**
 * The keys, [content], with the glide's [trail] drawn over them, at the [bounds] in the window that [onBounds] gets
 * as they are laid out.
 */
@Composable
internal fun TrailedKeys(
    trail: GlideTrail,
    bounds: () -> Rect,
    onBounds: (Rect) -> Unit,
    content: @Composable () -> Unit,
) {
    val trailColor = MaterialTheme.colorScheme.primary
    val trailWidth = with(LocalDensity.current) { TrailWidth.toPx() }
    Column(
        Modifier
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            // Over the keys, which draw first.
            .drawWithContent {
                drawContent()
                drawTrail(trail, trailColor, trailWidth, bounds().topLeft)
            },
    ) {
        content()
    }
}

/** Runs a frame at a time while [trail] shows, from the start of each glide until the trail fades away. */
@Composable
internal fun TrailFrames(trail: GlideTrail) {
    LaunchedEffect(trail.glides) {
        if (trail.glides == 0) return@LaunchedEffect
        do {
            withFrameMillis { trail.frame(it) }
        } while (trail.showing)
    }
}

/**
 * While a finger glides on [trail], hands the way it went so far to [onPreview] every [PREVIEW_EVERY], so the
 * strip can show what the glide may mean as it goes.
 */
@Composable
internal fun TrailPreviews(trail: GlideTrail, onPreview: (List<Offset>) -> Unit) {
    val currentOnPreview by rememberUpdatedState(onPreview)
    LaunchedEffect(trail.glides) {
        if (trail.glides == 0) return@LaunchedEffect
        delay(PREVIEW_EVERY)
        while (trail.gliding) {
            currentOnPreview(trail.pathSoFar())
            delay(PREVIEW_EVERY)
        }
    }
}

/**
 * Draws [trail] in [color], [width] pixels wide where it is newest, the window's points moved by [origin], where
 * the drawing area lies in the window: each segment fainter and thinner as it ages.
 */
internal fun DrawScope.drawTrail(trail: GlideTrail, color: Color, width: Float, origin: Offset) {
    val points = trail.points
    for (index in 1 until points.size) {
        val fade = trailFade(ageOf(points[index], trail.now))
        drawLine(
            color.copy(alpha = color.alpha * fade),
            points[index - 1].at - origin,
            points[index].at - origin,
            strokeWidth = width * fade,
            cap = StrokeCap.Round,
        )
    }
}
