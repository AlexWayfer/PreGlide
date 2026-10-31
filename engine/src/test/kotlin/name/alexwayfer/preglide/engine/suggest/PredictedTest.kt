package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.LIKELIEST_WORDS
import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.predict.Vocabulary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictedTest {
    // The model knows four words; "мирный" and "мираж" are the dictionary's alone.
    private val dictionary = Dictionary.parse(
        sequenceOf("дела\t50", "мир\t40", "мираж\t2", "мирный\t6", "Москва\t30", "привет\t20"),
    )
    private val vocabulary = Vocabulary(listOf("привет", "мир", "москва", "дела"))
    private val first = Vocabulary.FIXED.size
    private val prediction = predictionOver(dictionary, vocabulary, 4) { FloatArray(0) }

    private fun next(vararg probabilities: Pair<String, Float>) = NextTokens(
        prediction,
        FloatArray(vocabulary.size).apply {
            for ((token, probability) in probabilities) {
                val id = if (token == "<unk>") Vocabulary.UNKNOWN else first + vocabulary.words.indexOf(token)
                this[id] = probability
            }
        },
    )

    private fun model(personal: PersonalWords = PersonalWords()) = LanguageModel(dictionary, personal = personal)

    private val likely = next("дела" to 0.45f, "москва" to 0.3f, "мир" to 0.1f, "<unk>" to 0.08f)

    private val touches = Touches(KeyboardLayout.of(Language.Russian), emptyList())

    @Test
    fun `a prediction over the dictionary spells words as it does and leaves it the rest`() {
        assertEquals("Москва", prediction.spellings[first + 2])
        assertEquals("", prediction.spellings[Vocabulary.UNKNOWN])
        // Of the dictionary's 148 per billion, the vocabulary's words take 140: мирный and мираж leave 8.
        assertEquals(8L, prediction.unknownFrequency)
    }

    @Test
    fun `after a space the model's likeliest words come, spelled as the dictionary spells them`() {
        assertEquals(listOf("дела", "Москва", "мир"), predictedWords(model(), likely, "", listOf("как"), 3))
    }

    @Test
    fun `the typed start picks the model's words that begin so`() {
        assertEquals(listOf("Москва", "мир"), predictedWords(model(), likely, "м", emptyList(), 2).take(2))
    }

    @Test
    fun `fixed tokens are never suggested, however likely`() {
        val words = predictedWords(model(), next("<unk>" to 0.9f, "мир" to 0.1f), "", emptyList(), 3)

        assertEquals("мир", words.first())
        assertEquals(false, words.any { it.isEmpty() || it.startsWith("<") })
    }

    @Test
    fun `a word beyond the vocabulary shares the unknown token's probability by its frequency`() {
        // Of the unknown token's 0.08, мирный takes 6 of 8 parts, 0.06, below мир's own 0.1; мираж takes 0.02.
        assertEquals(
            listOf("Москва", "мир", "мирный", "мираж"),
            predictedWords(model(), likely, "м", emptyList(), 4),
        )
    }

    @Test
    fun `a next word the user typed once joins the model's likeliest`() {
        val personal = PersonalWords().apply { learn("привет", "как", time = 1) }

        // The model's words weigh as 8 uses against the user's 1: привет gets 1/9, 0.11, Москва 8/9 of 0.3, 0.27,
        // and мир 8/9 of 0.1, 0.09.
        val words = predictedWords(model(personal), likely, "", listOf("как"), 3)

        assertEquals(listOf("дела", "Москва", "привет"), words.take(3))
    }

    @Test
    fun `a next word the user typed a few times comes before the model's likeliest`() {
        val personal = PersonalWords().apply { repeat(4) { learn("привет", "как", time = it.toLong()) } }

        // 4 uses against the model's 8: привет gets 4/12, 0.33, дела 8/12 of 0.45, 0.3.
        val words = predictedWords(model(personal), likely, "", listOf("как"), 3)

        assertEquals(listOf("привет", "дела", "Москва"), words.take(3))
    }

    @Test
    fun `the user's next words count only after the word they followed`() {
        val personal = PersonalWords().apply { learn("привет", "как", time = 1) }

        assertEquals(listOf("дела", "Москва", "мир"), predictedWords(model(personal), likely, "", listOf("ну"), 3))
    }

    @Test
    fun `a phrase typed twice comes before the model's likeliest, where its pair alone would not`() {
        val personal = PersonalWords().apply { repeat(2) { learn("привет", listOf("ну", "как"), time = 1) } }

        // After "ну как", привет gets 2/10 and 8/10 of its pair's 2/10, 0.36, and дела 8/10 of 8/10 of 0.45, 0.29.
        // After "да как", only the pair counts: привет gets 0.2, and дела 8/10 of 0.45, 0.36.
        assertEquals("привет", predictedWords(model(personal), likely, "", listOf("ну", "как"), 3).first())
        assertEquals("дела", predictedWords(model(personal), likely, "", listOf("да", "как"), 3).first())
    }

    @Test
    fun `the user's share mixes the longer context with the shorter one as counts`() {
        val personal = PersonalWords().apply { learn("привет", listOf("ну", "как"), time = 1) }

        val mix = NextMix(model(personal), likely, listOf("ну", "Как"))

        assertEquals(1.0 / 9 + 8.0 / 9 / 9, mix.usersShare("Привет"), 1e-9)
        assertEquals(8.0 / 9 * 8 / 9, mix.modelShare, 1e-9)
    }

    @Test
    fun `the parts of a word's probability mix back into it as counts`() {
        val personal = PersonalWords().apply {
            learn("привет", listOf("ну", "как"), time = 1)
            learn("дела", "как", time = 2)
        }
        val mix = NextMix(model(personal), likely, listOf("ну", "как"))

        val parts = mix.parts("Привет", frequency = 20)
        val mixed = parts.levels.fold(parts.model / PER_BILLION) { below, level ->
            (level.count + PREDICTION_WEIGHT * below) / (level.total + PREDICTION_WEIGHT)
        }

        // After "как" the user typed привет and дела, after "ну как" привет alone; the model gives привет nothing.
        assertEquals(listOf(LevelCount(1, 2), LevelCount(1, 1)), parts.levels)
        assertEquals(0.0, parts.model, 0.0)
        assertEquals(mix.probability("привет", 20), mixed, 1e-12)
    }

    @Test
    fun `a context longer than the ranked one is learned but not read`() {
        val personal = PersonalWords().apply { learn("привет", listOf("а", "ну", "как"), time = 1) }

        val three = NextMix(model(personal), likely, listOf("а", "ну", "как"))
        val two = NextMix(model(personal), likely, listOf("ну", "как"))

        assertEquals(two.usersShare("привет"), three.usersShare("привет"), 0.0)
        assertEquals(listOf(Learned("привет", 1, 1)), personal.after(listOf("а", "ну", "как")))
    }

    @Test
    fun `a word the user typed adds to its score as it is completed`() {
        val personal = PersonalWords().apply { repeat(1000) { learn("мираж", null, time = it.toLong()) } }

        assertEquals("мираж", predictedWords(model(personal), likely, "мир", emptyList(), 1).first())
    }

    @Test
    fun `a word the model predicts, typed once, sums to three times its likelihood`() {
        val personal = PersonalWords().apply { learn("мир", null, time = 1) }

        val typedOnce = mirScore(predictedScores(model(personal), likely, "ми", emptyList(), 3))
        val never = mirScore(predictedScores(model(), likely, "ми", emptyList(), 3))

        assertEquals(usesBoost(1), typedOnce.toDouble() / never, 1e-6)
    }

    private fun mirScore(scores: List<Scored>): Long =
        scores.filter { it.word.equals("мир", ignoreCase = true) }.sumOf { it.score }

    @Test
    fun `a removed word is left out`() {
        val personal = PersonalWords().apply { remove("дела", time = 1) }

        assertEquals(listOf("Москва", "мир"), predictedWords(model(personal), likely, "", emptyList(), 2))
    }

    @Test
    fun `without words beyond the vocabulary the unknown token suggests nothing`() {
        val small = Dictionary.parse(sequenceOf("мир\t40"))
        val whole = predictionOver(small, Vocabulary(listOf("мир")), 4) { FloatArray(0) }
        val next = NextTokens(whole, FloatArray(whole.vocabulary.size) { if (it == Vocabulary.UNKNOWN) 0.9f else 0.1f })

        assertEquals(listOf("мир"), predictedWords(LanguageModel(small), next, "м", emptyList(), 3))
    }

    @Test
    fun `a context's likeliest words are its words by probability, the fixed tokens left out`() {
        assertEquals(listOf(first + 3, first + 2, first + 1, first), likely.likeliest)
    }

    @Test
    fun `a context's likeliest words stop at their count`() {
        val many = Vocabulary(List(LIKELIEST_WORDS + 5) { "слово$it" })
        val prediction = predictionOver(dictionary, many, 4) { FloatArray(0) }
        val next = NextTokens(prediction, FloatArray(many.size) { it.toFloat() })

        assertEquals(LIKELIEST_WORDS, next.likeliest.size)
        assertEquals(many.size - 1, next.likeliest.first())
    }

    @Test
    fun `the likeliest words keep their order and stop at the limit`() {
        assertEquals(listOf(first + 3, first + 2), likeliestWords(likely, "", 2).map { it.first })
    }

    @Test
    fun `the search order puts the words in the dictionary's order, whatever their ids`() {
        val words = listOf("мир", "ёлка", "дела", "Ель", "мираж")

        val order = searchOrderOf(Vocabulary(words))

        assertEquals(listOf("дела", "ёлка", "Ель", "мир", "мираж"), order.map { words[it - first] })
    }

    @Test
    fun `a typed start finds only the words that begin so, whatever their ids`() {
        assertEquals(listOf(first + 2), likeliestWords(likely, "мо", 3).map { it.first })
        assertEquals(listOf(first + 2, first + 1), likeliestWords(likely, "М", 3).map { it.first })
    }

    @Test
    fun `a typed start that no word has finds none`() {
        assertEquals(emptyList<Pair<Int, Float>>(), likeliestWords(likely, "я", 3))
        assertEquals(emptyList<Pair<Int, Float>>(), likeliestWords(likely, "дело", 3))
    }

    @Test
    fun `a typed e finds a word with yo, and a typed yo only that`() {
        val yo = predictionOver(dictionary, Vocabulary(listOf("ёлка", "дела", "еда")), 4) { FloatArray(0) }
        val next = NextTokens(yo, FloatArray(yo.vocabulary.size) { 0.1f * it })

        assertEquals(listOf(first + 2, first), likeliestWords(next, "е", 3).map { it.first })
        assertEquals(listOf(first), likeliestWords(next, "ё", 3).map { it.first })
    }

    @Test
    fun `suggestions take the prediction when it is given, and the pairs without it`() {
        val pairs = NextWords.parse(sequenceOf("как\tпривет\t10"), dictionary)
        val both = LanguageModel(dictionary, pairs)

        assertEquals(listOf("дела", "Москва", "мир"), suggestions("Ну как ", both, 3, likely))
        assertEquals(listOf("привет"), suggestions("Ну как ", both, 3))
    }

    @Test
    fun `without the prediction a phrase's next word adds its uses after the two words to its pair's`() {
        val pairs = NextWords.parse(sequenceOf("как\tдела\t1500000"), dictionary)
        val personal = PersonalWords().apply { learn("привет", listOf("ну", "как"), time = 1) }
        val model = LanguageModel(dictionary, pairs, personal)

        // привет counts a use after "как" and one after "ну как": 2,000,000 against дела's 1,500,000.
        assertEquals(listOf("привет", "дела"), suggestions("Ну как ", model, 3))
        assertEquals(listOf("дела", "привет"), suggestions("Да как ", model, 3))
    }

    @Test
    fun `right after a mark the next words are predicted, as after its space`() {
        assertEquals(suggestions("Привет, ", model(), 3, likely), suggestions("Привет,", model(), 3, likely))
        assertTrue(suggestions("Привет,", model(), 3, likely).isNotEmpty())
    }

    @Test
    fun `a slip gets the word the model expects`() {
        // к is the neighbor of е: "дкла" may be "дела".
        assertEquals("дела", suggestions("Ну как дкла", model(), 3, likely, touches).first())
    }

    @Test
    fun `the model, not the dictionary, ranks the words a slip may mean`() {
        // с is the neighbor of м. Of the unknown token's 0.08, мирный takes 0.06 and мираж 0.02; the model gives
        // мир nothing, though the dictionary uses it most.
        val next = next("дела" to 0.92f, "<unk>" to 0.08f)

        assertEquals(listOf("мирный", "мираж", "мир"), suggestions("сир", model(), 3, next, touches))
    }

    @Test
    fun `the user's next word is found by a slip too`() {
        val personal = PersonalWords().apply { learn("привет", "как", time = 1) }

        // к is the neighbor of е: "привкт" may be "привет", no likely word for the model, but the user's.
        val words = suggestions("как привкт", model(personal), 3, next("дела" to 1f), touches)

        assertEquals("привет", words.first())
    }

    @Test
    fun `the model reads the text before the word being typed`() {
        assertEquals("Ну как ", textBeforeWord("Ну как де").toString())
        assertEquals("Ну как ", textBeforeWord("Ну как ").toString())
    }
}
