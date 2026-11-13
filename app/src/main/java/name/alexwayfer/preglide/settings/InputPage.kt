package name.alexwayfer.preglide.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import name.alexwayfer.preglide.ime.GlideStrip
import name.alexwayfer.preglide.ime.SpaceGestures

/**
 * How the keyboard helps with typing: the rows to the suggestions' and the gestures' pages, which [onOpen] opens, each
 * with a hint of what is on there, and whether the keys vibrate and how strong.
 */
@Composable
fun InputPage(settings: KeyboardSettings, onOpen: (SettingsPage) -> Unit, onBack: () -> Unit) {
    InputListPage(stringResource(R.string.settings_input), onBack) {
        SuggestionsEntry(settings) { onOpen(SettingsPage.Suggestions) }
        GesturesEntry(settings) { onOpen(SettingsPage.Gestures) }
        DoubleSpacePeriodEntry(settings)
        VibrationEntries(settings.vibration)
    }
}

/** Sentence punctuation belongs to input itself, independently of suggestions and space-bar swipes. */
@Composable
private fun DoubleSpacePeriodEntry(settings: KeyboardSettings) {
    val enabled by settings.punctuation.doubleSpacePeriod.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SwitchEntry(
        R.drawable.ic_key_space,
        stringResource(R.string.settings_double_space_period),
        stringResource(R.string.settings_double_space_period_hint),
        checked = enabled,
        onChange = { scope.launch { settings.punctuation.setDoubleSpacePeriod(it) } },
    )
}

/**
 * What helps with the words: whether the strip shows above the keys, whether the neural network predicts the next
 * word, grayed out while neither the strip nor glides need it, whether autocorrect fixes typos, and whether fresh
 * screenshots are offered as if copied.
 */
@Composable
fun SuggestionsPage(settings: KeyboardSettings, onBack: () -> Unit) {
    val strip by settings.strip.collectAsStateWithLifecycle()
    val glides by settings.glide.typing.collectAsStateWithLifecycle()
    val usePrediction by settings.usePrediction.collectAsStateWithLifecycle()
    val autocorrect by settings.autocorrect.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    InputListPage(stringResource(R.string.settings_suggestions), onBack) {
        SwitchEntry(
            R.drawable.ic_toolbar,
            stringResource(R.string.settings_strip),
            stringResource(R.string.settings_strip_hint),
            checked = strip,
            onChange = { scope.launch { settings.setStrip(it) } },
        )
        SwitchEntry(
            R.drawable.ic_neurology,
            stringResource(R.string.settings_prediction),
            stringResource(R.string.settings_prediction_hint),
            checked = usePrediction,
            enabled = predictionMatters(strip, glides),
            onChange = { scope.launch { settings.setUsePrediction(it) } },
        )
        SwitchEntry(
            R.drawable.ic_spellcheck,
            stringResource(R.string.settings_autocorrect),
            stringResource(R.string.settings_autocorrect_hint),
            checked = autocorrect,
            onChange = { scope.launch { settings.setAutocorrect(it) } },
        )
        ScreenshotsEntry(rememberScreenshotSwitch(settings))
        LearningEntry(settings.learning)
    }
}

/**
 * What a finger's way does: whether a glide types a word, what a backspace after it does, and what a swipe along the
 * space bar and its long press do.
 */
@Composable
fun GesturesPage(settings: KeyboardSettings, onBack: () -> Unit) {
    val strip by settings.strip.collectAsStateWithLifecycle()
    InputListPage(stringResource(R.string.settings_gestures), onBack) {
        GlideEntries(settings.glide, strip)
        SpaceGesturesChoice(settings)
    }
}

/** The space bar's gestures under its title, one row for each choice, the current one selected. */
@Composable
private fun SpaceGesturesChoice(settings: KeyboardSettings) {
    val chosen by settings.spaceGestures.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.selectableGroup()) {
        Text(
            stringResource(R.string.settings_space),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        for (gestures in SpaceGestures.entries) {
            val (title, hint) = spaceGesturesText(gestures)
            RadioEntry(stringResource(title), stringResource(hint), selected = gestures == chosen) {
                scope.launch { settings.setSpaceGestures(gestures) }
            }
        }
    }
}

