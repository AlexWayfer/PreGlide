package name.alexwayfer.preglide.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppLogFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun dir() = File(folder.root, "logs")

    @Test
    fun linesAppendInOrderAndTheFolderIsCreated() {
        val log = AppLogFile(dir(), maxBytes = 1_000)
        log.append("one")
        log.append("two")
        assertEquals("one\ntwo\n", File(dir(), "app-log.txt").readText())
        assertFalse(File(dir(), "app-log.1.txt").exists())
    }

    @Test
    fun aFullFileBecomesThePreviousOneBeforeTheNextLine() {
        val log = AppLogFile(dir(), maxBytes = 8)
        log.append("1234567")
        log.append("next")
        assertEquals("1234567\n", File(dir(), "app-log.1.txt").readText())
        assertEquals("next\n", File(dir(), "app-log.txt").readText())
    }

    @Test
    fun onlyOnePreviousFileIsKept() {
        val log = AppLogFile(dir(), maxBytes = 4)
        log.append("aaaa")
        log.append("bbbb")
        log.append("cccc")
        assertEquals("bbbb\n", File(dir(), "app-log.1.txt").readText())
        assertEquals("cccc\n", File(dir(), "app-log.txt").readText())
    }

    @Test
    fun readGivesThePreviousFileBeforeTheCurrentOne() {
        val log = AppLogFile(dir(), maxBytes = 4)
        log.append("aaaa")
        log.append("bbbb")
        assertEquals(listOf("aaaa", "bbbb"), log.read())
    }

    @Test
    fun readOfAnEmptyLogIsEmpty() {
        assertEquals(emptyList<String>(), AppLogFile(dir()).read())
    }
}
