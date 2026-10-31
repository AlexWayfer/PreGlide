package name.alexwayfer.preglide.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideStripTest {
    @Test
    fun autoIsTheDefaultAndShowsTheNextWordsOnlyAfterASureGlide() {
        assertEquals(GlideStrip.Auto, GlideStrip.parse(null))
        assertEquals(GlideStrip.Auto, GlideStrip.parse("Gone"))
        assertEquals(GlideStrip.NextWords, GlideStrip.parse("NextWords"))
        assertTrue(GlideStrip.Auto.showsNextWords(sure = true))
        assertFalse(GlideStrip.Auto.showsNextWords(sure = false))
    }

    @Test
    fun theOtherChoicesShowTheSameWordsAfterAnyGlide() {
        for (sure in listOf(true, false)) {
            assertTrue(GlideStrip.NextWords.showsNextWords(sure))
            assertFalse(GlideStrip.GlideWords.showsNextWords(sure))
        }
    }
}
