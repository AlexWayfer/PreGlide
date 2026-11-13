package name.alexwayfer.preglide.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Optional punctuation shortcuts, off until the user asks for them. */
class PunctuationSettings(
    private val store: DataStore<Preferences>,
    preferences: Flow<Preferences>,
    scope: CoroutineScope,
) {
    /** Whether two consecutive explicit space taps replace their spaces with a period and a space. */
    val doubleSpacePeriod = preferences.map { it[DOUBLE_SPACE_PERIOD] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = false)

    suspend fun setDoubleSpacePeriod(enabled: Boolean) {
        store.edit { it[DOUBLE_SPACE_PERIOD] = enabled }
    }

    companion object {
        private val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
    }
}
