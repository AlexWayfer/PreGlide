package name.alexwayfer.preglide.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.R

/**
 * How tall the keys are, and what they show and offer: a row of digits, or the top row's digits, the symbols on the
 * letters, and an emoji key.
 */
@Composable
fun LayoutPage(settings: KeyboardSettings, onBack: () -> Unit) {
    val layout = settings.layout
    val digitRow by layout.digitRow.collectAsStateWithLifecycle()
    val topRowDigits by layout.topRowDigits.collectAsStateWithLifecycle()
    val emojiKey by layout.emojiKey.collectAsStateWithLifecycle()
    val letterSymbols by layout.letterSymbols.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    TryScaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The switch's row reaches the screen edges, as list rows do; the header and the slider keep off them.
            Column(Modifier.padding(horizontal = 16.dp)) {
                PageHeader(stringResource(R.string.settings_layout), onBack)
            }
            KeyHeightSection(settings)
            SwitchEntry(
                icon = null,
                stringResource(R.string.layout_digit_row),
                stringResource(R.string.layout_digit_row_hint),
                checked = digitRow,
                onChange = { scope.launch { layout.setDigitRow(it) } },
            )
            // The digit row holds the digits the top row's corners would: the corners have nothing to show then.
            AnimatedVisibility(
                !digitRow,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SwitchEntry(
                    icon = null,
                    stringResource(R.string.layout_top_row_digits),
                    stringResource(R.string.layout_top_row_digits_hint),
                    checked = topRowDigits,
                    onChange = { scope.launch { layout.setTopRowDigits(it) } },
                )
            }
            SwitchEntry(
                icon = null,
                stringResource(R.string.layout_letter_symbols),
                stringResource(R.string.layout_letter_symbols_hint),
                checked = letterSymbols,
                onChange = { scope.launch { layout.setLetterSymbols(it) } },
            )
            SwitchEntry(
                icon = null,
                stringResource(R.string.layout_emoji_key),
                stringResource(R.string.layout_emoji_key_hint),
                checked = emojiKey,
                onChange = { scope.launch { layout.setEmojiKey(it) } },
            )
        }
    }
}
