package name.alexwayfer.preglide.settings

import android.Manifest
import name.alexwayfer.preglide.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotAccessTest {
    private val selected = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    @Test
    fun eachAndroidAsksForItsOwnLeave() {
        assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), photoPermissions(32))
        assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), photoPermissions(33))
        // Android 14 may give some photos alone: asked for too, it tells so rather than give a leave that runs out.
        assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, selected), photoPermissions(34))
    }

    @Test
    fun allPhotosAreTheLeaveThatSeesScreenshots() {
        assertEquals(PhotoAccess.All, photoAccess(34) { it == Manifest.permission.READ_MEDIA_IMAGES })
        assertEquals(PhotoAccess.All, photoAccess(30) { it == Manifest.permission.READ_EXTERNAL_STORAGE })
        assertEquals(PhotoAccess.Selected, photoAccess(34) { it == selected })
        assertEquals(PhotoAccess.None, photoAccess(33) { it == selected })
        assertEquals(PhotoAccess.None, photoAccess(34) { false })
    }

    @Test
    fun screenshotsAreOfferedWhenWantedAndSeen() {
        assertTrue(offersScreenshots(wants = true, PhotoAccess.All))
        assertFalse(offersScreenshots(wants = true, PhotoAccess.Selected))
        assertFalse(offersScreenshots(wants = false, PhotoAccess.All))
    }

    @Test
    fun theSwitchTellsWhySomePhotosAreNotEnough() {
        assertEquals(R.string.settings_screenshots_selected, screenshotsHint(PhotoAccess.Selected))
        assertEquals(R.string.settings_screenshots_hint, screenshotsHint(PhotoAccess.None))
        assertEquals(R.string.settings_screenshots_hint, screenshotsHint(PhotoAccess.All))
    }
}
