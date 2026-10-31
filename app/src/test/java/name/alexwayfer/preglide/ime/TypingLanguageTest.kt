package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which language [TypingLearner] learns a word in: the one its letters were typed in. */
class TypingLanguageTest {
    private val bench = TypingBench()
    private val field = bench.field
    private val events = bench.events

    @Test
    fun aWordIsLearnedInTheLanguageItsLettersWereTypedIn() {
        field.type("Привет")
        bench.language = Language.English
        field.type(". Hello")
        bench.language = Language.Russian
        field.type("!")

        assertEquals(listOf("+Привет", "+Hello in en"), events)
    }

    @Test
    fun aWordTypedInTwoLanguagesIsNotLearned() {
        field.type("п")
        bench.language = Language.English
        field.type("p")
        bench.language = Language.Russian
        field.type("ивет ")

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun aLetterOfAnotherLanguageErasedLeavesTheWordInOne() {
        field.type("п")
        bench.language = Language.English
        field.type("p")
        field.backspace()
        bench.language = Language.Russian
        field.type("ривет ")

        assertEquals(listOf("+привет"), events)
    }

    @Test
    fun aHyphenAndADigitTypedInAnotherLanguageDoNotCount() {
        field.type("топ")
        bench.language = Language.English
        field.type("-3 ")

        assertEquals(listOf("+топ-3"), events)
    }

    @Test
    fun aWordChangedWithoutALetterTypedIsNotLearned() {
        val bench = TypingBench("Ну приввет")
        bench.field.cursor = 8
        bench.field.backspace()
        bench.learner.finishField()

        assertEquals(emptyList<String>(), bench.events)
    }

    @Test
    fun aFixOfTheWordTypedLastStaysInItsLanguage() {
        bench.language = Language.English
        field.type("Helloo ")
        bench.language = Language.Russian
        field.backspace(times = 2)
        field.type(" ")

        assertEquals(listOf("+Helloo in en", "-Helloo in en", "+Hello in en"), events)
    }

    @Test
    fun aFixOfTheWordTypedLastIntoTwoLanguagesOnlyTakesItBack() {
        bench.language = Language.English
        field.type("Hello ")
        bench.language = Language.Russian
        field.backspace()
        field.type("у ")

        assertEquals(listOf("+Hello in en", "-Hello in en"), events)
    }

    @Test
    fun aGlideOnAStartTypedInAnotherLanguageIsNotLearned() {
        bench.language = Language.English
        field.type("AI-")
        bench.language = Language.Russian
        bench.glide("вставка")

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun aChosenWordIsLearnedInTheKeyboardsLanguage() {
        bench.language = Language.English
        field.type("Hel")
        field.choose("Hello")

        assertEquals(listOf("+Hello in en"), events)
    }

    @Test
    fun lettersKeepTheLanguagesTheyWereTypedInAsTheWordChanges() {
        val ru = Language.Russian
        val en = Language.English

        assertEquals(listOf(ru, en, en), retyped("п", listOf(ru), "пpr", en))
        assertEquals(listOf(ru), retyped("пp", listOf(ru, en), "п", ru))
        // A letter put in between keeps the letters on both sides as they were.
        assertEquals(listOf(ru, en, null, ru), retyped("abc", listOf(ru, en, ru), "abxc", null))
        assertEquals(listOf(en, en), retyped("", emptyList(), "ab", en))
    }

    @Test
    fun aWordHasALanguageOnlyWhenAllItsLettersKnownShareIt() {
        val ru = Language.Russian
        val en = Language.English

        assertEquals(ru, soleLanguage("топ-3", listOf(ru, ru, ru, en, en)))
        assertEquals(en, soleLanguage("hellos", listOf(null, null, null, null, null, en)))
        assertNull(soleLanguage("пpивет", listOf(ru, en, ru, ru, ru, ru)))
        assertNull(soleLanguage("hello", List(5) { null }))
    }
}
