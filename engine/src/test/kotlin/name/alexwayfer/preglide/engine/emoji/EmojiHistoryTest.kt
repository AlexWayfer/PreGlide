package name.alexwayfer.preglide.engine.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiHistoryTest {
    private val wave = Emoji("👋", listOf("👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿"))

    @Test
    fun theLatestEmojiComesFirstAndOnce() {
        val history = EmojiHistory().typed("😀").typed("😂").typed("😀")

        assertEquals(listOf("😀", "😂"), history.recent)
    }

    @Test
    fun theRecentOnesStopAtTheirLimit() {
        val history = (0..RECENT_LIMIT).fold(EmojiHistory()) { history, n -> history.typed("e$n") }

        assertEquals(RECENT_LIMIT, history.recent.size)
        assertEquals("e$RECENT_LIMIT", history.recent.first())
        assertEquals("e1", history.recent.last())
    }

    @Test
    fun aToneChosenStaysForTheNextTap() {
        val history = EmojiHistory().typed("👋🏽", wave, tone = 3)

        assertEquals(3, history.toneOf(wave))
        assertEquals("👋🏽", history.shown(wave))
        assertEquals(listOf("👋🏽"), history.recent)
    }

    @Test
    fun aTapKeepsTheToneAsItWas() {
        val history = EmojiHistory().typed("👋🏽", wave, tone = 3).typed("👋🏽", wave)

        assertEquals(3, history.toneOf(wave))
    }

    @Test
    fun anEmojiWithNoToneChosenShowsItsBase() {
        assertEquals("👋", EmojiHistory().shown(wave))
    }

    @Test
    fun aToneTheEmojiDoesNotTakeGivesItsBase() {
        val history = EmojiHistory(tones = mapOf("👋" to 9, "😀" to 2))

        assertEquals("👋", history.shown(wave))
        assertEquals("😀", history.shown(Emoji("😀")))
    }

    @Test
    fun whatWasTypedBeforeTheFileCouldBeReadGoesOnTopOfIt() {
        val file = EmojiHistory(listOf("😂", "😀"), mapOf("👋" to 1, "👍" to 2))
        val typed = EmojiHistory(listOf("🐱", "😀"), mapOf("👋" to 4))

        assertEquals(EmojiHistory(listOf("🐱", "😀", "😂"), mapOf("👋" to 4, "👍" to 2)), typed.after(file))
    }

    @Test
    fun anEmojiTypedAfterAWordIsPairedWithItInItsBaseForm() {
        val history = EmojiHistory().typed("👋🏽", wave, tone = 3, after = "Привет").typed("😀")

        assertEquals(listOf("👋"), history.pairs.forWord("привет"))
        assertEquals(listOf("😀", "👋🏽"), history.recent)
    }

    @Test
    fun pairsTypedBeforeTheFileCouldBeReadAddToIt() {
        val file = EmojiHistory().typed("😅", after = "ой")
        val typed = EmojiHistory().typed("😁", after = "ой").typed("😁", after = "ой")

        assertEquals(listOf("😁", "😅"), typed.after(file).pairs.forWord("ой"))
    }

    @Test
    fun theHistoryReadsBackAsItWasWritten() {
        val history = EmojiHistory().typed("😀").typed("👋🏿", wave, tone = 5)
            .typed("😅", after = "ой").typed("😁", after = "ой").typed("😁", after = "ой").typed("🙄", after = "ну")

        val read = EmojiHistory.read(history.text().lineSequence())

        assertEquals(history, read)
        assertEquals(listOf("😁", "😅"), read.pairs.forWord("ой"))
    }

    @Test
    fun pairsKeepTheirOrderThroughTheFile() {
        val history = EmojiHistory().typed("😅", after = "ой").typed("😁", after = "ой")

        assertEquals(listOf("😁", "😅"), EmojiHistory.read(history.text().lineSequence()).pairs.forWord("ой"))
    }

    @Test
    fun linesThatCannotBeReadAreSkipped() {
        val text = "recent\t😀\nrecent\nrecent\t😀\ntone\t👋\tdark\ntone\t👋\nnoise\nrecent\t😂\n" +
            "pair\tой\t😁\npair\tой\t😁\tx\n"

        assertEquals(EmojiHistory(listOf("😀", "😂")), EmojiHistory.read(text.lineSequence()))
    }
}
