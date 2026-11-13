package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Learned
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.learn
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PersonalDataTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store = PersonalStore { File(folder.root, "personal") }
    private val now = testDispatchers.now
    private val personal = PersonalData(store, CoroutineScope(now), now)
    private val russian = personalName(Language.Russian)

    @Test
    fun aNameLoadsWithWhatTheOtherDevicesLearnedUnderIt() {
        val phone = PersonalWords().apply { learn("привет", previous = null, time = 9) }
        val withOthers = PersonalData(store, CoroutineScope(now), now) { name ->
            if (name == russian) mapOf("phone" to phone) else emptyMap()
        }
        store.write(russian, "w\tпривет\t2\t5\t0\n")

        assertEquals(Learned("привет", 3, 9), withOthers.load(russian).learned("привет"))
        assertEquals(emptyList<Learned>(), withOthers.load(EMAILS).all())
    }

    @Test
    fun flushingWritesWhatChangedBeforeItReturns() = runBlocking {
        val writes = PersonalData(store, CoroutineScope(now), testDispatchers.io.limitedParallelism(1))
        writes.load(russian).learn("привет", previous = null, time = 1)
        writes.changed(russian)

        writes.flush()

        assertEquals(listOf(Learned("привет", 1, 1)), store.read(russian).all())
    }

    @Test
    fun refreshingMergesWhatTheOtherDevicesLearnedSinceIntoTheLoadedWords() = runBlocking {
        var devices = emptyMap<String, PersonalWords>()
        val withOthers = PersonalData(store, CoroutineScope(now), now) { devices }
        val words = withOthers.load(russian)
        val changes = withOthers.changes.value

        devices = mapOf("pc" to PersonalWords().apply { learn("котлин", previous = null, time = 9) })
        withOthers.refreshOthers(listOf(russian), downloadsChanged = true)

        assertEquals(Learned("котлин", 1, 9), words.learned("котлин"))
        assertTrue(withOthers.changes.value > changes)
    }

    @Test
    fun reloadingTakesTheStoredWordsIntoTheSameLoadedObjects() {
        val words = personal.load(russian)
        words.learn("старое", previous = null, time = 1)
        personal.changed(russian)
        store.write(russian, "w\tновое\t1\t2\t0\n")

        personal.reload()

        assertSame(words, personal.load(russian))
        assertEquals(listOf("новое"), words.all().map { it.spelling })
        personal.save()
        assertEquals(listOf("новое"), store.read(russian).all().map { it.spelling })
    }

    @Test
    fun eachNameLoadsOnceSoTheKeyboardAndTheScreenChangeTheSameWords() {
        assertSame(personal.load(russian), personal.load(russian))
    }

    @Test
    fun aWordForgottenOnTheScreenStaysForgottenWhenTheKeyboardSavesLater() {
        val keyboard = personal.load(russian)
        keyboard.learn("превет", previous = null, time = 1)
        personal.changed(russian)

        personal.load(russian).forget("превет", time = 2)
        personal.changed(russian)
        personal.save()

        assertNull(store.read(russian).learned("превет"))
    }

    @Test
    fun onlyWhatChangedIsSaved() {
        personal.load(russian).learn("привет", previous = null, time = 1)
        personal.load(personalName(Language.English)).learn("hello", previous = null, time = 1)
        personal.changed(russian)

        personal.save()

        assertEquals(listOf(Learned("привет", 1, 1)), store.read(russian).all())
        assertEquals(emptyList<Learned>(), store.read(personalName(Language.English)).all())
    }

    @Test
    fun eachChangeIsCountedForTheScreen() {
        val before = personal.changes.value

        personal.changed(russian)
        personal.changed(EMAILS)

        assertEquals(before + 2, personal.changes.value)
    }

    @Test
    fun aChangeOfWhatWasNeverLoadedSavesNothing() {
        personal.changed(russian)

        personal.save()

        assertEquals(false, File(folder.root, "personal").exists())
    }
}
