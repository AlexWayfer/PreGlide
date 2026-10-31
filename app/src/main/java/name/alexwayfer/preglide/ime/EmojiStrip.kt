package name.alexwayfer.preglide.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import name.alexwayfer.preglide.R

private val StripIconSize = 20.dp
private val StripButtonSize = 40.dp

/** The room each round button at the strip's ends takes, which the search button keeps clear of. */
private val StripButtonRoom = 44.dp
private val SearchButtonWidth = 240.dp

/** The search's words take at most this much of the strip, the rest going to what they find. */
private val QueryWidth = 140.dp
private val CaretWidth = 2.dp
private val CaretHeight = 20.dp
private val FoundSize = 22.sp

/** What the strip's backspace does: [delete] a char or an emoji before the cursor, as the key, [backspace] the rest. */
class StripErase(val delete: () -> Unit, val backspace: Backspace)

/**
 * The strip while the emoji panel shows: the way back to the keys at its start, the button of the search in the
 * middle, and backspace at its end, which [erase] does as the key's.
 */
@Composable
fun EmojiRow(emoji: EmojiActions, erase: StripErase) {
    Box(Modifier.fillMaxSize()) {
        StripButton(R.drawable.ic_arrow_back, R.string.emoji_back, Modifier.align(Alignment.CenterStart), emoji::close)
        Box(
            Modifier.fillMaxSize().padding(horizontal = StripButtonRoom, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            SearchButton(emoji::startSearch)
        }
        StripBackspace(erase, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun SearchButton(onSearch: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .widthIn(max = SearchButtonWidth)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest)
            .clickable(onClick = onSearch)
            .padding(start = 10.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, Modifier.size(StripIconSize), tint = colors.onSurfaceVariant)
        Text(
            stringResource(R.string.emoji_search),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Backspace in the strip, which deletes and repeats and swipes as the key does, through [erase]; it lights up while
 * pressed, as the key does.
 */
@Composable
private fun StripBackspace(erase: StripErase, modifier: Modifier) {
    val haptics = rememberKeyHaptics()
    val current by rememberUpdatedState(erase)
    var pressed by remember { mutableStateOf(false) }
    val description = stringResource(R.string.key_backspace)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(StripButtonSize)
            .clip(CircleShape)
            .background(if (pressed) pressedColors(colors).background else Color.Transparent)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    haptics.press()
                    try {
                        handleBackspace(down, haptics, current.delete, current.backspace)
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_key_backspace),
            null,
            Modifier.size(StripIconSize),
            tint = if (pressed) pressedColors(colors).content else colors.onSurfaceVariant,
        )
    }
}

/**
 * The strip while the emoji search shows over the keys: the way back to the panel, the words typed, and what they
 * find, the recent emoji before a word is typed; a tap on one types it, through [emoji].
 */
@Composable
fun EmojiSearchRow(state: EmojiState, emoji: EmojiActions) {
    val query = state.query.orEmpty()
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        StripButton(R.drawable.ic_arrow_back, R.string.emoji_search_back, Modifier, emoji::endSearch)
        Query(query)
        LazyRow(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            items(searchResults(query, state), key = { it.emoji.base }) { cell ->
                val description = state.names?.nameOf(cell.emoji.base) ?: cell.form
                Box(
                    Modifier
                        .animateItem()
                        .size(StripButtonSize)
                        .clip(CircleShape)
                        .clickable { emoji.type(cell) }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(cell.form, fontSize = FoundSize, maxLines = 1)
                }
            }
        }
    }
}

/** The words typed, their end in sight, with the caret after them; before any, the caret and what it is for. */
@Composable
private fun Query(query: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.widthIn(max = QueryWidth).fillMaxHeight().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (query.isEmpty()) {
            Caret()
            Text(
                stringResource(R.string.emoji_search),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                query,
                Modifier.weight(1f, fill = false),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                // A long query keeps its end, where the typing goes on, in sight.
                overflow = TextOverflow.StartEllipsis,
            )
            Caret()
        }
    }
}

@Composable
private fun Caret() {
    Box(Modifier.width(CaretWidth).height(CaretHeight).background(MaterialTheme.colorScheme.primary))
}
