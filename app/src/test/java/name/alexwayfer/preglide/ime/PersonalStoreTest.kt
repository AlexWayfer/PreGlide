package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Learned
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.learn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PersonalStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store = PersonalStore { File(folder.root, "personal") }

    @Test
    fun aLanguageNotLearnedInYetHasNothing() {
        assertEquals(emptyList<Learned>(), store.read(personalName(Language.Russian)).all())
    }

    @Test
    fun eachLanguageReadsBackWhatWasWritten() {
        val russian = PersonalWords().apply { learn("привет", previous = null, time = 1) }
        val english = PersonalWords().apply { learn("hello", previous = null, time = 2) }

        store.write(personalName(Language.Russian), buildString { russian.write(this) })
        store.write(personalName(Language.English), buildString { english.write(this) })

        assertEquals(russian.all(), store.read(personalName(Language.Russian)).all())
        assertEquals(english.all(), store.read(personalName(Language.English)).all())
    }

    @Test
    fun aSaveReplacesTheFileAndLeavesNoPartialOne() {
        store.write(personalName(Language.Russian), "w\tстарое\t1\t1\n")

        store.write(personalName(Language.Russian), "w\tновое\t1\t2\n")

        assertEquals(listOf("новое"), store.read(personalName(Language.Russian)).all().map { it.spelling })
        assertFalse(File(folder.root, "personal/ru.tsv.part").exists())
    }
}
