package name.alexwayfer.preglide.ime

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnterRulesTest {
    @Test
    fun enterRunsTheFieldsAction() {
        assertEquals(EditorInfo.IME_ACTION_SEND, enterAction(EditorInfo.IME_ACTION_SEND))
        assertEquals(EditorInfo.IME_ACTION_SEARCH, enterAction(EditorInfo.IME_ACTION_SEARCH))
    }

    @Test
    fun enterBreaksTheLineWithoutAnAction() {
        assertNull(enterAction(EditorInfo.IME_ACTION_UNSPECIFIED))
        assertNull(enterAction(EditorInfo.IME_ACTION_NONE))
    }

    @Test
    fun enterBreaksTheLineWhenTheFieldAsksForIt() {
        assertNull(enterAction(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION))
    }
}
