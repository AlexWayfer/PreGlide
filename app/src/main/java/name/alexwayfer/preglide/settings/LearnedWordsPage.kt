package name.alexwayfer.preglide.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import name.alexwayfer.preglide.engine.suggest.undoWordEdit
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
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // Null while they load: off the main thread, since the keyboard may not have read them yet.
    val learned by produceState<Map<Language, PersonalWords>?>(null) {
        value = withContext(io) { Language.entries.associateWith { personal.load(personalName(it)) } }
    }
    // The keyboard learns while this page is open too, as the user types in another app's window over it.
    val changes by personal.changes.collectAsStateWithLifecycle()
    val devices = deviceChoice(learned, changes, deviceNames, io)
    val snackbar = remember { SnackbarHostState() }
    val offerUndo = rememberUndoOffer(snackbar)
    val words = learned?.get(language)
    val (dictionary, list) = key(language) {
        wordListing(language, words, devices.selected, changes, models, order)
    }
    val resources = LocalResources.current
    val name = personalName(language)
    val save = {
        personal.changed(name)
        personal.save()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LearnedList(padding, list, query, { query = it }, { editing = it }, { forgetting = it }) {
            PageHeader(stringResource(R.string.settings_learned_words), onBack)
            Choices(Language.entries, language, { languageName(it) }) { language = it }
            Choices(WordOrder.entries, order, { stringResource(it.label) }) { order = it }
            DeviceMenu(devices)
        }
    }

    forgetting?.let { word ->
        ForgetDialog(
            word,
            everywhere = devices.filters.isNotEmpty(),
            onForget = {
                val forgotten = words?.forget(word, System.currentTimeMillis())
                save()
                offerUndo(resources.getString(R.string.learned_forgotten, word)) {
                    forgotten?.restore()
                    save()
                }
                forgetting = null
            },
            onDismiss = { forgetting = null },
        )
    }
    editing?.let { from ->
        if (words != null && dictionary != null) WordEditDialog(from, words, dictionary, devices.filters.isNotEmpty(),
            onDismiss = { editing = null }) { id, to ->
            save()
            editing = null
            offerUndo(resources.getString(R.string.learned_edited, from, to)) {
                if (words.undoWordEdit(id)) save()
            }
        }
    }
}

@Composable
private fun WordSearch(query: String, onChange: (String) -> Unit) {
    DictionaryTextField(query, onChange, stringResource(R.string.learned_search)) {
        Icon(painterResource(R.drawable.ic_search), null)
    }
}

@Composable
private fun wordListing(
    language: Language,
    words: PersonalWords?,
    filter: DeviceFilter,
    changes: Int,
    models: LanguageModels,
    order: WordOrder,
): Pair<Dictionary?, List<Learned>?> {
    val all = remember(words, filter, changes) { words?.shownBy(filter) }
    val dictionary = produceState<Dictionary?>(null, all.isNullOrEmpty()) {
        if (!all.isNullOrEmpty()) value = models.await(language).dictionary
    }.value
    val sorted = remember(all, dictionary, order) {
        when {
            all == null -> null
            all.isEmpty() -> emptyList()
            else -> dictionary?.let { inOrder(beyondDictionary(all, it), order) }
        }
    }
    return dictionary to sorted
}

@Composable
private fun LearnedList(
    padding: PaddingValues,
    words: List<Learned>?,
    query: String,
    onQueryChange: (String) -> Unit,
    onEdit: (String) -> Unit,
    onForget: (String) -> Unit,
    controls: @Composable () -> Unit,
) {
    val now = wordListClock()
    val focus = LocalFocusManager.current
    val state = rememberLazyListState()
    val matches = remember(words, query) { words?.let { matchingWords(it, query) } }
    var searchHeight by remember { mutableIntStateOf(0) }
    val searchSpace = with(LocalDensity.current) { searchHeight.toDp() }
    val area = Modifier.fillMaxSize().pagePaddingAbove(padding, LocalLayoutDirection.current)
        .padding(top = 16.dp).clipToBounds()
    Box(area) {
        LazyColumn(
            Modifier.fillMaxSize(), state = state,
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        ) {
            item(key = "controls") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    controls()
                }
            }
            item(key = "search-space") { Spacer(Modifier.height(searchSpace)) }
            if (matches?.isEmpty() == true) item { EmptyWords(query.isNotBlank()) }
            items(matches.orEmpty(), key = { "word-${listKey(it)}" }) { word ->
                LearnedWordRow(word, now, {
                    focus.clearFocus()
                    onEdit(word.spelling)
                }, { onForget(word.spelling) }, Modifier.animateItem())
            }
        }
        PinnedWordSearch(state, query, Modifier.onSizeChanged { searchHeight = it.height }) { next ->
            state.keepWordOnScreen(matches.orEmpty(), matchingWords(words.orEmpty(), next))
            onQueryChange(next)
        }
    }
}

/** The field follows the controls up, then stays above the list. Editing its cursor cannot scroll the word rows. */
@Composable
private fun PinnedWordSearch(state: LazyListState, query: String, modifier: Modifier, onChange: (String) -> Unit) {
    Column(
        modifier.fillMaxWidth().offset {
            val controls = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            IntOffset(0, controls?.let { (it.offset + it.size).coerceAtLeast(0) } ?: 0)
        }.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        WordSearch(query, onChange)
    }
}
/** The controls and search precede the word rows. Request the new index for the same layout pass as the filter. */
private const val FIRST_WORD_INDEX = 2

private fun LazyListState.keepWordOnScreen(previous: List<Learned>, next: List<Learned>) {
    val word = previous.getOrNull(firstVisibleItemIndex - FIRST_WORD_INDEX) ?: return
    val index = next.indexOfFirst { it.spelling == word.spelling }
    if (index < 0) requestScrollToItem(FIRST_WORD_INDEX - 1)
    else requestScrollToItem(FIRST_WORD_INDEX + index, firstVisibleItemScrollOffset)
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

@Composable
private fun EmptyWords(searching: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(if (searching) R.string.learned_no_matches else R.string.learned_empty_title),
            style = MaterialTheme.typography.titleMedium)
        if (!searching) Text(
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
    val undo = stringResource(R.string.learned_forgotten_undo)
    return { message, onUndo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
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
