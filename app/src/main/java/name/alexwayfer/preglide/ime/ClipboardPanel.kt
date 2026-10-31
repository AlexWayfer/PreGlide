package name.alexwayfer.preglide.ime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.R

/** A card's height: three lines of a text, or an image's preview wide enough to tell it. */
private val CardHeight = 76.dp
private val CardGap = 6.dp
private const val TEXT_LINES = 3

/** The red circle of the remove button and the bin in it, as in the strip. */
private val BinSize = 32.dp
private val BinIconSize = 18.dp

/**
 * The clipboard's panel, in place of the keys: what the user copied in the last hour, the latest first, two cards a
 * row. A tap pastes a copy, and a long press asks whether to remove it. [clipboard] does what they ask.
 */
@Composable
fun ClipboardPanel(state: KeyboardState, clipboard: ClipboardActions, modifier: Modifier) {
    AnimatedContent(
        targetState = state.history.isEmpty(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
        label = "history",
    ) { empty ->
        if (empty) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.clipboard_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(CardGap),
                verticalArrangement = Arrangement.spacedBy(CardGap),
                horizontalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                items(state.history, key = { it.clip.copiedAt }) { item ->
                    HistoryCard(
                        item,
                        removing = state.removingClip == item.clip,
                        onPaste = { clipboard.paste(item.clip) },
                        onAskToRemove = { state.removingClip = item.clip },
                        onRemove = { clipboard.remove(item.clip) },
                        onCancel = { state.removingClip = null },
                        Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * A copy: a few lines of its text, or its image. While [removing], it asks whether to: the bin removes it, a tap
 * elsewhere on it keeps it.
 */
@Composable
private fun HistoryCard(
    item: HistoryItem,
    removing: Boolean,
    onPaste: () -> Unit,
    onAskToRemove: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val haptics = rememberKeyHaptics()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(CardHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHighest)
            .combinedClickable(
                onClickLabel = stringResource(R.string.clip_paste),
                onClick = if (removing) onCancel else onPaste,
                onLongClick = {
                    haptics.longPress()
                    onAskToRemove()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CardFace(item)
        AnimatedVisibility(removing, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(colors.surfaceContainerHighest.copy(alpha = 0.85f))) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(BinSize)
                        .clip(CircleShape)
                        .background(colors.error)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        stringResource(R.string.clipboard_remove),
                        Modifier.size(BinIconSize),
                        tint = colors.onError,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardFace(item: HistoryItem) {
    val colors = MaterialTheme.colorScheme
    val preview = item.preview
    val image = stringResource(R.string.clip_image)
    when (val clip = item.clip) {
        is SavedClip.Text -> Text(
            cardText(clip.text),
            Modifier.fillMaxSize().padding(10.dp),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = TEXT_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        is SavedClip.Image -> if (preview != null) {
            Image(preview, image, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(painterResource(R.drawable.ic_image), image, tint = colors.onSurfaceVariant)
        }
    }
}
