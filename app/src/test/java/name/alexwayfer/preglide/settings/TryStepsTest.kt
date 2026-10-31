package name.alexwayfer.preglide.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TryStepsTest {
    private fun status(enabled: Boolean, selected: Boolean) =
        KeyboardStatus(enabled, selected, spellChecker = false, notices = false)

    private fun signals(
        enabled: Boolean = true,
        selected: Boolean = true,
        focused: Boolean = true,
        imeVisible: Boolean = false,
    ) = TrySignals(enabled, selected, focused, imeVisible)

    @Test
    fun theButtonAsksToTurnTheKeyboardOnThenToChooseItThenOpensTheField() {
        assertNull(startTry(status(enabled = false, selected = false)))
        assertEquals(TryStep.Choosing, startTry(status(enabled = true, selected = false)))
        assertEquals(TryStep.Opening, startTry(status(enabled = true, selected = true)))
    }

    @Test
    fun backFromTheKeyboardListTheKeyboardJustTurnedOnIsChosenNext() {
        val off = signals(enabled = false, selected = false)

        // The list has not opened yet, then the user is in it.
        assertEquals(TryStep.Enabling, nextTryStep(TryStep.Enabling, off))
        assertEquals(TryStep.EnablingAway, nextTryStep(TryStep.Enabling, off.copy(focused = false)))
        assertEquals(TryStep.EnablingAway, nextTryStep(TryStep.EnablingAway, off.copy(focused = false)))

        assertEquals(TryStep.Choosing, nextTryStep(TryStep.EnablingAway, off.copy(enabled = true)))
        assertEquals(TryStep.Opening, nextTryStep(TryStep.EnablingAway, signals()))
        assertEquals(TryStep.Closed, nextTryStep(TryStep.EnablingAway, off))
    }

    @Test
    fun choosingTheKeyboardInThePickerOpensTheField() {
        val other = signals(selected = false)

        assertEquals(TryStep.Choosing, nextTryStep(TryStep.Choosing, other))
        assertEquals(TryStep.ChoosingAway, nextTryStep(TryStep.Choosing, other.copy(focused = false)))
        assertEquals(TryStep.Opening, nextTryStep(TryStep.ChoosingAway, signals()))
        // The picker takes no focus on some devices: the choice still counts.
        assertEquals(TryStep.Opening, nextTryStep(TryStep.Choosing, signals()))
    }

    @Test
    fun aPickerLeftWithAnotherKeyboardOpensNothing() {
        assertEquals(TryStep.Closed, nextTryStep(TryStep.ChoosingAway, signals(selected = false)))
    }

    @Test
    fun theFieldGoesWithTheKeyboard() {
        assertEquals(TryStep.Opening, nextTryStep(TryStep.Opening, signals()))
        assertEquals(TryStep.Open, nextTryStep(TryStep.Opening, signals(imeVisible = true)))
        assertEquals(TryStep.Open, nextTryStep(TryStep.Open, signals(imeVisible = true)))
        assertEquals(TryStep.Closed, nextTryStep(TryStep.Open, signals()))
        assertEquals(TryStep.Closed, nextTryStep(TryStep.Closed, signals(imeVisible = true)))
    }

    @Test
    fun theFieldStaysWhileTheUserSwitchesToAnotherKeyboard() {
        val switching = signals(selected = false).copy(switched = true)

        assertEquals(TryStep.Switching, nextTryStep(TryStep.Open, switching))
        // Until the other one comes up, which the field then sits over as over this one.
        assertEquals(TryStep.Switching, nextTryStep(TryStep.Switching, switching))
        assertEquals(TryStep.Open, nextTryStep(TryStep.Switching, switching.copy(imeVisible = true)))
        // The other one put down with its own key or back takes the field with it.
        assertEquals(TryStep.Closed, nextTryStep(TryStep.Open, signals(selected = false)))
    }

    @Test
    fun aKeyboardIsSwitchedWhenTheChosenOneIsNotTheOneLastUp() {
        assertTrue(keyboardSwitched("a/.Gboard", "a/.PreGlide"))
        assertFalse(keyboardSwitched("a/.PreGlide", "a/.PreGlide"))
        // Before any keyboard came up there is nothing to switch from.
        assertFalse(keyboardSwitched(null, "a/.PreGlide"))
    }

    @Test
    fun theFieldClosesAsTheScreenStops() {
        assertEquals(TryStep.Closed, stepOnStop(TryStep.Opening))
        assertEquals(TryStep.Closed, stepOnStop(TryStep.Open))
        assertEquals(TryStep.Closed, stepOnStop(TryStep.Switching))
    }

    @Test
    fun theWayThroughTheSystemGoesOnAsTheScreenStops() {
        val kept = TryStep.entries.filterNot(::fieldOut)

        assertEquals(
            listOf(TryStep.Closed, TryStep.Enabling, TryStep.EnablingAway, TryStep.Choosing, TryStep.ChoosingAway),
            kept,
        )
        assertEquals(kept, kept.map(::stepOnStop))
    }

    @Test
    fun theButtonHidesOnlyWhileTheFieldIsOut() {
        val hidden = TryStep.entries.filterNot(::showsTryButton)

        assertEquals(listOf(TryStep.Opening, TryStep.Open, TryStep.Switching), hidden)
        assertTrue(showsTryButton(TryStep.ChoosingAway))
        assertFalse(showsTryButton(TryStep.Open))
    }

    @Test
    fun backClosesTheFieldAllTheWhileItIsOut() {
        val closing = TryStep.entries.filter(::backClosesField)

        // With the keyboard up too: back may reach the screen before the keyboard.
        assertEquals(listOf(TryStep.Opening, TryStep.Open, TryStep.Switching), closing)
    }

    @Test
    fun theButtonIsOnThePagesThatChangeHowTheKeyboardLooksOrTypes() {
        val withButton = SettingsPage.entries.filter { hasTryButton(it.name) }

        assertEquals(
            listOf(
                SettingsPage.Main,
                SettingsPage.Appearance,
                SettingsPage.Colors,
                SettingsPage.Font,
                SettingsPage.Layout,
                SettingsPage.Input,
                SettingsPage.Suggestions,
                SettingsPage.Gestures,
            ),
            withButton,
        )
        // Before the first page is on the back stack, the screen is starting on it.
        assertTrue(hasTryButton(null))
        assertFalse(hasTryButton("NoSuchPage"))
    }

    @Test
    fun theFieldIsOutWhileItWaitsForTheKeyboardAndOverIt() {
        assertEquals(listOf(TryStep.Opening, TryStep.Open, TryStep.Switching), TryStep.entries.filter(::fieldOut))
    }

    @Test
    fun thePageGetsRoomForTheButtonOverTheSystemBars() {
        assertEquals(104.dp, pageBottom(barsBottom = 24.dp, keyboardHeight = 0.dp, fieldHeight = 0.dp, 80.dp))
    }

    @Test
    fun thePageGetsRoomForTheFieldOverTheKeyboardAsItRises() {
        // Halfway up the keyboard is still below the button's room; then the field over it reaches higher.
        assertEquals(104.dp, pageBottom(barsBottom = 24.dp, keyboardHeight = 40.dp, fieldHeight = 56.dp, 80.dp))
        assertEquals(356.dp, pageBottom(barsBottom = 24.dp, keyboardHeight = 300.dp, fieldHeight = 56.dp, 80.dp))
    }

    @Test
    fun thePagesPaddingKeepsItsSidesAndTopWithItsNewBottom() {
        val padding = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
        val roomy = withBottom(padding, 80.dp, LayoutDirection.Ltr)

        assertEquals(80.dp, roomy.calculateBottomPadding())
        assertEquals(2.dp, roomy.calculateTopPadding())
        assertEquals(1.dp, roomy.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(3.dp, roomy.calculateEndPadding(LayoutDirection.Ltr))
    }
}
