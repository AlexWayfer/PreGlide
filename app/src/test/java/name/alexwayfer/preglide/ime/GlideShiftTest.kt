package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GlideShiftTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bench = KeyboardBench { folder.root }

    @Test
    fun aGlidedWordErasedWholeGivesItsShiftBackForTheGlideMadeAgain() {
        bench.session.start(bench.field.inputType, 0, 0)
        bench.type("ну ")
        bench.press(KeyAction.Shift)
        bench.glide("привет")
        assertEquals("Ну Привет", bench.field.text)
        assertEquals(ShiftState.Off, bench.keyboard.shift)

        bench.press(KeyAction.Backspace)

        assertEquals("Ну ", bench.field.text)
        assertEquals(ShiftState.Once, bench.keyboard.shift)
    }

    @Test
    fun aGlidedWordTypedWithoutShiftLeavesTheShiftToTheField() {
        bench.session.start(bench.field.inputType, 0, 0)
        bench.type("ну ")
        bench.glide("привет")

        bench.press(KeyAction.Backspace)

        assertEquals(ShiftState.Off, bench.keyboard.shift)
    }

    @Test
    fun theShiftAfterAnErasedGlideIsTheWordsOneLetterShiftOrTheFieldsWish() {
        assertEquals(ShiftState.Once, shiftAfterGlideErased(ShiftState.Once, wanted = ShiftState.Off))
        assertEquals(ShiftState.Off, shiftAfterGlideErased(ShiftState.Off, wanted = ShiftState.Off))
        assertEquals(ShiftState.Once, shiftAfterGlideErased(ShiftState.Off, wanted = ShiftState.Once))
        assertEquals(ShiftState.Locked, shiftAfterGlideErased(ShiftState.Locked, wanted = ShiftState.Locked))
    }
}
