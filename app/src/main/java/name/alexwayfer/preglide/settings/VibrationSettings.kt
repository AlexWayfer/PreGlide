package name.alexwayfer.preglide.settings

import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ime.KeyHaptics
import name.alexwayfer.preglide.ime.SYSTEM_STRENGTH
import name.alexwayfer.preglide.ime.Vibration
import name.alexwayfer.preglide.ime.VibrationStrengths

/** The keys' vibration as the user set it, kept in [store] with the others, read from [preferences], its data. */
class VibrationSettings(
    private val store: DataStore<Preferences>,
    preferences: Flow<Preferences>,
    scope: CoroutineScope,
) {
    /** Whether the keys vibrate as they are pressed, and how strong. */
    val chosen: StateFlow<Vibration> = preferences.map { vibration(it[VIBRATES], it[STRENGTH]) }
        .stateIn(scope, SharingStarted.Eagerly, Vibration.System)

    suspend fun setOn(on: Boolean) {
        store.edit { it[VIBRATES] = on }
    }

    /** One of [VibrationStrengths]: [SYSTEM_STRENGTH] leaves it to the system. */
    val strength: StateFlow<Int> = chosen.map { it.strength }
        .stateIn(scope, SharingStarted.Eagerly, SYSTEM_STRENGTH)

    suspend fun setStrength(strength: Int) {
        store.edit { it[STRENGTH] = nearestStop(VibrationStrengths, strength.toFloat()) }
    }

    private companion object {
        val VIBRATES = booleanPreferencesKey("vibrates")
        val STRENGTH = intPreferencesKey("vibration_strength")
    }
}

/** On: the keys vibrated, as the system's haptics, before the user could turn it off. */
const val VIBRATES_DEFAULT = true

/** The vibration the store's [vibrates] and [strength] give: the system's until the user changes them. */
fun vibration(vibrates: Boolean?, strength: Int?): Vibration = Vibration(
    on = vibrates ?: VIBRATES_DEFAULT,
    strength = strength?.let { nearestStop(VibrationStrengths, it.toFloat()) } ?: SYSTEM_STRENGTH,
)

private const val PERCENT = 100

/** A list row's icon with the room after it, which the slider's own side padding adds to: under the row's text. */
private val StrengthIndent = 40.dp

/** The share of the strongest vibration that [strength], one of the keyboard's own, gives, in percent. */
fun vibrationPercent(strength: Int): Int = strength * PERCENT / VibrationStrengths.last

/**
 * Whether the keys vibrate, and, only then, how strong: the system's, or one of the keyboard's own. Each stop of the
 * strength is felt as a key press at that strength.
 */
@Composable
fun ColumnScope.VibrationEntries(settings: VibrationSettings) {
    val vibration by settings.chosen.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val vibrator = remember(view) { view.context.getSystemService(Vibrator::class.java) }
    SwitchEntry(
        R.drawable.ic_vibration,
        stringResource(R.string.settings_vibration),
        stringResource(R.string.settings_vibration_hint),
        checked = vibration.on,
        onChange = { scope.launch { settings.setOn(it) } },
    )
    // Only a vibration that is on has a strength. It stands under the switch's text, as a part of it.
    AnimatedVisibility(vibration.on) {
        Column(Modifier.padding(start = StrengthIndent, bottom = 8.dp)) {
            StepSliderSection(
                stringResource(R.string.settings_vibration_strength),
                VibrationStrengths,
                settings.strength,
                describe = {
                    if (it == SYSTEM_STRENGTH) {
                        stringResource(R.string.settings_vibration_system)
                    } else {
                        stringResource(R.string.settings_vibration_percent, vibrationPercent(it))
                    }
                },
                onPick = settings::setStrength,
                feel = { KeyHaptics(view, vibrator) { Vibration(on = true, strength = it) }.press() },
            )
        }
    }
}
