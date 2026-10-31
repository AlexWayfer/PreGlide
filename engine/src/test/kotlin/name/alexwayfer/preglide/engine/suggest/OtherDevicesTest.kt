package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherDevicesTest {
    private val personal = PersonalWords()

    private fun device(vararg lines: String) = PersonalWords.read(lines.asSequence())

    /** [words] as the devices "d0", "d1" and on. */
    private fun devices(vararg words: PersonalWords) = words.withIndex().associate { (index, it) -> "d$index" to it }

    private fun written(words: PersonalWords) = buildString { words.write(this) }.lines().filter { it.isNotEmpty() }

    @Test
    fun `each device's words show apart, with its own uses, past what any device forgot since`() {
        personal.learn("Привет", previous = null, time = 5)
        personal.learn("котлин", previous = null, time = 6)
        personal.mergeOthers(
            mapOf(
                "pc" to device("w\tПРИВЕТ\t2\t9\t0", "w\tслово\t3\t50\t0", "w\tджава\t1\t200\t0"),
                "tablet" to device("f\tслово\t100"),
            ),
        )

        assertEquals(setOf("pc", "tablet"), personal.deviceIds())
        assertEquals(listOf(Learned("ПРИВЕТ", 2, 9)), personal.allOn("pc").filter { it.spelling == "ПРИВЕТ" })
        assertEquals(setOf("ПРИВЕТ", "джава"), personal.allOn("pc").map { it.spelling }.toSet())
        assertEquals(setOf("Привет", "котлин"), personal.allOn(null).map { it.spelling }.toSet())
        assertEquals(emptyList<Learned>(), personal.allOn("tablet"))
        assertEquals(emptyList<Learned>(), personal.allOn("gone"))
    }

    @Test
    fun `without other devices there are none to show apart`() {
        personal.learn("привет", previous = null, time = 5)

        assertEquals(emptySet<String>(), personal.deviceIds())
        assertEquals(listOf("привет"), personal.allOn(null).map { it.spelling })
    }

    @Test
    fun `the uses of a word on every device add up`() {
        personal.learn("Привет", previous = null, time = 5)
        personal.mergeOthers(devices(device("w\tпривет\t2\t9\t1"), device("w\tПРИВЕТ\t1\t3\t0")))

        assertEquals(Learned("Привет", 4, 9, 1), personal.learned("привет"))
        assertEquals(listOf(Learned("Привет", 4, 9, 1)), personal.all())
    }

    @Test
    fun `the words of other devices alone show too`() {
        personal.mergeOthers(devices(device("w\tкотлин\t2\t9\t0")))

        assertEquals(listOf("котлин"), personal.all().map { it.spelling })
        assertEquals(listOf("котлин"), personal.completions("кот").map { it.spelling })
    }

    @Test
    fun `next words after a context add up across devices`() {
        personal.learn("дела", listOf("как"), time = 5)
        personal.mergeOthers(devices(device("n\tкак\tдела\t2\t7", "n\tкак\tты\t1\t3")))

        assertEquals(
            setOf(Learned("дела", 3, 7), Learned("ты", 1, 3)),
            personal.after(listOf("Как")).toSet(),
        )
    }

    @Test
    fun `field starts add up across devices`() {
        repeat(2) { personal.starts.learn("Привет", time = 5) }
        personal.mergeOthers(devices(device("s\tпривет\t1\t7")))

        assertEquals(listOf("Привет"), fieldStartWords(personal, count = 3))
    }

    @Test
    fun `only what this device learned is written`() {
        personal.learn("привет", previous = null, time = 5)
        personal.mergeOthers(devices(device("w\tпривет\t2\t9\t0", "w\tкотлин\t1\t9\t0", "r\tблин\t9")))

        assertEquals(listOf("w\tпривет\t1\t5\t0"), written(personal))
    }

    @Test
    fun `a word forgotten on another device drops the older uses here`() {
        personal.learn("слово", listOf("одно"), time = 50)
        personal.starts.learn("слово", time = 50)
        personal.mergeOthers(devices(device("f\tслово\t100")))

        assertNull(personal.learned("слово"))
        assertEquals(emptyList<Learned>(), personal.after(listOf("одно")))
        assertEquals(emptyList<Learned>(), personal.starts.all())
        assertEquals(emptyList<String>(), written(personal))
        assertFalse(personal.isRemoved("слово"))
    }

    @Test
    fun `a use after another device forgot the word stays`() {
        personal.learn("слово", previous = null, time = 150)
        personal.mergeOthers(devices(device("f\tслово\t100")))

        assertEquals(Learned("слово", 1, 150), personal.learned("слово"))
    }

    @Test
    fun `a word forgotten here hides the older uses of other devices, not the later ones`() {
        personal.learn("слово", previous = null, time = 10)
        personal.forget("слово", time = 100)
        personal.mergeOthers(devices(device("w\tслово\t3\t50\t0"), device("w\tслово\t2\t150\t0")))

        assertEquals(Learned("слово", 2, 150), personal.learned("слово"))
        assertEquals(listOf("f\tслово\t100"), written(personal))
    }

    @Test
    fun `devices drop the older uses another device forgot among themselves`() {
        personal.mergeOthers(devices(device("w\tслово\t3\t50\t0"), device("f\tслово\t100")))

        assertNull(personal.learned("слово"))
        assertEquals(emptyList<Learned>(), personal.all())
    }

    @Test
    fun `a word removed on another device stays out of suggestions until typed after`() {
        personal.mergeOthers(devices(device("r\tблин\t100")))

        assertTrue(personal.isRemoved("Блин"))
        assertEquals(setOf("блин"), personal.removedWords)
        assertEquals(1, personal.removedCount)

        personal.learn("блин", previous = null, time = 150)

        assertFalse(personal.isRemoved("блин"))
        assertEquals(0, personal.removedCount)
    }

    @Test
    fun `a word removed here comes back once another device typed it after`() {
        personal.remove("блин", time = 100)
        personal.mergeOthers(devices(device("w\tблин\t1\t150\t0")))

        assertFalse(personal.isRemoved("блин"))
        assertEquals(Learned("блин", 1, 150), personal.learned("блин"))
    }

    @Test
    fun `typing a removed word again keeps the time it was removed as forgotten`() {
        personal.remove("блин", time = 100)
        personal.learn("блин", previous = null, time = 150)
        personal.mergeOthers(devices(device("w\tблин\t4\t50\t0")))

        assertEquals(Learned("блин", 1, 150), personal.learned("блин"))
        assertEquals(setOf("w\tблин\t1\t150\t0", "f\tблин\t100"), written(personal).toSet())
    }

    @Test
    fun `undoing a forget brings back the uses of other devices too`() {
        personal.learn("слово", previous = null, time = 10)
        personal.mergeOthers(devices(device("w\tслово\t2\t50\t0")))

        val forgotten = personal.forget("слово", time = 100)
        assertNull(personal.learned("слово"))

        forgotten.restore()

        assertEquals(Learned("слово", 3, 50), personal.learned("слово"))
        assertEquals(listOf("w\tслово\t1\t10\t0"), written(personal))
    }

    @Test
    fun `forgotten words are read back as written`() {
        personal.forget("слово", time = 100)

        val back = PersonalWords.read(written(personal).asSequence())
        back.mergeOthers(devices(device("w\tслово\t1\t50\t0")))

        assertNull(back.learned("слово"))
    }

    @Test
    fun `a snapshot keeps the words of other devices`() {
        personal.mergeOthers(devices(device("n\tкак\tдела\t2\t7", "r\tблин\t9")))

        val snapshot = PersonalWords.snapshot(personal, listOf("как"))

        assertEquals(listOf(Learned("дела", 2, 7)), snapshot.after(listOf("как")))
        assertTrue(snapshot.isRemoved("блин"))
    }

    @Test
    fun `replacing takes all of the other words in place, with their devices`() {
        personal.learn("старое", listOf("одно"), time = 1)
        personal.remove("блин", time = 1)
        val before = personal.version
        val other = device("w\tновое\t2\t5\t0", "n\tкак\tдела\t1\t5", "s\tпривет\t1\t5", "f\tслово\t5")
        other.mergeOthers(devices(device("w\tкотлин\t1\t9\t0")))

        personal.replaceWith(other)

        assertEquals(setOf("новое", "котлин"), personal.all().map { it.spelling }.toSet())
        assertEquals(emptyList<Learned>(), personal.after(listOf("одно")))
        assertEquals(listOf(Learned("дела", 1, 5)), personal.after(listOf("как")))
        assertEquals(listOf("привет"), personal.starts.all().map { it.spelling })
        assertFalse(personal.isRemoved("блин"))
        assertEquals(written(other).toSet(), written(personal).toSet())
        assertTrue(personal.version > before)
    }

    @Test
    fun `merging other devices again replaces what came before`() {
        personal.mergeOthers(devices(device("w\tкотлин\t2\t9\t0")))
        val before = personal.version
        personal.mergeOthers(devices(device("w\tджава\t1\t9\t0")))

        assertEquals(listOf("джава"), personal.all().map { it.spelling })
        assertTrue(personal.version > before)
    }
}
