package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalWordsTest {
    private val personal = PersonalWords()

    @Test
    fun `learning counts a word and its pair, in any case, with the last time`() {
        personal.learn("привет", previous = "Ну", time = 1)
        personal.learn("Привет", previous = "ну", time = 2)

        assertEquals(Learned("Привет", 2, 2), personal.learned("привет"))
        assertEquals(listOf(Learned("Привет", 2, 2)), personal.after("НУ"))
        assertTrue(personal.version > 0)
    }

    @Test
    fun `a snapshot keeps the words, the removed ones and the pairs after one word, and stays as it was`() {
        personal.learn("привет", previous = "ну", time = 1)
        personal.learn("пока", previous = "ну", time = 1)
        personal.learn("мир", previous = "привет", time = 1)
        personal.remove("блин", time = 1)

        val snapshot = PersonalWords.snapshot(personal, listOf("Ну"))
        personal.learn("новое", previous = "ну", time = 2)

        assertEquals(setOf("привет", "пока", "мир"), snapshot.all().map { it.spelling }.toSet())
        assertEquals(setOf("привет", "пока"), snapshot.after("ну").map { it.spelling }.toSet())
        assertEquals(emptyList<Learned>(), snapshot.after("привет"))
        assertTrue(snapshot.isRemoved("блин"))
        assertEquals(emptyList<Learned>(), PersonalWords.snapshot(personal, emptyList()).after("ну"))
    }

    @Test
    fun `a snapshot keeps the next words after each end of the context`() {
        personal.learn("дела", listOf("ну", "как"), time = 1)
        personal.learn("дела", listOf("а", "как"), time = 1)

        val snapshot = PersonalWords.snapshot(personal, listOf("ну", "как"))

        assertEquals(listOf(Learned("дела", 2, 1)), snapshot.after(listOf("как")))
        assertEquals(listOf(Learned("дела", 1, 1)), snapshot.after(listOf("ну", "как")))
        assertEquals(emptyList<Learned>(), snapshot.after(listOf("а", "как")))
    }

    @Test
    fun `a word is learned after each end of its context, up to the longest`() {
        personal.learn("дела", listOf("Ну", "а", "как", "твои"), time = 3)

        assertEquals(listOf(Learned("дела", 1, 3)), personal.after(listOf("твои")))
        assertEquals(listOf(Learned("дела", 1, 3)), personal.after(listOf("КАК", "твои")))
        assertEquals(listOf(Learned("дела", 1, 3)), personal.after(listOf("а", "как", "твои")))
        assertEquals(emptyList<Learned>(), personal.after(listOf("ну", "а", "как", "твои")))
        assertEquals(emptyList<Learned>(), personal.after(listOf("а", "твои")))
    }

    @Test
    fun `unlearning takes a word back after every end of its context`() {
        personal.learn("дела", listOf("как", "твои"), time = 1)
        personal.learn("дела", listOf("а", "твои"), time = 2)

        personal.unlearn("дела", listOf("как", "твои"))

        assertEquals(listOf(Learned("дела", 1, 2)), personal.after(listOf("твои")))
        assertEquals(emptyList<Learned>(), personal.after(listOf("как", "твои")))
        assertEquals(listOf(Learned("дела", 1, 2)), personal.after(listOf("а", "твои")))
    }

    @Test
    fun `seen tells for each length whether the context came before, and with the word`() {
        personal.learn("дела", listOf("ну", "как", "твои"), time = 1)
        personal.learn("планы", listOf("а", "мои", "твои"), time = 1)

        assertEquals(
            listOf(
                ContextSeen(1, seen = true, withWord = true),
                ContextSeen(2, seen = true, withWord = false),
                ContextSeen(3, seen = true, withWord = false),
            ),
            personal.seen("Планы", listOf("ну", "как", "твои")),
        )
        assertEquals(
            listOf(
                ContextSeen(1, seen = true, withWord = true),
                ContextSeen(2, seen = true, withWord = true),
                ContextSeen(3, seen = false, withWord = false),
            ),
            personal.seen("дела", listOf("да", "как", "твои")),
        )
        assertEquals(emptyList<ContextSeen>(), personal.seen("дела", emptyList()))
    }

    @Test
    fun `a forgotten word goes after contexts of every length`() {
        personal.learn("дела", listOf("как", "твои"), time = 1)

        personal.forget("дела", time = 2)

        assertEquals(emptyList<Learned>(), personal.after(listOf("как", "твои")))
        assertEquals(emptyList<ContextSeen>(), personal.seen("дела", listOf("как", "твои")).filter { it.seen })
    }

    @Test
    fun `unlearning takes back one use and forgets a word at none`() {
        personal.learn("пртвет", previous = "ну", time = 1)
        personal.learn("пртвет", previous = null, time = 2)

        personal.unlearn("пртвет", previous = "ну")
        assertEquals(Learned("пртвет", 1, 2), personal.learned("пртвет"))
        assertEquals(emptyList<Learned>(), personal.after("ну"))

        personal.unlearn("пртвет", previous = null)
        assertNull(personal.learned("пртвет"))
        personal.unlearn("пртвет", previous = "ну")
        assertNull(personal.learned("пртвет"))
    }

    @Test
    fun `a next word is learned after another context alone`() {
        personal.learn("мир", previous = "пртвет", time = 1)

        personal.learnNext("мир", listOf("ну", "Привет"), time = 2)

        assertEquals(listOf(Learned("мир", 1, 1)), personal.after("пртвет"))
        assertEquals(listOf(Learned("мир", 1, 2)), personal.after("привет"))
        assertEquals(listOf(Learned("мир", 1, 2)), personal.after(listOf("ну", "привет")))
        assertEquals(Learned("мир", 1, 1), personal.learned("мир"))
    }

    @Test
    fun `a removed word is forgotten with its pairs until typed again`() {
        personal.learn("тадалафил", previous = "купить", time = 1)

        personal.remove("Тадалафил", time = 2)
        assertTrue(personal.isRemoved("тадалафил"))
        assertNull(personal.learned("тадалафил"))
        assertEquals(emptyList<Learned>(), personal.after("купить"))
        assertEquals(1, personal.removedCount)

        personal.learn("тадалафил", previous = null, time = 3)
        assertFalse(personal.isRemoved("тадалафил"))
    }

    @Test
    fun `a forgotten word goes with its pairs but is not kept out of suggestions`() {
        personal.learn("привет", previous = "ну", time = 1)

        personal.forget("Привет", time = 2)

        assertNull(personal.learned("привет"))
        assertEquals(emptyList<Learned>(), personal.after("ну"))
        assertFalse(personal.isRemoved("привет"))
        assertEquals(0, personal.removedCount)
    }

    @Test
    fun `a restored word comes back with its contexts and field starts`() {
        personal.learn("Дела", listOf("как", "твои"), time = 1, kept = true)
        personal.learn("дела", listOf("ну"), time = 2)
        personal.starts.learn("Дела", time = 3)
        val forgotten = personal.forget("дела", time = 4)
        val version = personal.version

        forgotten.restore()

        assertEquals(Learned("дела", 2, 2, kept = 1), personal.learned("Дела"))
        assertEquals(listOf(Learned("Дела", 1, 1)), personal.after(listOf("как", "твои")))
        assertEquals(listOf(Learned("Дела", 1, 1)), personal.after("твои"))
        assertEquals(listOf(Learned("дела", 1, 2)), personal.after("ну"))
        assertEquals(listOf(Learned("Дела", 1, 3)), personal.starts.all())
        assertTrue(personal.version > version)
    }

    @Test
    fun `a restored word adds up with what was typed of it since`() {
        personal.learn("дела", previous = "как", time = 1)
        personal.learn("дела", previous = "как", time = 2)
        val forgotten = personal.forget("дела", time = 3)
        personal.learn("Дела", previous = "как", time = 5)

        forgotten.restore()

        assertEquals(Learned("Дела", 3, 5), personal.learned("дела"))
        assertEquals(listOf(Learned("Дела", 3, 5)), personal.after("как"))
    }

    @Test
    fun `completions are the learned words with the start`() {
        personal.learn("привет", previous = null, time = 1)
        personal.learn("пока", previous = null, time = 1)

        assertEquals(listOf("привет"), personal.completions("Пр").map { it.spelling })
        assertEquals(2, personal.all().size)
    }

    @Test
    fun `a learned word with a digit completes only once typed twice`() {
        personal.learn("топ-3", previous = null, time = 1)
        assertEquals(emptyList<String>(), personal.completions("топ").map { it.spelling })

        personal.learn("топ-3", previous = null, time = 2)
        assertEquals(listOf("топ-3"), personal.completions("топ").map { it.spelling })
    }

    @Test
    fun `what is written reads back the same, and unknown lines are skipped`() {
        personal.learn("привет", previous = "ну", time = 5)
        personal.learn("Вася", previous = null, time = 6)
        personal.remove("тадалафил", time = 7)
        val text = StringBuilder().also { personal.write(it) }.toString()

        val read = PersonalWords.read((text + "x\tнечто\nw\tбитое\n").lineSequence())

        assertEquals(personal.all().toSet(), read.all().toSet())
        assertEquals(personal.after("ну"), read.after("ну"))
        assertTrue(read.isRemoved("тадалафил"))
        assertNull(read.learned("битое"))
    }

    @Test
    fun `next words after longer contexts read back, and pairs of an older file read as one-word contexts`() {
        personal.learn("дела", listOf("Ну", "как", "твои"), time = 5)
        val text = buildString { personal.write(this) }

        val read = PersonalWords.read(text.lineSequence())
        val older = PersonalWords.read(sequenceOf("p\tну\tпривет\t3\t4", "n\tа б в г\tслово\t1\t1"))

        assertEquals(listOf(Learned("дела", 1, 5)), read.after(listOf("ну", "как", "твои")))
        assertEquals(listOf(Learned("дела", 1, 5)), read.after(listOf("твои")))
        assertFalse(text.lines().any { it.startsWith("p\t") })
        assertEquals(listOf(Learned("привет", 3, 4)), older.after("ну"))
        assertEquals(emptyList<ContextSeen>(), older.seen("слово", listOf("б", "в", "г")).filter { it.seen })
    }

    @Test
    fun `the words fields began with are counted in any case, and taken back one use at a time`() {
        personal.starts.learn("привет", time = 1)
        personal.starts.learn("Привет", time = 2)
        personal.starts.learn("да", time = 3)
        val version = personal.version

        personal.starts.unlearn("да")

        assertEquals(listOf(Learned("Привет", 2, 2)), personal.starts.all())
        assertTrue(personal.version > version)
        // A start is no word learned, nor a pair: it counts apart from them.
        assertNull(personal.learned("привет"))
    }

    @Test
    fun `the words fields began with read back, are kept in a snapshot, and go with a forgotten word`() {
        personal.starts.learn("привет", time = 5)
        personal.starts.learn("слушай", time = 6)
        val text = buildString { personal.write(this) }

        val read = PersonalWords.read(text.lineSequence())
        val snapshot = PersonalWords.snapshot(personal, emptyList())
        personal.remove("слушай", time = 7)

        assertEquals(setOf(Learned("привет", 1, 5), Learned("слушай", 1, 6)), read.starts.all().toSet())
        assertEquals(read.starts.all().toSet(), snapshot.starts.all().toSet())
        assertEquals(listOf(Learned("привет", 1, 5)), personal.starts.all())
    }

    @Test
    fun `the removed words are listed in lowercase until typed again`() {
        personal.remove("Тадалафил", time = 1)
        personal.remove("спам", time = 2)
        personal.learn("спам", previous = null, time = 3)
        assertEquals(setOf("тадалафил"), personal.removedWords)
    }

    @Test
    fun `a word kept as typed counts its keepings, and taking one back takes the keeping back`() {
        personal.learn("кринж", previous = null, time = 1, kept = true)
        personal.learn("кринж", previous = null, time = 2)
        assertEquals(Learned("кринж", 2, 2, kept = 1), personal.learned("кринж"))

        personal.unlearn("кринж", previous = null, kept = true)
        assertEquals(Learned("кринж", 1, 2, kept = 0), personal.learned("кринж"))
    }

    @Test
    fun `keepings are saved, and a file from before them reads with none`() {
        personal.learn("кринж", previous = null, time = 1, kept = true)
        val text = buildString { personal.write(this) }

        assertEquals(1, PersonalWords.read(text.lineSequence()).learned("кринж")?.kept)
        assertEquals(Learned("кринж", 3, 5), PersonalWords.read(sequenceOf("w\tкринж\t3\t5")).learned("кринж"))
    }

    @Test
    fun `beyond the dictionary are the words it does not spell exactly so`() {
        val dictionary = Dictionary.parse(sequenceOf("привет\t30", "всё\t20", "api\t10"))
        val learned = listOf(
            Learned("привет", 5, 1),
            Learned("API", 4, 1),
            Learned("все", 3, 1),
            Learned("кринж", 2, 1),
        )

        assertEquals(listOf("API", "все", "кринж"), beyondDictionary(learned, dictionary).map { it.spelling })
    }
}
