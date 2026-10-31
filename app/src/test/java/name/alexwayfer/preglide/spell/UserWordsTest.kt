package name.alexwayfer.preglide.spell

import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class UserWordsTest {
    private val entries = listOf(
        UserWord("кринж", "ru_RU"),
        UserWord("PreGlide", null),
        UserWord("Twitch", ""),
        UserWord("lol", "en"),
        UserWord("bitte", "de_DE"),
    )

    @Test
    fun userWordsAreThoseOfTheLanguageAndOfEveryLanguage() {
        assertEquals(setOf("кринж", "PreGlide", "Twitch"), userWordsFor(Language.Russian, entries))
        assertEquals(setOf("lol", "PreGlide", "Twitch"), userWordsFor(Language.English, entries))
    }

    @Test
    fun userWordsAreReadOnceUntilTheyChange() {
        var reads = 0
        var stored = listOf(UserWord("кринж", null))
        val words = UserWords {
            reads++
            stored
        }
        assertEquals(setOf("кринж"), words.of(Language.Russian))
        assertEquals(setOf("кринж"), words.of(Language.English))
        assertEquals(1, reads)

        stored = stored + UserWord("база", "ru")
        words.changed()
        assertEquals(setOf("кринж", "база"), words.of(Language.Russian))
        assertEquals(2, reads)
    }
}
