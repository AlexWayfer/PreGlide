package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class MeantTest {
    private val dictionary = Dictionary.parse(
        sequenceOf("привет\t100", "приват\t5", "пример\t80", "кот\t50", "кит\t10", "код\t30"),
    )
    private val touches = Touches(KeyboardLayout.of(Language.Russian), emptyList())

    private fun typing(typed: String) = Typing(typed, touches)

    @Test
    fun `a slip to a key nearby finds the word and its completions, the likeliest first`() {
        // п and р are neighbors: "рри" may be "при".
        val meant = dictionary.meant(typing("рри"), 10)

        assertEquals(listOf("привет", "пример", "приват"), meant.map { it.word })
        assertEquals(listOf(100L, 80L, 5L), meant.map { it.frequency })
        for ((_, cost) in meant) assertEquals(3.125f, cost, DELTA)
    }

    @Test
    fun `only the likeliest few are kept`() {
        assertEquals(listOf("привет", "пример"), dictionary.meant(typing("рри"), 2).map { it.word })
        assertEquals(emptyList<Meant>(), dictionary.meant(typing("рри"), 0))
    }

    @Test
    fun `a word typed as it is is left out, and the others come by their frequency and cost`() {
        val dictionary = Dictionary.parse(sequenceOf("мил\t10", "мило\t5", "мыло\t20", "код\t30"))

        // мил with о brushed after л next to it; мыло with и read as ы, spelled alike; код's к lies far from м,
        // spelled unlike.
        val meant = dictionary.meant(typing("мило"), 10)

        assertEquals(listOf("мил", "мыло"), meant.map { it.word })
        assertEquals(listOf(4.5f, 5.5f), meant.map { it.cost })
    }

    @Test
    fun `words with the typed start are left to the completions`() {
        assertEquals(emptyList<Meant>(), dictionary.meant(typing("при"), 10).filter { it.word.startsWith("при") })
    }

    @Test
    fun `nothing near is nothing meant`() {
        assertEquals(emptyList<Meant>(), dictionary.meant(typing("жжж"), 10))
        assertEquals(emptyList<Meant>(), Dictionary.parse(emptySequence()).meant(typing("при"), 10))
    }

    @Test
    fun `the words likely next and the user's own join the dictionary's, each once`() {
        val personal = PersonalWords().apply {
            learn("Пример", null, 0)
            learn("Прювет", null, 0)
        }
        val model = LanguageModel(dictionary, personal = personal)

        val words = meantWords(model, typing("рри"), 1, more = listOf("привет", "приват", "Прибор"))

        // The learned word takes the user's spelling; one not near enough stays out.
        assertEquals(listOf("привет", "приват", "Прибор", "Пример"), words.map { it.word })
        assertEquals(listOf(100L, 5L, 0L, 0L), words.map { it.frequency })
    }

    @Test
    fun `a score is made less by its cost`() {
        assertEquals(1000L, lessBy(1000.0, 0f))
        assertEquals(368L, lessBy(1000.0, 1f))
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
