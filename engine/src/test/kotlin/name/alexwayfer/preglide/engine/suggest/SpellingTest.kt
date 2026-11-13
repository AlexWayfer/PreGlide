package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpellingTest {
    private val dictionary = Dictionary.parse(
        sequenceOf(
            "иронично\t1825",
            "иронии\t900",
            "привет\t100",
            "приветы\t20",
            "пират\t30",
            "Москва\t70",
            "ещё\t500",
            "онлайн\t300",
            "магазин\t400",
            "кот\t600",
            "кит\t200",
            "крот\t50",
        ),
    )

    private fun spelling(
        learned: List<Learned> = emptyList(),
        removed: Set<String> = emptySet(),
        userWords: Set<String> = emptySet(),
    ) = Spelling(Language.Russian, dictionary, learned, removed, userWords)

    @Test
    fun `a dictionary word is spelled right in any case and with yo typed as e`() {
        assertNull(spelling().check("иронично", 5))
        assertNull(spelling().check("Иронично", 5))
        assertNull(spelling().check("ЕЩЕ", 5))
        assertNull(spelling().check("москва", 5))
    }

    @Test
    fun `a misspelled word gets the nearest corrections first, then the most frequent`() {
        assertEquals(listOf("привет", "пират", "приветы"), spelling().check("привт", 5))
        assertEquals(listOf("привет"), spelling().check("привт", 1))
    }

    @Test
    fun `corrections are cased like the misspelled word`() {
        assertEquals(listOf("Привет", "Пират", "Приветы"), spelling().check("Привт", 5))
        assertEquals(listOf("Москва"), spelling().check("моква", 5))
    }

    @Test
    fun `a short word gets corrections only one edit away`() {
        assertEquals(listOf("кот", "крот"), spelling().check("ктот", 5))
        assertEquals(listOf("кот", "кит"), spelling().check("кат", 5))
    }

    @Test
    fun `a word the user typed often enough is spelled right`() {
        val once = listOf(Learned("кринж", 1, 0))
        val twice = listOf(Learned("кринж", TRUSTED_USES, 0))
        assertEquals(emptyList<String>(), spelling(learned = once).check("кринж", 5))
        assertNull(spelling(learned = twice).check("Кринж", 5))
    }

    @Test
    fun `a word the user kept as typed once is spelled right`() {
        assertNull(spelling(learned = listOf(Learned("кринж", 1, 0, kept = 1))).check("кринж", 5))
    }

    @Test
    fun `a model knows its dictionary words and the user's trusted words`() {
        val model = LanguageModel(dictionary)
        assertTrue(model.knows("Привет", Language.Russian))
        assertTrue(model.knows("YouTube", Language.Russian))
        assertFalse(model.knows("кринж", Language.Russian))

        model.personal.learn("кринж", previous = null, time = 0, kept = true)
        assertTrue(model.knows("кринж", Language.Russian))
    }

    @Test
    fun `a word the user added to the system dictionary is spelled right`() {
        assertNull(spelling(userWords = setOf("Кринж")).check("кринж", 5))
    }

    @Test
    fun `the user's own words are corrections too`() {
        val learned = listOf(Learned("кринжово", TRUSTED_USES, 0))
        assertEquals(listOf("кринжово"), spelling(learned = learned).check("крнжово", 5))
        assertEquals(listOf("кринжово"), spelling(userWords = setOf("кринжово")).check("крнжово", 5))
    }

    @Test
    fun `a removed word is no correction`() {
        assertEquals(listOf("пират", "приветы"), spelling(removed = setOf("привет")).check("привт", 5))
    }

    @Test
    fun `a word made of known words joined by hyphens is spelled right`() {
        assertNull(spelling().check("онлайн-магазин", 5))
        assertEquals(emptyList<String>(), spelling().check("онлайн-магзаинчик", 5))
        assertEquals(listOf("онлайн"), spelling().check("онлайн-", 5))
    }

    @Test
    fun `a word in another script or with a digit is not judged`() {
        assertNull(spelling().check("YouTube", 5))
        assertNull(spelling().check("8:40", 5))
        assertNull(spelling().check("2й", 5))
    }

    @Test
    fun `a typographic apostrophe is the same as a plain one`() {
        val english = Dictionary.parse(sequenceOf("don't\t400"))
        val spelling = Spelling(Language.English, english, emptyList(), emptySet(), emptySet())
        assertNull(spelling.check("don’t", 5))
        assertEquals(listOf("don't"), spelling.check("dont", 5))
    }

    @Test
    fun `a model's spelling knows its learned and removed words`() {
        val personal = PersonalWords()
        repeat(TRUSTED_USES) { personal.learn("кринж", previous = null, time = 0) }
        personal.remove("привет", time = 0)
        val spelling = LanguageModel(dictionary, personal = personal).spelling(Language.Russian, emptySet())
        assertNull(spelling.check("кринж", 5))
        assertEquals(listOf("пират", "приветы"), spelling.check("привт", 5))
    }

    @Test
    fun `the shipped dictionaries know common words and correct typos quickly`() {
        val assets = File("../app/src/main/assets/dictionaries")
        val russian = File(assets, "ru.tsv").reader().use(Dictionary::read)
        // The app builds the search tree while loading the model, before the spell checker can use it.
        russian.prepareWalks()
        val spelling = Spelling(Language.Russian, russian, emptyList(), emptySet(), emptySet())
        val words = listOf("иронично", "Ещё", "кто-нибудь", "сегодня", "приветствую")
        words.forEach { assertNull(it, spelling.check(it, 5)) }

        val started = System.nanoTime()
        assertEquals("привет", spelling.check("пирвет", 5)?.first())
        assertEquals("сегодня", spelling.check("сегодян", 5)?.first())
        assertEquals("иронично", spelling.check("ироничо", 5)?.first())
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("$millis ms", millis < CHECK_MILLIS)
    }

    private companion object {
        /** A generous bound for three checks on a build machine: a phone is slower, but not ten times. */
        const val CHECK_MILLIS = 300
    }
}
