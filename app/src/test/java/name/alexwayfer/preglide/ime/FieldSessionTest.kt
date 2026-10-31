package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FieldSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bench = KeyboardBench { folder.root }
    private val keyboard = bench.keyboard
    private val session = bench.session

    @Test
    fun aFieldStartsOnTheLettersWithShiftAsTheFieldWantsIt() {
        keyboard.page = Page.Symbols
        keyboard.shift = ShiftState.Locked
        keyboard.removing = "привет"
        keyboard.clipboardOpen = true
        keyboard.clearing = true
        keyboard.removingClip = SavedClip.Text("привет", copiedAt = 0)
        keyboard.selectingWords = true
        keyboard.emoji.open = true
        keyboard.emoji.query = "кот"

        session.start(InputType.TYPE_CLASS_TEXT, 0, 0)

        assertEquals(Page.Letters, keyboard.page)
        assertEquals(ShiftState.Once, keyboard.shift)
        assertNull(keyboard.removing)
        assertFalse(keyboard.clipboardOpen)
        assertFalse(keyboard.clearing)
        assertNull(keyboard.removingClip)
        assertFalse(keyboard.selectingWords)
        assertFalse(keyboard.emoji.open)
        assertNull(keyboard.emoji.query)
        assertTrue(keyboard.fieldGlides)
    }

    @Test
    fun aNumberFieldStartsOnTheNumberPadAndAPhoneFieldOnThePhonePad() {
        session.start(InputType.TYPE_CLASS_NUMBER, 0, 0)
        assertEquals(Page.Digits, keyboard.page)

        session.start(InputType.TYPE_CLASS_PHONE, 0, 0)
        assertEquals(Page.Phone, keyboard.page)

        session.start(InputType.TYPE_CLASS_TEXT, 0, 0)
        assertEquals(Page.Letters, keyboard.page)
    }

    @Test
    fun noGlidesInAnEmailOrPasswordField() {
        session.start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0, 0)
        assertFalse(keyboard.fieldGlides)

        session.start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0, 0)
        assertFalse(keyboard.fieldGlides)
    }

    @Test
    fun aFieldThatAsksForNoSuggestionsGlides() {
        session.start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0, 0)
        assertTrue(keyboard.fieldGlides)
    }

    @Test
    fun theSelectionIsKeptStartFirst() {
        session.start(InputType.TYPE_CLASS_TEXT, 5, 2)
        assertEquals(2 to 5, session.selectionStart to session.selectionEnd)

        session.selectionChanged(7, 3)
        assertEquals(3 to 7, session.selectionStart to session.selectionEnd)
    }

    @Test
    fun theLanguageChangesWhenTheKeyboardHasIt() {
        keyboard.language = Language.Russian

        session.languageChanged(Language.English)
        assertEquals(Language.English, keyboard.language)

        session.languageChanged(null)
        assertEquals(Language.English, keyboard.language)
    }

    @Test
    fun leavingTheFieldLearnsTheWordTypedAndEmptiesTheStrip() {
        session.start(InputType.TYPE_CLASS_TEXT, 0, 0)
        bench.type("прив")
        assertTrue(keyboard.suggestions.isNotEmpty())

        session.finish()

        assertEquals(1, bench.model.personal.learned("прив")?.count)
        assertEquals(emptyList<String>(), keyboard.suggestions)
    }

    @Test
    fun switchingTheLanguageLeavesTheWordBeingTyped() {
        session.start(InputType.TYPE_CLASS_TEXT, 0, 0)
        bench.type("прив")

        session.languageChanged(Language.English)

        assertNull(bench.model.personal.learned("прив"))
    }

    @Test
    fun aWordPickedForOneTypedRightBeforeAnotherAtTheFieldStartLeavesThatOneAfterIt() {
        bench.field.after = "alexwayfer.name"
        session.start(InputType.TYPE_CLASS_TEXT, 0, 0)
        bench.type("htt")

        bench.typing.chose("https")

        assertEquals("https", bench.field.text)
        assertEquals("alexwayfer.name", bench.field.after)
    }

    @Test
    fun aWordPickedForOneTypedRightBeforeAnotherTheCursorWasMovedToLeavesThatOneAfterIt() {
        session.start(InputType.TYPE_CLASS_TEXT, 0, 0)
        bench.field.after = "alexwayfer.name"
        bench.reported()
        bench.type("htt")

        bench.typing.chose("https")

        assertEquals("https", bench.field.text)
        assertEquals("alexwayfer.name", bench.field.after)
    }

    @Test
    fun aWordPickedWithTheCursorMovedIntoItReplacesItWhole() {
        session.start(InputType.TYPE_CLASS_TEXT, 0, 0)
        bench.field.text = "Ну прив"
        bench.field.after = "ет всем"
        bench.reported()

        bench.typing.chose("привет")

        assertEquals("Ну привет", bench.field.text)
        assertEquals(" всем", bench.field.after)
    }
}
