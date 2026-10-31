package name.alexwayfer.preglide.ime

import android.app.Application
import android.content.Intent
import name.alexwayfer.preglide.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsLinkTest {
    @Test
    fun theSettingsOpenAnewInTheirOwnTask() {
        val intent = settingsIntent(RuntimeEnvironment.getApplication())

        assertEquals(SettingsActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        // A screen left in the background, as on the setup, would wait under the new one, and back would lead to it.
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }
}
