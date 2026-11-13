package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val dictionary = Dictionary.parse(
        sequenceOf(
            "привет\t100",
            "приветствие\t50",
            "Москва\t70",
            "мост\t60",
            "как\t90",
            "для\t80",
            "дела\t10",
            "долго\t9",
            "ты\t40",
            "раз\t30",
        ),
    )
    private val model = LanguageModel(
        dictionary,
        NextWords.parse(sequenceOf("как\tдела\t50", "как\tты\t40", "как\tраз\t30", "как\tдолго\t20"), dictionary),
    )

    @Test
    fun `suggestions complete the typed word in its case`() {
        assertEquals(listOf("Привет", "Приветствие"), suggestions("Прив", model, 3))
        assertEquals(listOf("Москва", "мост"), suggestions("Ну мос", model, 3))
    }

    @Test
    fun `a word typed in caps gets suggestions in caps`() {
        assertEquals(listOf("ПРИВЕТ"), suggestions("ПРИВЕТ", model, 1))
    }

    @Test
    fun `after a space the next words come, the most frequent first`() {
        assertEquals(listOf("дела", "ты", "раз"), suggestions("Ну как ", model, 3))
        assertEquals(listOf("дела", "ты", "раз"), suggestions("Ну, как, ", model, 3))
    }

    @Test
    fun `next words that fit the typed start go before other completions`() {
        assertEquals(listOf("дела", "долго", "для"), suggestions("как д", model, 3))
        assertEquals(listOf("Дела", "Долго", "Для"), suggestions("как Д", model, 3))
    }

    @Test
    fun `no next words before the space after a word, or in a new sentence without the model`() {
        assertEquals(emptyList<String>(), suggestions("как", model, 3).filter { it != "как" })
        assertEquals(emptyList<String>(), suggestions("Вот как. ", model, 3))
        assertEquals(emptyList<String>(), suggestions("Вот как.", model, 3))
        assertEquals(emptyList<String>(), suggestions("", model, 3))
    }

    @Test
    fun `right after a mark a word goes a space after, the next words come`() {
        assertEquals(listOf("дела", "ты", "раз"), suggestions("как,", model, 3))
    }

    @Test
    fun `after a hyphen the whole words known come first, then the next words after the part before it`() {
        val words = Dictionary.parse(
            sequenceOf("как\t90", "как-то\t20", "дела\t10", "долго\t9", "для\t80", "ты\t40"),
        )
        val pairs = NextWords.parse(sequenceOf("как\tдела\t50", "как\tты\t40", "как\tдолго\t20"), words)
        val hyphens = LanguageModel(words, pairs)

        assertEquals(listOf("как-то", "как-с", "как-дела"), suggestions("Ну как-", hyphens, 3))
        assertEquals(listOf("Как-то", "Как-с", "Как-дела"), suggestions("Как-", hyphens, 3))
        assertEquals(listOf("как-дела", "как-долго", "как-для"), suggestions("как-д", hyphens, 3))
    }

    @Test
    fun `a word split by a hyphen is predicted as if a space stood for the hyphen`() {
        assertEquals(HyphenSplit("потихоньку-", "Ну потихоньку п"), hyphenSplit("Ну потихоньку-п"))
        assertEquals(HyphenSplit("кто-", "кто "), hyphenSplit("кто-"))
        assertEquals(HyphenSplit("Ну-ка-", "Ну-ка "), hyphenSplit("Ну-ка-"))
        assertEquals("Ну потихоньку п", predictedText("Ну потихоньку-п"))
        assertEquals("Ну как д", predictedText("Ну как д"))
    }

    @Test
    fun `a hyphen before a digit, at the word's start, or after a number splits no word`() {
        assertEquals(null, hyphenSplit("в топ-3"))
        assertEquals(null, hyphenSplit("— то"))
        assertEquals(null, hyphenSplit("с 2-го"))
        assertEquals(null, hyphenSplit("как"))
    }

    @Test
    fun `a typed yo gets only the words with yo there, and a typed e both`() {
        val che = LanguageModel(Dictionary.parse(sequenceOf("через\t738", "чего\t611", "чём\t214", "чёрт\t143")))

        assertEquals(listOf("чём", "чёрт"), suggestions("чё", che, 3))
        assertEquals(listOf("Чём", "Чёрт"), suggestions("Чё", che, 3))
        assertEquals(listOf("через", "чего", "чём"), suggestions("че", che, 3))
    }

    @Test
    fun `the user's own spelling with yo is suggested for a typed yo`() {
        val wheels = LanguageModel(Dictionary.parse(sequenceOf("колесики\t60", "колесико\t40")))
        assertEquals(emptyList<String>(), suggestions("колёс", wheels, 3))

        wheels.personal.learn("колёсики", previous = null, time = 1)
        assertEquals(listOf("колёсики"), suggestions("колёс", wheels, 3))
        assertEquals(listOf("колёсики", "колесики", "колесико"), suggestions("колес", wheels, 3))
    }

    @Test
    fun `a word the user typed comes before the dictionary's`() {
        model.personal.learn("привокзальный", previous = null, time = 1)

        assertEquals(listOf("привокзальный", "привет", "приветствие"), suggestions("прив", model, 3))
    }

    @Test
    fun `the user's use makes a dictionary word likelier`() {
        model.personal.learn("приветствие", previous = null, time = 1)

        assertEquals(listOf("приветствие", "привет"), suggestions("прив", model, 3))
    }

    @Test
    fun `uses multiply a dictionary word's likelihood up to a bound, and add to a word beyond it`() {
        assertEquals(listOf(1.0, 3.0, 5.0, 9.0, 10.0, 10.0), listOf(0, 1, 2, 4, 5, 40).map(::usesBoost))
        assertEquals(500.0, withUses(100.0, frequency = 100, uses = 2), 0.0)
        assertEquals(2.0 * WORD_USE + 7, withUses(7.0, frequency = 0, uses = 2), 0.0)
        assertEquals(400.0, usesAdd(100.0, frequency = 100, uses = 2), 0.0)
    }

    @Test
    fun `a removed word is not suggested, the dictionary's included`() {
        model.personal.remove("привет", time = 1)

        assertEquals(listOf("приветствие"), suggestions("прив", model, 3))
    }

    @Test
    fun `the user's own next word comes first`() {
        model.personal.learn("мост", previous = "как", time = 1)

        assertEquals(listOf("мост", "дела", "ты"), suggestions("Ну как ", model, 3))
        assertEquals(listOf("мост", "Москва"), suggestions("как мо", model, 3))
    }

    @Test
    fun `a word with no next words known suggests nothing after it`() {
        assertEquals(emptyList<String>(), suggestions("мост ", model, 3))
        assertEquals(emptyList<String>(), suggestions("Ну как ", LanguageModel(dictionary), 3))
    }

    @Test
    fun `the previous word is the one before the typed one, in the same sentence`() {
        assertEquals("как", previousWord("Ну как "))
        assertEquals("как", previousWord("Ну как д"))
        assertEquals("как", previousWord("как — «д"))
        assertEquals("кто-то", previousWord("вот кто-то "))
        assertEquals(null, previousWord("Вот как. Д"))
        assertEquals(null, previousWord("Вот как\nд"))
        assertEquals(null, previousWord("в 2024 "))
        assertEquals(null, previousWord("д"))
        assertEquals(null, previousWord(""))
    }

    @Test
    fun `the previous words are those of the same sentence, the nearest last, up to the count`() {
        assertEquals(listOf("ну", "как", "твои"), previousWords("Да. ну, как — «твои д", 4))
        assertEquals(listOf("как", "твои"), previousWords("Ну как твои ", 2))
        assertEquals(listOf("кто-то", "там"), previousWords("вот кто-то там ", 2))
        assertEquals(listOf("твои"), previousWords("в 2024 твои ", 3))
        assertEquals(listOf("числа"), previousWords("с 2-го числа ", 3))
        assertEquals(emptyList<String>(), previousWords("с 2-го ", 3))
        assertEquals(emptyList<String>(), previousWords("Вот как\nд", 3))
        assertEquals(emptyList<String>(), previousWords("", 3))
    }

    @Test
    fun `a word right after a number and a hyphen comes after a number, any other after the words before`() {
        assertEquals(listOf(AFTER_NUMBER), wordContext("на 2-р", 3))
        assertEquals(listOf(AFTER_NUMBER), wordContext("на 2-", 3))
        assertEquals(listOf("как", "твои"), wordContext("Ну как твои д", 2))
        assertEquals(emptyList<String>(), wordContext("на 2-разовое ", 3))
    }

    @Test
    fun `after a number and a hyphen the words the user typed there come first`() {
        val numbers = LanguageModel(Dictionary.parse(sequenceOf("раз\t90", "разве\t80", "разовое\t5")))
        numbers.personal.learn("разовое", listOf(AFTER_NUMBER), 0)

        assertEquals(listOf("разовое", "раз", "разве"), suggestions("на 2-ра", numbers, 3))
    }

    @Test
    fun `one capital letter capitalizes, not caps`() {
        assertEquals("Привет", casedLike("привет", "П"))
        assertEquals("I'm", casedLike("i'm", "I"))
    }

    @Test
    fun `a lowercase word keeps the dictionary's case`() {
        assertEquals("iPhone", casedLike("iPhone", "iph"))
        assertEquals("Москва", casedLike("Москва", "моск"))
    }

    @Test
    fun `capitals that match the word's own start keep its case, not caps`() {
        assertEquals("TDLib", casedLike("TDLib", "TD"))
        assertEquals("TDLib", casedLike("TDLib", "TDL"))
        assertEquals("TDLIB", casedLike("TDLib", "TDLI"))
        assertEquals("ПРИВЕТ", casedLike("привет", "ПР"))
    }

    @Test
    fun `the current word is the end of the text from its last letters`() {
        assertEquals("прив", currentWord("Ну, прив"))
        assertEquals("кто-то", currentWord("вот кто-то"))
        assertEquals("кто-", currentWord("кто-"))
        assertEquals("don't", currentWord("I don't"))
        assertEquals("Привет", currentWord("Привет"))
    }

    @Test
    fun `digits after a word's letters are part of it, and a number before them is not`() {
        assertEquals("топ-3", currentWord("в топ-3"))
        assertEquals("mp3", currentWord("в mp3"))
        assertEquals("го", currentWord("с 2-го"))
        assertEquals("", currentWord("в 2024"))
        assertEquals("п-3", wordRest("п-3 и"))
        assertEquals("p3", wordRest("p3, и"))
    }

    @Test
    fun `the next word is the one after the cursor's word, in the same sentence`() {
        assertEquals("мир", nextWord("вет мир"))
        assertEquals("мир", nextWord(", — «мир»"))
        assertEquals("кто-то", nextWord(" кто-то"))
        assertEquals(null, nextWord("вет. Мир"))
        assertEquals(null, nextWord("вет\nмир"))
        assertEquals(null, nextWord("вет 2024"))
        assertEquals(null, nextWord("вет"))
        assertEquals(null, nextWord(""))
    }

    @Test
    fun `the rest of a word is the start of the text after the cursor`() {
        assertEquals("вет", wordRest("вет мир"))
        assertEquals("то-то", wordRest("то-то, да"))
        assertEquals("ет", wordRest("ет- нет"))
        assertEquals("", wordRest(" мир"))
        assertEquals("", wordRest("-мир"))
        assertEquals("", wordRest(""))
    }

    @Test
    fun `a word after a digit and a hyphen is a number's ending`() {
        assertTrue(isNumberEnding("с 2-го"))
        assertTrue(isNumberEnding("в 90-х"))
        // Before its first letter too.
        assertTrue(isNumberEnding("2-"))
        assertFalse(isNumberEnding("кто-то"))
        assertFalse(isNumberEnding("2 го"))
        assertFalse(isNumberEnding("2—го"))
        assertFalse(isNumberEnding("-го"))
        assertFalse(isNumberEnding(""))
    }

    @Test
    fun `there is no current word after a space or a mark`() {
        assertEquals("", currentWord("привет "))
        assertEquals("", currentWord("привет,"))
        assertEquals("", currentWord("2024"))
        assertEquals("", currentWord(""))
    }

    @Test
    fun `a dash or a quote before a word is not part of it`() {
        assertEquals("слово", currentWord("— -слово"))
        assertEquals("hello", currentWord("'hello"))
        assertEquals("", currentWord("слово -"))
    }

    @Test
    fun `a slip to a key nearby still gets the word meant, in the typed case`() {
        val touches = Touches(KeyboardLayout.of(Language.Russian), emptyList())

        // м is the neighbor of и.
        assertEquals(listOf("Привет", "Приветствие"), suggestions("Прмвет", model, 3, touches = touches))
        // Without the keys, only the typed start counts.
        assertEquals(emptyList<String>(), suggestions("Прмвет", model, 3))
    }

    @Test
    fun `a tap on a key's edge leans toward its neighbor, one in its middle toward the key`() {
        val english = KeyboardLayout.of(Language.English)
        val model = LanguageModel(Dictionary.parse(sequenceOf("tip\t1000", "top\t10000")))
        val i = english.places.getValue('i')
        fun touches(across: Float) = Touches(english, listOf(null, i.pointAt(across, 0.5f), null))

        // On the edge toward o, top is nearly as likely typed as tip, and far more frequent.
        assertEquals(listOf("top", "tip"), suggestions("tip", model, 2, touches = touches(1f)))
        assertEquals(listOf("tip", "top"), suggestions("tip", model, 2, touches = touches(0.5f)))
    }
}
