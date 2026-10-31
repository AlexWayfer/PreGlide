package name.alexwayfer.preglide.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import name.alexwayfer.preglide.ime.GlideStrip
import name.alexwayfer.preglide.ime.SpaceGestures
import name.alexwayfer.preglide.ime.Vibration
import name.alexwayfer.preglide.ime.VibrationStrengths
import name.alexwayfer.preglide.testDispatchers
import name.alexwayfer.preglide.ui.theme.DarkMode
import name.alexwayfer.preglide.ui.theme.KeyFont
import name.alexwayfer.preglide.ui.theme.presetPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

class KeyboardSettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun <T> withSettings(file: File, use: suspend (KeyboardSettings) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(testDispatchers.io + job)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            val start = System.nanoTime()
            return runBlocking {
                try {
                    withTimeout(TIMEOUT) { use(KeyboardSettings(store, scope)) }
                } catch (timeout: TimeoutCancellationException) {
                    // What the store holds tells a slow store from settings that stopped following it.
                    val stored = withTimeoutOrNull(TIMEOUT) { store.data.first().asMap() }
                    val waited = (System.nanoTime() - start) / NANOS_PER_MILLI
                    throw AssertionError("Timed out after $waited ms; the store holds $stored", timeout)
                }
            }
        } finally {
            // A store lets go of its file once its scope is done, and the next store of the file must find it free.
            runBlocking { job.cancelAndJoin() }
        }
    }

    @Test
    fun screenshotsAreOffUntilTurnedOn() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(false, settings.screenshots.value)
            settings.setScreenshots(true)
            assertEquals(true, settings.screenshots.first { it })
        }
    }

    @Test
    fun theSpaceBarSwitchesTheLanguageUntilTheUserChoosesOtherwise() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(SpaceGestures.SwipeLanguage, settings.spaceGestures.value)
            settings.setSpaceGestures(SpaceGestures.SwipeCursor)
            assertEquals(SpaceGestures.SwipeCursor, settings.spaceGestures.first { it == SpaceGestures.SwipeCursor })
        }
    }

    @Test
    fun predictionIsOnUntilTurnedOff() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertTrue(settings.usePrediction.value)
            settings.setUsePrediction(false)
            assertEquals(false, settings.usePrediction.first { !it })
        }
    }

    @Test
    fun theStripShowsUntilHidden() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertTrue(settings.strip.value)
            settings.setStrip(false)
            assertEquals(false, settings.strip.first { !it })
        }
        withSettings(file) { settings -> assertEquals(false, settings.strip.first { !it }) }
    }

    @Test
    fun glideTypingAndItsBackspaceAreOnUntilTurnedOff() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertTrue(settings.glide.typing.value)
            assertTrue(settings.glide.erasesWord.value)
            assertTrue(settings.glide.bringsBackWords.value)
            settings.glide.setTyping(false)
            settings.glide.setErasesWord(false)
            settings.glide.setBringsBackWords(false)
            assertEquals(false, settings.glide.typing.first { !it })
            assertEquals(false, settings.glide.erasesWord.first { !it })
            assertEquals(false, settings.glide.bringsBackWords.first { !it })
        }
    }

    @Test
    fun theStripAfterAGlideIsAutoUntilTheUserChoosesOtherwise() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(GlideStrip.Auto, settings.glide.strip.value)
            settings.glide.setStrip(GlideStrip.NextWords)
            assertEquals(GlideStrip.NextWords, settings.glide.strip.first { it == GlideStrip.NextWords })
        }
        withSettings(file) { settings ->
            assertEquals(GlideStrip.NextWords, settings.glide.strip.first { it == GlideStrip.NextWords })
        }
    }

    @Test
    fun topRowDigitsAreOnUntilTurnedOff() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertTrue(settings.layout.topRowDigits.value)
            settings.layout.setTopRowDigits(false)
            assertEquals(false, settings.layout.topRowDigits.first { !it })
        }
        withSettings(file) { settings -> assertEquals(false, settings.layout.topRowDigits.first { !it }) }
    }

    @Test
    fun theDigitRowIsOffUntilTurnedOn() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(false, settings.layout.digitRow.value)
            settings.layout.setDigitRow(true)
            assertTrue(settings.layout.digitRow.first { it })
        }
        withSettings(file) { settings -> assertTrue(settings.layout.digitRow.first { it }) }
    }

    @Test
    fun theEmojiKeyIsOffUntilTurnedOn() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(false, settings.layout.emojiKey.value)
            settings.layout.setEmojiKey(true)
            assertTrue(settings.layout.emojiKey.first { it })
        }
        withSettings(file) { settings -> assertTrue(settings.layout.emojiKey.first { it }) }
    }

    @Test
    fun theLettersSymbolsAreOffUntilTurnedOn() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(false, settings.layout.letterSymbols.value)
            settings.layout.setLetterSymbols(true)
            assertTrue(settings.layout.letterSymbols.first { it })
        }
        withSettings(file) { settings -> assertTrue(settings.layout.letterSymbols.first { it }) }
    }

    @Test
    fun keyBackgroundsAreOnUntilTurnedOff() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertTrue(settings.keyBackgrounds.value)
            settings.setKeyBackgrounds(false)
            assertEquals(false, settings.keyBackgrounds.first { !it })
        }
        withSettings(file) { settings -> assertEquals(false, settings.keyBackgrounds.first { !it }) }
    }

    @Test
    fun keyDividersAreOffUntilTurnedOn() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(false, settings.keyDividers.value)
            settings.setKeyDividers(true)
            assertTrue(settings.keyDividers.first { it })
        }
        withSettings(file) { settings -> assertTrue(settings.keyDividers.first { it }) }
    }

    @Test
    fun animationsAreOnUntilTurnedOff() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertTrue(settings.animations.value)
            settings.setAnimations(false)
            assertEquals(false, settings.animations.first { !it })
        }
        withSettings(file) { settings -> assertEquals(false, settings.animations.first { !it }) }
    }

    @Test
    fun keyCornersKeepTheirRadiusUntilTheUserPicksAnother() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(DEFAULT_KEY_CORNER_RADIUS, settings.keyCornerRadius.value)
            settings.setKeyCornerRadius(12)
            assertEquals(12, settings.keyCornerRadius.first { it == 12 })
        }
        withSettings(file) { settings ->
            assertEquals(12, settings.keyCornerRadius.first { it == 12 })
            // A radius between the stops is kept as the nearest stop.
            settings.setKeyCornerRadius(3)
            assertEquals(2, settings.keyCornerRadius.first { it != 12 })
        }
    }

    @Test
    fun keysKeepTheirHeightUntilTheUserPicksAnother() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(DEFAULT_KEY_HEIGHT, settings.keyHeight.value)
            settings.setKeyHeight(58)
            assertEquals(58, settings.keyHeight.first { it == 58 })
        }
        withSettings(file) { settings ->
            assertEquals(58, settings.keyHeight.first { it == 58 })
            // A height between the stops is kept as the nearest stop.
            settings.setKeyHeight(43)
            assertEquals(42, settings.keyHeight.first { it != 58 })
        }
    }

    @Test
    fun theKeysVibrateAsTheSystemSaysUntilTheUserChanges() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            val vibration = settings.vibration
            assertEquals(Vibration.System, vibration.chosen.value)
            vibration.setStrength(7)
            assertEquals(7, vibration.strength.first { it == 7 })
            vibration.setOn(false)
            assertEquals(Vibration(on = false, strength = 7), vibration.chosen.first { !it.on })
        }
        withSettings(file) { settings ->
            val vibration = settings.vibration
            assertEquals(Vibration(on = false, strength = 7), vibration.chosen.first { !it.on })
            // A strength past the scale is kept as its end.
            vibration.setStrength(15)
            assertEquals(VibrationStrengths.last, vibration.strength.first { it != 7 })
        }
    }

    @Test
    fun theFontIsTheSystemsUntilTheUserPicksOne() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(KeyFont.System, settings.keyFont.value)
            settings.setKeyFont(KeyFont.Inter)
            assertEquals(KeyFont.Inter, settings.keyFont.first { it == KeyFont.Inter })
        }
        withSettings(file) { settings ->
            assertEquals(KeyFont.Inter, settings.keyFont.first { it == KeyFont.Inter })
            settings.setKeyFont(KeyFont.System)
            assertEquals(KeyFont.System, settings.keyFont.first { it == KeyFont.System })
        }
    }

    @Test
    fun aSettingStaysAsTheUserLeftIt() {
        val file = File(folder.root, "settings.preferences_pb")
        withSettings(file) { it.setUsePrediction(false) }

        // A new store, as after the app restarted, reads it back.
        withSettings(file) { settings -> assertEquals(false, settings.usePrediction.first { !it }) }
    }

    @Test
    fun theColorsAreTheSystemsUntilAPaletteIsChosen() {
        val file = File(folder.root, "settings.preferences_pb")
        val palette = presetPalettes.last()

        withSettings(file) { settings ->
            assertNull(settings.colors.palette.value)
            settings.colors.setPalette(palette)
            assertEquals(palette, settings.colors.palette.first { it != null })
        }
        // A new store, as after the app restarted, reads it back; null gives the system's colors back.
        withSettings(file) { settings ->
            assertEquals(palette, settings.colors.palette.first { it != null })
            settings.colors.setPalette(null)
            assertNull(settings.colors.palette.first { it == null })
        }
    }

    @Test
    fun theModeIsTheSystemsUntilOneIsPicked() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(DarkMode.System, settings.colors.darkMode.value)
            settings.colors.setDarkMode(DarkMode.Light)
            assertEquals(DarkMode.Light, settings.colors.darkMode.first { it == DarkMode.Light })
        }
        withSettings(file) { settings ->
            assertEquals(DarkMode.Light, settings.colors.darkMode.first { it == DarkMode.Light })
        }
    }

    @Test
    fun theAppsColorsAreOffUntilTurnedOn() {
        val file = File(folder.root, "settings.preferences_pb")

        withSettings(file) { settings ->
            assertEquals(false, settings.colors.fromApps.value)
            settings.colors.setFromApps(true)
            assertEquals(true, settings.colors.fromApps.first { it })
        }
        withSettings(file) { settings ->
            assertEquals(true, settings.colors.fromApps.first { it })
        }
    }

    @Test
    fun theThemeComesAsOneReadOfTheStore() {
        val file = File(folder.root, "settings.preferences_pb")
        val palette = presetPalettes.last()

        withSettings(file) { settings ->
            assertEquals(ThemeSettings(null, DarkMode.System), settings.colors.theme.first { it != null })
            settings.colors.setPalette(palette)
            settings.colors.setDarkMode(DarkMode.Dark)
            val expected = ThemeSettings(palette, DarkMode.Dark)
            assertEquals(expected, settings.colors.theme.first { it == expected })
        }
    }

    private companion object {
        val TIMEOUT = 5.seconds
        const val NANOS_PER_MILLI = 1_000_000
    }
}
