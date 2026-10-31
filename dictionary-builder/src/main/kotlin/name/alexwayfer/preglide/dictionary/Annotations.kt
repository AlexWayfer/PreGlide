package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.emoji.EmojiName
import org.w3c.dom.Element
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * CLDR's annotations of a language, by emoji or symbol, written without U+FE0F as CLDR writes them: the [names] a
 * screen reader says and the [keywords] to find each by.
 */
class Annotations(val names: Map<String, String>, val keywords: Map<String, List<String>>)

/** The annotations of a CLDR `annotations/<language>.xml` file. */
fun readAnnotations(input: InputStream): Annotations {
    val factory = DocumentBuilderFactory.newInstance()
    // The file names CLDR's DTD by a relative path, which is not here, and nothing in it is needed.
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    val nodes = factory.newDocumentBuilder().parse(input).getElementsByTagName("annotation")
    val names = mutableMapOf<String, String>()
    val keywords = mutableMapOf<String, List<String>>()
    for (index in 0 until nodes.length) {
        val node = nodes.item(index) as Element
        val emoji = node.getAttribute("cp")
        val text = node.textContent.trim()
        if (node.getAttribute("type") == "tts") {
            names[emoji] = text
        } else {
            keywords[emoji] = text.split('|').map(String::trim).filter(String::isNotEmpty)
        }
    }
    return Annotations(names, keywords)
}

private const val ZERO_WIDTH_JOINER = 0x200D
private const val KEYCAP = 0x20E3
private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
private const val BLACK_FLAG = 0x1F3F4
private val TAG_LETTERS = 0xE0061..0xE007A

/** The word for a flag in each language, which a flag's name starts with, as CLDR's derived names do. */
private val flagWords = mapOf("ru" to "флаг", "en" to "flag")

/** The flags of the parts of a country, which Java names no region for, by their tag: England, Scotland, Wales. */
private val subdivisions = mapOf(
    "gbeng" to mapOf("ru" to "Англия", "en" to "England"),
    "gbsct" to mapOf("ru" to "Шотландия", "en" to "Scotland"),
    "gbwls" to mapOf("ru" to "Уэльс", "en" to "Wales"),
)

/**
 * The name of [emoji], [english] in emoji-test.txt, in [language] by its [annotations]. CLDR names most emoji
 * itself. Flags it names in its derived annotations, from the names of countries, which Java's locale data, CLDR's
 * too, has. A sequence it names nowhere, as a man with red hair, gets the names of its parts. Failing all, English
 * keeps the name emoji-test.txt gives; other languages have none.
 */
fun nameOf(emoji: String, english: String, annotations: Annotations, language: String): EmojiName? {
    val key = emoji.filterSelector()
    annotations.names[key]?.let { name -> return EmojiName(emoji, name, annotations.keywords[key].orEmpty()) }
    val named = flagName(emoji, language) ?: partsName(emoji, annotations)
    return named ?: EmojiName(emoji, english).takeIf { language == "en" }
}

private fun String.filterSelector(): String =
    codePoints().filter { it != EMOJI_SELECTOR }.toArray().let { String(it, 0, it.size) }

/** A flag's name: the word for a flag and the country's name; null for an emoji that is not a flag. */
private fun flagName(emoji: String, language: String): EmojiName? {
    val codes = emoji.codePoints().toArray()
    val place = when {
        codes.size == 2 && codes.all { it in REGIONAL_INDICATORS } -> {
            val region = codes.joinToString("") { ('A' + (it - REGIONAL_INDICATORS.first)).toString() }
            Locale.of("", region).getDisplayCountry(Locale.of(language)).takeIf { it != region }
        }
        codes.first() == BLACK_FLAG && codes.drop(1).any { it in TAG_LETTERS } -> {
            val tag = codes.filter { it in TAG_LETTERS }
                .joinToString("") { ('a' + (it - TAG_LETTERS.first)).toString() }
            subdivisions[tag]?.get(language)
        }
        else -> null
    } ?: return null
    val flag = flagWords.getValue(language)
    return EmojiName(emoji, "$flag: $place", listOf(place, flag))
}

/** A sequence's name from the names of its parts, past the joiners, selectors and tones; null when none is named. */
private fun partsName(emoji: String, annotations: Annotations): EmojiName? {
    val parts = emoji.codePoints().toArray()
        .filter { it != ZERO_WIDTH_JOINER && it != EMOJI_SELECTOR && it != KEYCAP && it !in TONE_MODIFIERS }
        .map { String(Character.toChars(it)) }
        .filter { it in annotations.names }
    if (parts.isEmpty()) return null
    val keywords = parts.flatMap { annotations.keywords[it].orEmpty() }.distinct()
    return EmojiName(emoji, parts.joinToString(", ") { annotations.names.getValue(it) }, keywords)
}
