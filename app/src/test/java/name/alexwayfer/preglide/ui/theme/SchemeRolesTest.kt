package name.alexwayfer.preglide.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SchemeRolesTest {
    @Test
    fun eachRoleGoesBackToItsOwnPlace() {
        val roles = lightColorScheme().roles().indices.map { Color(0xFF000000 + it) }

        assertEquals(roles, colorSchemeOf(roles).roles())
    }

    @Test
    fun aSchemeListsEveryOneOfItsColors() {
        val scheme = lightColorScheme()

        assertEquals(scheme.surfaceContainer, colorSchemeOf(scheme.roles()).surfaceContainer)
        assertEquals(scheme.onTertiaryFixedVariant, colorSchemeOf(scheme.roles()).onTertiaryFixedVariant)
    }

    @Test
    fun blendingGoesFromTheStartToTheStop() {
        val start = listOf(Color.Black, Color.Red)
        val stop = listOf(Color.White, Color.Blue)

        assertClose(start, blend(start, stop, 0f))
        assertClose(stop, blend(start, stop, 1f))
        blend(start, stop, 0.5f).forEachIndexed { index, color ->
            assertNotEquals(start[index], color)
            assertNotEquals(stop[index], color)
        }
    }

    /** Blending goes through Oklab, which may miss a color by a rounding. */
    private fun assertClose(expected: List<Color>, actual: List<Color>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (want, got) ->
            listOf(want.red - got.red, want.green - got.green, want.blue - got.blue, want.alpha - got.alpha)
                .forEach { assertEquals("$want vs $got", 0f, it, TOLERANCE) }
        }
    }

    private companion object {
        const val TOLERANCE = 1f / 255
    }
}
