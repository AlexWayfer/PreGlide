package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WordFormsTest {
    private val affixes = """
        SET UTF-8
        TRY иаоент

        SFX L Y 3
        SFX L   ать  ают   ать
        SFX L   ать  ала   ать
        SFX L   ть   л     [ая]ть

        SFX A Y 2
        SFX A   0    а     [^аеиоуыэюя]
        SFX A   0    ы     [^аеиоуыэюя]
    """.trimIndent()

    private fun forms(vararg stems: String): Set<String> =
        wordForms(affixes.lineSequence(), sequenceOf("${stems.size}") + stems.asSequence())

    @Test
    fun `a stem gives itself and the forms its flags add`() {
        assertEquals(
            setOf("нахваливать", "нахваливают", "нахваливала", "нахваливал"),
            forms("нахваливать/L"),
        )
    }

    @Test
    fun `a rule applies only to a stem its condition matches`() {
        // ать strips nothing from "кот", and "окно" ends in a vowel, where A adds nothing.
        assertEquals(setOf("кот", "кота", "коты", "окно"), forms("кот/AL", "окно/A"))
    }

    @Test
    fun `a stem without flags is a word on its own`() {
        assertEquals(setOf("еще", "мир"), forms("ещё", "мир"))
    }

    @Test
    fun `forms are keyed in lowercase with e for yo`() {
        assertEquals(setOf("москва", "елка"), forms("Москва", "Ёлка"))
        assertEquals("елка", formKey("Ёлка"))
    }

    @Test
    fun `the first line, the count of stems, and blank lines are no words`() {
        assertEquals(setOf("мир"), wordForms(affixes.lineSequence(), sequenceOf("2", "", "мир")))
    }

    @Test
    fun `prefixes and other flag formats are refused rather than read wrong`() {
        for (line in listOf("PFX P Y 1", "FLAG long")) {
            assertThrows(IllegalArgumentException::class.java) {
                wordForms(sequenceOf(line), sequenceOf("1", "мир"))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            wordForms(sequenceOf("SFX A   0    а/B     ."), sequenceOf("1", "мир"))
        }
    }
}
