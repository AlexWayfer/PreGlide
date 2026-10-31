package name.alexwayfer.preglide.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page
import name.alexwayfer.preglide.engine.suggest.after
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeyTypingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bench = KeyboardBench { folder.root }
    private val field = bench.field
    private val keyboard = bench.keyboard

    @Before
    fun startTheField() {
        bench.session.start(field.inputType, 0, 0)
    }

    @Test
    fun lettersGoInAtTheCursorTheFirstOfASentenceACapital() {
        bench.type("ну")

        assertEquals("Ну", field.text)
        assertEquals(ShiftState.Off, keyboard.shift)
    }

    @Test
    fun aSentenceEndAsksForACapitalAgain() {
        bench.type("ну. к")

        assertEquals("Ну. К", field.text)
    }

    @Test
    fun capsLockStaysOn() {
        keyboard.shift = ShiftState.Locked
        bench.type("ну")

        assertEquals("НУ", field.text)
        assertEquals(ShiftState.Locked, keyboard.shift)
    }

    @Test
    fun shiftTurnsOnForTheNextLetter() {
        bench.type("ну ")
        bench.press(KeyAction.Shift)
        bench.type("к")

        assertEquals("Ну К", field.text)
    }

    @Test
    fun aLongPressOfSpaceShowsTheKeyboardsToSwitchTo() {
        bench.press(KeyAction.Space, longPress = true)

        assertEquals(1, bench.output.picked)
        assertEquals("", field.text)
    }

    @Test
    fun aSpaceRightAfterTheKeyboardsOwnIsDropped() {
        bench.type("при")
        // The strip offers it as the word typed so far begins.
        bench.typing.chose("Привет")
        bench.reported()
        assertEquals("Привет ", field.text)

        bench.press(KeyAction.Space)

        assertEquals("Привет ", field.text)
    }

    @Test
    fun aPasteLeavesTheKeyboardsOwnSpaceInsideTheText() {
        bench.type("при")
        bench.typing.chose("Привет")
        bench.reported()

        // What a paste does first, as the service wires it.
        bench.suggestions.ownSpace.forget()
        bench.press(KeyAction.Space)

        // The space after the paste is the user's, not one typed out of habit.
        assertEquals("Привет  ", field.text)
    }

    @Test
    fun typingAfterAChoiceBeforeAMarkStartsANewWord() {
        for (reported in listOf(false, true)) {
            for (mark in listOf(".", ",", "!", "?", ";", ":", "…", "»", "\"", ")")) {
                field.text = "Ну (\"при"
                field.after = mark
                bench.session.start(field.inputType, field.text.length, field.text.length)
                bench.typing.chose("привет")
                if (reported) bench.reported()
                assertEquals("Ну (\"привет", field.text)

                bench.type("бы")

                assertEquals("Ну (\"привет бы", field.text)
                assertEquals(mark, field.after)
            }
        }
    }

    @Test
    fun otherKeysCancelTheSpaceAfterAChoiceButShiftKeepsIt() {
        for (action in listOf(KeyAction.Backspace, KeyAction.Space, KeyAction.Letter(','))) {
            field.text = "Ну при"
            field.after = "."
            bench.session.start(field.inputType, field.text.length, field.text.length)
            bench.typing.chose("привет")
            bench.reported()
            bench.press(action)
            val before = field.text

            bench.type("б")

            assertEquals(before + "б", field.text)
        }
        field.text = "Ну при"
        bench.session.start(field.inputType, field.text.length, field.text.length)
        bench.typing.chose("привет")
        bench.reported()
        bench.press(KeyAction.Shift)
        bench.type("б")
        assertEquals("Ну привет Б", field.text)
    }

    @Test
    fun movingAwayAndBackCancelsTheSpaceAfterAChoice() {
        field.text = "Ну при"
        field.after = "."
        bench.session.start(field.inputType, field.text.length, field.text.length)
        bench.typing.chose("привет")
        bench.reported()
        bench.session.selectionChanged(0, 0)
        bench.reported()

        bench.type("б")

        assertEquals("Ну приветб", field.text)
    }

    @Test
    fun typingAfterChoosingAGlideBeforeAMarkStartsANewWord() {
        bench.type("ну ")
        field.after = "."
        bench.glide("привет")
        bench.typing.chose("приват")
        bench.reported()

        bench.type("бы")

        assertEquals("Ну приват бы", field.text)
        assertEquals(".", field.after)
    }

    @Test
    fun aFieldRestartOrSelectionCancelsTheSpaceAfterAChoice() {
        for (restart in listOf(false, true)) {
            field.text = "Ну при"
            field.after = "."
            bench.session.start(field.inputType, field.text.length, field.text.length)
            bench.typing.chose("привет")
            bench.reported()
            if (restart) {
                bench.session.finish()
                bench.session.start(field.inputType, field.text.length, field.text.length)
            } else {
                bench.suggestions.update(cursorIsPoint = false)
                bench.suggestions.update(cursorIsPoint = true)
            }

            bench.type("б")

            assertEquals("Ну приветб", field.text)
        }
    }

    @Test
    fun choosingBeforeAnExistingSpaceAddsNoSecondSpace() {
        field.text = "Ну при"
        field.after = " ."
        bench.session.start(field.inputType, field.text.length, field.text.length)
        bench.typing.chose("привет")
        bench.reported()

        bench.type("б")

        assertEquals("Ну приветб", field.text)
        assertEquals(" .", field.after)
    }

    @Test
    fun aMarkRightAfterAMarkThatTookTheKeyboardsSpaceGoesRightOn() {
        bench.press(KeyAction.Letter('"'))
        bench.type("при")
        bench.typing.chose("Привет")
        bench.reported()
        bench.press(KeyAction.Letter('"'))
        bench.press(KeyAction.Letter(','))

        assertEquals("\"Привет\", ", field.text)
    }

    @Test
    fun aBackspaceRightAfterAMarkTookTheKeyboardsSpaceBringsTheSpaceBackBeforeIt() {
        bench.type("при")
        bench.typing.chose("Привет")
        bench.reported()
        bench.press(KeyAction.Letter(':'))
        assertEquals("Привет: ", field.text)

        bench.press(KeyAction.Backspace)
        assertEquals("Привет :", field.text)
        bench.type("smile")

        assertEquals("Привет :smile", field.text)
    }

    @Test
    fun backspacesThroughMarksThatTookTheKeyboardsSpaceLeaveItTheKeyboardsOwnAgain() {
        bench.type("при")
        bench.typing.chose("Привет")
        bench.reported()
        bench.press(KeyAction.Letter('?'))
        bench.press(KeyAction.Letter('!'))
        assertEquals("Привет?! ", field.text)

        bench.press(KeyAction.Backspace)
        assertEquals("Привет ?!", field.text)
        bench.press(KeyAction.Backspace)
        assertEquals("Привет ?", field.text)
        bench.press(KeyAction.Backspace)
        assertEquals("Привет ", field.text)
        bench.press(KeyAction.Letter('.'))

        assertEquals("Привет. ", field.text)
    }

    @Test
    fun aCharFromTheSymbolsKeepsItsCase() {
        bench.type("ну")
        bench.press(KeyAction.ShowPage(Page.Symbols))
        keyboard.shift = ShiftState.Once
        bench.press(KeyAction.Letter('!'))

        assertEquals("Ну!", field.text)
    }

    @Test
    fun aGapTypesNothing() {
        bench.type("ну")
        bench.press(KeyAction.Gap)

        assertEquals("Ну", field.text)
    }

    @Test
    fun withoutAFieldShiftStaysAsItIs() {
        keyboard.shift = ShiftState.Once
        bench.output.connected = false

        bench.typing.autoCapitalize()

        assertEquals(ShiftState.Once, keyboard.shift)
    }

    @Test
    fun aTypoEndedWithASpaceIsCorrectedAndABackspacePutsItBack() {
        bench.type("как пртвет ")
        assertEquals("Как привет ", field.text)

        bench.press(KeyAction.Backspace)

        assertEquals("Как пртвет", field.text)
    }

    @Test
    fun aMoveOfTheCursorPutsTheCorrectionPastUndoing() {
        bench.type("как пртвет ")
        bench.session.selectionChanged(0, 0)
        bench.reported()

        bench.press(KeyAction.Backspace)

        assertEquals("Как привет", field.text)
    }

    @Test
    fun backspaceDeletesACharWithTheKey() {
        bench.type("ну")
        bench.press(KeyAction.Backspace)

        assertEquals("Н", field.text)
    }

    @Test
    fun aBackspaceRightAfterAGlideErasesItsWord() {
        bench.type("ну ")
        bench.glide("привет")
        assertEquals("Ну привет", field.text)

        bench.press(KeyAction.Backspace)

        assertEquals("Ну ", field.text)
    }

    @Test
    fun aHyphenAndADigitTypedOnAGlideWhoseSpaceWasErasedAreLearnedWithIt() {
        bench.type("ну ")
        bench.glide("привет")
        bench.press(KeyAction.Space)
        bench.press(KeyAction.Backspace)
        bench.type("-3 ")

        assertEquals("Ну привет-3 ", field.text)
        assertEquals(1, bench.model.personal.learned("привет-3")?.count)
        assertNull(bench.model.personal.learned("привет"))
    }

    @Test
    fun anEmojiChosenInTheStripEndsTheWordAsASpaceWould() {
        bench.type("ну как")

        bench.emojiChoice.choose("😁")
        bench.reported()

        assertEquals("Ну как 😁 ", field.text)
        assertEquals(1, bench.model.personal.learned("как")?.count)
        assertEquals(listOf("как"), bench.model.personal.after("ну").map { it.spelling })

        bench.type(".")
        assertEquals("Ну как 😁. ", field.text)
    }

    @Test
    fun aLetterRightAfterAGlideStartsAWordOfItsOwnLearnedAfterTheGlidedOne() {
        bench.type("ну ")
        bench.glide("привет")

        bench.type("как ")

        assertEquals("Ну привет как ", field.text)
        assertEquals(1, bench.model.personal.learned("как")?.count)
        assertEquals(listOf("как"), bench.model.personal.after("привет").map { it.spelling })
    }

    @Test
    fun aLetterWhereAGlidedWordWasErasedStartsAWordOfItsOwn() {
        bench.type("ну ")
        bench.glide("привет")
        bench.glide("как")
        assertEquals("Ну привет как", field.text)
        bench.press(KeyAction.Backspace)
        assertEquals("Ну привет", field.text)

        bench.type("как ")

        assertEquals("Ну привет как ", field.text)
        assertEquals(listOf("как"), bench.model.personal.after("привет").map { it.spelling })
    }

    @Test
    fun aLetterAfterAnotherBackspaceGoesOnWithTheWordBefore() {
        bench.type("ну ")
        bench.glide("привет")
        bench.glide("как")
        bench.press(KeyAction.Backspace)
        bench.press(KeyAction.Backspace)
        assertEquals("Ну приве", field.text)

        bench.type("т")

        assertEquals("Ну привет", field.text)
    }

    @Test
    fun aLetterWhereAGlidedWordAfterASpaceWasErasedGoesRightOn() {
        bench.type("ну ")
        bench.glide("привет")
        bench.press(KeyAction.Backspace)

        bench.type("к")

        assertEquals("Ну к", field.text)
    }

    @Test
    fun aMarkRightAfterAGlideGoesRightAfterIt() {
        bench.type("ну ")
        bench.glide("привет")

        bench.press(KeyAction.Letter(','))

        assertEquals("Ну привет,", field.text)
    }

    @Test
    fun aWordChosenRightAfterAGlideSwapsInForIt() {
        bench.type("ну ")
        bench.glide("привет")

        bench.typing.chose("приват")

        // With a space after it, as a word chosen from the strip.
        assertEquals("Ну приват ", field.text)
    }

    @Test
    fun aBackspaceThroughTheSpaceAfterAGlideBringsBackItsWordsToSwapIn() {
        bench.type("ну ")
        bench.glide("привет")
        val offered = keyboard.suggestions
        bench.type(" ")

        bench.press(KeyAction.Backspace)

        assertEquals("Ну привет", field.text)
        assertEquals("привет", bench.suggestions.glides.last()?.word)
        assertEquals(offered, keyboard.suggestions)
        bench.typing.chose("приват")
        bench.reported()
        assertEquals("Ну приват ", field.text)
        assertNull(bench.model.personal.learned("привет"))
        assertEquals(1, bench.model.personal.learned("приват")?.count)
    }

    @Test
    fun aGlideBroughtBackThroughAMarkOffersItsWordsButTheNextBackspaceErasesAChar() {
        bench.type("ну ")
        bench.glide("привет")
        val offered = keyboard.suggestions
        bench.type(",")

        bench.press(KeyAction.Backspace)
        assertEquals(offered, keyboard.suggestions)
        bench.press(KeyAction.Backspace)

        // Only the backspace right after the glide erases its word whole: back here, the user fixes its end.
        assertEquals("Ну приве", field.text)
    }

    @Test
    fun aBackspaceThroughMoreThanTheCharAfterAGlideLeavesItDone() {
        bench.type("ну ")
        bench.glide("привет")
        bench.type(" к")

        bench.press(KeyAction.Backspace)
        bench.press(KeyAction.Backspace)

        assertEquals("Ну привет", field.text)
        assertNull(bench.suggestions.glides.last())
    }

    @Test
    fun withTheSettingOffABackspaceThroughTheSpaceAfterAGlideLeavesItDone() {
        bench.bringsBackGlides = false
        bench.type("ну ")
        bench.glide("привет")
        bench.type(" ")

        bench.press(KeyAction.Backspace)
        assertNull(bench.suggestions.glides.last())
        bench.press(KeyAction.Backspace)

        assertEquals("Ну приве", field.text)
    }

    @Test
    fun enterRunsTheFieldsAction() {
        field.imeOptions = EditorInfo.IME_ACTION_SEND
        bench.type("ну")

        bench.press(KeyAction.Enter)

        assertEquals(listOf(EditorInfo.IME_ACTION_SEND), bench.output.actions)
        assertEquals("Ну", field.text)
    }

    @Test
    fun enterWithoutAnActionBreaksTheLine() {
        bench.type("ну")

        bench.press(KeyAction.Enter)

        assertEquals("Ну\n", field.text)
        assertEquals(emptyList<Int>(), bench.output.actions)
    }

    @Test
    fun aLineBreakAfterASymbolReturnsToTheLetters() {
        bench.press(KeyAction.ShowPage(Page.Symbols))
        bench.press(KeyAction.Letter(')'))

        bench.press(KeyAction.Enter)

        assertEquals(")\n", field.text)
        assertEquals(Page.Letters, keyboard.page)
    }

    @Test
    fun aNewLineStartsWithShiftWhereTheFieldAsksForCapitals() {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        bench.type("ну.")
        assertEquals(ShiftState.Off, keyboard.shift)

        bench.press(KeyAction.Enter)

        assertEquals(ShiftState.Once, keyboard.shift)
    }

    @Test
    fun aNewLineStartsWithoutShiftWhereTheFieldAsksForNoCapitals() {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        bench.type("ну.")
        bench.press(KeyAction.Shift)
        assertEquals(ShiftState.Once, keyboard.shift)

        bench.press(KeyAction.Enter)

        assertEquals(ShiftState.Off, keyboard.shift)
    }

    @Test
    fun aPageKeyShowsItsPage() {
        bench.press(KeyAction.ShowPage(Page.Symbols))

        assertEquals(Page.Symbols, keyboard.page)
    }

    @Test
    fun withoutAFieldTheKeysDoNothing() {
        bench.output.connected = false
        bench.type("ну")

        assertEquals("", field.text)
    }

    @Test
    fun typingOnAnswersTheStripsQuestionOfRemovingAWord() {
        keyboard.removing = "привет"
        bench.type("н")

        assertNull(keyboard.removing)
    }

    @Test
    fun aWordRemovedFromTheStripIsSuggestedNoMore() {
        bench.type("ну прив")
        assertTrue("привет" in keyboard.suggestions)

        bench.typing.removed("привет")

        assertFalse("привет" in keyboard.suggestions)
    }

    @Test
    fun selectingWordsToEraseAnswersTheStripsQuestionToo() {
        keyboard.removing = "привет"

        bench.typing.beforeSelectingWords()

        assertNull(keyboard.removing)
    }

    @Test
    fun theStripStaysAsItWasWhileASwipeFromBackspaceSelects() {
        bench.type("при")
        val shown = keyboard.suggestions
        assertTrue(shown.isNotEmpty())

        bench.typing.beforeSelectingWords()
        // The swipe selected all there is.
        bench.session.selectionChanged(0, 3)
        assertEquals(shown, keyboard.suggestions)

        bench.typing.afterSelectingWords()
        field.text = ""
        bench.session.selectionChanged(0, 0)
        assertEquals(emptyList<String>(), keyboard.suggestions)
    }

    @Test
    fun erasedWordsLeaveShiftAsTheFieldWantsIt() {
        bench.type("ну. как")
        // A swipe from backspace erased "как".
        field.text = "Ну. "

        bench.typing.afterErasingWords(capital = false)

        assertEquals(ShiftState.Once, keyboard.shift)
    }

    @Test
    fun erasedWordsThatBeganWithACapitalTurnShiftBackOn() {
        bench.type("ну как")
        // A swipe from backspace erased "Как", typed with shift.
        field.text = "Ну "

        bench.typing.afterErasingWords(capital = true)

        assertEquals(ShiftState.Once, keyboard.shift)
    }

    @Test
    fun backspacingToTheStartOfAWordTypedWithShiftTurnsShiftBackOn() {
        bench.type("город ")
        bench.press(KeyAction.Shift)
        bench.type("москва")
        assertEquals("Город Москва", field.text)

        repeat("осква".length) { bench.press(KeyAction.Backspace) }
        assertEquals(ShiftState.Off, keyboard.shift)
        bench.press(KeyAction.Backspace)

        assertEquals("Город ", field.text)
        assertEquals(ShiftState.Once, keyboard.shift)
    }

    @Test
    fun backspacingALowercaseWordLeavesShiftAsTheFieldWantsIt() {
        bench.type("город москва")

        repeat("москва".length) { bench.press(KeyAction.Backspace) }

        assertEquals(ShiftState.Off, keyboard.shift)
    }

    @Test
    fun backspacingACapitalKeepsCapsLock() {
        bench.type("город ")
        bench.press(KeyAction.Shift)
        bench.press(KeyAction.Shift)
        bench.type("мск")

        repeat("мск".length) { bench.press(KeyAction.Backspace) }

        assertEquals(ShiftState.Locked, keyboard.shift)
    }
}
