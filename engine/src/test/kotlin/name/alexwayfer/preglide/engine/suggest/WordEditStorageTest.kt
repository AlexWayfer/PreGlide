package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WordEditStorageTest {
    private val first = "000000000000000a0000000000000001"
    private val second = "00000000000000140000000000000001"

    @Test
    fun aSnapshotUsesChainedEditsForWordsFieldStartsAndContexts() {
        val rows = listOf(
            "w\told\t4\t1", EDIT_BASE,
            "e\t$second\tnew\tlatest\t0", "e\t$first\told\tnew\t0",
            "h\t$first\t\told\t4\t1\t1", "hs\t$first\told\t1\t1\t0",
            "h\t$first\tmy old\tname\t1\t2\t0",
            "bn\tmy latest\tname\t1\t30",
        )
        val words = PersonalWords.read(rows.asSequence())
        val context = listOf("my", "latest")
        val snapshot = PersonalWords.snapshot(words, context)
        assertEquals(Learned("latest", 4, 1, 1), snapshot.learned("latest"))
        assertEquals(Learned("name", 2, 30), snapshot.after(context).single())
        assertEquals(ContextSeen(2, seen = true, withWord = true), snapshot.seen("name", context).last())
        assertEquals("latest", snapshot.allStarts().single().spelling)
        assertNull(snapshot.learned("old"))
    }

    @Test
    fun editsKeepYoDistinctAndIgnoreMalformedCommands() {
        val words = PersonalWords.read(sequenceOf(
            EDIT_BASE, "e\t$first\tвсе\tВсё\t0", "e\t$second\tВсё\tдва слова\t0",
            "e\tbad\tВсё\tлишнее\t0", "e\t$second\tВсё\tлишнее\t2",
            "h\t$first\t\tвсе\t3\t1", "h\t$first\t\tвсё\t1\t2", "h\t$first",
        ))
        assertNull(words.learned("все"))
        assertEquals(Learned("Всё", 4, 2), words.learned("всё"))
        assertFalse(buildString { words.write(this) }.contains(second))
    }
}
