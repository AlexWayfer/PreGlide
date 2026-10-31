package name.alexwayfer.preglide.engine.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EmojiPaletteTest {
    private val wave = Emoji("👋", listOf("👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿"))
    private val groups = mapOf(
        EmojiGroup.Smileys to listOf(Emoji("😀"), Emoji("🫫")),
        EmojiGroup.People to listOf(wave),
    )

    private fun read(text: String, renders: (String) -> Boolean = { true }) =
        EmojiPalette.read(text.lineSequence(), renders)

    @Test
    fun thePaletteReadsBackAsItWasWritten() {
        assertEquals(groups, read(paletteText(groups)).groups)
    }

    @Test
    fun anEmojiThePhoneCannotDrawIsLeftOut() {
        val palette = read(paletteText(groups)) { it != "🫫" }

        assertEquals(listOf(Emoji("😀")), palette.groups[EmojiGroup.Smileys])
    }

    @Test
    fun tonesThePhoneCannotAllDrawAreLeftOutWithTheEmojiKept() {
        val palette = read(paletteText(groups)) { it != "👋🏽" }

        assertEquals(listOf(Emoji("👋")), palette.groups[EmojiGroup.People])
    }

    @Test
    fun everyFormOfAnEmojiLeadsToIt() {
        val palette = read(paletteText(groups))

        assertEquals(wave, palette.emojiOf("👋"))
        assertEquals(wave, palette.emojiOf("👋🏿"))
        assertNull(palette.emojiOf("🐱"))
    }

    @Test
    fun anEmojiBeforeAnyGroupIsAnError() {
        assertThrows(IllegalStateException::class.java) { read("😀\n=Smileys\n") }
    }

    @Test
    fun blankLinesAreSkipped() {
        assertEquals(listOf(Emoji("😀")), read("=Smileys\n\n😀\n\n").groups[EmojiGroup.Smileys])
    }

    @Test
    fun aToneOutsideTheEmojisOwnGivesItsBase() {
        assertEquals("👋", wave.toned(0))
        assertEquals("👋🏻", wave.toned(1))
        assertEquals("👋🏿", wave.toned(5))
        assertEquals("😀", Emoji("😀").toned(3))
    }

    @Test
    fun anEmojiTakesAllTonesOrNone() {
        assertThrows(IllegalArgumentException::class.java) { Emoji("👋", listOf("👋🏻")) }
    }
}
