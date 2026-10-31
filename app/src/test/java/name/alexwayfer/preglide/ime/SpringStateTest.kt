package name.alexwayfer.preglide.ime

import androidx.compose.animation.core.Spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringStateTest {
    private val stiffness = Spring.StiffnessMediumLow
    private val frame = 1f / 60

    /** The state after [frames] frames toward 1. */
    private fun SpringState.after(frames: Int): SpringState =
        (1..frames).fold(this) { state, _ -> state.toward(1f, frame, stiffness) }

    @Test
    fun valueReachesItsTargetAndStopsThere() {
        val settled = SpringState(0f).after(frames = 60)

        assertEquals(SpringState(1f), settled)
    }

    @Test
    fun valueNeverOvershootsItsTarget() {
        var state = SpringState(0f)
        repeat(60) {
            state = state.toward(1f, frame, stiffness)
            assertTrue("overshot to ${state.value}", state.value <= 1f)
        }
    }

    @Test
    fun valueMovesFromTheFirstFrame() {
        assertTrue(SpringState(0f).toward(1f, frame, stiffness).value > 0f)
    }

    @Test
    fun aLongFrameDoesNotShootPast() {
        val state = SpringState(0f, velocity = 50f).toward(1f, seconds = 2f, stiffness)

        assertEquals(1f, state.value, 0.001f)
    }

    @Test
    fun valueAtItsTargetStaysPut() {
        assertEquals(SpringState(3f), SpringState(3f).toward(3f, frame, stiffness))
    }

    @Test
    fun aNewTargetIsFollowedFromWhereTheValueIsWithItsSpeed() {
        val moving = SpringState(0f).after(frames = 5)
        val turned = moving.toward(2f, frame, stiffness)

        assertTrue(turned.value > moving.value)
        assertTrue(turned.velocity > moving.velocity)
    }

    @Test
    fun edgesMoveWhileTheirTargetChangesEveryFrame() {
        // A finger moving smoothly changes the target on every frame; the edges must keep up anyway.
        val motion = IndicatorMotion(0f to 1f)
        repeat(30) { frameIndex ->
            val pull = frameIndex / 30f * 0.5f
            motion.targets = indicatorTargets(cell = 1, pull = -0.5f + pull)
            motion.advance(frame)
        }

        assertTrue("left edge stuck at ${motion.edges.first}", motion.edges.first > 0.8f)
        assertTrue("right edge at ${motion.edges.second}", motion.edges.second > 1.8f)
    }

    @Test
    fun edgesJumpToTheirTargetsWithAnimationsOff() {
        val motion = IndicatorMotion(0f to 1f)
        motion.targets = 2f to 3f
        motion.advance(frame, scale = 0f)

        assertEquals(2f to 3f, motion.edges)
    }

    @Test
    fun edgesGoAtTheSystemsPaceOfAnimations() {
        val slowed = IndicatorMotion(0f to 1f)
        val plain = IndicatorMotion(0f to 1f)
        slowed.targets = 2f to 3f
        plain.targets = 2f to 3f
        slowed.advance(frame, scale = 2f)
        plain.advance(frame / 2)

        assertEquals(plain.edges, slowed.edges)
    }

    @Test
    fun crossingABoundaryKeepsTheIndicatorShort() {
        // Settled at the boundary of cells 1 and 2, then the finger crosses and moves on through cell 2.
        val motion = IndicatorMotion(indicatorTargets(cell = 1, pull = 0.5f))
        repeat(40) { frameIndex ->
            motion.targets = indicatorTargets(cell = 2, pull = -0.5f + frameIndex / 40f * 0.5f)
            motion.advance(frame)
            val (left, right) = motion.edges
            assertTrue("indicator ${right - left} cells long", right - left < 1.2f)
        }
    }
}
