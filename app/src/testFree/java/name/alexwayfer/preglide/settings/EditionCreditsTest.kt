package name.alexwayfer.preglide.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class EditionCreditsTest {
    @Test
    fun theFreeEditionShipsNoLibraryOfItsOwn() {
        assertEquals(emptyList<Credit>(), editionCredits)
    }
}
