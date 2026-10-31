package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.ui.theme.KeyFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsTest {
    @Test
    fun everySourceOfTheDictionariesIsCredited() {
        assertEquals(
            listOf(
                "FineWeb",
                "FineWeb-2",
                "Common Crawl",
                "OpenSubtitles.org",
                "eyo-kernel",
                "Russian spelling dictionary",
                "Unicode Emoji",
                "Unicode CLDR",
            ),
            dataCredits.map { it.name },
        )
    }

    @Test
    fun theYoListCarriesItsMitCopyright() {
        val yo = dataCredits.single { it.name == "eyo-kernel" }

        assertEquals(License.Mit, yo.license)
        assertEquals("Copyright (c) 2026 Denis Seleznev", yo.notice)
    }

    @Test
    fun theSpellingDictionaryCarriesLebedevsCopyrightAndLicense() {
        val spelling = dataCredits.single { it.name == "Russian spelling dictionary" }

        assertEquals(License.Lebedev, spelling.license)
        assertEquals("Copyright (c) 1997-2008, Alexander I. Lebedev", spelling.notice)
    }

    @Test
    fun everyLibraryInTheAppIsCredited() {
        assertEquals(
            listOf(
                "Android Jetpack",
                "Kotlin",
                "LiteRT",
                "Material Symbols",
                "MaterialKolor",
                "Material Color Utilities",
            ),
            sharedSoftwareCredits.map { it.name },
        )
        assertEquals(sharedSoftwareCredits + editionCredits, softwareCredits)
    }

    @Test
    fun thePaletteLibraryCarriesItsMitCopyright() {
        val palettes = softwareCredits.single { it.name == "MaterialKolor" }

        assertEquals(License.Mit, palettes.license)
        assertEquals("Copyright (c) 2025 Jordon de Hoog", palettes.notice)
    }

    @Test
    fun everyFontTheKeysCanTakeIsCredited() {
        assertEquals(KeyFont.entries.mapNotNull { it.title }, fontCredits.map { it.name })
    }

    @Test
    fun theFontsComeUnderTheOflButDejaVuUnderItsOwnLicense() {
        val dejaVu = fontCredits.single { it.name == "DejaVu Sans" }

        assertEquals(License.BitstreamVera, dejaVu.license)
        assertTrue((fontCredits - dejaVu).all { it.license == License.Ofl })
    }

    @Test
    fun everyFontCarriesItsCopyright() {
        assertTrue(fontCredits.all { it.notice.orEmpty().startsWith("Copyright") })
    }

    @Test
    fun eachLicenseThatAsksForItsTextCarriesItOnce() {
        val texts = listOf(
            License.Apache2,
            License.Mit,
            License.Ofl,
            License.Unicode3,
            License.Lebedev,
            License.BitstreamVera,
        )

        assertEquals(texts, licenseTexts(dataCredits + softwareCredits + fontCredits))
        texts.forEach { assertNotNull(it.text) }
    }

    @Test
    fun aLicenseNoCreditComesUnderCarriesNoText() {
        assertEquals(listOf(License.Mit), licenseTexts(dataCredits.filter { it.name == "eyo-kernel" }))
    }

    @Test
    fun theEmojiDataCarriesUnicodesCopyright() {
        val unicode = dataCredits.filter { it.name.startsWith("Unicode") }

        assertEquals(2, unicode.size)
        assertTrue(unicode.all { it.license == License.Unicode3 && it.notice == "Copyright © 1991-2026 Unicode, Inc." })
    }

    @Test
    fun fineWebIsCreditedUnderItsAttributionLicense() {
        assertTrue(dataCredits.filter { it.name.startsWith("FineWeb") }.all { it.license == License.OdcBy })
    }

    @Test
    fun openSubtitlesLinksToTheSiteAndCarriesTheCitation() {
        val subtitles = dataCredits.single { it.name == "OpenSubtitles.org" }

        assertEquals("https://www.opensubtitles.org/", subtitles.url)
        assertNotNull(subtitles.notice)
    }

    @Test
    fun everyLinkIsHttps() {
        val links = (dataCredits + softwareCredits + fontCredits).map { it.url } + License.entries.map { it.url }

        assertTrue(links.toString(), links.all { it.startsWith("https://") })
    }
}
