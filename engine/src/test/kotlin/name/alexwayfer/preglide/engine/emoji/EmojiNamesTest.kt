package name.alexwayfer.preglide.engine.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiNamesTest {
    private val russian = listOf(
        EmojiName("😀", "широкая улыбка", listOf("лицо", "смех", "улыбка")),
        EmojiName("😂", "смех сквозь слёзы", listOf("лицо", "слезы", "радость")),
        EmojiName("🎄", "ёлка", listOf("новый год", "праздник")),
        EmojiName("🇷🇺", "флаг: Россия", listOf("Россия", "флаг")),
    )
    private val english = listOf(
        EmojiName("😀", "grinning face", listOf("face", "grin")),
        EmojiName("😂", "face with tears of joy", listOf("face", "joy", "laugh")),
    )
    private val names = EmojiNames(russian + english)

    @Test
    fun aWordOfTheNameComesBeforeAKeyword() {
        // "смех" is in the name of 😂 and among the keywords of 😀.
        assertEquals(listOf("😂", "😀"), names.search("смех"))
    }

    @Test
    fun aWholeWordComesBeforeTheStartOfOne() {
        val cats = EmojiNames(listOf(EmojiName("🐱", "кошка"), EmojiName("🐈", "кош")))

        assertEquals(listOf("🐈", "🐱"), cats.search("кош"))
        assertEquals(listOf("🇷🇺"), names.search("росс"))
    }

    @Test
    fun everyWordOfTheQueryHasToMatch() {
        assertEquals(listOf("😂"), names.search("лицо слез"))
        assertEquals(emptyList<String>(), names.search("лицо флаг"))
    }

    @Test
    fun caseAndYoDoNotMatter() {
        assertEquals(listOf("🎄"), names.search("Елка"))
        assertEquals(listOf("😂"), names.search("СЛЁЗЫ"))
    }

    @Test
    fun anEnglishWordFindsByTheEnglishNames() {
        assertEquals(listOf("😀", "😂"), names.search("face"))
    }

    @Test
    fun anEmojiFoundInTwoLanguagesComesOnce() {
        val twice = EmojiNames(listOf(EmojiName("😀", "grin"), EmojiName("😀", "grinning face")))

        assertEquals(listOf("😀"), twice.search("grin"))
    }

    @Test
    fun aQueryWithNoWordFindsNothing() {
        assertEquals(emptyList<String>(), names.search(""))
        assertEquals(emptyList<String>(), names.search(" ,"))
    }

    @Test
    fun aSearchStopsAtItsLimitKeepingTheOrderOfEqualMatches() {
        assertEquals(listOf("😀"), names.search("лицо", limit = 1))
    }

    @Test
    fun theFirstLanguageNamesAnEmoji() {
        assertEquals("широкая улыбка", names.nameOf("😀"))
        assertNull(names.nameOf("🐱"))
    }

    @Test
    fun namesReadBackAsTheyWereWritten() {
        assertEquals(russian, readEmojiNames(emojiNamesText(russian).lineSequence()))
    }

    @Test
    fun aNameWithoutKeywordsReadsBack() {
        val name = EmojiName("🫫", "cracking face")

        assertEquals(listOf(name), readEmojiNames(emojiNamesText(listOf(name)).lineSequence()))
    }

    @Test
    fun aWordThatIsAWholeNameGetsItsEmoji() {
        val food = EmojiNames(
            listOf(
                EmojiName("🍕", "пицца", listOf("еда", "сыр")),
                EmojiName("🐶", "морда собаки", listOf("собака")),
                EmojiName("🪼", "медуза", listOf("ой", "жжётся")),
                EmojiName("🍕", "pizza", listOf("cheese")),
            ),
        )

        assertEquals(listOf("🍕"), food.named("Пицца"))
        assertEquals(listOf("🍕"), food.named("pizza"))
        // A keyword or a part of a name is too loose a match: "ой" would offer the jellyfish.
        assertEquals(emptyList<String>(), food.named("ой"))
        assertEquals(emptyList<String>(), food.named("собака"))
        assertEquals(emptyList<String>(), food.named("морда"))
    }

    @Test
    fun aWholeNameMatchesWithCaseAndYoAside() {
        assertEquals(listOf("🎄"), names.named("ЕЛКА"))
    }

    @Test
    fun wordsAreCutAtAnythingButLettersAndDigits() {
        assertEquals(listOf("флаг", "россия", "10"), wordsOf("Флаг: Россия, 10!"))
    }
}
