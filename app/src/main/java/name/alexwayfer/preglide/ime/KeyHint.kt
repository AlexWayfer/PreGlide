package name.alexwayfer.preglide.ime

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page

/**
 * The size of a key's hint, as a digit in its corner: small, so it does not compete with the letter, and keeps off
 * it on a round key; about as Gboard has its digits.
 */
private val HintSize = 9.5.sp

/** How much of the pressed key's text color a hint on a pressed key takes: muted, as the gray hint is. */
private const val PRESSED_HINT_ALPHA = 0.7f

/**
 * A hint's room from the top and the end of a square key: right in the corner, as the font keeps a little room
 * above a digit and beside it on its own.
 */
private val SquareHintInset = HintInset(top = 0.dp, end = 1.dp)

/**
 * How far in from the key's corner, along its diagonal, the hint's box corner may be before the rounding moves it:
 * the insets above and the font's own room, about 1.5 dp averaged over the two edges, less 1 dp kept clear of the
 * arc, as a digit right against it looks cramped.
 */
private val HintCornerRoom = 0.5.dp

/** How far in a rounded corner's edge is at the diagonal, from each edge, for each dp of the radius: 1 - 1/√2. */
private const val ARC_INSET_PER_RADIUS = 0.2929f

/** A hint's room from the key's [top] and [end] edges. */
internal data class HintInset(val top: Dp, val end: Dp)

/**
 * Where a hint stands on a key with corners of [cornerRadius]: as near the corner as a square one allows, and moved
 * in along the diagonal only as far as the rounded edge would cut its corner off. So a slight rounding leaves it in
 * place, and a round key brings it in just past its arc, no farther toward the letter.
 */
internal fun hintInset(cornerRadius: Dp): HintInset {
    val shift = (cornerRadius * ARC_INSET_PER_RADIUS - HintCornerRoom).coerceAtLeast(0.dp)
    return HintInset(SquareHintInset.top + shift, SquareHintInset.end + shift)
}

/**
 * The key's hint: the smile over the comma, the marks over the period, the gear in the corner of the symbols' key
 * while it offers the keys' [menu], or in its corner what [shownCorner] picks, small. It comes and goes with its
 * key's label when the page changes, and the corner's hints fade into each other as the settings change.
 */
@Composable
internal fun KeyHint(
    action: KeyAction,
    page: Page,
    pressed: Boolean,
    look: KeyLook,
    modifier: Modifier,
    menu: Boolean = false,
) {
    val marks = marksHint(action)
    when {
        opensEmoji(action, page) -> EmojiHint(pressed, look, modifier)
        marks != null -> MarksHint(marks, pressed, modifier)
        offersMenu(action) -> CornerIcon(R.drawable.ic_settings, menu, pressed, look, modifier)
        action is KeyAction.Letter -> {
            val shown = shownCorner(action, look.hints, look.symbols)
            action.hint?.let { CornerHint(it.toString(), shown == Corner.Digit, pressed, look, modifier) }
            action.symbols.firstOrNull()?.let {
                CornerHint(it.toString(), shown == Corner.Symbol, pressed, look, modifier)
            }
            action.optionsHint?.let { CornerHint(it, shown == Corner.Options, pressed, look, modifier) }
        }
    }
}

/** What a key's corner can show: its top row digit, its symbol, or an option of its long press. */
internal enum class Corner { Digit, Symbol, Options }

/**
 * What [action]'s corner shows: its digit while [hints] are shown, its symbol while [symbols] are, and the option
 * that tells of its long press while neither shows; null for nothing. One at a time, as the corner holds one.
 */
internal fun shownCorner(action: KeyAction, hints: Boolean, symbols: Boolean): Corner? {
    val letter = action as? KeyAction.Letter ?: return null
    return when {
        letter.hint != null && hints -> Corner.Digit
        letter.symbols.isNotEmpty() && symbols -> Corner.Symbol
        letter.optionsHint != null -> Corner.Options
        else -> null
    }
}

/**
 * The space bar's hint of what its long press does, small in its corner as a key's options: the cursor's mark while
 * it moves the cursor by [space]'s gestures, or the globe of the keyboard picker it opens otherwise.
 */
@Composable
internal fun SpaceHint(space: SpaceBar?, pressed: Boolean, look: KeyLook, modifier: Modifier) {
    when (spaceHold(space)) {
        SpaceHold.Cursor -> CornerHint(CURSOR_HINT, shown = true, pressed, look, modifier)
        SpaceHold.Picker -> CornerIcon(R.drawable.ic_language, shown = true, pressed, look, modifier)
    }
}

