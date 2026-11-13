package name.alexwayfer.preglide.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordErasingTest {
    /** A field with a selection, which gives at most [shown] chars before it, as an app may. */
    private class Field(var text: String, var start: Int = text.length, var end: Int = start) : WordField {
        var shown = Int.MAX_VALUE
        var tells = true

        val selected get() = text.substring(start, end)

        override fun canDelete() = start > 0 || selected.isNotEmpty()

        override fun selection(length: Int): FieldSelection? {
            if (!tells) return null
            val before = text.substring(0, start).takeLast(minOf(length, shown))
            return FieldSelection(before, start, end)
        }

        override fun select(start: Int, end: Int) {
            this.start = start
            this.end = end
        }

        override fun eraseSelection() {
            text = text.removeRange(start, end)
            end = start
        }
    }

    private val events = mutableListOf<String>()

    private fun erasing(field: Field) =
        WordErasing(
            field,
            { events += "selecting" },
            { capital -> events += if (capital) "erased a capital" else "erased" },
            { events += "ended" },
        )

    @Test
    fun eachStepSelectsOneMoreWordBackFromTheCursor() {
        val field = Field("Привет. Как дела?")
        val erasing = erasing(field)

        assertEquals(3, erasing.startSwipe())
        erasing.select(1)
        assertEquals("дела?", field.selected)
        erasing.select(2)
        assertEquals("Как дела?", field.selected)
        erasing.select(3)
        assertEquals("Привет. Как дела?", field.selected)
    }

    @Test
    fun theWordsSelectedAreErasedAsTheFingerLifts() {
        val field = Field("Привет. Как дела?")
        val erasing = erasing(field)

        erasing.startSwipe()
        erasing.select(2)
        erasing.endSwipe(lifted = true)

        assertEquals("Привет. ", field.text)
        assertEquals(listOf("selecting", "erased a capital", "ended"), events)
    }

    @Test
    fun hyphenatedPartsCanBeErasedInSuccessiveSwipes() {
        val field = Field("Вот рок-н-ролл!  ")
        val erasing = erasing(field)

        for (remaining in listOf("Вот рок-н-", "Вот рок-", "Вот ")) {
            erasing.startSwipe()
            erasing.select(1)
            erasing.endSwipe(lifted = true)
            assertEquals(remaining, field.text)
        }
    }

    @Test
    fun hyphenatedSelectionShrinksBackAndCancellationRestoresTheCursor() {
        val field = Field("кто-нибудь")
        val erasing = erasing(field)

        assertEquals(2, erasing.startSwipe())
        erasing.select(2)
        assertEquals("кто-нибудь", field.selected)
        erasing.select(1)
        assertEquals("нибудь", field.selected)
        erasing.endSwipe(lifted = false)

        assertEquals("кто-нибудь", field.text)
        assertEquals(10 to 10, field.start to field.end)
        assertEquals(listOf("selecting", "ended"), events)
    }

    @Test
    fun wordsErasedFromALowercaseOneTellNoCapital() {
        val field = Field("город Москва и область")
        val erasing = erasing(field)

        erasing.startSwipe()
        erasing.select(2)
        erasing.endSwipe(lifted = true)

        assertEquals("город Москва ", field.text)
        assertEquals(listOf("selecting", "erased", "ended"), events)
    }

    @Test
    fun comingBackToTheStartPutsTheSelectionBackAndErasesNothing() {
        val field = Field("Привет. Как дела?", start = 3, end = 5)
        val erasing = erasing(field)

        erasing.startSwipe()
        erasing.select(1)
        erasing.select(0)
        erasing.select(1)
        erasing.select(0)
        erasing.endSwipe(lifted = true)

        assertEquals("Привет. Как дела?", field.text)
        assertEquals(3 to 5, field.start to field.end)
        // One swipe, however many times it comes back.
        assertEquals(listOf("selecting", "ended"), events)
    }

    @Test
    fun aCanceledSwipePutsTheSelectionBack() {
        val field = Field("Привет. Как дела?")
        val erasing = erasing(field)

        erasing.startSwipe()
        erasing.select(2)
        erasing.endSwipe(lifted = false)

        assertEquals("Привет. Как дела?", field.text)
        assertEquals(17 to 17, field.start to field.end)
        assertEquals(listOf("selecting", "ended"), events)
    }

    @Test
    fun aSelectionGrowsBackFromItsStartAndKeepsItsEnd() {
        val field = Field("Привет. Как дела?", start = 12, end = 16)
        val erasing = erasing(field)

        assertEquals(2, erasing.startSwipe())
        erasing.select(1)

        assertEquals("Как дела", field.selected)
    }

    @Test
    fun aSwipeSelectsNoMoreWordsThanThereAre() {
        val field = Field("Как дела?")
        val erasing = erasing(field)

        erasing.startSwipe()
        erasing.select(5)

        assertEquals("Как дела?", field.selected)
    }

    @Test
    fun aWordCutOffByWhatTheFieldGivesIsNotSelected() {
        val field = Field("Привет. Как дела?").apply { shown = 12 }
        val erasing = erasing(field)

        // "т. Как дела?": its first word may go on before it.
        assertEquals(2, erasing.startSwipe())
        erasing.select(2)

        assertEquals("Как дела?", field.selected)
    }

    @Test
    fun aFieldThatHidesItsTextGetsNoSwipe() {
        val field = Field("Как дела?").apply { tells = false }
        val erasing = erasing(field)

        assertEquals(0, erasing.startSwipe())
        erasing.select(1)
        erasing.endSwipe(lifted = true)

        assertEquals("Как дела?", field.text)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun aPressDeletesWhatTheFieldSays() {
        assertTrue(erasing(Field("а")).canDelete())
        assertFalse(erasing(Field("")).canDelete())
    }
}
