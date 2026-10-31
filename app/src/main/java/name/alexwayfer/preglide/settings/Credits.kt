package name.alexwayfer.preglide.settings

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import name.alexwayfer.preglide.R

/**
 * A license a part of the app comes under, where to read it, and its [text], a raw resource, when it asks for a copy
 * to go along. A text is the same for every part under the license; each part's copyright goes in its notice.
 */
enum class License(val title: String, val url: String, @param:RawRes val text: Int? = null) {
    Apache2("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0", R.raw.apache_license_2_0),
    OdcBy("ODC Attribution License 1.0", "https://opendatacommons.org/licenses/by/1-0/"),
    CommonCrawlTerms("Common Crawl Terms of Use", "https://commoncrawl.org/terms-of-use"),
    Mit("MIT License", "https://opensource.org/license/mit", R.raw.mit_license),
    Ofl("SIL Open Font License 1.1", "https://openfontlicense.org/", R.raw.ofl_1_1),
    Unicode3("Unicode License v3", "https://www.unicode.org/license.txt", R.raw.unicode_license_v3),

    // The terms of Google's Android libraries that are not open source, as Play services; they ask for no copy.
    AndroidSdk("Android Software Development Kit License", "https://developer.android.com/studio/terms"),

    // The BSD-style license of Lebedev's Russian spelling dictionary, as LibreOffice's copy of it states.
    Lebedev(
        "Russian spelling dictionary license",
        "https://github.com/LibreOffice/dictionaries/blob/master/ru_RU/README_ru_RU.txt",
        R.raw.lebedev_license,
    ),

    // DejaVu's own: Bitstream Vera's, with Arev's for the glyphs taken from it; DejaVu's changes are public domain.
    BitstreamVera(
        "Bitstream Vera Fonts License",
        "https://dejavu-fonts.github.io/License.html",
        R.raw.bitstream_vera_license,
    ),
}

/**
 * Something the app is built on or from, credited on the licenses screen: its [name] linking to [url], what the
 * app takes from it ([role]), its [license], if it states one, and a [notice] it asks to be shown with it, as the
 * copyright the MIT License asks to keep.
 */
data class Credit(
    val name: String,
    val url: String,
    @param:StringRes val role: Int,
    val license: License?,
    val notice: String? = null,
)

/** The licenses whose text goes along with [credits], each once, in a fixed order. */
fun licenseTexts(credits: List<Credit>): List<License> =
    License.entries.filter { license -> license.text != null && credits.any { it.license == license } }

/** Where the dictionaries' words and frequencies and the emoji come from; see dictionary-builder/README.md. */
val dataCredits = listOf(
    Credit("FineWeb", "https://huggingface.co/datasets/HuggingFaceFW/fineweb", R.string.credit_fineweb, License.OdcBy),
    Credit(
        "FineWeb-2",
        "https://huggingface.co/datasets/HuggingFaceFW/fineweb-2",
        R.string.credit_fineweb_2,
        License.OdcBy,
    ),
    Credit("Common Crawl", "https://commoncrawl.org/", R.string.credit_common_crawl, License.CommonCrawlTerms),
    // OPUS states no license for it, and asks for a link to the site the subtitles come from and this citation.
    Credit(
        "OpenSubtitles.org",
        "https://www.opensubtitles.org/",
        R.string.credit_opensubtitles,
        license = null,
        notice = "P. Lison and J. Tiedemann, 2016. OpenSubtitles2016: Extracting Large Parallel Corpora from " +
            "Movie and TV Subtitles. In Proceedings of the 10th International Conference on Language Resources " +
            "and Evaluation (LREC 2016).",
    ),
    // MIT asks for its copyright and permission notices to go with a copy: the copyright here, the license's text once
    // for every part under it.
    Credit(
        "eyo-kernel",
        "https://github.com/hcodes/eyo-kernel",
        R.string.credit_eyo,
        License.Mit,
        notice = "Copyright (c) 2026 Denis Seleznev",
    ),
    // Its word forms let rarer Russian words into the dictionary; the license asks for its copyright to go along.
    Credit(
        "Russian spelling dictionary",
        "https://github.com/LibreOffice/dictionaries/tree/master/ru_RU",
        R.string.credit_russian_spelling,
        License.Lebedev,
        notice = "Copyright (c) 1997-2008, Alexander I. Lebedev",
    ),
    // The emoji panel's emoji and their names; see dictionary-builder/README.md. The license asks for its copyright
    // and permission notice to go with a copy.
    Credit(
        "Unicode Emoji",
        "https://www.unicode.org/emoji/",
        R.string.credit_unicode_emoji,
        License.Unicode3,
        notice = UNICODE_COPYRIGHT,
    ),
    Credit("Unicode CLDR", "https://cldr.unicode.org/", R.string.credit_cldr, License.Unicode3, UNICODE_COPYRIGHT),
)

