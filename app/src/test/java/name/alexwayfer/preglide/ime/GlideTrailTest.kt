package name.alexwayfer.preglide.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideTrailTest {
    private val trail = GlideTrail()

    @Test
    fun pointsGetTheTimeOfTheFrameThatFirstDrawsThem() {
        trail.start(Offset(1f, 1f))
        trail.move(Offset(2f, 1f))
        trail.frame(1000)
        trail.move(Offset(3f, 1f))
        trail.frame(1050)

        assertEquals(listOf(1000L, 1000L, 1050L), trail.points.map { it.time })
        assertEquals(1050L, trail.now)
        assertEquals(1, trail.glides)
    }

    @Test
    fun theTailFadesAwayWhileTheFingerGoesOn() {
        trail.start(Offset(1f, 1f))
        trail.frame(1000)
        trail.move(Offset(2f, 1f))
        trail.frame(1100)
        trail.frame(1200)

        assertEquals(listOf(Offset(2f, 1f)), trail.points.map { it.at })
        assertTrue(trail.showing)
    }

    @Test
    fun aLiftHandsOverTheWholePathAndTheTrailFadesOut() {
        trail.start(Offset(1f, 1f))
        trail.frame(1000)
        trail.move(Offset(2f, 1f))
        trail.frame(1300)

        assertEquals(listOf(Offset(1f, 1f), Offset(2f, 1f)), trail.lift())
        assertTrue(trail.showing)
        trail.move(Offset(5f, 5f))
        trail.frame(1500)
        assertFalse(trail.showing)
    }

    @Test
    fun aCanceledGlideStopsTakingPoints() {
        trail.start(Offset(1f, 1f))
        trail.cancel()
        trail.move(Offset(2f, 1f))

        assertEquals(1, trail.points.size)
        assertFalse(trail.gliding)
    }

    @Test
    fun aPointFadesOverTheFadeTime() {
        assertEquals(1f, trailFade(0), DELTA)
        assertEquals(0.5f, trailFade(TRAIL_FADE_MS / 2), DELTA)
        assertEquals(0f, trailFade(TRAIL_FADE_MS * 2), DELTA)
        assertEquals(0L, ageOf(TrailPoint(Offset.Zero, -1), 500))
        assertEquals(20L, ageOf(TrailPoint(Offset.Zero, 480), 500))
    }

    @Test
    fun windowPointsAreReadOnTheKeys() {
        // Eleven key widths over 440 pixels, four rows of 60.
        val keys = Rect(20f, 100f, 460f, 340f)

        assertEquals(KeyPoint(5.5f, 1.5f), keyPointOf(Offset(240f, 190f), keys, 11f, 4))
        assertEquals(1.5f, rowHeightOf(keys, 11f, 4), DELTA)

        val glide = glideOf(listOf(Offset(240f, 190f)), keys, KeyboardLayout.of(Language.Russian))
        assertEquals(1.5f, glide.rowHeight, DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
