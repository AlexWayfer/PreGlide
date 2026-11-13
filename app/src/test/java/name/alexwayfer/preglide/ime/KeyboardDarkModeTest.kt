package name.alexwayfer.preglide.ime

import android.app.Application
import android.content.pm.PackageManager.GET_META_DATA
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class KeyboardDarkModeTest {
    @Test
    fun miuiLeavesTheAppsOwnDarkThemeInControl() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getApplicationInfo(app.packageName, GET_META_DATA)

        assertTrue(info.metaData?.getBoolean("force_dark_google") == true)
    }
}
