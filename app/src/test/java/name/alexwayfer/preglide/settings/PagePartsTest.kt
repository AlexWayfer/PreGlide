package name.alexwayfer.preglide.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PagePartsTest {
    private val padding = PaddingValues(start = 1.dp, top = 24.dp, end = 3.dp, bottom = 104.dp)

    @Test
    fun aPageKeepsOffTheStatusBarAndTheSidesButNotTheBottom() {
        assertEquals(
            Modifier.padding(start = 1.dp, top = 24.dp, end = 3.dp),
            Modifier.pagePaddingAbove(padding, LayoutDirection.Ltr),
        )
    }

    @Test
    fun aPageScrollsIntoTheRoomBelowRatherThanEndAboveIt() {
        val scroll = ScrollState(0)

        assertEquals(
            Modifier.padding(start = 1.dp, top = 24.dp, end = 3.dp).verticalScroll(scroll).padding(bottom = 104.dp),
            Modifier.scrollingPage(padding, scroll, LayoutDirection.Ltr),
        )
    }
}
