package name.alexwayfer.preglide.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.ime.ComposeHost
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeviceChoiceTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun aNameThatCameAfterTheDevicesWordsShowsOnceTheWordsChange() {
        val words = PersonalWords().apply { mergeOthers(mapOf("pc" to PersonalWords())) }
        var names = emptyMap<String, String>()
        var changes by mutableIntStateOf(0)
        lateinit var choice: DeviceChoice
        compose.setContent {
            choice = deviceChoice(mapOf(Language.Russian to words), changes, { names }, testDispatchers.now)
        }
        compose.waitForIdle()
        assertEquals(DeviceFilter.Other("pc", name = null, number = null), choice.filters.last())

        // A sync brought the device's name, and its words changed with it, but no device came or went.
        names = mapOf("pc" to "AlexWin11PC")
        changes++
        compose.waitForIdle()

        assertEquals(DeviceFilter.Other("pc", "AlexWin11PC", number = null), choice.filters.last())
    }
}
