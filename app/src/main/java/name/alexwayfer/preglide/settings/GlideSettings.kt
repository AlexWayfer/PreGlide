package name.alexwayfer.preglide.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import name.alexwayfer.preglide.ime.GlideStrip

/** Glide typing's settings, kept in [store] with the others, read from [preferences], its data. */
class GlideSettings(
    private val store: DataStore<Preferences>,
    preferences: Flow<Preferences>,
    scope: CoroutineScope,
) {
    /** Whether a finger gliding over the letters types the word whose keys it passes. */
    val typing: StateFlow<Boolean> = preferences.map { it[TYPING] ?: TYPING_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, TYPING_DEFAULT)

    suspend fun setTyping(glide: Boolean) {
        store.edit { it[TYPING] = glide }
    }

    /** Whether a backspace right after a glide erases the glided word whole, rather than its last letter. */
    val erasesWord: StateFlow<Boolean> = preferences.map { it[ERASES_WORD] ?: ERASES_WORD_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, ERASES_WORD_DEFAULT)

    suspend fun setErasesWord(erases: Boolean) {
        store.edit { it[ERASES_WORD] = erases }
    }

    /**
     * Whether a backspace that erases the space or mark typed right after a glided word brings the glide's words back
     * to the strip, rather than words like the one typed.
     */
    val bringsBackWords: StateFlow<Boolean> = preferences.map { it[BRINGS_BACK_WORDS] ?: BRINGS_BACK_WORDS_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, BRINGS_BACK_WORDS_DEFAULT)

    suspend fun setBringsBackWords(bringsBack: Boolean) {
        store.edit { it[BRINGS_BACK_WORDS] = bringsBack }
    }

    /** What the strip offers right after a glide: the glide's other words, the next ones, or either as it fits. */
    val strip: StateFlow<GlideStrip> = preferences.map { GlideStrip.parse(it[STRIP]) }
        .stateIn(scope, SharingStarted.Eagerly, GlideStrip.parse(null))

    suspend fun setStrip(strip: GlideStrip) {
        store.edit { it[STRIP] = strip.name }
    }

    companion object {
        private val TYPING = booleanPreferencesKey("glide_typing")
        private val ERASES_WORD = booleanPreferencesKey("glide_erases_word")
        private val BRINGS_BACK_WORDS = booleanPreferencesKey("glide_brings_back_words")
        private val STRIP = stringPreferencesKey("glide_strip")

        /** On: a glide starts only once the finger leaves a letter, so taps type as before. */
        const val TYPING_DEFAULT = true

        /** On: a glide that read the wrong word is one backspace away, as in other keyboards. */
        const val ERASES_WORD_DEFAULT = true

        /** On: the word meant is often among the glide's others, seen only once the space went in. */
        const val BRINGS_BACK_WORDS_DEFAULT = true
    }
}
