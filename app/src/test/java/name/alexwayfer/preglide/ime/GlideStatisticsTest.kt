package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.glide.GlidedWord
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GlideStatisticsTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val file get() = File(folder.root, "statistics/totals")
    private val failures = mutableListOf<String>()
    private val store get() = GlideStatisticsStore({ file }) { stage, _ -> failures += stage }
    private val scope = CoroutineScope(testDispatchers.now)

    private fun statistics() = GlideStatistics(store, scope, testDispatchers.now) {}

    private fun slip(): GlideSlip {
        val places = KeyboardLayout.of(Language.Russian).glidePlaces
        val score = GlidedWord("кот", 100, null, 0, 2f)
        return GlideSlip(1, 0, Language.Russian, "кот", "кот", listOf("кот"), listOf(score),
            Glide(listOf(places.getValue('к').center, places.getValue('т').center)))
    }

    @Test
    fun totalsSurviveRestartAndReportsDoNotClearThem() = runBlocking {
        val first = statistics()
        first.record(slip())
        val report = first.lines()
        assertTrue(report.first().contains("kept 1"))
        assertEquals(report, first.lines())
        assertEquals(report, statistics().lines())
        val restarted = statistics()
        restarted.record(slip().erased())
        assertTrue(restarted.lines().first().contains("erased 1, of 2"))
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("кот"))
    }

    @Test
    fun corruptFilesAreReportedAndACompleteNewSnapshotCanBeSaved() = runBlocking {
        file.parentFile!!.mkdirs()
        file.writeText("broken")
        val statistics = statistics()
        assertEquals(emptyList<String>(), statistics.lines())
        assertEquals(listOf("read"), failures)
        statistics.record(slip())
        assertEquals(statistics.lines(), statistics().lines())
    }

    @Test
    fun failedWritesAreReportedAndTheInMemoryTotalsAreRetained() = runBlocking {
        file.parentFile!!.writeText("blocked")
        val statistics = statistics()
        statistics.record(slip())
        assertTrue(statistics.lines().first().contains("kept 1"))
        assertEquals(listOf("write"), failures)
        file.parentFile!!.delete()
        statistics.record(slip().erased())
        assertEquals(statistics.lines(), statistics().lines())
    }

    @Test
    fun interruptedSnapshotDoesNotReplaceTheSavedTotals() = runBlocking {
        val statistics = statistics()
        statistics.record(slip())
        val report = statistics.lines()
        File(file.path + ".next").writeText("unfinished")
        assertEquals(report, statistics().lines())
    }

    @Test
    fun reportsWaitForQueuedBackgroundWritesAndUnknownChoicesAreNotCounted() = runBlocking {
        val background = GlideStatistics(store, scope, testDispatchers.io.limitedParallelism(1)) {}
        background.record(slip().swappedFor("unknown"))
        assertEquals(emptyList<String>(), background.lines())
        repeat(30) { background.record(slip()) }
        val report = background.lines()
        assertTrue(report.first().contains("kept 30"))
        assertEquals(report, statistics().lines())
    }
}
