package name.alexwayfer.preglide.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.deviceIds

/** The [filters] of the devices whose words the page can show, none without other devices, and the [selected] one. */
internal class DeviceChoice(
    val filters: List<DeviceFilter>,
    val selected: DeviceFilter,
    val select: (DeviceFilter) -> Unit,
)

/**
 * Whose words the page shows: every device's at first. The devices are those of the words [learned] in any language,
 * anew with each of the [changes], as after a sync; their names come from [deviceNames] on [io], anew with them too.
 */
@Composable
internal fun deviceChoice(
    learned: Map<Language, PersonalWords>?,
    changes: Int,
    deviceNames: () -> Map<String, String>,
    io: CoroutineDispatcher,
): DeviceChoice {
    var key by rememberSaveable { mutableStateOf(DeviceFilter.All.key) }
    val ids = remember(learned, changes) { learned?.values?.flatMapTo(HashSet()) { it.deviceIds() }.orEmpty() }
    // Read anew with every change, not only as devices come or go: a sync may bring a device's name after its words.
    val names by produceState(emptyMap(), changes) { value = withContext(io) { deviceNames() } }
    val filters = remember(ids, names) { deviceFilters(ids, names) }
    return DeviceChoice(filters, filters.byKey(key)) { key = it.key }
}

/** The menu of whose words to show, there only once words came from other devices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceMenu(choice: DeviceChoice) {
    var expanded by remember { mutableStateOf(false) }
    AnimatedVisibility(choice.filters.isNotEmpty()) {
        ExposedDropdownMenuBox(expanded, { expanded = it }) {
            OutlinedTextField(
                deviceLabel(choice.selected),
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                readOnly = true,
                label = { Text(stringResource(R.string.learned_device)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                singleLine = true,
            )
            ExposedDropdownMenu(expanded, { expanded = false }) {
                for (filter in choice.filters) {
                    DropdownMenuItem(
                        text = { Text(deviceLabel(filter)) },
                        onClick = {
                            choice.select(filter)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun deviceLabel(filter: DeviceFilter): String = when (filter) {
    DeviceFilter.All -> stringResource(R.string.learned_all_devices)
    DeviceFilter.ThisDevice -> stringResource(R.string.learned_this_device)
    is DeviceFilter.Other -> filter.name
        ?: filter.number?.let { stringResource(R.string.learned_other_device_numbered, it) }
        ?: stringResource(R.string.learned_other_device)
}
