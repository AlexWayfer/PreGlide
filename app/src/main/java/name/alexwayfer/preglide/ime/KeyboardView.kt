package name.alexwayfer.preglide.ime

import android.view.WindowInsetsController
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.layout.Key
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import name.alexwayfer.preglide.ui.theme.isPreGlideDark
import java.util.Locale

/**
 * [look] is how the keys look by the user's settings; [onKey] gets the pressed action, whether it was a long press,
 * and for a tap, where it landed on the key; [backspace] is what backspace does in the field besides its key;
 * [onSuggestion] gets the suggestion tapped; [onRemove], the suggestion the user removed; [clipboard], the strip's
 * clipboard button and chip, and the clipboard's panel, which shows in place of the keys; [emoji], the emoji panel,
 * which shows there too, and its search, which the keys type into; [onSettings], the strip's button to the
 * keyboard's settings. Without the strip, as [look] tells, a panel shows its row of the strip in its own room, and
 * the symbols' key offers the clipboard and the settings by its long press (see [keysMenu]); and [glides], a finger's
 * glide over the letters as it goes and once it lifts, drawn as a fading trail. Without [glides], a finger leaving
 * a key glides nowhere, as it does while the emoji search shows. With [space], the space bar does the gestures the
 * user chose, but moves no cursor of the field while the emoji search shows.
 */
@Composable
fun KeyboardView(
    state: KeyboardState,
    look: KeyLook,
    onKey: (KeyAction, Boolean, KeyTap?) -> Unit,
    backspace: Backspace,
    onSuggestion: (String) -> Unit,
    onRemove: (String) -> Unit,
    clipboard: ClipboardActions,
    emoji: EmojiActions,
    onSettings: () -> Unit,
    glides: GlideListener? = null,
    space: SpaceBar? = null,
) {
    val shownLook = flowing(look)
    val layout = KeyboardLayout.of(state.language, state.page)
    // Where the keys are in the window, so the glide's points can be read on them.
    var keysBounds by remember { mutableStateOf(Rect.Zero) }
    val trail = remember { GlideTrail() }
    val searching = state.emoji.searching
    val currentGlides by rememberUpdatedState(glides.takeUnless { searching })
    // While the emoji search shows, backspace erases its words, not the field's.
    val keysBackspace = remember(searching, backspace) { if (searching) QueryBackspace(state.emoji) else backspace }
    // And the space bar types into it, rather than moving the field's cursor.
    val keysSpace = if (searching) space?.copy(cursor = null) else space
    val erase = remember(onKey, backspace) { StripErase({ onKey(KeyAction.Backspace, false, null) }, backspace) }
    val stripRow: @Composable () -> Unit = {
        SuggestionStrip(state, shownLook.stripHeight, onSuggestion, onRemove, clipboard, emoji, erase, onSettings)
    }
    // Without the strip, the panels show its row of their own, and the symbols' key offers its buttons.
    val ownRows = !look.strip
    val menu = remember(look.strip, searching, clipboard, onSettings) {
        keysMenu(look.strip, searching, clipboard::open, onSettings)
    }
    // The glide's handlers are remembered once: they read the look as it is when the finger goes.
    val currentLook by rememberUpdatedState(shownLook)
    val glideOn = { path: List<Offset> -> lettersGlide(path, keysBounds, currentLook, state.language) }
    val glideStart = remember(trail) { GlideStart(trail) { path -> currentGlides?.glided(glideOn(path)) } }
    TrailFrames(trail)
    TrailPreviews(trail) { path -> currentGlides?.preview(glideOn(path)) }
    NavigationButtonsFollow(isPreGlideDark())
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // System bars, not just the navigation bar: with gesture navigation Android draws its own back and
            // keyboard switcher buttons inside the keyboard window and reports their strip as the caption bar.
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        AnimatedVisibility(look.strip) { stripRow() }
        AnimatedContent(
            targetState = shownPanel(state),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "keys",
        ) { panel ->
            // A panel is as tall as the keys, so the keyboard does not jump, its own row of the strip included.
            val height = panelHeight(keysHeight(shownLook, layout.rows.size), shownLook.stripHeight, ownRows)
            val panelSize = Modifier.fillMaxWidth().height(height)
            when (panel) {
                KeysPanel.Clipboard -> UnderStripRow(ownRows, stripRow) { ClipboardPanel(state, clipboard, panelSize) }
                KeysPanel.Emoji -> UnderStripRow(ownRows, stripRow) { EmojiPanel(state.emoji, emoji, panelSize) }
                // The emoji search's row, over the keys it types on.
                KeysPanel.Keys -> UnderStripRow(ownRows && searching, stripRow, animated = true) {
                    TrailedKeys(trail, { keysBounds }, { keysBounds = it }) {
                        ShownKeys(layout, state, shownLook, onKey, keysBackspace, keysSpace, menu) { key, page ->
                            glideStart.takeIf { currentGlides != null && glidesFrom(key.action, page) }
                        }
                    }
                }
            }
        }
    }
}


