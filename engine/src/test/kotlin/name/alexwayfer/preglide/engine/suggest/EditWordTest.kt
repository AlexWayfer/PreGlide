package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditWordTest {
    private val words = PersonalWords()
    private val dictionary = Dictionary.parse(sequenceOf("right\t1"))

    @Test
    fun aConfirmedMergeKeepsEachOwnersHistoryAndUndoPreservesNewUses() {
        val pc = PersonalWords().apply { learn("wrong", null, 10, kept = true) }
        words.learn("wrong", null, 12)
        words.starts.learn("wrong", 12)
        words.learn("right", "wrong", 18)
        words.mergeOthers(mapOf("pc" to pc))
        val edit = checkNotNull(words.previewEdit("wrong", "right", dictionary))
        assertEquals(2, edit.source.count)
        assertEquals(1, edit.target?.count)
        assertTrue(edit.builtIn)
        val id = checkNotNull(words.editWord(edit, dictionary, 20))
        assertTrue(id.matches(Regex("[0-9a-f]{32}")))
        assertEquals(Learned("right", 3, 18, 1), words.learned("right"))
        assertEquals("right", words.after("right").single().spelling)
        assertEquals(1, words.allOn("pc").single().count)
        assertEquals("right", words.allStarts().single().spelling)
        words.learn("right", null, 24)
        assertTrue(words.undoWordEdit(id))
        assertEquals(Learned("wrong", 2, 12, 1), words.learned("wrong"))
        assertEquals(Learned("right", 2, 24), words.learned("right"))
        assertEquals("wrong", words.allStarts().single().spelling)
        assertFalse(words.undoWordEdit("unknown"))
    }

    @Test
    fun invalidOrStaleEditsCannotOverwriteTyping() {
        words.learn("api", null, 1)
        for (to in listOf("", "api", "two words", "api-")) assertNull(words.previewEdit("api", to, dictionary))
        assertNull(words.previewEdit("gone", "API", dictionary))
        val preview = checkNotNull(words.previewEdit("api", "API", dictionary))
        assertNull(preview.target)
        words.learn("api", null, 2)
        assertNull(words.editWord(preview, dictionary, 4))
        val fresh = checkNotNull(words.previewEdit("api", "API", dictionary))
        val id = checkNotNull(words.editWord(fresh, dictionary, 4))
        assertEquals(Learned("API", 2, 2), words.learned("api"))
        val another = checkNotNull(words.previewEdit("API", "Api", dictionary))
        val next = words.editWord(another, dictionary, 1)
        assertNotNull(next)
        assertTrue(checkNotNull(next) > id)
    }
}
