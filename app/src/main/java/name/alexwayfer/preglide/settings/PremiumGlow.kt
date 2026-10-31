package name.alexwayfer.preglide.settings

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// The golds of premium's icon: its glide's gradient, pale at the top left and deep at the bottom right, and its rim.
val PremiumGoldPale = Color(0xFFFAE38C)
val PremiumGold = Color(0xFFE8BE2E)
val PremiumGoldDeep = Color(0xFFD49E12)
private val PremiumGoldRim = Color(0xFFF8E7A6)

/** The text on a gold button: dark on every gold of it, in light and dark alike. */
val GoldButtonText = Color(0xFF241A00)

private val GoldButtonBrush = Brush.linearGradient(listOf(PremiumGoldPale, PremiumGold, PremiumGoldDeep))
private val GOLD_BUTTON_RIM = 0.5.dp

/** A gold Premium button, as premium's icon draws its glide: a gradient and a thin light rim. */
fun Modifier.goldButtonFill(): Modifier = this
    .background(GoldButtonBrush, CircleShape)
    .border(GOLD_BUTTON_RIM, PremiumGoldRim, CircleShape)

// How strong each background glow starts at its corner, and how far toward the diagonal it reaches before it fades
// out, leaving the middle plain.
private const val ACCENT_GLOW_ALPHA = 0.25f
private const val GOLD_GLOW_ALPHA = 0.15f
private const val ACCENT_GLOW_REACH = 0.6f
private const val GOLD_GLOW_REACH = 0.5f

// How much of the sideways part of a glow's direction stays: below 1 turns it toward vertical.
private const val GLOW_SIDEWAYS = 0.35f

/**
 * The Premium page's background, as CustomTV's Premium banner has it: [base] with a glow of the theme's [accent]
 * from the top right corner and a gold one from the bottom left, each fading out before the diagonal between the
 * other two corners, whatever the screen's proportions.
 */
fun Modifier.premiumBackground(base: Color, accent: Color): Modifier = drawWithCache {
    val glows = premiumBackgroundGlows(size.width, size.height)
    val accentGlow = Brush.linearGradient(
        colors = listOf(accent.copy(alpha = ACCENT_GLOW_ALPHA), Color.Transparent),
        start = glows.accentFrom,
        end = premiumGlowEnd(glows.accentFrom, glows.accentTo, ACCENT_GLOW_REACH, GLOW_SIDEWAYS),
    )
    val goldGlow = Brush.linearGradient(
        colors = listOf(PremiumGold.copy(alpha = GOLD_GLOW_ALPHA), Color.Transparent),
        start = glows.goldFrom,
        end = premiumGlowEnd(glows.goldFrom, glows.goldTo, GOLD_GLOW_REACH, GLOW_SIDEWAYS),
    )
    onDrawBehind {
        drawRect(base)
        drawRect(accentGlow)
        drawRect(goldGlow)
    }
}

/** Where the background's glows start, at their corners, and where they head. */
data class PremiumBackgroundGlows(
    val accentFrom: Offset,
    val accentTo: Offset,
    val goldFrom: Offset,
    val goldTo: Offset,
)

/**
 * Each glow runs from its corner straight to the diagonal from the top left to the bottom right corner, so the
 * diagonal is where both fade out. On a zero size every point is the origin.
 */
fun premiumBackgroundGlows(width: Float, height: Float): PremiumBackgroundGlows {
    val diagonalSquared = width * width + height * height
    if (diagonalSquared == 0f) return PremiumBackgroundGlows(Offset.Zero, Offset.Zero, Offset.Zero, Offset.Zero)
    // The point of the diagonal nearest to a corner (x, y) lies at (x·w + y·h) / (w² + h²) of its length.
    fun nearestOnDiagonal(x: Float, y: Float): Offset {
        val along = (x * width + y * height) / diagonalSquared
        return Offset(width * along, height * along)
    }
    return PremiumBackgroundGlows(
        accentFrom = Offset(width, 0f),
        accentTo = nearestOnDiagonal(width, 0f),
        goldFrom = Offset(0f, height),
        goldTo = nearestOnDiagonal(0f, height),
    )
}

/**
 * Where a glow from [from] fades out: [reach] of the way to [toward], turned toward vertical by keeping only
 * [sideways] of the sideways part of the direction. The distance stays the same as [reach] of the straight way.
 */
fun premiumGlowEnd(from: Offset, toward: Offset, reach: Float, sideways: Float): Offset {
    val straight = toward - from
    val turned = Offset(straight.x * sideways, straight.y)
    val turnedLength = turned.getDistance()
    if (turnedLength == 0f) return from
    return from + turned * (straight.getDistance() * reach / turnedLength)
}

private const val GLOW_TURN_MILLIS = 6000
private val GLOW_STEP = 1.dp
private const val GLOW_ALPHA = 0.3f
private const val GLOW_ACCENT_ALPHA = 0.4f
private const val FULL_TURN = 360f

/** The angle of [premiumGlow]'s colors, turning slowly all the time; it stands still with the animations off. */
@Composable
fun rememberPremiumGlowAngle(): State<Float> =
    rememberInfiniteTransition(label = "premium edge").animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(GLOW_TURN_MILLIS, easing = LinearEasing)),
        label = "premium edge angle",
    )

