package name.alexwayfer.preglide.ime

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyboardLayout

/** How near the space bar's ends its arrows come while the cursor moves. */
private val ArrowEdge = 4.dp

/** The size of the space bar's arrows: small, beside the language's small name. */
private val ArrowSize = 18.dp

/** The cursor mark in the space bar's middle while the cursor moves: a text cursor's thin line. */
private val MarkWidth = 2.dp
private val MarkHeight = 18.dp

/**
 * The space bar's face: the language's name, or its sign on a keypad, with arrows beside it while [arrows] says a
 * swipe switches the language; the bar of a new language comes in with its keys. While a long press moves the
 * cursor, as [touch] shows, the name fades into a cursor mark and the arrows go out to the bar's ends, coming in if
 * they were not there. [layout] is the one the bar is on, which may be leaving with its language.
 */
@Composable
internal fun BoxScope.SpaceFace(state: KeyboardState, layout: KeyboardLayout, touch: KeyTouch, arrows: Boolean) {
    val colors = MaterialTheme.colorScheme
    val content = if (touch.pressed) pressedColors(colors).content else colors.onSurface
    // One motion for the name, the mark and the arrows' way out, so they change together.
    val cursorMode by animateFloatAsState(if (touch.cursorMode) 1f else 0f)
    val arrowsAlpha by animateFloatAsState(if (arrows || touch.cursorMode) 1f else 0f)
    var labelWidth by remember { mutableIntStateOf(0) }
    val icon = icon(KeyAction.Space, state, layout.page)
    val face = Modifier
        .graphicsLayer { alpha = 1f - cursorMode }
        .onSizeChanged { labelWidth = it.width }
    if (icon != null) {
        // The key's semantics already describe it.
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = face)
    } else {
        val size = labelSize(KeyAction.Space)
        Text(spaceLabel(layout.language), face, color = content, fontSize = size, maxLines = 1)
    }
    Box(
        Modifier
            .size(MarkWidth, MarkHeight)
            .graphicsLayer {
                alpha = cursorMode
                scaleY = cursorMode
            }
            .background(content, RoundedCornerShape(percent = 50)),
    )
    SpaceArrows(labelWidth, cursorMode, arrowsAlpha, content)
}

/**
 * The space bar's two arrows, faded by [alpha]: beside the language's name, [labelWidth] pixels wide, and [toEdges]
 * of the way out to the bar's ends (see [arrowInset]).
 */
@Composable
private fun BoxScope.SpaceArrows(labelWidth: Int, toEdges: Float, alpha: Float, tint: Color) {
    var barWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val inset = with(density) {
        val arrow = ArrowSize.roundToPx()
        arrowInset(barWidth, labelWidth, arrow, ArrowEdge.roundToPx(), toEdges).toDp()
    }
    Box(
        Modifier
            .matchParentSize()
            .onSizeChanged { barWidth = it.width }
            .graphicsLayer { this.alpha = alpha },
    ) {
        val placed = Modifier.padding(horizontal = inset)
        SpaceArrow(R.drawable.ic_chevron_left, tint, placed.align(Alignment.CenterStart))
        SpaceArrow(R.drawable.ic_chevron_right, tint, placed.align(Alignment.CenterEnd))
    }
}

/** One of the space bar's arrows, [icon]. */
@Composable
private fun SpaceArrow(@DrawableRes icon: Int, tint: Color, modifier: Modifier) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(ArrowSize))
}