@Composable
internal fun KeyView(
    key: Key,
    state: KeyboardState,
    look: KeyLook,
    onKey: (KeyAction, Boolean, KeyTap?) -> Unit,
    backspace: Backspace,
    glide: GlideStart?,
    modifier: Modifier,
    space: SpaceBar? = null,
    layout: KeyboardLayout = KeyboardLayout.of(state.language, state.page),
    menu: KeyMenu? = null,
) {
    val action = key.action
    val page = layout.page
    if (action == KeyAction.Gap) {
        Box(modifier)
        return
    }
    val offered = offeredAction(action, look.hints, look.symbols)
    val currentOnKey by rememberUpdatedState(onKey)
    val currentBackspace by rememberUpdatedState(backspace)
    val currentSpace by rememberUpdatedState(space)
    val currentMenu by rememberUpdatedState(menu)
    val touch = remember { KeyTouch() }
    val view = LocalView.current
    val haptics = rememberKeyHaptics()
    // Where the key is in the window, for the long press popup.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val popupPadding = with(LocalDensity.current) { PopupPadding.roundToPx() }
    val geometry = { KeyGeometry(bounds, view.rootView.width, popupPadding) }
    val description = descriptionRes(action)?.let { stringResource(it) }
    // The key shows inset in its cell, but the whole cell takes the finger: a touch between two keys goes to one of
    // them, not to none.
    Box(
        modifier.pointerInput(offered, glide) {
            awaitEachGesture {
                // Read as the finger comes down: the block waits for it from the end of the touch before.
                val special = SpecialKeys({ currentBackspace }, { currentSpace }) { currentMenu }
                handleKeyGesture(offered, TouchParts(touch, geometry, haptics), currentOnKey, special, glide)
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .keySurface(key, touch.pressed, look, MaterialTheme.colorScheme)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                .onGloballyPositioned { bounds = it.boundsInWindow() },
            contentAlignment = Alignment.Center,
        ) {
            touch.popup?.let { OptionsPopup(optionFaces(offered, state, menu), it, touch, geometry()) }
            if (action == KeyAction.Space) {
                SpaceFace(state, layout, touch, showsLanguageArrows(space?.gestures, state.otherLanguages, page.keypad))
                SpaceHint(space, touch.pressed, look, Modifier.align(Alignment.TopEnd))
            } else {
                KeyFace(action, state, page, touch.pressed)
            }
            KeyHint(action, page, touch.pressed, look, Modifier.align(hintAlignment(action, page)), menu != null)
        }
    }
}

/**
 * [look] with the keys' backgrounds, dividers, corners and height, the digit row, the emoji key, and the strip's
 * height, flowing from the ones they had, together, rather than jumping when the user changes them or turns the
 * screen while the keyboard shows.
 */
@Composable
private fun flowing(look: KeyLook): KeyLook {
    val backgrounds by animateFloatAsState(look.backgrounds)
    val dividers by animateFloatAsState(look.dividers)
    val cornerRadius by animateDpAsState(look.cornerRadius)
    val height by animateDpAsState(look.height)
    val stripHeight by animateDpAsState(look.stripHeight)
    val digitRow by animateFloatAsState(look.digitRow)
    val emojiKey by animateFloatAsState(look.emojiKey)
    return look.copy(
        backgrounds = backgrounds,
        dividers = dividers,
        cornerRadius = cornerRadius,
        height = height,
        stripHeight = stripHeight,
        digitRow = digitRow,
        emojiKey = emojiKey,
    )
}

/**
 * Android tints the back and keyboard switcher buttons it draws in the keyboard's window by that window's own look
 * (NavigationBarController): dark buttons on a light keyboard, which may be light while the system is dark.
 */
@Composable
private fun NavigationButtonsFollow(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val light = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        view.windowInsetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
    }
}

