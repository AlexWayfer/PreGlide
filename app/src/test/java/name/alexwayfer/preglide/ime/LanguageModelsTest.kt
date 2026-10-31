package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class LanguageModelsTest {
    private val reads = mutableListOf<Language>()

    // A load waits in the queue until the test runs it.
    private val queued = ArrayDeque<Runnable>()
    private val later = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued += block
        }
    }

    private val models = LanguageModels(CoroutineScope(testDispatchers.now), later) {
        reads += it
        LanguageModel(Dictionary.parse(sequenceOf("${it.tag}\t1")))
    }

    @Test
    fun awaitingAModelLoadsItAndWaitsForIt() = runBlocking {
        val waiting = async(testDispatchers.now) { models.await(Language.Russian) }
        assertFalse(waiting.isCompleted)

        while (queued.isNotEmpty()) queued.removeFirst().run()

        assertEquals("ru", waiting.await().dictionary.atLine(0))
    }

    @Test
    fun awaitingALoadedModelGivesItAtOnceAndReadsItOnce() = runBlocking {
        models.load(listOf(Language.English))
        while (queued.isNotEmpty()) queued.removeFirst().run()

        assertSame(models[Language.English], models.await(Language.English))
        assertEquals(listOf(Language.English), reads)
    }

    @Test
    fun aModelAskedForBeforeItLoadedStartsLoadingAndComesOnceLoaded() {
        assertNull(models.loadedOrLoading(Language.Russian))
        while (queued.isNotEmpty()) queued.removeFirst().run()

        assertSame(models[Language.Russian], models.loadedOrLoading(Language.Russian))
        assertEquals(listOf(Language.Russian), reads)
    }

    @Test
    fun theDictionaryLoadLineTellsTheWordsAndMillisecondsAlone() {
        assertEquals(
            "ru dictionary of 310762 words read in 412 ms, its tree built in 95 ms",
            dictionaryLoadLine(Language.Russian, 310_762, readNanos = 412_900_000, treeNanos = 95_100_000),
        )
    }
}
