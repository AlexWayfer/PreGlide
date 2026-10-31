package name.alexwayfer.preglide.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import name.alexwayfer.preglide.engine.layout.KeyAction

/** Room around the cells inside the popup. */
internal val PopupPadding = 8.dp

/** The selection indicator's height as a share of the cell's. */
private const val INDICATOR_SIZE = 0.85f

/** How far the indicator moves toward the finger, in cells, with the finger at the boundary. */
private const val MAX_SHIFT = 0.05f

/** The finger's largest pull, at the boundary with the next cell. */
private const val MAX_PULL = 0.5f

/** A pill: a popup with a single option would be a circle. */
private val PopupShape = RoundedCornerShape(percent = 50)

/**
 * What a touch of the key offers: its hint only while [hints] are shown, and its symbols only while [symbols] are,
 * so a hidden digit or symbol is not in the popup.
 */
internal fun offeredAction(action: KeyAction, hints: Boolean, symbols: Boolean): KeyAction =
    if (action is KeyAction.Letter) {
        action.copy(hint = action.hint.takeIf { hints }, symbols = action.symbols.takeIf { symbols }.orEmpty())
    } else {
        action
    }

/** What a key's long press popup shows for its [action]'s options: each as the key types it now, as a capital. */
internal fun optionLabels(action: KeyAction, state: KeyboardState): List<String> =
    (action as? KeyAction.Letter)?.options.orEmpty().map { state.typed(it).toString() }

/** What a cell of a long press popup shows: a [Label], as a char or an emoji, or a menu item's [MenuIcon]. */
internal sealed interface OptionFace {
    data class Label(val text: String) : OptionFace

    data class MenuIcon(val item: KeyMenuItem) : OptionFace
}

/**
 * What a key's long press popup shows: its [action]'s options as [optionLabels] has them, or, for a key with none,
 * the icons of its [menu]'s items.
 */
internal fun optionFaces(action: KeyAction, state: KeyboardState, menu: KeyMenu?): List<OptionFace> {
    val labels = optionLabels(action, state)
    return if (labels.isEmpty() && menu != null) {
        menu.items.map(OptionFace::MenuIcon)
    } else {
        labels.map(OptionFace::Label)
    }
}

/**
 * A row of cells above the key showing [faces], laid out by [layout]. An indicator marks the selected option of
 * [touch]; it leans toward the finger as it pulls to a neighbor, and slides over once the finger gets there.
 */
@Composable
internal fun OptionsPopup(faces: List<OptionFace>, layout: OptionsLayout, touch: KeyTouch, geometry: KeyGeometry) {
    val motion = rememberIndicatorMotion(layout.cellOf(touch.selected), touch.pull)
    val position = remember(layout, geometry.cellSize.width, geometry.popupPadding) {
        OptionsPopupPosition(keyCenterInPopup(layout, geometry.cellSize.width, geometry.popupPadding))
    }
    Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false)) {
        OptionsRow(faces, layout, geometry.cellSize, motion)
    }
}

/** The popup's pill with its cells, and the indicator moving by [motion] behind them. */
@Composable
private fun OptionsRow(faces: List<OptionFace>, layout: OptionsLayout, cellSize: IntSize, motion: IndicatorMotion) {
    val colors = MaterialTheme.colorScheme
    // The pressed key's colors, so the indicator looks as the key under the finger did.
    val pressed = pressedColors(colors)
    val cellWidth = with(LocalDensity.current) { cellSize.width.toDp() }
    val cellHeight = with(LocalDensity.current) { cellSize.height.toDp() }
    Row(
        Modifier
            .shadow(4.dp, PopupShape)
            .clip(PopupShape)
            .background(colors.surfaceContainerHighest)
            .padding(PopupPadding)
            .drawBehind {
                val (left, right) = motion.edges
                val bounds = indicatorBounds(left, right, cellSize)
                drawRoundRect(pressed.background, bounds.topLeft, bounds.size, CornerRadius(bounds.height / 2))
            },
    ) {
        for (cell in 0 until layout.width) {
            Box(Modifier.size(cellWidth, cellHeight), contentAlignment = Alignment.Center) {
                // Flows into the pressed key's text color as the indicator comes over the cell, and back.
                val color = lerp(colors.onSurface, pressed.content, cellCoverage(cell, motion.edges))
                when (val face = faces[layout.optionInCell(cell)]) {
                    is OptionFace.Label -> Text(text = face.text, color = color, fontSize = 22.sp)
                    is OptionFace.MenuIcon ->
                        Icon(painterResource(face.item.icon), stringResource(face.item.label), tint = color)
                }
            }
        }
    }
}

