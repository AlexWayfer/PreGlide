package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.ime.SpaceGestures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTipsTest {
    private val default = GestureHelpState(SpaceGestures.SwipeLanguage, strip = true, autocorrect = true,
        emojiKey = false, GlideHelp(typing = true, erasesWord = true, bringsBackWords = true))

    @Test
    fun doubleSpaceIsExplainedOnlyWhileEnabled() {
        assertFalse(GestureTip.DoubleSpacePeriod in gestureTips(default))
        assertTrue(GestureTip.DoubleSpacePeriod in gestureTips(default.copy(doubleSpacePeriod = true)))
    }

    @Test
    fun defaultHelpExplainsActiveGesturesAndTheirUndo() {
        assertEquals(listOf(GestureTip.KeyOptions, GestureTip.DeleteHold, GestureTip.DeleteSwipe, GestureTip.Shift,
            GestureTip.Glide, GestureTip.GlideErase, GestureTip.GlideVariants, GestureTip.SpaceLanguage,
            GestureTip.UndoCorrection, GestureTip.RemoveSuggestion, GestureTip.CommaEmoji), gestureTips(default))
    }

    @Test
    fun hiddenControlsAndDisabledActionsAreNotAdvertised() {
        val state = default.copy(space = SpaceGestures.Off, strip = false, autocorrect = false, emojiKey = true,
            glide = default.glide.copy(typing = false))
        assertEquals(listOf(GestureTip.KeyOptions, GestureTip.DeleteHold, GestureTip.DeleteSwipe, GestureTip.Shift,
            GestureTip.SpacePicker, GestureTip.SymbolsMenu), gestureTips(state))
    }

    @Test
    fun theCursorChoiceAndGlideOptionsFollowTheirOwnSwitches() {
        val state = default.copy(space = SpaceGestures.SwipeCursor, strip = false,
            glide = default.glide.copy(erasesWord = false))
        val tips = gestureTips(state)
        assertTrue(GestureTip.SpaceCursor in tips)
        assertFalse(GestureTip.SpaceLanguage in tips)
        assertTrue(GestureTip.Glide in tips)
        assertFalse(GestureTip.GlideErase in tips)
        assertFalse(GestureTip.GlideVariants in tips)
        assertFalse(GestureTip.RemoveSuggestion in tips)
        assertTrue(GestureTip.SymbolsMenu in tips)
    }
}
