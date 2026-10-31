package name.alexwayfer.preglide.spell

import android.view.textservice.SuggestionsInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SpellingRulesTest {
    @Test
    fun aWordIsMarkedOnlyWhenCheckedAndUnknown() {
        assertEquals(0, spellingAttributes(checked = false, corrections = null))
        val known = spellingAttributes(checked = true, corrections = null)
        assertEquals(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, known)
        assertEquals(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, spellingAttributes(checked = true, emptyList()))
    }
}