/** A page of the input's list rows under its [title]: the rows reach the screen edges, the header keeps off them. */
@Composable
private fun InputListPage(title: String, onBack: () -> Unit, rows: @Composable ColumnScope.() -> Unit) {
    TryScaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                PageHeader(title, onBack)
            }
            Column(content = rows)
        }
    }
}

/** The row to the suggestions' page, with what is on there. */
@Composable
private fun SuggestionsEntry(settings: KeyboardSettings, onOpen: () -> Unit) {
    val strip by settings.strip.collectAsStateWithLifecycle()
    val usePrediction by settings.usePrediction.collectAsStateWithLifecycle()
    val autocorrect by settings.autocorrect.collectAsStateWithLifecycle()
    val screenshots = rememberScreenshotSwitch(settings)
    PageEntry(
        R.drawable.ic_lightbulb,
        stringResource(R.string.settings_suggestions),
        switchesHint(suggestionsOn(strip, usePrediction, autocorrect, screenshots.on)),
        onOpen,
    )
}

/** The row to the gestures' page, with what is on there. */
@Composable
private fun GesturesEntry(settings: KeyboardSettings, onOpen: () -> Unit) {
    val glide by settings.glide.typing.collectAsStateWithLifecycle()
    val spaceGestures by settings.spaceGestures.collectAsStateWithLifecycle()
    PageEntry(
        R.drawable.ic_gesture,
        stringResource(R.string.settings_gestures),
        switchesHint(gesturesOn(glide, spaceGestures)),
        onOpen,
    )
}

/** The hint of the switches [on], by their names. */
@Composable
private fun switchesHint(@StringRes on: List<Int>): String =
    switchesHint(on.map { stringResource(it) }, stringResource(R.string.input_hint_all_off))

/**
 * Whether a glide types a word, and, only then, whether a backspace right after it erases the word whole, one
 * through the space or mark after it brings its words back, and what the strip offers right after it; the last two
 * grayed out without the [strip], which would show those words.
 */
@Composable
private fun ColumnScope.GlideEntries(settings: GlideSettings, strip: Boolean) {
    val typing by settings.typing.collectAsStateWithLifecycle()
    val erasesWord by settings.erasesWord.collectAsStateWithLifecycle()
    val bringsBackWords by settings.bringsBackWords.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SwitchEntry(
        R.drawable.ic_swipe,
        stringResource(R.string.settings_glide_typing),
        stringResource(R.string.settings_glide_typing_hint),
        checked = typing,
        onChange = { scope.launch { settings.setTyping(it) } },
    )
    // Only glides have a word to erase whole, words to bring back, or a strip right after them.
    AnimatedVisibility(typing) {
        Column {
            SwitchEntry(
                R.drawable.ic_key_backspace,
                stringResource(R.string.settings_glide_erases_word),
                stringResource(R.string.settings_glide_erases_word_hint),
                checked = erasesWord,
                onChange = { scope.launch { settings.setErasesWord(it) } },
            )
            SwitchEntry(
                R.drawable.ic_undo,
                stringResource(R.string.settings_glide_brings_back_words),
                stringResource(R.string.settings_glide_brings_back_words_hint),
                checked = bringsBackWords,
                enabled = strip,
                onChange = { scope.launch { settings.setBringsBackWords(it) } },
            )
            GlideStripChoice(settings, strip)
        }
    }
}

/**
 * What the strip offers right after a glide, under its title, one row for each choice, the current one selected;
 * grayed out while the strip is hidden, as [enabled] tells.
 */
@Composable
private fun GlideStripChoice(settings: GlideSettings, enabled: Boolean) {
    val chosen by settings.strip.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.selectableGroup()) {
        Text(
            stringResource(R.string.settings_glide_strip),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        for (strip in GlideStrip.entries) {
            val (title, hint) = glideStripText(strip)
            RadioEntry(stringResource(title), stringResource(hint), selected = strip == chosen, enabled) {
                scope.launch { settings.setStrip(strip) }
            }
        }
    }
}
