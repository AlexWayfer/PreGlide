package name.alexwayfer.preglide.ime

import android.text.InputType
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AutocorrectTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** A field that holds its text, with the cursor at its end, which asks for corrections unless told otherwise. */
    private class Field : SuggestionField {
        var text = ""

        /** What follows the cursor, which stays after it. */
        var after = ""
        override var inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        override val imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = after.take(length)

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
        }
    }

    private val field = Field()
    private val now = testDispatchers.now
    private val personal = PersonalData(PersonalStore { File(folder.root, "personal") }, CoroutineScope(now), now)

    // Lazy: the words load from the folder, which the rule makes only once the test starts.
    private val model by lazy {
        val dictionary = Dictionary.parse(sequenceOf("привет\t100000", "как\t50000"))
        LanguageModel(dictionary, personal = personal.load(personalName(Language.Russian)))
    }
    private val models = LanguageModels(CoroutineScope(now), now) { model }
    private val keyboard = KeyboardState()
    private var corrects = true
    private val suggestions =
        WordSuggestions(keyboard, field, KeyboardWork(CoroutineScope(now), now), WordSources(models), { corrects })
    private val learning = Learning(
        field,
        language = { Language.Russian },
        models = { suggestions.model(it) },
        emails = { null },
        personal = personal,
        clock = { 42 },
    )
    private val autocorrect = Autocorrect(suggestions, learning)

    /** Types [typed] as the keyboard does, a key at a time, and reports each edit back as the field does. */
    private fun type(typed: String) {
        for (char in typed) {
            autocorrect.beforeKey(KeyAction.Letter(char))
            // A correction ending with a space brings the space itself.
            if (!autocorrect.beforeTyping(char) || char != ' ') {
                learning.beforeTyping(char)
                learning.expectEdit(suggestions.ownSpace.type(char))
            }
            reported()
        }
    }

    private fun backspace() {
        autocorrect.beforeKey(KeyAction.Backspace)
        if (!autocorrect.revert()) {
            learning.beforeBackspace()
            field.replace(1, "")
        }
        reported()
    }

    private fun reported() {
        learning.selectionChanged(field.text.length, field.text.length)
        suggestions.update()
    }

    @Test
    fun aTypoEndedWithASpaceIsCorrectedAndOnlyTheWordMeantIsLearned() {
        type("Как пртвет ")

        assertEquals("Как привет ", field.text)
        assertEquals(1, model.personal.learned("привет")?.count)
        assertNull(model.personal.learned("пртвет"))
    }

    @Test
    fun theStripShowsTheCorrectionFirstBeforeTheWordEnds() {
        type("Как пртвет")

        assertEquals("привет", keyboard.correction)
        assertEquals("привет", keyboard.suggestions.first())
    }

    @Test
    fun aTypoEndedWithAMarkIsCorrectedRightBeforeIt() {
        type("Как пртвет, как")

        assertEquals("Как привет, как", field.text)
    }

    @Test
    fun aTypoBeforeAClosingQuoteOrBracketIsCorrectedToo() {
        type("Он сказал \"пртвет\" (пртвет)")

        assertEquals("Он сказал \"привет\" (привет)", field.text)
    }

    @Test
    fun aBackspaceRightAfterPutsTheTypedWordBackKeptOnceEndedSo() {
        type("Как пртвет ")
        backspace()
        assertEquals("Как пртвет", field.text)
        // Not yet: the user may still change it.
        assertNull(model.personal.learned("пртвет"))
        assertNull(model.personal.learned("привет"))

        type(" как ")

        assertEquals("Как пртвет как ", field.text)
        assertEquals(1, model.personal.learned("пртвет")?.kept)
    }

    @Test
    fun aBackspaceRightAfterAMarkPutsTheTypedWordBackKeptOnceEndedSo() {
        type("Как пртвет,")
        backspace()
        assertEquals("Как пртвет", field.text)
        assertNull(model.personal.learned("привет"))

        type(", как ")

        assertEquals("Как пртвет, как ", field.text)
        assertEquals(1, model.personal.learned("пртвет")?.kept)
    }

    @Test
    fun aBackspaceAfterTheMarkAndMoreDeletesAsUsual() {
        type("Как пртвет, ")
        backspace()
        backspace()

        assertEquals("Как привет", field.text)
    }

    @Test
    fun aWordPutBackAndChangedIsLearnedAsItEnds() {
        type("Как пртвет ")
        backspace()
        backspace()
        type("ы ")

        assertEquals("Как пртвеы ", field.text)
        assertEquals(0, model.personal.learned("пртвеы")?.kept)
        assertNull(model.personal.learned("пртвет"))
    }

    @Test
    fun aBackspaceAfterAnythingElseDeletesAsUsual() {
        type("Как пртвет к")
        backspace()
        backspace()

        assertEquals("Как привет", field.text)
    }

    @Test
    fun aSpaceInsideAWordSplitsItWithoutCorrecting() {
        type("Как пртвет")
        field.after = "ик"
        reported()
        assertNull(keyboard.correction)

        type(" ")

        assertEquals("Как пртвет ", field.text)
    }

    @Test
    fun aGlidedWordIsNotCorrectedAsTheNextKeyEndsIt() {
        type("Как пртвет")

        autocorrect.beforeKey(KeyAction.Space, afterGlide = true)

        assertFalse(autocorrect.beforeTyping(' '))
        assertEquals("Как пртвет", field.text)
    }

    @Test
    fun nothingIsCorrectedWithAutocorrectOff() {
        corrects = false
        type("Как пртвет ")

        assertEquals("Как пртвет ", field.text)
        assertNull(keyboard.correction)
    }

    @Test
    fun nothingIsCorrectedInAFieldThatDoesNotAskForIt() {
        field.inputType = InputType.TYPE_CLASS_TEXT
        type("Как пртвет ")

        assertEquals("Как пртвет ", field.text)
    }
}
