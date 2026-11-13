package name.alexwayfer.preglide.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import name.alexwayfer.preglide.engine.suggest.CANDIDATE_DAYS
import name.alexwayfer.preglide.engine.suggest.LEARNING_USES
import name.alexwayfer.preglide.engine.suggest.LearningPolicy

class LearningSettings(
    private val store: DataStore<Preferences>,
    preferences: Flow<Preferences>,
    scope: CoroutineScope,
) {
    private val uses = intPreferencesKey("learning_uses")
    private val days = intPreferencesKey("candidate_days")
    private val default = LearningPolicy()
    val policy = preferences.map { values ->
        LearningPolicy(
            values[uses]?.takeIf { it in LEARNING_USES } ?: default.uses,
            values[days]?.takeIf { it in CANDIDATE_DAYS } ?: default.days,
        )
    }.stateIn(scope, SharingStarted.Eagerly, default)

    suspend fun setPolicy(policy: LearningPolicy) {
        store.edit {
            it[uses] = policy.uses
            it[days] = policy.days
        }
    }
}

internal fun learningPolicy(uses: String, days: String): LearningPolicy? {
    val count = uses.toIntOrNull()?.takeIf { it in LEARNING_USES }
    val span = days.toIntOrNull()?.takeIf { it in CANDIDATE_DAYS }
    return if (count != null && span != null) LearningPolicy(count, span) else null
}
