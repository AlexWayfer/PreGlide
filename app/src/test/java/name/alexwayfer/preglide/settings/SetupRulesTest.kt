package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRulesTest {
    private val done = KeyboardStatus(enabled = true, selected = true, spellChecker = true, notices = true)

    @Test
    fun theSetupNeedsTheKeyboardOnAndChosen() {
        assertTrue(needsSetup(done.copy(enabled = false, selected = false)))
        assertTrue(needsSetup(done.copy(selected = false)))
        assertFalse(needsSetup(done))
    }

    @Test
    fun theOptionalStepsLeaveNoBadge() {
        assertFalse(needsSetup(done.copy(spellChecker = false, notices = false)))
    }

    @Test
    fun aFreshStartOpensTheSetupWhileTheKeyboardIsOff() {
        assertTrue(opensSetupFirst(done.copy(enabled = false, selected = false), restored = false))
        assertFalse(opensSetupFirst(done.copy(enabled = false, selected = false), restored = true))
    }

    @Test
    fun aKeyboardOnButNotChosenOpensTheFirstPage() {
        assertFalse(opensSetupFirst(done.copy(selected = false), restored = false))
        assertFalse(opensSetupFirst(done, restored = false))
    }

    @Test
    fun anUpdateNotificationDoesNotOpenInitialSetupOverTheUpdatesPage() {
        assertFalse(opensSetupFirst(done.copy(enabled = false), restored = false, updatesRequested = true))
    }

    @Test
    fun theHintSaysWhatIsLeftUntilTheKeyboardIsOnAndChosen() {
        assertEquals(R.string.settings_setup_unfinished, setupHint(done.copy(enabled = false)))
        assertEquals(R.string.settings_setup_ready, setupHint(done.copy(notices = false)))
    }

    @Test
    fun theButtonAsksUntilTheLeaveIsGranted() {
        assertTrue(asksToNotify(granted = false))
        assertFalse(asksToNotify(granted = true))
    }

    @Test
    fun aDenialWithoutADialogOpensTheSettings() {
        assertTrue(opensSettingsAfterAsking(granted = false, rationaleBefore = false, rationaleAfter = false))
    }

    @Test
    fun aFirstOrSecondDeclineInTheDialogStaysOnTheScreen() {
        assertFalse(opensSettingsAfterAsking(granted = false, rationaleBefore = false, rationaleAfter = true))
        assertFalse(opensSettingsAfterAsking(granted = false, rationaleBefore = true, rationaleAfter = false))
    }

    @Test
    fun theScreenshotsStepComesLast() {
        assertEquals(5, screenshotStepNumber(33))
        // Before Android 13 notices need no step.
        assertEquals(4, screenshotStepNumber(32))
    }

    @Test
    fun aGrantOpensNothing() {
        assertFalse(opensSettingsAfterAsking(granted = true, rationaleBefore = false, rationaleAfter = false))
    }
}
