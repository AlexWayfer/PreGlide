package name.alexwayfer.preglide.ui.theme

import android.app.Application
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.findViewTreeCompositionContext
import kotlinx.coroutines.flow.MutableStateFlow
import name.alexwayfer.preglide.ime.ComposeHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppMotionTest {
    @get:Rule
    val host = ComposeHost()

    private val resolver = RuntimeEnvironment.getApplication().contentResolver

    private fun systemScale(scale: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun animationsGoAtTheSystemsPace() {
        systemScale(0.5f)

        assertEquals(0.5f, AppMotion(resolver, MutableStateFlow(true)).scaleFactor)
    }

    @Test
    fun animationsTurnedOffEndAtOnce() {
        systemScale(1f)

        assertEquals(0f, AppMotion(resolver, MutableStateFlow(false)).scaleFactor)
    }

    @Test
    fun animationsTurnedOffInTheSystemStayOff() {
        systemScale(0f)

        assertEquals(0f, AppMotion(resolver, MutableStateFlow(true)).scaleFactor)
    }

    @Test
    fun turningAnimationsOffTakesHoldAtOnce() {
        systemScale(1f)
        val animations = MutableStateFlow(true)
        val motion = AppMotion(resolver, animations)
        animations.value = false

        assertEquals(0f, motion.scaleFactor)
    }

    @Test
    fun aWindowsAnimationsRunAtItsPace() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val motion = AppMotion(resolver, MutableStateFlow(false))

        activity.window.decorView.runAnimationsAt(motion, activity.lifecycle)
        activity.setContent {}
        val content = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val recomposer = content.findViewTreeCompositionContext() as Recomposer

        assertSame(motion, recomposer.effectCoroutineContext[MotionDurationScale])
    }
}