/** Corner radii in pixels, clockwise from the top left. */
data class GlowCorners(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
) {
    fun scaled(factor: Float) =
        GlowCorners(topLeft * factor, topRight * factor, bottomRight * factor, bottomLeft * factor)

    companion object {
        val Square = GlowCorners(0f, 0f, 0f, 0f)
    }
}

// The edge's faint thin line, and the glow from it into the screen.
private val EDGE_LINE = 0.5.dp
private const val EDGE_LINE_ALPHA = 0.6f
private val EDGE_GLOW = 8.dp

/**
 * A Premium border along the inside of the screen's edge with [corners], as CustomTV's Premium banner has: a thin
 * line, and a glow into the screen that starts faint and fades away from it, in gold and the theme's [accent], whose
 * colors turn by [angle], read only while drawing. The glow is drawn as thin rings, each a little fainter than the one
 * before.
 */
fun Modifier.premiumGlow(corners: GlowCorners, accent: Color, angle: () -> Float): Modifier = drawWithCache {
    val line = EDGE_LINE.toPx()
    val step = GLOW_STEP.toPx()
    val layers = (EDGE_GLOW / GLOW_STEP).toInt()
    val lineInset = line / 2
    val linePath = Path().apply { addRoundRect(premiumGlowRing(size, corners, lineInset)) }
    val glowPaths = List(layers) { layer ->
        // Each ring's middle: half a step past the ring before it.
        val inset = lineInset + line / 2 + step * layer + step / 2
        Path().apply { addRoundRect(premiumGlowRing(size, corners, inset)) }
    }
    val lineStroke = Stroke(line)
    val glowStroke = Stroke(step)
    onDrawWithContent {
        drawContent()
        val brush = premiumGlowBrush(size, angle(), accent)
        glowPaths.forEachIndexed { layer, path ->
            drawPath(path, brush, alpha = GLOW_ALPHA * premiumGlowFade(layer, layers), style = glowStroke)
        }
        drawPath(linePath, brush, alpha = EDGE_LINE_ALPHA, style = lineStroke)
    }
}

/**
 * A rectangle [inset] inside [size] whose corners keep the centers of [corners], so the rings stay parallel to the
 * edge. It never turns inside out on a size smaller than twice the inset.
 */
fun premiumGlowRing(size: Size, corners: GlowCorners, inset: Float): RoundRect {
    fun radius(edge: Float) = (edge - inset).coerceAtLeast(0f).let { CornerRadius(it, it) }
    return RoundRect(
        left = inset,
        top = inset,
        right = (size.width - inset).coerceAtLeast(inset),
        bottom = (size.height - inset).coerceAtLeast(inset),
        topLeftCornerRadius = radius(corners.topLeft),
        topRightCornerRadius = radius(corners.topRight),
        bottomRightCornerRadius = radius(corners.bottomRight),
        bottomLeftCornerRadius = radius(corners.bottomLeft),
    )
}

/** How strong the glow ring [layer] of [layers] is: full at the line, fading to nothing away from it. */
fun premiumGlowFade(layer: Int, layers: Int): Float {
    if (layers <= 0) return 0f
    val left = (1f - layer.toFloat() / layers).coerceIn(0f, 1f)
    return left * left
}

/** The gold and [accent] colors of the Premium border over [size], turned by [degrees]. */
private fun premiumGlowBrush(size: Size, degrees: Float, accent: Color): Brush {
    val (start, end) = premiumGlowLine(size, degrees)
    return Brush.linearGradient(
        colors = listOf(PremiumGold, accent.copy(alpha = GLOW_ACCENT_ALPHA), PremiumGold),
        start = start,
        end = end,
    )
}

/**
 * The line the border's gradient runs along: through the center at [degrees], long enough to cover the corners at
 * every angle. On a zero size both ends are the origin.
 */
fun premiumGlowLine(size: Size, degrees: Float): Pair<Offset, Offset> {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = center.getDistance()
    val radians = Math.toRadians(degrees.toDouble())
    val reach = Offset(cos(radians).toFloat(), sin(radians).toFloat()) * radius
    return (center - reach) to (center + reach)
}

// The system's rounded corners overlay masks more of each corner than the radius it reports, which thins the line
// there; a larger radius keeps the line inside the visible glass.
private const val SCREEN_CORNER_SCALE = 1.15f

/**
 * The rounded corners of the screen the activity fills, read again when the screen turns; square where the system
 * does not tell, as before Android 12.
 */
@Composable
fun rememberScreenCorners(): GlowCorners {
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    return remember(activity, configuration) {
        val insets = activity?.window?.decorView?.rootWindowInsets
        if (insets == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            GlowCorners.Square
        } else {
            fun radius(position: Int) = insets.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
            GlowCorners(
                topLeft = radius(RoundedCorner.POSITION_TOP_LEFT),
                topRight = radius(RoundedCorner.POSITION_TOP_RIGHT),
                bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
                bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT),
            ).scaled(SCREEN_CORNER_SCALE)
        }
    }
}
