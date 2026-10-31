package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val KEY = "space"

@RunWith(RobolectricTestRunner::class)
// The plain application: the keyboard's own loads its models and settings, which a touch of a key does not need.
@Config(application = Application::class)
class SpaceGestureTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /** What the touches typed, a long press as "long" and a glide as "glide"; the cursor's moves and swipes. */
    private val typed = mutableListOf<String>()
    private val moves = mutableListOf<CursorMove>()
    private val swipes = mutableListOf<CursorSteps>()

    /** How far the cursor can go left of where it starts before it stops at the field's start. */
    private var roomLeft = Int.MAX_VALUE

    /** The bar's gestures, whether a cursor is there to move, and whether a glide starts from the bar. */
    private var gestures = SpaceGestures.SwipeCursor
    private var movesCursor = true
    private var glides = false

    /** Which way each language switch went, true for forward, and whether there is another language to switch to. */
    private val switched = mutableListOf<Boolean>()
    private var otherLanguage = true

    private val languages = LanguageSwitch { forward ->
        switched += forward
        otherLanguage
    }

    private val touch = KeyTouch()

    private val cursor = object : Cursor {
        override fun move(move: CursorMove): Boolean {
            val moves = move != CursorMove.Left || roomLeft > 0
            if (moves) {
                if (move == CursorMove.Left) roomLeft--
                if (move == CursorMove.Right) roomLeft++
                this@SpaceGestureTest.moves += move
            }
            return moves
        }

        override fun slid(chars: Int, lines: Int) {
            swipes += CursorSteps(chars, lines)
        }
    }

    @Before
    fun showSpace() {
        // The clock moves only when a test moves it, so a finger stays down exactly as long as the test says.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val haptics = rememberKeyHaptics()
            Box(
                Modifier
                    .size(200.dp, 48.dp)
                    .testTag(KEY)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val onKey = { action: KeyAction, longPress: Boolean, _: KeyTap? ->
                                assertEquals(KeyAction.Space, action)
                                typed += if (longPress) "long" else "space"
                            }
                            val down = awaitFirstDown()
                            val space = SpaceBar(gestures, cursor.takeIf { movesCursor }, languages)
                            try {
                                // The bar opens no popup: its place does not matter.
                                val parts = TouchParts(touch, { KeyGeometry(Rect.Zero, 0, 0) }, haptics)
                                if (handleSpace(down, parts, onKey, space, glides)) typed += "glide"
                            } finally {
                                touch.cursorMode = false
                            }
                        }
                    },
            )
        }
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        compose.onNodeWithTag(KEY).performTouchInput(block)
    }

    private fun holdForLongPress() {
        compose.mainClock.advanceTimeBy(LONG_PRESS_MS, ignoreFrameDuration = true)
    }

    private fun TouchInjectionScope.move(right: Dp, down: Dp = 0.dp) {
        moveBy(Offset(right.toPx(), down.toPx()))
    }

    @Test
    fun aTapTypesASpace() {
        touch {
            down(center)
            move(23.dp)
            up()
        }

        assertEquals(listOf("space"), typed)
        assertEquals(emptyList<CursorMove>(), moves)
    }

    @Test
    fun aLongPressDoesTheBarsLongPress() {
        touch { down(center) }
        holdForLongPress()
        touch {
            move(100.dp)
            up()
        }

        assertEquals(listOf("long"), typed)
        assertEquals(emptyList<CursorMove>(), moves)
    }

    @Test
    fun aSwipeShowsTheCursorMovingAtOnce() {
        touch {
            down(center)
            move(25.dp)
        }
        assertTrue(touch.cursorMode)
        touch { up() }

        assertFalse(touch.cursorMode)
    }

    @Test
    fun aSwipeMovesTheCursorACharForEachStep() {
        touch {
            down(center)
            // Past the start: a swipe, already two chars along.
            move(25.dp)
            move(1.dp)
            move(10.dp)
            up()
        }

        assertEquals(emptyList<String>(), typed)
        assertEquals(List(3) { CursorMove.Right }, moves)
        assertEquals(listOf(CursorSteps(3, 0)), swipes)
    }

    @Test
    fun comingBackMovesTheCursorBack() {
        touch {
            down(center)
            move((-25).dp)
            move((-14).dp)
            move(25.dp)
            up()
        }

        assertEquals(List(3) { CursorMove.Left } + List(2) { CursorMove.Right }, moves)
        assertEquals(listOf(CursorSteps(-1, 0)), swipes)
    }

    @Test
    fun aSwipeThatStartedToASideMovesTheCursorALineForEachKeysHeightUp() {
        touch {
            down(center)
            // To a side, past the start, then back and up.
            move(25.dp)
            move((-25).dp, (-50).dp)
            move(0.dp, (-50).dp)
            up()
        }

        assertEquals(listOf(CursorMove.Up, CursorMove.Up), moves)
    }

    @Test
    fun aFingerThatGoesUpFirstTypesNothingAndMovesNoCursor() {
        touch {
            down(center)
            // Out of the bar before it went far to a side, as a glide from the letter above.
            move(5.dp, (-30).dp)
            move(40.dp, (-20).dp)
            up()
        }

        assertEquals(emptyList<String>(), typed)
        assertEquals(emptyList<CursorMove>(), moves)
    }

    @Test
    fun aFingerThatGoesUpFromTheTopEdgeGlidesThoughMoreAcrossThanUp() {
        glides = true
        touch {
            // At the bar's top edge, as a finger aimed at a letter of the row above lands, toward one far to a side.
            down(Offset(centerX, 4.dp.toPx()))
            move(10.dp, (-3).dp)
            // Past where the cursor started once, but out of the bar's top first.
            move(8.dp, (-3).dp)
            move(60.dp, (-30).dp)
            up()
        }

        assertEquals(listOf("glide"), typed)
        assertEquals(emptyList<CursorMove>(), moves)
        assertEquals(emptyList<CursorSteps>(), swipes)
    }

    @Test
    fun aSwipeAlongTheBarStillMovesTheCursorWhereGlidesStart() {
        glides = true
        touch {
            down(center)
            move(25.dp)
            move(10.dp, (-30).dp)
            up()
        }

        assertEquals(emptyList<String>(), typed)
        assertEquals(List(3) { CursorMove.Right }, moves)
    }

    @Test
    fun withoutACursorASlideAlongTheBarTypesASpace() {
        movesCursor = false
        touch {
            down(center)
            move(40.dp)
            up()
        }

        assertEquals(listOf("space"), typed)
    }

    @Test
    fun aCursorStoppedAtTheStartGoesBackAtOnce() {
        roomLeft = 1
        touch {
            down(center)
            move((-25).dp)
            move((-50).dp)
            move(10.dp)
            up()
        }

        assertEquals(listOf(CursorMove.Left, CursorMove.Right), moves)
        assertEquals(listOf(CursorSteps(0, 0)), swipes)
    }

    @Test
    fun aSwipeSwitchesTheLanguageOnceItsWay() {
        gestures = SpaceGestures.SwipeLanguage
        touch {
            down(center)
            move(25.dp)
            move(40.dp)
            up()
        }

        assertEquals(listOf(true), switched)
        assertEquals(emptyList<String>(), typed)
        assertEquals(emptyList<CursorMove>(), moves)
    }

    @Test
    fun aSwipeToTheLeftSwitchesTheLanguageBack() {
        gestures = SpaceGestures.SwipeLanguage
        touch {
            down(center)
            move((-25).dp)
            up()
        }

        assertEquals(listOf(false), switched)
    }

    @Test
    fun aSwipeWithNoOtherLanguageTypesNothing() {
        gestures = SpaceGestures.SwipeLanguage
        otherLanguage = false
        touch {
            down(center)
            move(25.dp)
            up()
        }

        assertEquals(listOf(true), switched)
        assertEquals(emptyList<String>(), typed)
    }

    @Test
    fun aLongPressMovesTheCursorFromWhereTheFingerIsHeld() {
        gestures = SpaceGestures.SwipeLanguage
        touch {
            down(center)
            // Short of a swipe, so the finger is held there.
            move(10.dp)
        }
        holdForLongPress()
        assertTrue(touch.cursorMode)
        touch {
            move(25.dp)
            up()
        }

        assertEquals(List(2) { CursorMove.Right }, moves)
        assertEquals(listOf(CursorSteps(2, 0)), swipes)
        assertEquals(emptyList<String>(), typed)
        assertEquals(emptyList<Boolean>(), switched)
    }

    @Test
    fun aLongPressLetGoInPlaceOpensThePicker() {
        gestures = SpaceGestures.SwipeLanguage
        touch { down(center) }
        holdForLongPress()
        touch { up() }

        assertEquals(listOf("long"), typed)
        assertEquals(listOf(CursorSteps(0, 0)), swipes)
    }

    @Test
    fun aLongPressThatWentAndCameBackOpensNoPicker() {
        gestures = SpaceGestures.SwipeLanguage
        roomLeft = 0
        touch { down(center) }
        holdForLongPress()
        touch {
            // Toward the field's start, where the cursor already is and cannot go, and back.
            move((-25).dp)
            move(25.dp)
            up()
        }

        assertEquals(emptyList<String>(), typed)
    }

    @Test
    fun aCanceledLongPressOpensNoPicker() {
        gestures = SpaceGestures.SwipeLanguage
        touch { down(center) }
        holdForLongPress()
        touch { cancel() }

        assertEquals(emptyList<String>(), typed)
    }

    @Test
    fun withoutACursorALongPressDoesTheBarsLongPress() {
        gestures = SpaceGestures.SwipeLanguage
        movesCursor = false
        touch { down(center) }
        holdForLongPress()
        touch { up() }

        assertFalse(touch.cursorMode)
        assertEquals(listOf("long"), typed)
    }

    @Test
    fun withNoGesturesASlideAlongTheBarTypesASpace() {
        gestures = SpaceGestures.Off
        touch {
            down(center)
            move(40.dp)
            up()
        }

        assertEquals(listOf("space"), typed)
        assertEquals(emptyList<Boolean>(), switched)
    }

    @Test
    fun aCanceledSwipeKeepsWhereTheCursorWent() {
        touch {
            down(center)
            move(25.dp)
            move(1.dp)
            cancel()
        }

        assertEquals(emptyList<String>(), typed)
        assertEquals(List(2) { CursorMove.Right }, moves)
        assertEquals(listOf(CursorSteps(2, 0)), swipes)
    }
}
