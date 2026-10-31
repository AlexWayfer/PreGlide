package name.alexwayfer.preglide.engine.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiPairsTest {
    @Test
    fun anEmojiTypedAfterAWordIsOfferedForIt() {
        val pairs = EmojiPairs().learned("ой", "😁")

        assertEquals(listOf("😁"), pairs.forWord("ой"))
        assertEquals(emptyList<String>(), pairs.forWord("ну"))
    }

    @Test
    fun caseAndYoDoNotMatter() {
        val pairs = EmojiPairs().learned("Всё", "👍")

        assertEquals(listOf("👍"), pairs.forWord("все"))
        assertEquals(listOf("👍"), pairs.forWord("ВСЁ"))
    }

    @Test
    fun theOneTypedMostComesFirst() {
        val pairs = EmojiPairs().learned("ой", "😅").learned("ой", "😁").learned("ой", "😁").learned("ой", "🙈")

        assertEquals(listOf("😁", "🙈", "😅"), pairs.forWord("ой"))
    }

    @Test
    fun ofTwoTypedAsOftenTheLatestComesFirst() {
        val pairs = EmojiPairs().learned("ой", "😅").learned("ой", "😁")

        assertEquals(listOf("😁", "😅"), pairs.forWord("ой"))
    }

    @Test
    fun pastItsLimitAWordForgetsTheEmojiTypedLeastAndLongestAgo() {
        val often = EmojiPairs().learned("ой", "😁").learned("ой", "😁")
        val pairs = (1..PAIR_EMOJI_LIMIT).fold(often) { pairs, n -> pairs.learned("ой", "e$n") }

        val kept = pairs.forWord("ой")
        assertEquals(PAIR_EMOJI_LIMIT, kept.size)
        assertEquals("😁", kept.first())
        assertEquals("e$PAIR_EMOJI_LIMIT", kept[1])
        assertEquals(false, "e1" in kept)
    }

    @Test
    fun pastTheLimitTheWordUsedLongestAgoIsForgotten() {
        val pairs = (0..PAIR_WORDS_LIMIT).fold(EmojiPairs()) { pairs, n -> pairs.learned("w$n", "😁") }
            .learned("w1", "🙈")
            .learned("new", "😁")

        assertEquals(emptyList<String>(), pairs.forWord("w0"))
        assertEquals(emptyList<String>(), pairs.forWord("w2"))
        assertEquals(listOf("🙈", "😁"), pairs.forWord("w1"))
        assertEquals(listOf("😁"), pairs.forWord("new"))
    }

    @Test
    fun whatWasTypedBeforeTheFileCouldBeReadAddsToIt() {
        val file = EmojiPairs().learned("ой", "😅").learned("ой", "😅").learned("ну", "🙄")
        val typed = EmojiPairs().learned("ой", "😁").learned("ой", "😁").learned("ой", "😁")

        val pairs = typed.after(file)

        assertEquals(listOf("😁", "😅"), pairs.forWord("ой"))
        assertEquals(listOf("🙄"), pairs.forWord("ну"))
        assertEquals(listOf("😅", "😁"), typed.learned("ой", "😅").after(file).forWord("ой"))
    }
}
