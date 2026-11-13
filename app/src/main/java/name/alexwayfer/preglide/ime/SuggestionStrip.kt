package name.alexwayfer.preglide.ime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import name.alexwayfer.preglide.R

private val WordSize = 16.sp

/** A long word shrinks to fit its slot, down to this size, rather than lose its end. */
private val SmallestWordSize = 10.sp

/** The emoji for the word typed: a little larger than a word, as a picture reads smaller than letters. */
private val EmojiSize = 20.sp

/** The room the cancel button takes at the end of the strip, kept free at both ends of the remove button. */
private val CancelRoom = 88.dp

/** The red circle of the remove button and the bin in it. */
private val BinSize = 26.dp
private val BinIconSize = 16.dp

/** Short: the words change with every letter typed, and a slow fade would lag behind the typing. */
private const val FADE_MILLIS = 120

/**
 * As quick as a fade: the slide alone tells the next words after a glide or a pick as moving on, and it is done
 * before the next glide lifts.
 */
private const val TURN_MILLIS = 120

/**
 * How far the next words slide in, and the words before them slide out: a short step onward, as Material's shared
 * axis transition takes, so each word stays over its own slot and reads at once.
 */
private val TurnShift = 30.dp

/** The mark under the glided word among its others: a short dash in the accent color, a little off the slot's edge. */
private val GlidedMarkWidth = 16.dp
private val GlidedMarkHeight = 2.dp
private val GlidedMarkGap = 5.dp

/**
 * The row above the keys with the words to finish the one being typed, or the next one. It keeps its height when
 * it has nothing to show, so the keys do not jump as suggestions come and go. While the dictionary loads it says
 * so. A long press on a word asks, in the same row, whether to remove it, and [onRemove] removes it. With no word
 * to show, it has the button of the clipboard's panel, and offers what the user copied a moment ago; while the panel
 * shows, the way back to the keys. [clipboard] does what they ask. A strip with no word also has the button that
 * opens the keyboard's settings, through [onSettings]. While the emoji panel shows, the way back to the keys, its
 * search and a backspace, which [erase] does; while its search shows, the words typed and what they find. [emoji]
 * does what they ask. The strip is [height] tall.
 */
@Composable
fun SuggestionStrip(
    state: KeyboardState,
    height: Dp,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    clipboard: ClipboardActions,
    emoji: EmojiActions,
    erase: StripErase,
    onSettings: () -> Unit,
) {
    AnimatedContent(
        targetState = stripContent(state),
        // The words change within the row, which fades each of them; the row itself changes only with its kind. A
        // clip comes and goes within the row of the clipboard's button.
        contentKey = ::stripRow,
        transitionSpec = { fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS)) },
        modifier = Modifier.fillMaxWidth().height(height),
        label = "strip",
    ) { content ->
        when (content) {
            StripContent.Suggestions -> SuggestionRow(
                stripWords(state),
                onPick,
                onEmoji = emoji::chose,
                onAskToRemove = { state.removing = it },
            )
            StripContent.Loading -> LoadingRow()
            is StripContent.Removing -> RemovalRow(
                content.word,
                onRemove = {
                    state.removing = null
                    onRemove(content.word)
                },
                onCancel = { state.removing = null },
            )
            is StripContent.Clip -> ClipRow(content.clip, state.showing, clipboard::open, clipboard::paste, onSettings)
            StripContent.Empty -> ClipRow(null, state.showing, clipboard::open, clipboard::paste, onSettings)
            StripContent.Clipboard -> ClipboardRow(state.history.isNotEmpty(), clipboard) { state.clearing = true }
            StripContent.Clearing -> RemovalRow(
                stringResource(R.string.clipboard_clear_all),
                onRemove = clipboard::clear,
                onCancel = { state.clearing = false },
            )
            StripContent.Emoji -> EmojiRow(emoji, erase)
            StripContent.EmojiSearch -> EmojiSearchRow(state.emoji, emoji)
        }
    }
}

/**
 * [content] under the strip's [row] while it [shows], as a panel shows its own row while the strip is hidden: at
 * once, as the panel it comes with fades in, or, when [animated], unfolding over the keys that stay.
 */
@Composable
internal fun UnderStripRow(
    shows: Boolean,
    row: @Composable () -> Unit,
    animated: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column {
        if (animated) AnimatedVisibility(shows) { row() } else if (shows) row()
        content()
    }
}