/**
 * An [icon] small in a key's corner, as the globe of the space bar's keyboard picker, fading in and out as it is
 * [shown] or not.
 */
@Composable
private fun CornerIcon(@DrawableRes icon: Int, shown: Boolean, pressed: Boolean, look: KeyLook, modifier: Modifier) {
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "hint")
    val inset = hintInset(look.cornerRadius)
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = modifier
            .padding(top = inset.top + SpaceIconTop, end = inset.end)
            .size(SpaceIconSize)
            .graphicsLayer { this.alpha = alpha },
        tint = hintColor(pressed, MaterialTheme.colorScheme),
    )
}

/** The cursor between two arrows, as it moves after the finger. */
private const val CURSOR_HINT = "<|>"

/** The globe and the gear, about as tall as a hint's char. */
private val SpaceIconSize = 10.dp

/**
 * How much lower than a corner hint's box the globe's icon starts, so its top is level with the bar of the cursor's
 * `<|>` and the globe is centered on the digits' line: measured on a Pixel 9, it stood 2 px higher without.
 */
private val SpaceIconTop = 0.7.dp

/** A short text small in a key's corner, a top row digit or options, fading in and out as it is [shown] or not. */
@Composable
private fun CornerHint(hint: String, shown: Boolean, pressed: Boolean, look: KeyLook, modifier: Modifier) {
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "hint")
    val inset = hintInset(look.cornerRadius)
    Text(
        text = hint,
        modifier = modifier
            .padding(top = inset.top, end = inset.end)
            .graphicsLayer { this.alpha = alpha },
        color = hintColor(pressed, MaterialTheme.colorScheme),
        fontSize = HintSize,
        // A notch bolder than the letters, so the small gray digit still reads, as Gboard's does.
        fontWeight = FontWeight.Medium,
        // The body style's taller line would push the digit down toward the letter.
        lineHeight = HintSize,
        maxLines = 1,
    )
}

/**
 * The smile over the comma, which tells that its long press opens the emoji: always, as it hides nothing, but while
 * the emoji key shows, fading as the key comes and goes (see [emojiHintAlpha]). In the middle of the key's top, clear
 * of its corners however round, so the corners do not move it.
 */
@Composable
private fun EmojiHint(pressed: Boolean, look: KeyLook, modifier: Modifier) {
    Icon(
        painterResource(R.drawable.ic_mood),
        contentDescription = null,
        modifier = modifier
            .padding(top = EmojiHintTop)
            .size(EmojiHintSize)
            .graphicsLayer { alpha = emojiHintAlpha(look) },
        tint = hintColor(pressed, MaterialTheme.colorScheme),
    )
}

/** A notch larger than a digit hint, and as far down from the top as keeps it clear of the comma below. */
private val EmojiHintSize = 13.dp
private val EmojiHintTop = 4.dp

/**
 * The marks over the period, which tell that its long press offers them: always, as the smile over the comma, since
 * they hide nothing. In the middle of the key's top, on a line with the smile.
 */
@Composable
private fun MarksHint(marks: String, pressed: Boolean, modifier: Modifier) {
    Text(
        text = marks,
        modifier = modifier.padding(top = EmojiHintTop),
        color = hintColor(pressed, MaterialTheme.colorScheme),
        fontSize = MarksHintSize,
        fontWeight = FontWeight.Medium,
        lineHeight = MarksHintSize,
        maxLines = 1,
    )
}

/** A notch larger than a digit, as the thin marks read smaller than a digit of the same size. */
private val MarksHintSize = 11.sp

/** The marks a hint shows over a key, the question and exclamation marks its long press offers; null for none. */
internal fun marksHint(action: KeyAction): String? =
    (action as? KeyAction.Letter)?.alternates?.filter { it in HINTED_MARKS }?.joinToString("")?.ifEmpty { null }

/** The marks a hint shows: the ones that end a sentence besides the period, which a key's own label already shows. */
private const val HINTED_MARKS = "?!"

/**
 * Where a key's hint stands: over the comma, the emoji's, and over the period, its marks, in the middle of the top;
 * a digit in the corner.
 */
internal fun hintAlignment(action: KeyAction, page: Page): Alignment =
    if (opensEmoji(action, page) || marksHint(action) != null) Alignment.TopCenter else Alignment.TopEnd

/** A hint is gray, the muted text color, and on a pressed key a muted version of the pressed key's text color. */
internal fun hintColor(pressed: Boolean, colors: ColorScheme): Color =
    if (pressed) pressedColors(colors).content.copy(alpha = PRESSED_HINT_ALPHA) else colors.onSurfaceVariant
