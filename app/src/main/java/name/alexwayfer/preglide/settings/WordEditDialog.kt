package name.alexwayfer.preglide.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.EditPreview
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.editWord
import name.alexwayfer.preglide.engine.suggest.previewEdit

@Composable
internal fun WordEditDialog(
    word: String,
    words: PersonalWords,
    dictionary: Dictionary,
    everywhere: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String, String) -> Unit,
) {
    var to by rememberSaveable(word) { mutableStateOf(word) }
    var confirmation by remember(word) { mutableStateOf<EditPreview?>(null) }
    var stale by rememberSaveable(word) { mutableStateOf(false) }
    val preview = words.previewEdit(word, to, dictionary)
    val save = { edit: EditPreview ->
        val id = words.editWord(edit, dictionary, System.currentTimeMillis())
        confirmation = null
        if (id == null) stale = true else onSaved(id, edit.to)
    }
    val confirmed = confirmation
    val dismiss = { if (confirmed == null) onDismiss() else confirmation = null }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(if (confirmed == null) stringResource(R.string.learned_edit)
                else stringResource(R.string.learned_confirm, confirmed.source.spelling, confirmed.to))
        },
        text = {
            if (confirmed != null) MergeWarning(confirmed) else {
                Column {
                    DictionaryTextField(to, {
                        to = it
                        stale = false
                    }, stringResource(R.string.learned_spelling))
                    if (everywhere) Text(stringResource(R.string.learned_edit_everywhere))
                    if (stale) Text(stringResource(R.string.learned_edit_stale))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = confirmed != null || preview != null, onClick = {
                if (confirmed != null) save(confirmed)
                else preview?.let { if (it.target != null || it.builtIn) confirmation = it else save(it) }
            }) { Text(stringResource(R.string.learned_save)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun MergeWarning(edit: EditPreview) {
    Column {
        edit.target?.let { target ->
            val total = edit.source.count + target.count
            Text(stringResource(R.string.learned_merge, edit.source.count, target.count, total))
        }
        if (edit.builtIn) Text(stringResource(R.string.learned_merge_builtin))
    }
}
