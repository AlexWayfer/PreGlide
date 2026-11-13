package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PeriodSpaceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bench = KeyboardBench { folder.root }
    private val field = bench.field

    @Before
    fun startField() {
        bench.session.start(field.inputType, 0, 0)
    }

    @Test
    fun doubleSpaceIsOffUntilEnabled() {
        bench.type("ну  ")
        assertEquals("Ну  ", field.text)
    }

    @Test
    fun doubleSpaceEndsTheSentenceAndBackspaceUndoesIt() {
        enable()
        bench.type("ну  ")
        assertEquals("Ну. ", field.text)
        assertEquals(ShiftState.Once, bench.keyboard.shift)
        bench.press(KeyAction.Backspace)
        assertEquals("Ну  ", field.text)
        bench.press(KeyAction.Backspace)
        assertEquals("Ну ", field.text)
    }

    @Test
    fun undoAlsoWorksPastTheTextContextLimit() {
        enable()
        val text = "а".repeat(TEXT_BEFORE_CURSOR * 2)
        field.text = text
        bench.session.start(field.inputType, text.length, text.length)
        bench.type("  ")
        assertEquals("$text. ", field.text)
        bench.press(KeyAction.Backspace)
        assertEquals("$text  ", field.text)
    }

    @Test
    fun typingOnLeavesThePeriodInPlace() {
        enable()
        bench.type("ну  к")
        assertEquals("Ну. К", field.text)
        bench.press(KeyAction.Backspace)
        assertEquals("Ну. ", field.text)
        bench.press(KeyAction.Backspace)
        assertEquals("Ну.", field.text)
    }

    @Test
    fun twoExplicitSpacesAfterAChoiceOrAGlideEndTheSentence() {
        enable()
        bench.typing.chose("Привет")
        bench.reported()
        bench.press(KeyAction.Space)
        assertEquals("Привет ", field.text)
        bench.press(KeyAction.Space)
        assertEquals("Привет. ", field.text)
        bench.type("ну")
        bench.glide("привет")
        bench.type("  ")
        assertEquals("Привет. Ну привет. ", field.text)
    }

    @Test
    fun theSpaceThatCorrectsATypoCountsAsTheFirstTap() {
        enable()
        bench.type("привте ")
        assertEquals("Привет ", field.text)
        bench.press(KeyAction.Space)
        assertEquals("Привет. ", field.text)
    }

    @Test
    fun selectionCursorMovesAndFieldRestartsCancelThePair() {
        enable()
        for (reset in listOf<() -> Unit>(
            { bench.session.selectionChanged(0, 0); bench.reported() },
            { bench.suggestions.update(cursorIsPoint = false); bench.suggestions.update(cursorIsPoint = true) },
            { bench.session.finish(); bench.session.start(field.inputType, field.text.length, field.text.length) },
            { bench.session.languageChanged(Language.Russian) },
        )) {
            field.text = "Ну"
            bench.session.start(field.inputType, field.text.length, field.text.length)
            bench.press(KeyAction.Space)
            reset()
            bench.press(KeyAction.Space)
            assertEquals("Ну  ", field.text)
        }
    }

    @Test
    fun anotherActionOrALongPressCancelsThePair() {
        enable()
        for (action in listOf(KeyAction.Shift, KeyAction.ShowPage(Page.Symbols), KeyAction.Space)) {
            field.text = "Ну"
            bench.session.start(field.inputType, field.text.length, field.text.length)
            bench.press(KeyAction.Space)
            bench.press(action, longPress = action == KeyAction.Space)
            bench.press(KeyAction.Space)
            assertEquals("Ну  ", field.text)
        }
    }

    @Test
    fun sensitiveAndStructuredFieldsKeepTheirSpaces() {
        enable()
        for (inputType in listOf(
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        )) {
            field.inputType = inputType
            field.text = "hello"
            bench.session.start(inputType, field.text.length, field.text.length)
            bench.type("  ")
            assertEquals("hello  ", field.text)
        }
    }

    private fun enable() {
        bench.suggestions.periodSpace.enabled = { true }
    }
}
