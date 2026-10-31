package name.alexwayfer.preglide.ui.theme

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class AppColorReaderTest {
    /** A dispatcher that runs what it is given only when told to, so a test sees the time a reading takes. */
    private class Queue : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private val io = Queue()
    private val reads = mutableListOf<String>()
    private val colors = mapOf(GMAIL to RED, TELEGRAM to BLUE)
    private val reader = AppColorReader(OWN, CoroutineScope(testDispatchers.now), io) { host ->
        reads += host
        colors[host]
    }

    private fun open(host: String?, enabled: Boolean = true) {
        reader.follow(host, enabled)
        io.runAll()
    }

    @Test
    fun anAppsColorComesOnceRead() {
        reader.follow(GMAIL, enabled = true)
        assertNull(reader.seed.value)

        io.runAll()
        assertEquals(RED, reader.seed.value)
    }

    @Test
    fun theLastAppsColorGivesWayWhileTheNextOneIsRead() {
        open(GMAIL)

        reader.follow(TELEGRAM, enabled = true)
        assertNull(reader.seed.value)

        io.runAll()
        assertEquals(BLUE, reader.seed.value)
    }

    @Test
    fun anAppReadBeforeShowsAtOnceWithoutAReading() {
        open(GMAIL)
        open(TELEGRAM)

        reader.follow(GMAIL, enabled = true)

        assertEquals(RED, reader.seed.value)
        assertEquals(listOf(GMAIL, TELEGRAM), reads)
    }

    @Test
    fun anAppOfNoColorIsReadOnce() {
        open(PLAIN)
        open(GMAIL)
        open(PLAIN)

        assertNull(reader.seed.value)
        assertEquals(listOf(PLAIN, GMAIL), reads)
    }

    @Test
    fun aReadingTheNextFieldOvertakesIsDropped() {
        reader.follow(GMAIL, enabled = true)
        open(TELEGRAM)

        assertEquals(BLUE, reader.seed.value)
    }

    @Test
    fun theKeyboardsOwnAppKeepsTheUsersColors() {
        open(GMAIL)
        open(OWN)

        assertNull(reader.seed.value)
        assertEquals(listOf(GMAIL), reads)
    }

    @Test
    fun noColorIsReadWhileTurnedOffOrWithoutAnApp() {
        open(GMAIL)
        open(TELEGRAM, enabled = false)
        assertNull(reader.seed.value)

        open(null)
        assertNull(reader.seed.value)
        assertEquals(listOf(GMAIL), reads)
    }

    private companion object {
        const val OWN = "name.alexwayfer.preglide"
        const val GMAIL = "com.google.android.gm"
        const val TELEGRAM = "org.telegram.messenger"
        const val PLAIN = "com.example.plain"
        const val RED = 0xFFDB4437.toInt()
        const val BLUE = 0xFF2AABEE.toInt()
    }
}
