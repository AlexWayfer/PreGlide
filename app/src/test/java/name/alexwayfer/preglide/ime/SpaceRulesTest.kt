package name.alexwayfer.preglide.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceRulesTest {
    @Test
    fun theLanguageOnASwipeIsTheDefault() {
        assertEquals(SpaceGestures.SwipeLanguage, SpaceGestures.parse(null))
        assertEquals(SpaceGestures.SwipeLanguage, SpaceGestures.parse("Unknown"))
        assertEquals(SpaceGestures.Off, SpaceGestures.parse("Off"))
    }

    @Test
    fun aSwipeDoesWhatTheUserChose() {
        assertEquals(SpaceSwipe.Language, spaceSwipe(SpaceGestures.SwipeLanguage, cursor = false))
        assertEquals(SpaceSwipe.Cursor, spaceSwipe(SpaceGestures.SwipeCursor, cursor = true))
        // No cursor to move, as while the emoji search shows.
        assertNull(spaceSwipe(SpaceGestures.SwipeCursor, cursor = false))
        assertNull(spaceSwipe(SpaceGestures.Off, cursor = true))
    }

    @Test
    fun aLongPressMovesTheCursorOnlyWhenASwipeSwitchesTheLanguage() {
        assertTrue(holdMovesCursor(SpaceGestures.SwipeLanguage, cursor = true))
        assertFalse(holdMovesCursor(SpaceGestures.SwipeLanguage, cursor = false))
        assertFalse(holdMovesCursor(SpaceGestures.SwipeCursor, cursor = true))
        assertFalse(holdMovesCursor(SpaceGestures.Off, cursor = true))
    }

    @Test
    fun theBarGivesTheCursorToALongPressOnlyWhenItMovesIt() {
        val cursor = Cursor { true }
        val languages = LanguageSwitch { true }
        assertSame(cursor, SpaceBar(SpaceGestures.SwipeLanguage, cursor, languages).holdCursor)
        assertNull(SpaceBar(SpaceGestures.SwipeCursor, cursor, languages).holdCursor)
        assertEquals(SpaceSwipe.Cursor, SpaceBar(SpaceGestures.SwipeCursor, cursor, languages).swipe)
    }

    @Test
    fun theBarHintsTheCursorWhileALongPressMovesItAndThePickerOtherwise() {
        val cursor = Cursor { true }
        val languages = LanguageSwitch { true }
        assertEquals(SpaceHold.Cursor, spaceHold(SpaceBar(SpaceGestures.SwipeLanguage, cursor, languages)))
        // No cursor to move, as while the emoji search shows: the long press opens the picker then.
        assertEquals(SpaceHold.Picker, spaceHold(SpaceBar(SpaceGestures.SwipeLanguage, null, languages)))
        assertEquals(SpaceHold.Picker, spaceHold(SpaceBar(SpaceGestures.SwipeCursor, cursor, languages)))
        assertEquals(SpaceHold.Picker, spaceHold(SpaceBar(SpaceGestures.Off, cursor, languages)))
        assertEquals(SpaceHold.Picker, spaceHold(null))
    }

    @Test
    fun aSwipeToTheRightGoesForward() {
        assertTrue(swipesForward(30f))
        assertFalse(swipesForward(-30f))
    }

    @Test
    fun theNextLanguageGoesRound() {
        val enabled = listOf("ru", "en", "uk")
        assertEquals("en", nextLanguage(enabled, "ru", forward = true))
        assertEquals("ru", nextLanguage(enabled, "uk", forward = true))
        assertEquals("uk", nextLanguage(enabled, "ru", forward = false))
    }

    @Test
    fun theNextLanguageIsTheFirstFromAnotherKeyboardsAndNoneAlone() {
        assertEquals("ru", nextLanguage(listOf("ru", "en"), "de", forward = true))
        assertNull(nextLanguage(listOf("ru"), "ru", forward = true))
        assertNull(nextLanguage(emptyList<String>(), null, forward = true))
    }

    @Test
    fun aSwipedSwitchTellsTheKeyboardWhichWayTheLanguageCame() {
        val state = KeyboardState()
        var other = true
        val languages = SwipedLanguageSwitch(state) { other }

        assertTrue(languages.switch(forward = false))
        assertEquals(false, state.swipingLanguage)

        other = false
        assertFalse(languages.switch(forward = true))
        // No language comes, so none waits for the swipe's way.
        assertNull(state.swipingLanguage)
    }

    @Test
    fun aNewLanguageComesInBehindTheFinger() {
        // A swipe to the right: the name comes from the left, as a page follows the finger.
        assertEquals(-80, languageEntryOffset(forward = true, width = 80))
        assertEquals(80, languageEntryOffset(forward = false, width = 80))
    }

    @Test
    fun theArrowsShowOnlyWhenASwipeHasALanguageToSwitchTo() {
        assertTrue(showsLanguageArrows(SpaceGestures.SwipeLanguage, otherLanguages = true, keypad = false))
        assertFalse(showsLanguageArrows(SpaceGestures.SwipeLanguage, otherLanguages = false, keypad = false))
        assertFalse(showsLanguageArrows(SpaceGestures.SwipeLanguage, otherLanguages = true, keypad = true))
        assertFalse(showsLanguageArrows(SpaceGestures.SwipeCursor, otherLanguages = true, keypad = false))
        assertFalse(showsLanguageArrows(null, otherLanguages = true, keypad = false))
    }

    @Test
    fun theArrowsStandBesideTheNameAndGoOutToTheEnds() {
        // A bar of 300 with a name of 100: 100 on each side, an arrow of 20 beside the name stands 80 in.
        assertEquals(80f, arrowInset(300, 100, 20, edge = 4, toEdges = 0f), 0.01f)
        assertEquals(42f, arrowInset(300, 100, 20, edge = 4, toEdges = 0.5f), 0.01f)
        assertEquals(4f, arrowInset(300, 100, 20, edge = 4, toEdges = 1f), 0.01f)
        // A name nearly as wide as the bar: the arrows keep at the ends.
        assertEquals(4f, arrowInset(120, 110, 20, edge = 4, toEdges = 0f), 0.01f)
    }
}
