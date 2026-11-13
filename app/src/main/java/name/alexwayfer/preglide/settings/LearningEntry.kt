package name.alexwayfer.preglide.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.suggest.CANDIDATE_DAYS
import name.alexwayfer.preglide.engine.suggest.LEARNING_USES
import name.alexwayfer.preglide.engine.suggest.LearningPolicy

@Composable
internal fun LearningEntry(settings: LearningSettings) {
    val policy by settings.policy.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PageEntry(R.drawable.ic_dictionary, stringResource(R.string.learning_title),
        stringResource(R.string.learning_summary, policy.uses, policy.days), onOpen = { open = true })
    if (open) LearningDialog(policy, onDismiss = { open = false }) {
        scope.launch { settings.setPolicy(it) }
        open = false
    }
}

@Composable
private fun LearningDialog(policy: LearningPolicy, onDismiss: () -> Unit, onSave: (LearningPolicy) -> Unit) {
    var uses by rememberSaveable { mutableStateOf(policy.uses.toString()) }
    var days by rememberSaveable { mutableStateOf(policy.days.toString()) }
    val selected = learningPolicy(uses, days)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.learning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LearningNumber(uses, { uses = it }, stringResource(R.string.learning_uses), LEARNING_USES)
                LearningNumber(days, { days = it }, stringResource(R.string.learning_days), CANDIDATE_DAYS)
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onSave) }, enabled = selected != null) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun LearningNumber(value: String, change: (String) -> Unit, label: String, range: IntRange) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        supportingText = { Text("${range.first}–${range.last}") },
        isError = value.toIntOrNull()?.let { it !in range } ?: value.isNotEmpty(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
