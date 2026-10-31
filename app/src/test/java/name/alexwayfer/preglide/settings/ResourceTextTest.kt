package name.alexwayfer.preglide.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import name.alexwayfer.preglide.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ResourceTextTest {
    private val resources = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun aTextReadsItsArgumentsAsGetStringDoes() {
        assertEquals(
            resources.getString(R.string.colors_app),
            resourceText(resources, R.string.colors_app, emptyList()),
        )
        assertEquals(
            resources.getString(R.string.colors_hint_with_mode, "A", "B"),
            resourceText(resources, R.string.colors_hint_with_mode, listOf("A", "B")),
        )
    }

    @Test
    fun aTextTakesOneArgumentToo() {
        assertEquals(
            resources.getString(R.string.appearance_key_corners_value, 8),
            resourceText(resources, R.string.appearance_key_corners_value, listOf(8)),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun moreThanTwoArgumentsAreAMistake() {
        resourceText(resources, R.string.colors_hint_with_mode, listOf("A", "B", "C"))
    }
}
