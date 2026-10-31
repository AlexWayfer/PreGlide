package name.alexwayfer.preglide.premium

import android.app.Application
import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SimCountryTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun simIso(iso: String) {
        shadowOf(app.getSystemService(TelephonyManager::class.java)).setSimCountryIso(iso)
    }

    @Test
    fun theSimTellsItsCountry() {
        simIso("ru")

        assertEquals("ru", simCountry(app))
    }

    @Test
    fun noSimTellsNoCountry() {
        simIso("")

        assertNull(simCountry(app))
    }
}