/** The key's icon, or its label when it has no icon. */
@Composable
private fun KeyFace(action: KeyAction, state: KeyboardState, page: Page, pressed: Boolean) {
    val colors = MaterialTheme.colorScheme
    val content = if (pressed) pressedColors(colors).content else colors.onSurface
    val icon = icon(action, state, page)
    if (icon != null) {
        // The key's semantics already describe it.
        Icon(painterResource(icon), contentDescription = null, tint = content)
    } else {
        Text(text = label(action, state), color = content, fontSize = labelSize(action), maxLines = 1)
    }
}

/** A key that [Key.standsOut] is brighter than the function keys, and a pressed key lights up. */
internal fun keyBackground(key: Key, pressed: Boolean, colors: ColorScheme): Color = when {
    pressed -> pressedColors(colors).background
    key.standsOut -> colors.surfaceBright
    else -> colors.surfaceContainerHighest
}

/** Letters are the largest; the language name on the space bar is small, so it does not compete with them. */
internal fun labelSize(action: KeyAction): TextUnit = when (action) {
    is KeyAction.Letter -> 22.sp
    KeyAction.Space -> 13.sp
    else -> 16.sp
}

/** What a screen reader says for a key whose label does not say it, such as an icon. */
internal fun descriptionRes(action: KeyAction): Int? = when (action) {
    KeyAction.Shift -> R.string.key_shift
    KeyAction.Backspace -> R.string.key_backspace
    KeyAction.Enter -> R.string.key_enter
    KeyAction.Space -> R.string.key_space
    KeyAction.Emoji -> R.string.key_emoji
    is KeyAction.ShowPage -> when (action.page) {
        Page.Letters -> R.string.key_letters
        Page.Symbols -> R.string.key_symbols
        Page.MoreSymbols -> R.string.key_more_symbols
        // No key leads to a keypad: a field opens it.
        Page.Digits, Page.Phone -> null
    }
    is KeyAction.Letter, KeyAction.Gap -> null
}

/**
 * Shift shows its state: an outline for lowercase, filled for one capital, filled and underlined for caps lock. The
 * space bar of a keypad is a key as narrow as a digit's, too narrow for the language's name, and shows its sign.
 * [page] is the one the key is on, which may be fading out, rather than the keyboard's page.
 */
@DrawableRes
internal fun icon(action: KeyAction, state: KeyboardState, page: Page = state.page): Int? = when (action) {
    KeyAction.Space -> R.drawable.ic_key_space.takeIf { page.keypad }
    KeyAction.Shift -> when (state.shift) {
        ShiftState.Off -> R.drawable.ic_key_shift
        ShiftState.Once -> R.drawable.ic_key_shift_once
        ShiftState.Locked -> R.drawable.ic_key_shift_locked
    }
    KeyAction.Backspace -> R.drawable.ic_key_backspace
    KeyAction.Enter -> R.drawable.ic_key_enter
    // The smile the comma shows small while there is no emoji key.
    KeyAction.Emoji -> R.drawable.ic_mood
    else -> null
}

internal fun label(action: KeyAction, state: KeyboardState): String = when (action) {
    is KeyAction.Letter -> state.typed(action.char).toString()
    // Drawn as icons.
    KeyAction.Shift, KeyAction.Backspace, KeyAction.Enter, KeyAction.Emoji -> ""
    KeyAction.Space -> spaceLabel(state.language)
    is KeyAction.ShowPage -> when (action.page) {
        Page.Letters -> when (state.language) {
            Language.Russian -> "АБВ"
            Language.English -> "ABC"
        }
        Page.Symbols -> "?123"
        Page.MoreSymbols -> "=\\<"
        Page.Digits, Page.Phone -> "123"
    }
    KeyAction.Gap -> ""
}

/** The space bar's label: the name of the [language] it types, in that language. */
internal fun spaceLabel(language: Language): String =
    Locale.forLanguageTag(language.tag).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.titlecase() }
