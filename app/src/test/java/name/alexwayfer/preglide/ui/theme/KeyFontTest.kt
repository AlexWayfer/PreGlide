package name.alexwayfer.preglide.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import name.alexwayfer.preglide.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFontTest {
    private fun faces(font: KeyFont): List<ResourceFont> =
        (keyFontFamily(font) as FontListFontFamily).fonts.map { it as ResourceFont }

    @Test
    fun aFontIsKeptByNameAndAnUnknownOneGivesTheSystems() {
        KeyFont.entries.forEach { assertEquals(it, KeyFont.parse(it.name)) }
        assertEquals(KeyFont.System, KeyFont.parse(null))
        assertEquals(KeyFont.System, KeyFont.parse("Comic Sans"))
    }

    @Test
    fun theSystemsFontComesFirstAndTheOthersByName() {
        assertEquals(KeyFont.System, KeyFont.entries.first())
        val titles = KeyFont.entries.drop(1).map { requireNotNull(it.title) }
        assertEquals(titles.sortedBy { it.lowercase() }, titles)
    }

    @Test
    fun theSystemsFontHasNoFamilyOfItsOwn() {
        assertNull(KeyFont.System.title)
        assertNull(keyFontFamily(KeyFont.System))
    }

    @Test
    fun everyCarriedFontHasARegularAndAMediumFace() {
        KeyFont.entries.drop(1).forEach { font ->
            assertEquals(font.name, listOf(FontWeight.Normal, FontWeight.Medium), faces(font).map { it.weight })
        }
    }

    @Test
    fun aVariableFontSetsItsOneFileToEachWeight() {
        val (regular, medium) = faces(KeyFont.Inter)

        assertEquals(R.font.inter, regular.resId)
        assertEquals(R.font.inter, medium.resId)
        assertEquals(400f, weightAxis(regular))
        assertEquals(500f, weightAxis(medium))
    }

    @Test
    fun aFontWithoutAMediumFaceGivesItsBoldOne() {
        val (regular, medium) = faces(KeyFont.PtSans)

        assertEquals(R.font.pt_sans_regular, regular.resId)
        assertEquals(R.font.pt_sans_bold, medium.resId)
        assertTrue(medium.variationSettings.settings.isEmpty())
    }

    @Test
    fun aFontWithAMediumFaceGivesIt() {
        assertEquals(R.font.fira_sans_medium, faces(KeyFont.FiraSans)[1].resId)
    }

    @Test
    fun everyStyleTakesTheFamily() {
        val family = keyFontFamily(KeyFont.Rubik)
        val styles = Typography().withFontFamily(family).run {
            listOf(
                displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
                titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
                labelLarge, labelMedium, labelSmall,
            )
        }

        assertTrue(styles.all { it.fontFamily == family })
    }

    @Test
    fun theSystemsFontLeavesTheStylesAsTheyAre() {
        val typography = Typography()

        assertSame(typography, typography.withFontFamily(null))
    }

    private fun weightAxis(font: ResourceFont): Float =
        font.variationSettings.settings.single { it.axisName == "wght" }.toVariationValue(null)
}
