package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.emoji.EmojiName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A few annotations as CLDR's ru.xml has them, with its DTD and the selectors left out. */
val russianAnnotations = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <!DOCTYPE ldml SYSTEM "../../common/dtd/ldml.dtd">
    <ldml>
        <annotations>
            <annotation cp="😀">лицо | радость | смех</annotation>
            <annotation cp="😀" type="tts">широко улыбается</annotation>
            <annotation cp="☺">лицо | улыбка</annotation>
            <annotation cp="☺" type="tts">улыбающееся лицо</annotation>
            <annotation cp="👨">мужчина | взрослый</annotation>
            <annotation cp="👨" type="tts">мужчина</annotation>
            <annotation cp="🦰">рыжий | волосы</annotation>
            <annotation cp="🦰" type="tts">рыжие волосы</annotation>
            <annotation cp="&amp;">амперсанд | и</annotation>
            <annotation cp="&amp;" type="tts">амперсанд</annotation>
        </annotations>
    </ldml>
""".trimIndent()

class AnnotationsTest {
    private val annotations = readAnnotations(russianAnnotations.byteInputStream())

    private fun name(emoji: String, language: String = "ru", english: String = "an emoji") =
        nameOf(emoji, english, annotations, language)

    @Test
    fun anAnnotationGivesTheNameAndTheKeywords() {
        assertEquals(EmojiName("😀", "широко улыбается", listOf("лицо", "радость", "смех")), name("😀"))
    }

    @Test
    fun anEmojiIsFoundWithoutItsSelector() {
        assertEquals("улыбающееся лицо", name("☺️")?.name)
    }

    @Test
    fun theFileIsReadAsXml() {
        assertEquals("амперсанд", annotations.names["&"])
    }

    @Test
    fun aSequenceNamedNowhereGetsTheNamesOfItsParts() {
        val name = name("👨‍🦰")

        assertEquals("мужчина, рыжие волосы", name?.name)
        assertEquals(listOf("мужчина", "взрослый", "рыжий", "волосы"), name?.keywords)
    }

    @Test
    fun aCountrysFlagIsNamedByTheCountry() {
        assertEquals(EmojiName("🇷🇺", "флаг: Россия", listOf("Россия", "флаг")), name("🇷🇺"))
        assertEquals("flag: Germany", name("🇩🇪", "en")?.name)
    }

    @Test
    fun theFlagOfAPartOfACountryIsNamedByThatPart() {
        assertEquals("флаг: Шотландия", name("🏴󠁧󠁢󠁳󠁣󠁴󠁿")?.name)
    }

    @Test
    fun aFlagOfNoKnownPlaceAndAnEmojiNamedNowhereGetNoNameButInEnglish() {
        // Sark, which Java's locale data has no name for.
        val unknownRegion = "🇨🇶"
        val unknownPart = "🏴󠁧󠁢󠁸󠁸󠁸󠁿"

        assertNull(name(unknownRegion))
        assertNull(name(unknownPart))
        assertNull(name("🫫"))
        assertEquals(EmojiName("🫫", "cracking face"), name("🫫", "en", english = "cracking face"))
    }
}