/** Tells that the dictionary is loading: the strip is blank only for now. */
@Composable
private fun LoadingRow() {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.strip_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The [words], or the emoji for the word typed, in their slots; autocorrect's word stands out in the accent color, and
 * the glided word among its others has a mark under it. The next words after a glide or a word picked from the strip
 * slide in from the right.
 * [onEmoji] types an emoji tapped.
 */
@Composable
private fun SuggestionRow(
    words: StripWords,
    onPick: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onAskToRemove: (String) -> Unit,
) {
    // Onward, as the text goes on: the words for the word before leave to the left. Any other change of the words fades
    // each of them in its slot.
    val shift = with(LocalDensity.current) { TurnShift.roundToPx() }
    AnimatedContent(
        targetState = words,
        contentKey = StripWords::turn,
        transitionSpec = {
            val slide = tween<IntOffset>(TURN_MILLIS)
            val fade = tween<Float>(TURN_MILLIS)
            (slideInHorizontally(slide) { shift } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(slide) { -shift } + fadeOut(fade))
        },
        modifier = Modifier.fillMaxSize(),
        label = "nextWords",
    ) { shown ->
        Row(Modifier.fillMaxSize()) {
            shown.slots.forEachIndexed { slot, content ->
                val word = (content as? SlotContent.Word)?.word
                val ask = onAskToRemove.takeIf { isRemovable(word, shown.typedWord, shown.typedWordRemovable) }
                val look = slotLook(slot, word, shown.correction, shown.glidedWord)
                SuggestionSlot(content, look, onPick, onEmoji, ask, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SuggestionSlot(
    content: SlotContent?,
    look: SlotLook,
    onPick: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onAskToRemove: ((String) -> Unit)?,
    modifier: Modifier,
) {
    val word = (content as? SlotContent.Word)?.word
    val haptics = rememberKeyHaptics()
    val colors = MaterialTheme.colorScheme
    // The color flows as the word becomes autocorrect's or stops being it, with the same word in place.
    val color by animateColorAsState(if (look.correcting) colors.primary else colors.onSurface, label = "correcting")
    val best = look.best
    val style = TextStyle(
        color = color,
        // The keyboard's font, which the theme's styles carry; the rest of the body style would change the look.
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = if (best) FontWeight.Medium else FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
    val touch = if (word == null) {
        Modifier
    } else {
        Modifier.combinedClickable(
            onClick = { onPick(word) },
            onLongClick = onAskToRemove?.let { ask ->
                {
                    haptics.longPress()
                    ask(word)
                }
            },
        )
    }
    Box(
        modifier.padding(horizontal = 2.dp, vertical = 2.dp).clip(RoundedCornerShape(8.dp)).then(touch),
        contentAlignment = Alignment.Center,
    ) {
        // A word and the emoji that take its place fade into each other, as two words do.
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS)) },
            contentAlignment = Alignment.Center,
            label = "suggestion",
        ) { shown ->
            if (shown is SlotContent.Emoji) {
                EmojiChoices(shown.emoji, onEmoji)
            } else {
                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    if (shown is SlotContent.Word) {
                        BasicText(
                            text = shown.styledLabel,
                            style = style,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = SmallestWordSize, maxFontSize = WordSize),
                        )
                    }
                }
            }
        }
        GlidedMark(look.glided)
    }
}

/**
 * The mark under the glided word among its others, while [shown]: the word in the text, which a tap on another swaps
 * out. It fades as the word in its slot does.
 */
@Composable
private fun BoxScope.GlidedMark(shown: Boolean) {
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(FADE_MILLIS), label = "glided")
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = GlidedMarkGap)
            .size(GlidedMarkWidth, GlidedMarkHeight)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

/** The [emoji] for the word typed, side by side in their slot, each typed by its own tap through [onEmoji]. */
@Composable
private fun EmojiChoices(emoji: List<String>, onEmoji: (String) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        for (each in emoji) {
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).clickable { onEmoji(each) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(each, style = TextStyle(fontSize = EmojiSize))
            }
        }
    }
}

/**
 * Asks whether to remove [word] from suggestions, as a long press is easy to make by chance: a button with a bin
 * and the word in the middle, and a cancel button at the end.
 */
@Composable
private fun RemovalRow(word: String, onRemove: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
        TextButton(
            onClick = onRemove,
            // Kept off both ends alike: centered, and clear of the cancel button however long the word.
            modifier = Modifier.align(Alignment.Center).padding(horizontal = CancelRoom).fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Box(
                Modifier.size(BinSize).background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    stringResource(R.string.strip_remove),
                    Modifier.size(BinIconSize),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(word, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
            Text(stringResource(R.string.strip_remove_cancel))
        }
    }
}
