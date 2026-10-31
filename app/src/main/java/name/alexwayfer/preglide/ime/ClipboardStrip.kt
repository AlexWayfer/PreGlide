package name.alexwayfer.preglide.ime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.R

/** The clip's chip: at most about half the row, so it reads as one thing to tap, and its picture or icon. */
val ClipChipWidth = 240.dp

/** The test tag of the clip's chip. */
const val CLIP_CHIP = "clipChip"
private val ClipLeadSize = 24.dp
private val ClipIconSize = 20.dp

/** The round buttons at the strip's ends, and the room each takes, which the chip keeps clear of. */
private val StripButtonSize = 40.dp
private val StripButtonRoom = 44.dp

/**
 * A strip with no word: the button of the clipboard's panel at its start, and what the user copied, [offered], if
 * anything, in a chip in the middle: a line of the text, or an image's preview; with nothing offered, the logo's
 * glide, [GlideMark], in its place, drawn in as [showing] tells. The chip and the mark fade into each other, the
 * buttons staying in place. [onOpen] opens the clipboard's panel, [onPaste] pastes the clip, and [onSettings], the
 * button at the row's end, opens the keyboard's settings.
 */
@Composable
fun ClipRow(
    offered: OfferedClip?,
    showing: KeyboardShowing,
    onOpen: () -> Unit,
    onPaste: (Clip) -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        StripButton(R.drawable.ic_content_paste, R.string.clipboard, Modifier.align(Alignment.CenterStart), onOpen)
        StripButton(R.drawable.ic_settings, R.string.keyboard_settings, Modifier.align(Alignment.CenterEnd), onSettings)
        AnimatedContent(
            targetState = offered,
            contentKey = { it?.clip },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            // Centered in the whole row, clear of the button however narrow the screen.
            modifier = Modifier.fillMaxSize().padding(horizontal = StripButtonRoom, vertical = 4.dp),
            label = "clip",
        ) { shown ->
            // The content gets the whole row as its least width; the box lets the chip be as wide as its words.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (shown != null) ClipChip(shown) { onPaste(shown.clip) } else GlideMark(showing)
            }
        }
    }
}

@Composable
private fun ClipChip(offered: OfferedClip, onPaste: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .testTag(CLIP_CHIP)
            .widthIn(max = ClipChipWidth)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest)
            .clickable(onClickLabel = stringResource(R.string.clip_paste), onClick = onPaste)
            .padding(start = 6.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val preview = offered.preview
        if (preview != null) {
            Image(
                preview,
                contentDescription = null,
                Modifier.size(ClipLeadSize).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            val icon = if (offered.clip is Clip.Image) R.drawable.ic_image else R.drawable.ic_content_paste
            Icon(painterResource(icon), null, Modifier.size(ClipIconSize), tint = colors.onSurfaceVariant)
        }
        Text(
            when (val clip = offered.clip) {
                is Clip.Image -> stringResource(imageLabel(clip))
                is Clip.Text -> chipTextOf(clip).orEmpty()
            },
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The strip while the clipboard's panel shows: the way back to the keys and the panel's name, and, with [anything]
 * in the history, the button to clear it, which asks first through [onAskToClear].
 */
@Composable
fun ClipboardRow(anything: Boolean, clipboard: ClipboardActions, onAskToClear: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            StripButton(R.drawable.ic_arrow_back, R.string.clipboard_back, Modifier) { clipboard.close() }
            Text(
                stringResource(R.string.clipboard),
                Modifier.padding(start = 4.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        AnimatedVisibility(anything, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
            TextButton(onClick = onAskToClear, modifier = Modifier.fillMaxHeight()) {
                Text(stringResource(R.string.clipboard_clear))
            }
        }
    }
}

@Composable
internal fun StripButton(icon: Int, description: Int, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(StripButtonSize).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            stringResource(description),
            Modifier.size(ClipIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
