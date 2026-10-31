package name.alexwayfer.preglide.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.Learned
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.beyondDictionary
import name.alexwayfer.preglide.ime.LanguageModels
import name.alexwayfer.preglide.ime.PersonalData
import name.alexwayfer.preglide.ime.personalName

/**
 * The words beyond the dictionary the keyboard learned from the user's typing, a list per language, where the user
 * can forget one, and, once words came from other devices, pick whose to show, named as [deviceNames] gives. The
 * [models] give the dictionaries; the lists and the names load on [io].
 */
@Composable
fun LearnedWordsPage(
    personal: PersonalData,
    models: LanguageModels,
    deviceNames: () -> Map<String, String>,
    io: CoroutineDispatcher,
    onBack: () -> Unit,
) {
    var language by rememberSaveable { mutableStateOf(Language.entries.first()) }
    var order by rememberSaveable { mutableStateOf(WordOrder.Frequency) }
    var forgetting by rememberSaveable { mutableStateOf<String?>(null) }
    // Null while they load: off the main thread, since the keyboard may not have read them yet.
    val learned by produceState<Map<Language, PersonalWords>?>(null) {
        value = withContext(io) { Language.entries.associateWith { personal.load(personalName(it)) } }
    }
    // The keyboard learns while this page is open too, as the user types in another app's window over it.
    val changes by personal.changes.collectAsStateWithLifecycle()
    val devices = deviceChoice(learned, changes, deviceNames, io)
    val snackbar = remember { SnackbarHostState() }
    val offerUndo = rememberUndoOffer(snackbar)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .pagePaddingAbove(padding, LocalLayoutDirection.current)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The word rows reach the screen edges, as list rows do; the controls keep off them.
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader(stringResource(R.string.settings_learned_words), onBack)
                Choices(Language.entries, language, { languageName(it) }) { language = it }
                Choices(WordOrder.entries, order, { stringResource(it.label) }) { order = it }
                DeviceMenu(devices)
            }
            Crossfade(language to devices.selected, label = "words") { (shown, filter) ->
                // Nothing while loading: it takes a moment, too short for a spinner.
                val words = learned?.get(shown) ?: return@Crossfade
                val list = ownWords(shown, words, filter, models, changes, order) ?: return@Crossfade
                Crossfade(list.isEmpty(), label = "empty") { empty ->
                    if (empty) EmptyWords() else WordList(list, padding.calculateBottomPadding()) { forgetting = it }
                }
            }
        }
    }

    forgetting?.let { word ->
        ForgetDialog(
            word,
            everywhere = devices.filters.isNotEmpty(),
            onForget = {
                learned?.get(language)?.let { words ->
                    val name = personalName(language)
                    val saved = {
                        personal.changed(name)
                        personal.save()
                    }
                    val forgotten = words.forget(word, System.currentTimeMillis())
                    saved()
                    // Back into the language the word was forgotten in, whichever one shows by then.
                    offerUndo(word) {
                        forgotten.restore()
                        saved()
                    }
                }
                forgetting = null
            },
            onDismiss = { forgetting = null },
        )
    }
}

/**
 * The words of [language] in [words] that [filter] shows, beyond its dictionary, in [order], anew with each of the
 * [changes]; null while the dictionary loads.
 */
@Composable
private fun ownWords(
    language: Language,
    words: PersonalWords,
    filter: DeviceFilter,
    models: LanguageModels,
    changes: Int,
    order: WordOrder,
): List<Learned>? {
    val all = remember(words, filter, changes) { words.shownBy(filter) }
    // With nothing learned there is nothing to load the dictionary for, as for a language the user does not type.
    val dictionary = produceState<Dictionary?>(null, language, all.isEmpty()) {
        if (all.isNotEmpty()) value = models.await(language).dictionary
    }.value
    return remember(all, dictionary, order) {
        if (all.isEmpty()) emptyList() else dictionary?.let { inOrder(beyondDictionary(all, it), order) }
    }
}

/** A row of [options], one of them [selected]: picking another tells [onSelect]. */
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label(option))
            }
        }
    }
}

/**
 * The [words], each with a button to forget it; [bottom] is room below the last one, to scroll it out from under
 * the navigation bar.
 */
@Composable
private fun WordList(words: List<Learned>, bottom: Dp, onForget: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        items(words, key = ::listKey) { word ->
            ListItem(
                headlineContent = { Text(word.spelling) },
                supportingContent = {
                    Text(pluralStringResource(R.plurals.learned_typed_times, word.count, word.count))
                },
                trailingContent = {
                    IconButton(onClick = { onForget(word.spelling) }) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.learned_forget))
                    }
                },
                // A forgotten word leaves, and the rest close up and reorder, in motion.
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun EmptyWords() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.learned_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.learned_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shows in [snackbar] that a word was forgotten, with a button that calls the given undo; a later word's offer
 * replaces it, so only the latest word can come back.
 */
@Composable
private fun rememberUndoOffer(snackbar: SnackbarHostState): (String, () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val undo = stringResource(R.string.learned_forgotten_undo)
    return { word, onUndo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val message = resources.getString(R.string.learned_forgotten, word)
            val result = snackbar.showSnackbar(message, undo, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
}

/** Asks before [word] goes; once other devices sync, it goes from them too, [everywhere], and the dialog says so. */
@Composable
private fun ForgetDialog(word: String, everywhere: Boolean, onForget: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.learned_forget_question, word)) },
        text = {
            Text(stringResource(if (everywhere) R.string.learned_forget_everywhere else R.string.learned_forget_text))
        },
        confirmButton = {
            TextButton(onClick = onForget) { Text(stringResource(R.string.learned_forget)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.learned_forget_cancel)) }
        },
    )
}
