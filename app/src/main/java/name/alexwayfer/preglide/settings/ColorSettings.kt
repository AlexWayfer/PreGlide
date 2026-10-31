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
import name.alexwayfer.preglide.ui.theme.DarkMode
import name.alexwayfer.preglide.ui.theme.Palette

/** The keyboard's own [colors], null for the system's, and whether it is light or dark ([darkMode]). */
data class ThemeSettings(val colors: Palette?, val darkMode: DarkMode)

/** The keyboard's colors, kept in [store] with the other settings, read from [preferences], its data. */
class ColorSettings(
    private val store: DataStore<Preferences>,
    preferences: Flow<Preferences>,
    scope: CoroutineScope,
) {
    /** The keyboard's own colors, or null for the system's: Material You's, where Android has them. */
    val palette: StateFlow<Palette?> = preferences.map { it[PALETTE]?.let(Palette::parse) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setPalette(palette: Palette?) {
        store.edit { if (palette == null) it.remove(PALETTE) else it[PALETTE] = palette.encode() }
    }

    /**
     * Whether the keyboard takes the color of the app it types in, where the app's theme has one; the [palette] is
     * for the other apps.
     */
    val fromApps: StateFlow<Boolean> = preferences.map { it[FROM_APPS] ?: FROM_APPS_DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, FROM_APPS_DEFAULT)

    suspend fun setFromApps(take: Boolean) {
        store.edit { it[FROM_APPS] = take }
    }

    /** Whether the keyboard and the app are light or dark, as the system is until the user picks one. */
    val darkMode: StateFlow<DarkMode> = preferences.map { DarkMode.parse(it[DARK_MODE]) }
        .stateIn(scope, SharingStarted.Eagerly, DarkMode.System)

    suspend fun setDarkMode(mode: DarkMode) {
        store.edit { it[DARK_MODE] = mode.name }
    }

    /**
     * The colors and the mode together, as one read of the store gives them; null until the store is read, so a
     * screen can wait for them rather than show the defaults first and then flow into the user's colors.
     */
    val theme: StateFlow<ThemeSettings?> = preferences
        .map { ThemeSettings(it[PALETTE]?.let(Palette::parse), DarkMode.parse(it[DARK_MODE])) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    companion object {
        private val PALETTE = stringPreferencesKey("colors")
        private val FROM_APPS = booleanPreferencesKey("app_colors")
        private val DARK_MODE = stringPreferencesKey("dark_mode")

        /** Off: a keyboard that changes its colors from app to app is a choice, and many apps' themes tell nothing. */
        const val FROM_APPS_DEFAULT = false
    }
}
