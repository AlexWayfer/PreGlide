package name.alexwayfer.preglide.ui.theme

import android.content.ContentResolver
import android.provider.Settings
import android.view.View
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.StateFlow

/** The system's pace of animations while the developer options leave it as it comes. */
private const val SYSTEM_SCALE_DEFAULT = 1f

/**
 * The pace of the app's animations, which Compose reads from the context they run in: none while the user turns
 * [animations] off, so each one ends at once, else the system's, which the developer options can slow down, speed up
 * or turn off. The system's is read from [resolver] each time, so a change there counts at once.
 */
class AppMotion(private val resolver: ContentResolver, private val animations: StateFlow<Boolean>) :
    MotionDurationScale {
    override val scaleFactor: Float
        get() = motionScale(
            animations.value,
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, SYSTEM_SCALE_DEFAULT),
        )
}

/** The pace of animations: the [system]'s while [animations] are on, and none while they are off. */
internal fun motionScale(animations: Boolean, system: Float): Float = if (animations) system else 0f

/**
 * Gives the compositions in this view's window, the root view's, a recomposer of their own whose animations go at
 * [motion]'s pace: Compose's own follows the system's pace only. Call it before the window's content first composes;
 * a window that has a recomposer already keeps it.
 */
fun View.runAnimationsAt(motion: MotionDurationScale, lifecycle: Lifecycle) {
    if (compositionContext == null) compositionContext = createLifecycleAwareWindowRecomposer(motion, lifecycle)
}
