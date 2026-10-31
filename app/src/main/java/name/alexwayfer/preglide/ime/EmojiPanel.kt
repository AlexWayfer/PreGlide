package name.alexwayfer.preglide.ime

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.emoji.EmojiGroup

/** The row of the panel's tabs. */
private val TabRowHeight = 40.dp
private val TabIconSize = 20.dp

/** The pill behind the selected tab's icon. */
private val TabIndicatorWidth = 32.dp
private val TabIndicatorHeight = 28.dp

/** The least width of an emoji's cell: as many cells as fit, about nine on a phone held upright. */
private val EmojiCellWidth = 40.dp
private val EmojiSize = 26.sp

/**
 * The emoji panel, in place of the keys: the tabs of the recent emoji and the groups, over their pages, which the
 * finger pulls sideways from one to the next. A tap on an emoji types it, a long press offers its skin tones;
 * [emoji] does what they ask.
 */
@Composable
fun EmojiPanel(state: EmojiState, emoji: EmojiActions, modifier: Modifier) {
    val pager = key(state.openings) {
        rememberPagerState(initialPage = emojiTabs.indexOf(state.tab)) { emojiTabs.size }
    }
    // The page shown is kept, so the panel stays on it when it is shown anew, as the phone turns.
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { state.tab = emojiTabs[it] } }
    Column(modifier) {
        EmojiTabs(pager)
        // The emoji fade in once they are read, which takes a moment the first time.
        AnimatedVisibility(state.palette != null, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                EmojiPage(emojiTabs[page], state, emoji)
            }
        }
    }
}

/** The tabs' icons, and behind them the selection pill, which slides with the pages as the finger pulls them. */
@Composable
private fun EmojiTabs(pager: PagerState) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val indicator = colors.secondaryContainer
    val indicatorSize = with(LocalDensity.current) { Size(TabIndicatorWidth.toPx(), TabIndicatorHeight.toPx()) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(TabRowHeight)
            .drawBehind {
                // Read while drawing, so the pill follows the pages on every frame without a new composition.
                val tabWidth = size.width / emojiTabs.size
                val center = (pager.currentPage + pager.currentPageOffsetFraction + HALF) * tabWidth
                drawRoundRect(
                    indicator,
                    Offset(center - indicatorSize.width / 2, (size.height - indicatorSize.height) / 2),
                    indicatorSize,
                    CornerRadius(indicatorSize.height / 2),
                )
            },
    ) {
        emojiTabs.forEachIndexed { index, tab ->
            val name = stringResource(tabName(tab))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClickLabel = name) { scope.launch { pager.animateScrollToPage(index) } },
                contentAlignment = Alignment.Center,
            ) {
                val icon = painterResource(tabIcon(tab))
                Icon(icon, name, Modifier.size(TabIconSize), tint = colors.onSurfaceVariant)
                // The selected look over it, fading in and out with the pages, read in the layer, not in composition.
                Icon(
                    icon,
                    contentDescription = null,
                    Modifier
                        .size(TabIconSize)
                        .graphicsLayer {
                            alpha = tabSelection(index, pager.currentPage + pager.currentPageOffsetFraction)
                        },
                    tint = colors.onSecondaryContainer,
                )
            }
        }
    }
}

private const val HALF = 0.5f

/** A tab's page: its emoji in rows, or for the recent ones while there are none, what will be there. */
@Composable
private fun EmojiPage(tab: EmojiTab, state: EmojiState, emoji: EmojiActions) {
    val cells = cellsOf(tab, state)
    if (cells.isEmpty() && tab == EmojiTab.Recent) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.emoji_recent_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyVerticalGrid(
        GridCells.Adaptive(EmojiCellWidth),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    ) {
        items(cells, key = { cellKey(tab, it) }) { cell ->
            EmojiCellView(
                cell,
                description = state.names?.nameOf(cell.emoji.base) ?: cell.form,
                onType = emoji::type,
                Modifier.animateItem(),
            )
        }
    }
}

/**
 * An emoji's cell: it lights up while pressed, as a key does, and a long press opens the popup of its tones over
 * it, the one the cell shows over the cell. [onType] types what the touch chose.
 */
