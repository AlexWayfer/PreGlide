package name.alexwayfer.preglide.ime

import android.app.Application
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.Page
import name.alexwayfer.preglide.engine.layout.keyTapAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val KEY = "key"

/** How long a finger stays down for a long press, as the keys wait for it. */
private const val LONG_PRESS = 350L

/** Where the key stands in the window: far enough from both edges for a popup to open over it. */
private const val KEY_LEFT = 400f
private const val WINDOW_WIDTH = 1000

/** The е key, whose long press offers е and ё, ё chosen at first. */
private val YE = KeyAction.Letter('е', alternates = listOf('ё'))

/** The key to the symbols, whose long press offers the menu while the strip is hidden. */
private val SYMBOLS = KeyAction.ShowPage(Page.Symbols)

@RunWith(RobolectricTestRunner::class)
// The plain application: the keyboard's own loads its models and settings, which a touch of a key does not need.
@Config(application = Application::class)
class KeyGestureTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /** What backspace does in the field, which its tap here does not ask, and no other key does. */
    private class FieldBackspace : Backspace {
        override fun canDelete() = true

        override fun startSwipe() = 0

        override fun select(words: Int) = Unit

        override fun endSwipe(lifted: Boolean) = Unit
    }

    /** What a touch of a key typed: the action, whether by a long press, and for a tap, where it landed. */
    private data class Typed(val action: KeyAction, val longPress: Boolean, val tap: KeyTap?)

    private val trail = GlideTrail()
    private val lifts = mutableListOf<List<Offset>>()
    private val typed = mutableListOf<Typed>()
    private val touch = KeyTouch()

    @Before
    fun stopTheClock() {
        // The clock moves only when a test moves it, so no long press comes while the finger glides, and one comes
        // when a test holds the finger long enough.
        compose.mainClock.autoAdvance = false
    }

    private val chosen = mutableListOf<KeyMenuItem>()
    private val menu = KeyMenu(KeyMenuItem.entries) { chosen += it }

    /** Shows a key with [action], from which a finger glides when [glides], with the [menu] its long press offers. */
    private fun show(action: KeyAction, glides: Boolean = true, menu: KeyMenu? = null) {
        val glide = GlideStart(trail) { path -> lifts += path }.takeIf { glides }
        compose.setContent {
            val haptics = rememberKeyHaptics()
            val bounds = with(LocalDensity.current) { Rect(KEY_LEFT, 0f, KEY_LEFT + 60.dp.toPx(), 48.dp.toPx()) }
            Box(
                Modifier
                    .size(60.dp, 48.dp)
                    .testTag(KEY)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            handleKeyGesture(
                                action,
                                TouchParts(touch, { KeyGeometry(bounds, WINDOW_WIDTH, popupPadding = 0) }, haptics),
                                { key, longPress, tap -> typed += Typed(key, longPress, tap) },
                                SpecialKeys({ FieldBackspace() }, menu = { menu }),
                                glide,
                            )
                        }
                    },
            )
        }
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        compose.onNodeWithTag(KEY).performTouchInput(block)
    }

    private fun holdForLongPress() {
        touch { down(center) }
        compose.mainClock.advanceTimeBy(LONG_PRESS, ignoreFrameDuration = true)
    }

    @Test
    fun aTapTypesTheKeyWithWhereItLanded() {
        show(YE)
        var tap: KeyTap? = null
        touch {
            val at = Offset(width * 0.25f, height * 0.75f)
            tap = keyTapAt(at.x, at.y, width, height)
            down(at)
        }
        assertTrue(touch.pressed)
        touch { up() }

        assertEquals(listOf(Typed(YE, longPress = false, tap)), typed)
        assertFalse(touch.pressed)
    }

    @Test
    fun aFingerThatRollsLessThanAGlideStillTapsWhereItCameDown() {
        show(YE)
        var tap: KeyTap? = null
        touch {
            tap = keyTapAt(center.x, center.y, width, height)
            down(center)
            moveBy(Offset(10.dp.toPx(), 0f))
            up()
        }

        assertEquals(listOf(Typed(YE, longPress = false, tap)), typed)
        assertEquals(emptyList<List<Offset>>(), lifts)
    }

    @Test
    fun aTouchCanceledBeforeTheLongPressTypesNothing() {
        show(YE)
        touch {
            down(center)
            cancel()
        }

        assertEquals(emptyList<Typed>(), typed)
        assertFalse(touch.pressed)
    }

    @Test
    fun aKeyThatDoesNotGlideTapsToo() {
        show(KeyAction.Space, glides = false)
        touch {
            down(center)
            up()
        }

        assertEquals(KeyAction.Space, typed.single().action)
        assertFalse(typed.single().longPress)
    }

    @Test
    fun aFingerThatLeavesAKeyThatDoesNotGlideTypesNothing() {
        show(KeyAction.Space, glides = false)
        touch {
            down(center)
            moveBy(Offset(100.dp.toPx(), 0f))
            up()
        }

        assertEquals(emptyList<Typed>(), typed)
    }

    @Test
    fun aLongPressOfAKeyWithoutOptionsIsItsOwnLongPress() {
        show(KeyAction.Space, glides = false)
        holdForLongPress()

        assertEquals(listOf(Typed(KeyAction.Space, longPress = true, tap = null)), typed)
        touch { up() }
        assertEquals(1, typed.size)
    }

    @Test
    fun aLongPressOpensThePopupWithTheFirstAlternateChosen() {
        show(YE)
        holdForLongPress()

        // Both е and ё, ё over the key.
        assertEquals(2, touch.popup?.width)
        assertEquals(0, touch.popup?.columns?.get(PRESELECTED_OPTION))
        assertEquals(PRESELECTED_OPTION, touch.selected)
        assertEquals(emptyList<Typed>(), typed)
    }

    @Test
    fun aLongPressTypesTheOptionTheFingerLiftsOn() {
        show(YE)
        holdForLongPress()
        touch { up() }

        assertEquals(listOf(Typed(KeyAction.Letter('ё'), longPress = false, tap = null)), typed)
        assertNull(touch.popup)
    }

    @Test
    fun theFingerMovesTheChoiceToAnotherOption() {
        show(YE)
        holdForLongPress()
        // A cell to the left, over the key's own char.
        touch { moveBy(Offset(-60.dp.toPx(), 0f)) }
        assertEquals(0, touch.selected)
        assertEquals(0f, touch.pull)
        touch { up() }

        assertEquals(listOf(Typed(KeyAction.Letter('е'), longPress = false, tap = null)), typed)
    }

    @Test
    fun theFingerPullsWithinItsOptionWithoutLeavingIt() {
        show(YE)
        holdForLongPress()
        // A quarter of a cell to the left: still over ё, pulling toward е.
        touch { moveBy(Offset(-15.dp.toPx(), 0f)) }

        assertEquals(PRESELECTED_OPTION, touch.selected)
        assertEquals(-0.25f, touch.pull, 0.01f)
    }

    @Test
    fun aCanceledLongPressTypesNothing() {
        show(YE)
        holdForLongPress()
        touch { cancel() }

        assertEquals(emptyList<Typed>(), typed)
        assertNull(touch.popup)
    }

    @Test
    fun aLongPressOfAKeyWithAMenuOpensItOnItsFirstItem() {
        show(SYMBOLS, glides = false, menu)
        holdForLongPress()

        assertEquals(KeyMenuItem.entries.size, touch.popup?.width)
        assertEquals(0, touch.selected)
        assertEquals(emptyList<Typed>(), typed)
    }

    @Test
    fun theMenuDoesTheItemTheFingerLiftsOn() {
        show(SYMBOLS, glides = false, menu)
        holdForLongPress()
        // A cell to the right, over the settings.
        touch { moveBy(Offset(60.dp.toPx(), 0f)) }
        touch { up() }

        assertEquals(listOf(KeyMenuItem.Settings), chosen)
        assertEquals(emptyList<Typed>(), typed)
        assertNull(touch.popup)
    }

    @Test
    fun aCanceledMenuDoesNothing() {
        show(SYMBOLS, glides = false, menu)
        holdForLongPress()
        touch { cancel() }

        assertEquals(emptyList<KeyMenuItem>(), chosen)
        assertEquals(emptyList<Typed>(), typed)
    }

    @Test
    fun aGlideHandsItsWayOverAsTheFingerLifts() {
        show(YE)
        touch {
            down(center)
            // Half the key away: a glide.
            moveBy(Offset(31.dp.toPx(), 0f))
            assertFalse(touch.pressed)
            moveBy(Offset(40.dp.toPx(), 0f))
            up()
        }

        assertEquals(1, lifts.size)
        // Where the finger came down, then where it went, each placed in the window.
        assertEquals(3, lifts.single().size)
        assertTrue(lifts.single().all { it.x >= KEY_LEFT })
        assertEquals(emptyList<Typed>(), typed)
        assertFalse(trail.gliding)
    }

    @Test
    fun aCanceledGlideTypesNothing() {
        show(YE)
        touch {
            down(center)
            moveBy(Offset(31.dp.toPx(), 0f))
            moveBy(Offset(40.dp.toPx(), 0f))
            cancel()
        }

        assertEquals(emptyList<List<Offset>>(), lifts)
        assertEquals(emptyList<Typed>(), typed)
        assertFalse(trail.gliding)
    }

    @Test
    fun aFingerThatGoesUpFromTheSpaceBarGlides() {
        show(KeyAction.Space)
        touch {
            // At the bar's top edge, as a finger aimed at a letter above lands, then up and out of the bar.
            down(Offset(center.x, 4.dp.toPx()))
            moveBy(Offset(10.dp.toPx(), (-8).dp.toPx()))
            assertFalse(touch.pressed)
            moveBy(Offset(40.dp.toPx(), (-30).dp.toPx()))
            up()
        }

        assertEquals(1, lifts.size)
        assertEquals(emptyList<Typed>(), typed)
        assertFalse(trail.gliding)
    }

    @Test
    fun aSlideAlongASpaceBarThatGlidesStillTypesASpace() {
        show(KeyAction.Space)
        touch {
            down(center)
            moveBy(Offset(25.dp.toPx(), 0f))
            up()
        }

        assertEquals(KeyAction.Space, typed.single().action)
        assertEquals(emptyList<List<Offset>>(), lifts)
    }

    @Test
    fun backspaceDeletesThroughItsOwnGesture() {
        show(KeyAction.Backspace, glides = false)
        touch {
            down(center)
            up()
        }

        assertEquals(listOf(Typed(KeyAction.Backspace, longPress = false, tap = null)), typed)
    }
}
