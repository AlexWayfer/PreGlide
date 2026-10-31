package name.alexwayfer.preglide.update

import android.app.Application
import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpdateNotificationTest {
    @Test
    fun aNewReleaseReplacesTheNoticeAndAnInstalledReleaseClearsIt() {
        val context = RuntimeEnvironment.getApplication()
        val notifications = context.getSystemService(NotificationManager::class.java)
        val release = UpdateRelease(1, checkNotNull(ReleaseVersion.parse("1.1")), 3, null)
        updateNotification(context, release)
        updateNotification(context, release.copy(version = checkNotNull(ReleaseVersion.parse("1.2"))))
        assertEquals(1, shadowOf(notifications).size())
        val notification = shadowOf(notifications).allNotifications.single()
        assertEquals("PreGlide 1.2 is available", notification.extras.getString("android.title"))
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(true, intent.getBooleanExtra(OPEN_UPDATES, false))
        updateNotification(context, release = null)
        assertEquals(0, shadowOf(notifications).size())
    }
}
