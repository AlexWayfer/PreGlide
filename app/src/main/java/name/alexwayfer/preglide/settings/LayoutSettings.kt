package name.alexwayfer.preglide.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What keys the layout shows besides its letters, kept in [store] with the others, read from [preferences]. */
class LayoutSettings(
    private val store: DataStore<Preferences>,
    preferences: Flow<Preferences>,
    scope: CoroutineScope,
) {
    /** Whether the top row's keys show their digits small in the corner and offer them on a long press. */
    val topRowDigits: StateFlow<Boolean> = preferences.map { it[TOP_ROW_DIGITS] ?: TOP_ROW_DIGITS_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, TOP_ROW_DIGITS_DEFAULT)

    suspend fun setTopRowDigits(show: Boolean) {
        store.edit { it[TOP_ROW_DIGITS] = show }
    }

    /** Whether a row of digits shows above the letters, in place of the top row's digits in the corner. */
    val digitRow: StateFlow<Boolean> = preferences.map { it[DIGIT_ROW] ?: DIGIT_ROW_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, DIGIT_ROW_DEFAULT)

    suspend fun setDigitRow(show: Boolean) {
        store.edit { it[DIGIT_ROW] = show }
    }

    /** Whether the bottom row has a key that opens the emoji, in place of the smile over the comma. */
    val emojiKey: StateFlow<Boolean> = preferences.map { it[EMOJI_KEY] ?: EMOJI_KEY_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, EMOJI_KEY_DEFAULT)

    suspend fun setEmojiKey(show: Boolean) {
        store.edit { it[EMOJI_KEY] = show }
    }

    /**
     * Whether the letters under the top row show the symbols page's keys at their places small in the corner and
     * offer them on a long press.
     */
    val letterSymbols: StateFlow<Boolean> = preferences.map { it[LETTER_SYMBOLS] ?: LETTER_SYMBOLS_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, LETTER_SYMBOLS_DEFAULT)

    suspend fun setLetterSymbols(show: Boolean) {
        store.edit { it[LETTER_SYMBOLS] = show }
    }

    companion object {
        private val TOP_ROW_DIGITS = booleanPreferencesKey("top_row_digits")
        private val DIGIT_ROW = booleanPreferencesKey("digit_row")
        private val EMOJI_KEY = booleanPreferencesKey("emoji_key")
        private val LETTER_SYMBOLS = booleanPreferencesKey("letter_symbols")

        /** On: a digit is then a long press away, without a trip to the symbols. */
        const val TOP_ROW_DIGITS_DEFAULT = true

        /** Off: the row takes room from the screen, and the top row's corners already hold the digits. */
        const val DIGIT_ROW_DEFAULT = false

        /** Off: the key takes room from the space bar, and the comma's long press already opens the emoji. */
        const val EMOJI_KEY_DEFAULT = false

        /** Off: the symbols crowd the keys, and the symbols page holds them a tap away. */
        const val LETTER_SYMBOLS_DEFAULT = false
    }
}
