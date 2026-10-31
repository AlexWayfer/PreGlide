package name.alexwayfer.preglide.settings

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTitleTest {
    @Test
    fun theTileShowsTheMiddleOfTheIconAsALauncherMaskDoes() {
        assertEquals(108.dp, logoCanvas(72.dp))
    }
}
