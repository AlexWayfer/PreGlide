package name.alexwayfer.preglide.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.Key
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.settings.DEFAULT_KEY_CORNER_RADIUS

/** How much of a divider's height the line takes, centered; it fades out toward its ends. */
private const val DIVIDER_HEIGHT_SHARE = 0.6f

/** How much of the line's length each end fades over; the rest between them is whole. */
private const val DIVIDER_FADE_SHARE = 0.33f

private val DividerWidth = 1.dp

/**
 * How much shorter a row of keys is in landscape, where the screen is low: AOSP's keyboard is 176 dp tall there
 * and 205.6 dp in portrait (LatinIME's `config_default_keyboard_height`).
 */
private const val LANDSCAPE_ROW_SHARE = 176f / 205.6f

/** The suggestion strip's height in portrait and in landscape, as AOSP's `config_suggestions_strip_height`. */
private val PortraitStripHeight = 40.dp
private val LandscapeStripHeight = 36.dp

/**
 * How the keys look by the user's settings: whether they show their [hints], how much of each key's own background
 * shows ([backgrounds]: 1 all of it, 0 none, and between them while it fades), as much of the [dividers] between
 * them, how round their corners are, how tall a row of them is, and the suggestion strip above them; how much of the
 * [digitRow] above the letters shows, 1 all of it, 0 none, as [backgrounds]; as much of the [emojiKey] beside the
 * comma, of its width; whether the letters show their [symbols]; and whether the [strip] shows above the keys, or
 * only the panels' own rows of it.
 */
data class KeyLook(
    val hints: Boolean,
    val backgrounds: Float,
    val dividers: Float,
    val cornerRadius: Dp,
    val height: Dp,
    val stripHeight: Dp,
    val digitRow: Float = 0f,
    val emojiKey: Float = 0f,
    val symbols: Boolean = false,
    val strip: Boolean = true,
)

/**
 * The user's settings the keys' look comes from, and whether the screen is in [landscape]. With the keys'
 * [backgrounds], their corners are of [cornerRadius] dp. Without them, only a pressed key shows its shape, and the
 * settings hide the radius, so it takes the default; and the keys can have [dividers] instead, which the settings
 * offer only then. Each row of keys is [height] dp tall, and lower in [landscape], as the strip above them. With the
 * [digitRow], the top row shows no [hints]: the row holds its digits. With the [emojiKey], the bottom row has a key
 * of its own for the emoji. With the [symbols], the letters under the top row show and offer the symbols page's keys
 * at their places, whatever the digit row. Without the [strip], only a panel shows its row of the strip.
 */
data class LookSettings(
    val hints: Boolean,
    val backgrounds: Boolean,
    val dividers: Boolean,
    val cornerRadius: Int,
    val height: Int,
    val landscape: Boolean,
    val digitRow: Boolean = false,
    val emojiKey: Boolean = false,
    val symbols: Boolean = false,
    val strip: Boolean = true,
)

/** The look of the user's [settings]: see [LookSettings]. */
fun keyLook(settings: LookSettings): KeyLook = with(settings) {
    KeyLook(
        hints && !digitRow,
        backgrounds = if (backgrounds) 1f else 0f,
        dividers = if (dividers && !backgrounds) 1f else 0f,
        cornerRadius = (if (backgrounds) cornerRadius else DEFAULT_KEY_CORNER_RADIUS).dp,
        height = rowHeight(height, landscape),
        stripHeight = stripHeight(landscape),
        digitRow = if (digitRow) 1f else 0f,
        emojiKey = if (emojiKey) 1f else 0f,
        symbols = symbols,
        strip = strip,
    )
}

/** A row of keys of the user's [height] in dp: as tall in portrait, a share of it in [landscape]. */
internal fun rowHeight(height: Int, landscape: Boolean): Dp =
    if (landscape) (height * LANDSCAPE_ROW_SHARE).dp else height.dp

/** The suggestion strip's height, lower in [landscape]. */
internal fun stripHeight(landscape: Boolean): Dp = if (landscape) LandscapeStripHeight else PortraitStripHeight

/** Whether the key at [index] of [row] has a divider after it: between two keys, not beside a gap. */
internal fun dividedFromNext(row: List<Key>, index: Int): Boolean {
    val next = row.getOrNull(index + 1)?.action
    return row[index].action != KeyAction.Gap && next != null && next != KeyAction.Gap
}

/**
 * A divider on the key's end edge, if it is [divided] from the next key: there, in the middle of the room between
 * their backgrounds, a thin line of [color] across the middle of the key's height, fading out toward its ends, as
 * much of it as the keys' [dividers] show.
 */
internal fun Modifier.keyDivider(divided: Boolean, dividers: Float, color: Color): Modifier =
    if (!divided || dividers == 0f) {
        this
    } else {
        drawBehind {
            val x = if (layoutDirection == LayoutDirection.Ltr) size.width else 0f
            val top = size.height * (1 - DIVIDER_HEIGHT_SHARE) / 2
            val bottom = size.height - top
            drawLine(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    DIVIDER_FADE_SHARE to color,
                    1 - DIVIDER_FADE_SHARE to color,
                    1f to Color.Transparent,
                    startY = top,
                    endY = bottom,
                ),
                Offset(x, top),
                Offset(x, bottom),
                strokeWidth = DividerWidth.toPx(),
                alpha = dividers,
            )
        }
    }

/** A key's room around it, its rounded shape and its background. */
internal fun Modifier.keySurface(key: Key, pressed: Boolean, look: KeyLook, colors: ColorScheme): Modifier =
    padding(horizontal = 2.dp, vertical = 3.dp)
        .clip(RoundedCornerShape(look.cornerRadius))
        .background(keySurfaceColor(key, pressed, look.backgrounds, colors))

/**
 * A key's background, as much of it as the keys' [backgrounds] show; a pressed key lights up whole all the same, at
 * once, as the press must show while typing.
 */
internal fun keySurfaceColor(key: Key, pressed: Boolean, backgrounds: Float, colors: ColorScheme): Color {
    val color = keyBackground(key, pressed, colors)
    return if (pressed) color else color.copy(alpha = color.alpha * backgrounds)
}
