package name.alexwayfer.preglide.ui.theme

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ThemePrimaryTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun anAppsThemeGivesItsPrimaryColor() {
        assertNotNull(themePrimary(app, app.packageName))
    }

    @Test
    fun anAppNotInstalledGivesNoColor() {
        assertNull(themePrimary(app, MISSING))
    }

    @Test
    fun androidsOwnThemesGiveTheirPrimaryColors() {
        assertEquals(PLATFORM_THEMES, platformPrimaries(app).size)
    }

    @Test
    fun theReaderReadsTheAppsThemes() {
        val reader = appColorReader(app, CoroutineScope(testDispatchers.now), testDispatchers.now)

        reader.follow(MISSING, enabled = true)
        assertNull(reader.seed.value)

        reader.follow(app.packageName, enabled = true)
        assertNull(reader.seed.value)
    }

    private companion object {
        const val MISSING = "com.example.missing"

        /** Material's and the maker's, dark and light. */
        const val PLATFORM_THEMES = 4
    }
}
