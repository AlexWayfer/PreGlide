package name.alexwayfer.preglide.ime

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Registers the empty activity a Compose test shows its content in, which Compose's test manifest would declare in
 * the debug APK: the APK stays without it. Runs before the Compose rule, as `@get:Rule(order = 0)`.
 */
class ComposeHost : ExternalResource() {
    override fun before() {
        val app = RuntimeEnvironment.getApplication()
        val activity = ComponentName(app, ComponentActivity::class.java)
        val packages = shadowOf(app.packageManager)
        packages.addActivityIfNotPresent(activity)
        packages.addIntentFilterForActivity(
            activity,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }
}
