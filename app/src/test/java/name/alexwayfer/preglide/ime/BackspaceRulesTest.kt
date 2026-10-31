package name.alexwayfer.preglide.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackspaceRulesTest {
    @Test
    fun aCharBeforeTheCursorIsDeleted() {
        assertTrue(backspaceDeletes("а", null))
    }

    @Test
    fun aSelectionIsDeletedEvenAtTheStart() {
        assertTrue(backspaceDeletes("", "привет"))
    }

    @Test
    fun nothingIsDeletedAtTheStartWithoutASelection() {
        assertFalse(backspaceDeletes("", null))
        assertFalse(backspaceDeletes("", ""))
    }

    @Test
    fun aFieldThatHidesItsTextMayStillTakeTheKey() {
        assertTrue(backspaceDeletes(null, null))
    }
}
