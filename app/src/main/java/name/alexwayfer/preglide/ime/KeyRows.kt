package name.alexwayfer.preglide.ime

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import name.alexwayfer.preglide.engine.layout.Key
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Page

/**
 * The rows of [layout]'s keys (see [KeyRows]), each key with the glide that starts on it on its page ([glideOf]).
 * A keypad and the letters fade into each other, as a field brings either; the letters and the symbols switch at
 * once, as a key switches them while typing. The letters of a new language follow the swipe that brought it, and fade
 * in when it came otherwise (see [keysSlide]); the coming keys lie over the leaving ones and take the touches. The
 * symbols' key offers the [menu], if any (see [offersMenu]).
 */
@Composable
internal fun ShownKeys(
    layout: KeyboardLayout,
    state: KeyboardState,
    look: KeyLook,
    onKey: (KeyAction, Boolean, KeyTap?) -> Unit,
    backspace: Backspace,
    space: SpaceBar?,
    menu: KeyMenu? = null,
    glideOf: (Key, Page) -> GlideStart?,
) {
    AnimatedContent(
        targetState = layout,
        modifier = Modifier.clipToBounds(),
        transitionSpec = { keysTransform(keysSlide(initialState, targetState, state.languageSwiped)) },
        contentKey = ::keysKey,
        label = "page",
    ) { shown ->
        KeyRows(shown, state, look, onKey, backspace, space, menu) { key -> glideOf(key, shown.page) }
    }
}

/**
 * The rows of [layout]'s keys, under the digit row while [look] shows it (see [shownRows]), each key with the glide
 * that starts on it ([glideOf]), if any, the space bar with its gestures, the [space] bar's, if any, and the
 * symbols' key with the [menu], if any.
 */
@Composable
internal fun KeyRows(
    layout: KeyboardLayout,
    state: KeyboardState,
    look: KeyLook,
    onKey: (KeyAction, Boolean, KeyTap?) -> Unit,
    backspace: Backspace,
    space: SpaceBar?,
    menu: KeyMenu? = null,
    glideOf: (Key) -> GlideStart?,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    // A column of their own: a parent that stacks its children, as AnimatedContent does, would lay them over another.
    Column {
        for ((row, height, digits) in shownRows(layout, look)) {
            Row(Modifier.fillMaxWidth().height(height).then(if (digits) Modifier.clipToBounds() else Modifier)) {
                row.forEachIndexed { index, shown ->
                    // By its action, so the keys keep their state as the emoji key comes in among them.
                    key(shown.action) {
                        val modifier = Modifier
                            .weight(shown.width)
                            .fillMaxHeight()
                            .keyDivider(dividedFromNext(row, index), look.dividers, dividerColor)
                        val keyMenu = menu?.takeIf { offersMenu(shown.action) }
                        KeyView(shown, state, look, onKey, backspace, glideOf(shown), modifier, space, layout, keyMenu)
                    }
                }
            }
        }
    }
}
