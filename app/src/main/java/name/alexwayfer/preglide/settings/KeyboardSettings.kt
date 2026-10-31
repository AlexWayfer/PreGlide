package name.alexwayfer.preglide.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import name.alexwayfer.preglide.ime.SpaceGestures
import name.alexwayfer.preglide.ui.theme.KeyFont
import java.io.IOException

/**
 * The user's settings, kept in [store]: the app's screen changes them, and the keyboard, in the same process,
 * follows at once. Each holds its default until the store is read, and when it cannot be, as before the first
 * unlock, when the app's storage is locked.
 */
class KeyboardSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val preferences = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    /**
     * Whether the strip of suggestions shows above the keys, with its buttons of the clipboard and the settings.
     * Without it, autocorrect and glides go on, and the symbols' key offers the buttons by its long press.
     */
    val strip: StateFlow<Boolean> = preferences.map { it[STRIP] ?: STRIP_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, STRIP_DEFAULT)

    suspend fun setStrip(show: Boolean) {
        store.edit { it[STRIP] = show }
    }

    /**
     * Whether the next word comes from the prediction model, rather than from the pairs of words the dictionary
     * counted. With it off, the model does not load.
     */
    val usePrediction: StateFlow<Boolean> = preferences.map { it[USE_PREDICTION] ?: USE_PREDICTION_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, USE_PREDICTION_DEFAULT)

    suspend fun setUsePrediction(use: Boolean) {
        store.edit { it[USE_PREDICTION] = use }
    }

    /** Whether a word typed with a slip or a mistake is replaced with the word meant as the user ends it. */
    val autocorrect: StateFlow<Boolean> = preferences.map { it[AUTOCORRECT] ?: AUTOCORRECT_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, AUTOCORRECT_DEFAULT)

    suspend fun setAutocorrect(correct: Boolean) {
        store.edit { it[AUTOCORRECT] = correct }
    }

    /**
     * Whether a fresh screenshot is offered in the strip and kept in the clipboard's history, as if copied. It works
     * only with the leave to read all photos, which the screen asks for as the user turns it on.
     */
    val screenshots: StateFlow<Boolean> = preferences.map { it[SCREENSHOTS] ?: SCREENSHOTS_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, SCREENSHOTS_DEFAULT)

    suspend fun setScreenshots(offer: Boolean) {
        store.edit { it[SCREENSHOTS] = offer }
    }

    /** What a swipe along the space bar and its long press do. */
    val spaceGestures: StateFlow<SpaceGestures> = preferences.map { SpaceGestures.parse(it[SPACE_GESTURES]) }
        .stateIn(scope, SharingStarted.Eagerly, SpaceGestures.parse(null))

    suspend fun setSpaceGestures(gestures: SpaceGestures) {
        store.edit { it[SPACE_GESTURES] = gestures.name }
    }

    /** Whether the keys vibrate as they are pressed, and how strong. */
    val vibration = VibrationSettings(store, preferences, scope)

    /** Glide typing's settings. */
    val glide = GlideSettings(store, preferences, scope)

    /** The top row's digits, the digit row and the emoji key. */
    val layout = LayoutSettings(store, preferences, scope)

    /** Whether each key has a background of its own; without, only a pressed key lights up. */
    val keyBackgrounds: StateFlow<Boolean> = preferences.map { it[KEY_BACKGROUNDS] ?: KEY_BACKGROUNDS_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, KEY_BACKGROUNDS_DEFAULT)

    suspend fun setKeyBackgrounds(show: Boolean) {
        store.edit { it[KEY_BACKGROUNDS] = show }
    }

    /** Whether keys without backgrounds have thin lines between them. */
    val keyDividers: StateFlow<Boolean> = preferences.map { it[KEY_DIVIDERS] ?: KEY_DIVIDERS_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, KEY_DIVIDERS_DEFAULT)

    suspend fun setKeyDividers(show: Boolean) {
        store.edit { it[KEY_DIVIDERS] = show }
    }

    /** How round the keys' corners are, in dp: one of [KeyCornerRadii]. */
    val keyCornerRadius: StateFlow<Int> = preferences.map { keyCornerRadius(it[KEY_CORNER_RADIUS]) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_KEY_CORNER_RADIUS)

    suspend fun setKeyCornerRadius(radius: Int) {
        store.edit { it[KEY_CORNER_RADIUS] = nearestCornerRadius(radius.toFloat()) }
    }

    /** How tall each row of keys is, in dp: one of [KeyHeights]. */
    val keyHeight: StateFlow<Int> = preferences.map { keyHeight(it[KEY_HEIGHT]) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_KEY_HEIGHT)

    suspend fun setKeyHeight(height: Int) {
        store.edit { it[KEY_HEIGHT] = nearestKeyHeight(height.toFloat()) }
    }

    /** The font of the keys and the suggestions: the system's until the user picks one. */
    val keyFont: StateFlow<KeyFont> = preferences.map { KeyFont.parse(it[KEY_FONT]) }
        .stateIn(scope, SharingStarted.Eagerly, KeyFont.System)

    suspend fun setKeyFont(font: KeyFont) {
        store.edit { it[KEY_FONT] = font.name }
    }

    /** The keyboard's colors and whether it is light or dark. */
    val colors = ColorSettings(store, preferences, scope)

    /** Whether the keyboard's and the app's views move, fade and change in motion; without, at once. */
    val animations: StateFlow<Boolean> = preferences.map { it[ANIMATIONS] ?: ANIMATIONS_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, ANIMATIONS_DEFAULT)

    suspend fun setAnimations(run: Boolean) {
        store.edit { it[ANIMATIONS] = run }
    }

    companion object {
        private val STRIP = booleanPreferencesKey("strip")
        private val USE_PREDICTION = booleanPreferencesKey("use_prediction")
        private val AUTOCORRECT = booleanPreferencesKey("autocorrect")
        private val SCREENSHOTS = booleanPreferencesKey("screenshots")
        private val SPACE_GESTURES = stringPreferencesKey("space_gestures")
        private val KEY_BACKGROUNDS = booleanPreferencesKey("key_backgrounds")
        private val KEY_DIVIDERS = booleanPreferencesKey("key_dividers")
        private val KEY_CORNER_RADIUS = intPreferencesKey("key_corner_radius")
        private val KEY_HEIGHT = intPreferencesKey("key_height")
        private val KEY_FONT = stringPreferencesKey("key_font")
        private val ANIMATIONS = booleanPreferencesKey("animations")

        /** On: the words above the keys are much of what the keyboard helps with. */
        const val STRIP_DEFAULT = true

        /** On: the model predicts better than the pairs on text it never saw. */
        const val USE_PREDICTION_DEFAULT = true

        /** On: it fixes typos only when fairly sure, and a wrong fix is a backspace away. */
        const val AUTOCORRECT_DEFAULT = true

        /** Off: it needs the leave to read all photos, which a keyboard should not take unasked. */
        const val SCREENSHOTS_DEFAULT = false

        /** On: the keys looked so before the user could turn their backgrounds off. */
        const val KEY_BACKGROUNDS_DEFAULT = true

        /** Off: the keys without backgrounds stand apart by their room alone until the user asks for lines. */
        const val KEY_DIVIDERS_DEFAULT = false

        /** On: motion shows where a view comes from and goes, until the user would rather do without. */
        const val ANIMATIONS_DEFAULT = true
    }
}