/**
 * The indicator's left and right edges, in cells from the popup's left edge, each springing toward its target in
 * [targets] on the same spring. So when the finger crosses into the next cell and both targets shift by the same
 * amount, the edges move together: neither draws in, shakes, nor lags into a longer indicator.
 *
 * The edges move on every frame by [advance] toward whatever the targets are then. A new target does not restart
 * an animation: restarting on every finger move, faster than frames come, kept an edge from moving at all.
 */
internal class IndicatorMotion(var targets: Pair<Float, Float>) {
    var left by mutableStateOf(SpringState(targets.first))
        private set
    var right by mutableStateOf(SpringState(targets.second))
        private set

    val edges: Pair<Float, Float> get() = left.value to right.value

    /**
     * Moves the edges [seconds] on at the [scale] of the system's or the user's pace of animations: slower as it
     * grows, and straight onto the targets at 0, when animations are off.
     */
    fun advance(seconds: Float, scale: Float = 1f) {
        if (scale == 0f) {
            left = SpringState(targets.first)
            right = SpringState(targets.second)
            return
        }
        left = left.toward(targets.first, seconds / scale, EDGE_STIFFNESS)
        right = right.toward(targets.second, seconds / scale, EDGE_STIFFNESS)
    }
}

/** The indicator heading for [cell], reaching toward the finger's [pull], moved on every frame. */
@Composable
private fun rememberIndicatorMotion(cell: Int, pull: Float): IndicatorMotion {
    val targets = indicatorTargets(cell, pull)
    val motion = remember { IndicatorMotion(targets) }
    SideEffect { motion.targets = targets }
    LaunchedEffect(motion) {
        // The pace of animations, which Compose's own animations read from the same context.
        val pace = coroutineContext[MotionDurationScale]
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                motion.advance((now - last) / NANOS_PER_SECOND, pace?.scaleFactor ?: 1f)
                last = now
            }
        }
    }
    return motion
}

private const val NANOS_PER_SECOND = 1_000_000_000f

/** Compose's medium spring: an edge settles in about a sixth of a second. */
private const val EDGE_STIFFNESS = 1500f

/**
 * Where the indicator's edges head, in cells: its [cell], moved toward the neighbor the finger [pull]s to by up to
 * [MAX_SHIFT] at the boundary. The indicator keeps its size, so crossing a boundary moves both edges alike.
 */
internal fun indicatorTargets(cell: Int, pull: Float): Pair<Float, Float> {
    val shift = pull / MAX_PULL * MAX_SHIFT
    return cell + shift to cell + 1 + shift
}

/** How much of [cell] the indicator between its [edges], in cells, covers: 0 for none of it, 1 for all of it. */
internal fun cellCoverage(cell: Int, edges: Pair<Float, Float>): Float =
    (minOf(edges.second, cell + 1f) - maxOf(edges.first, cell.toFloat())).coerceIn(0f, 1f)

/**
 * The indicator between its [left] and [right] edges, in cells: a circle on its cell, and a pill should the edges
 * ever be more than a cell apart.
 */
internal fun indicatorBounds(left: Float, right: Float, cellSize: IntSize): Rect {
    val inset = cellSize.height * (1 - INDICATOR_SIZE) / 2
    val diameter = cellSize.height - 2 * inset
    // Centered in the cell when the cell is wider than tall, as in landscape.
    val sideInset = (cellSize.width - diameter) / 2
    return Rect(left * cellSize.width + sideInset, inset, right * cellSize.width - sideInset, cellSize.height - inset)
}

/** Places the popup right above its key, [keyCenterOffset] from its left edge over the key's middle. */
internal class OptionsPopupPosition(private val keyCenterOffset: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        popupLeft(anchorBounds, keyCenterOffset, popupContentSize.width, windowSize.width),
        anchorBounds.top - popupContentSize.height,
    )
}