@Composable
internal fun EmojiCellView(
    cell: EmojiCell,
    description: String,
    onType: (EmojiCell, Int?) -> Unit,
    modifier: Modifier,
) {
    val touch = remember { KeyTouch() }
    val view = LocalView.current
    val haptics = rememberKeyHaptics()
    // Where the cell is in the window, for the popup of its tones.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val popupPadding = with(LocalDensity.current) { PopupPadding.roundToPx() }
    val currentOnType by rememberUpdatedState(onType)
    val on = remember(view, haptics, popupPadding) {
        val geometry = { KeyGeometry(bounds, view.rootView.width, popupPadding) }
        EmojiCellTouch(TouchParts(touch, geometry, haptics)) { chosen, chosenTone ->
            currentOnType(chosen, chosenTone)
        }
    }
    val colors = MaterialTheme.colorScheme
    val pressed = if (touch.pressed) pressedColors(colors).background else Color.Transparent
    val markColor = hintColor(touch.pressed, colors)
    Box(
        modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(pressed)
            // Behind the emoji, which may reach into the corner.
            .drawBehind {
                if (cell.emoji.tones.isNotEmpty()) {
                    val (left, corner, top) = toneMark(size, ToneMarkSide.toPx(), ToneMarkInset.toPx())
                    val mark = Path().apply {
                        moveTo(left.x, left.y)
                        lineTo(corner.x, corner.y)
                        lineTo(top.x, top.y)
                        close()
                    }
                    drawPath(mark, markColor)
                }
            }
            .semantics { contentDescription = description }
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pointerInput(cell) { awaitEachGesture { handleEmojiGesture(cell, on) } },
        contentAlignment = Alignment.Center,
    ) {
        touch.popup?.let { OptionsPopup(cell.emoji.forms.map(OptionFace::Label), it, touch, on.parts.geometry()) }
        Text(cell.form, fontSize = EmojiSize, maxLines = 1)
    }
}

/** The mark of an emoji with skin tones: a small triangle in its cell's corner, as Gboard has. */
private val ToneMarkSide = 5.dp

/** Far enough in from the corner to stay clear of the cell's rounding. */
private val ToneMarkInset = 3.dp

/**
 * The corners of the mark of an emoji with skin tones, in a cell of [size]: a right triangle with legs of [side],
 * its right angle [inset] in from the bottom right corner. Bottom left, bottom right, top right.
 */
internal fun toneMark(size: Size, side: Float, inset: Float): List<Offset> {
    val right = size.width - inset
    val bottom = size.height - inset
    return listOf(Offset(right - side, bottom), Offset(right, bottom), Offset(right, bottom - side))
}

/** A tab's icon: Material Symbols' own for each group. */
@DrawableRes
internal fun tabIcon(tab: EmojiTab): Int = when (tab) {
    EmojiTab.Recent -> R.drawable.ic_emoji_recent
    is EmojiTab.Group -> when (tab.group) {
        EmojiGroup.Smileys -> R.drawable.ic_mood
        EmojiGroup.People -> R.drawable.ic_emoji_people
        EmojiGroup.Animals -> R.drawable.ic_emoji_nature
        EmojiGroup.Food -> R.drawable.ic_emoji_food_beverage
        EmojiGroup.Travel -> R.drawable.ic_emoji_transportation
        EmojiGroup.Activities -> R.drawable.ic_emoji_events
        EmojiGroup.Objects -> R.drawable.ic_emoji_objects
        EmojiGroup.Symbols -> R.drawable.ic_emoji_symbols
        EmojiGroup.Flags -> R.drawable.ic_emoji_flags
    }
}

/** A tab's name, which a screen reader says for its icon. */
@StringRes
internal fun tabName(tab: EmojiTab): Int = when (tab) {
    EmojiTab.Recent -> R.string.emoji_recent
    is EmojiTab.Group -> when (tab.group) {
        EmojiGroup.Smileys -> R.string.emoji_group_smileys
        EmojiGroup.People -> R.string.emoji_group_people
        EmojiGroup.Animals -> R.string.emoji_group_animals
        EmojiGroup.Food -> R.string.emoji_group_food
        EmojiGroup.Travel -> R.string.emoji_group_travel
        EmojiGroup.Activities -> R.string.emoji_group_activities
        EmojiGroup.Objects -> R.string.emoji_group_objects
        EmojiGroup.Symbols -> R.string.emoji_group_symbols
        EmojiGroup.Flags -> R.string.emoji_group_flags
    }
}
