package name.alexwayfer.preglide.settings

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class PremiumGlowTest {
    @Test
    fun atZeroDegreesTheGradientRunsLeftToRightThroughTheCenterReachingTheCorners() {
        val (start, end) = premiumGlowLine(Size(60f, 80f), 0f)

        assertEquals(Offset(-20f, 40f), start)
        assertEquals(Offset(80f, 40f), end)
    }

    @Test
    fun atNinetyDegreesTheGradientRunsTopToBottom() {
        val (start, end) = premiumGlowLine(Size(60f, 80f), 90f)

        assertEquals(30f, start.x, 0.001f)
        assertEquals(-10f, start.y, 0.001f)
        assertEquals(30f, end.x, 0.001f)
        assertEquals(90f, end.y, 0.001f)
    }

    @Test
    fun aZeroSizeKeepsBothEndsAtTheOrigin() {
        assertEquals(Offset.Zero to Offset.Zero, premiumGlowLine(Size.Zero, 45f))
    }

    private val corners = GlowCorners(topLeft = 40f, topRight = 40f, bottomRight = 30f, bottomLeft = 0f)

    @Test
    fun aRingInsideTheEdgeShrinksEachCornerByTheInset() {
        val rect = premiumGlowRing(Size(200f, 400f), corners, 10f)

        assertEquals(10f, rect.left)
        assertEquals(10f, rect.top)
        assertEquals(190f, rect.right)
        assertEquals(390f, rect.bottom)
        assertEquals(CornerRadius(30f), rect.topLeftCornerRadius)
        assertEquals(CornerRadius(20f), rect.bottomRightCornerRadius)
    }

    @Test
    fun aCornerSmallerThanTheInsetBecomesSquare() {
        val rect = premiumGlowRing(Size(200f, 400f), corners, 35f)

        assertEquals(CornerRadius(5f), rect.topLeftCornerRadius)
        assertEquals(CornerRadius.Zero, rect.bottomRightCornerRadius)
        assertEquals(CornerRadius.Zero, rect.bottomLeftCornerRadius)
    }

    @Test
    fun aSizeNarrowerThanTwiceTheInsetCollapsesInsteadOfTurningInsideOut() {
        val rect = premiumGlowRing(Size(15f, 0f), corners, 10f)

        assertEquals(10f, rect.left)
        assertEquals(10f, rect.right)
        assertEquals(10f, rect.top)
        assertEquals(10f, rect.bottom)
    }

    @Test
    fun theGlowIsFullAtTheLineAndFadesAway() {
        assertEquals(1f, premiumGlowFade(0, 4), 0.0001f)
        assertEquals(0.25f, premiumGlowFade(2, 4), 0.0001f)
        assertEquals(0f, premiumGlowFade(4, 4), 0.0001f)
    }

    @Test
    fun noLayersMeansNoGlow() {
        assertEquals(0f, premiumGlowFade(0, 0), 0.0001f)
    }

    @Test
    fun scaledCornersGrowAlike() {
        assertEquals(GlowCorners(44f, 44f, 33f, 0f), corners.scaled(1.1f))
    }
}