private const val UNICODE_COPYRIGHT = "Copyright © 1991-2026 Unicode, Inc."

/** The libraries in the app and the icons on its keys: those of both editions, then this edition's own. */
val softwareCredits get() = sharedSoftwareCredits + editionCredits

/** The libraries both editions ship and the icons on the keys. */
val sharedSoftwareCredits = listOf(
    Credit("Android Jetpack", "https://developer.android.com/jetpack", R.string.credit_jetpack, License.Apache2),
    Credit("Kotlin", "https://kotlinlang.org/", R.string.credit_kotlin, License.Apache2),
    Credit("LiteRT", "https://ai.google.dev/edge/litert", R.string.credit_litert, License.Apache2),
    Credit("Material Symbols", "https://fonts.google.com/icons", R.string.credit_material_symbols, License.Apache2),
    Credit(
        "MaterialKolor",
        "https://github.com/jordond/MaterialKolor",
        R.string.credit_materialkolor,
        License.Mit,
        notice = "Copyright (c) 2025 Jordon de Hoog",
    ),
    // MaterialKolor's POM names it beside its own MIT License: the upstream code it ports keeps Google's.
    Credit(
        "Material Color Utilities",
        "https://github.com/material-foundation/material-color-utilities",
        R.string.credit_material_color_utilities,
        License.Apache2,
    ),
)

/**
 * The fonts the keys can take, named as [name.alexwayfer.preglide.ui.theme.KeyFont] names them. The OFL asks for its
 * copyright notice to go with each copy: the notice of the font's own OFL.txt here, the license's text once.
 */
val fontCredits = listOf(
    googleFont(
        "Comfortaa",
        "Copyright 2011 The Comfortaa Project Authors (https://github.com/alexeiva/comfortaa), " +
            "with Reserved Font Name \"Comfortaa\".",
    ),
    // The LGC build: the Latin, Greek and Cyrillic glyphs of DejaVu Sans.
    Credit(
        "DejaVu Sans",
        "https://dejavu-fonts.github.io/",
        R.string.credit_font,
        License.BitstreamVera,
        notice = "Copyright (c) 2003 by Bitstream, Inc. All Rights Reserved. Bitstream Vera is a trademark of " +
            "Bitstream, Inc. Copyright (c) 2006 by Tavmjong Bah. All Rights Reserved. DejaVu changes are in " +
            "public domain.",
    ),
    googleFont("Fira Sans", "Copyright (c) 2012-2015, The Mozilla Foundation and Telefonica S.A."),
    // Unmodified, as its TRADEMARKS.md asks of a copy that keeps the name.
    googleFont(
        "Google Sans",
        "Copyright 2025 The Google Sans Project Authors (https://github.com/googlefonts/googlesans)",
    ),
    googleFont("IBM Plex Sans", "Copyright © 2017 IBM Corp. with Reserved Font Name \"Plex\""),
    googleFont("Inter", "Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter)"),
    googleFont("Manrope", "Copyright 2018 The Manrope Project Authors (https://github.com/googlefonts/manrope)"),
    googleFont(
        "Montserrat",
        "Copyright 2024 The Montserrat.Git Project Authors (https://github.com/JulietaUla/Montserrat.git)",
    ),
    googleFont(
        "Noto Sans",
        "Copyright 2022 The Noto Project Authors (https://github.com/notofonts/latin-greek-cyrillic)",
    ),
    googleFont("Nunito", "Copyright 2014 The Nunito Project Authors (https://github.com/googlefonts/nunito)"),
    googleFont("Open Sans", "Copyright 2020 The Open Sans Project Authors (https://github.com/googlefonts/opensans)"),
    googleFont(
        "PT Sans",
        "Copyright (c) 2010, ParaType Ltd. (http://www.paratype.com/public), " +
            "with Reserved Font Names \"PT Sans\" and \"ParaType\".",
    ),
    googleFont("Rubik", "Copyright 2015 The Rubik Project Authors (https://github.com/googlefonts/rubik)"),
)

/** A font from Google Fonts, under the OFL, linking to its page there, with its copyright [notice]. */
private fun googleFont(name: String, notice: String) = Credit(
    name,
    "https://fonts.google.com/specimen/${name.replace(' ', '+')}",
    R.string.credit_font,
    License.Ofl,
    notice,
)
