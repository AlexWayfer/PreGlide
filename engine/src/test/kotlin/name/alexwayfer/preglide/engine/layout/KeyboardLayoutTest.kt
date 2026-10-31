package name.alexwayfer.preglide.engine.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    private fun KeyboardLayout.letters(): Set<Char> =
        rows.flatten().mapNotNull { (it.action as? KeyAction.Letter)?.char }.filter { it.isLetter() }.toSet()

    @Test
    fun `Russian layout has all 33 letters with yo and the hard sign on long press`() {
        val layout = KeyboardLayout.of(Language.Russian)
        val alternates = layout.rows.flatten().flatMap { (it.action as? KeyAction.Letter)?.alternates.orEmpty() }
            .filter { it.isLetter() }.toSet()

        assertEquals(('а'..'я').toSet() + 'ё', layout.letters() + alternates)
    }

    @Test
    fun `a language tag names its language whatever its region`() {
        assertEquals(Language.Russian, Language.forTag("ru"))
        assertEquals(Language.Russian, Language.forTag("ru-RU"))
        assertEquals(Language.English, Language.forTag("en_US"))
        assertEquals(Language.English, Language.forTag("EN"))
        assertEquals(null, Language.forTag("de-DE"))
        assertEquals(null, Language.forTag(""))
    }

    @Test
    fun `English layout has all 26 letters`() {
        assertEquals(('a'..'z').toSet(), KeyboardLayout.of(Language.English).letters())
    }

    @Test
    fun `every letters layout has shift, backspace, space, enter and the way to symbols`() {
        for (language in Language.entries) {
            val actions = KeyboardLayout.of(language).actions()

            assertTrue(
                language.tag,
                actions.containsAll(
                    listOf(
                        KeyAction.Shift,
                        KeyAction.Backspace,
                        KeyAction.Space,
                        KeyAction.Enter,
                        KeyAction.ShowPage(Page.Symbols),
                    ),
                ),
            )
        }
    }

    @Test
    fun `symbols have every digit and the language's own currency`() {
        for ((language, currency) in listOf(Language.Russian to '₽', Language.English to '$')) {
            val chars = KeyboardLayout.of(language, Page.Symbols).chars()

            assertTrue(language.tag, chars.containsAll(('0'..'9').toList() + currency))
        }
    }

    @Test
    fun `the currency key offers the popular currencies`() {
        for ((language, currency) in listOf(Language.Russian to '₽', Language.English to '$')) {
            val key = KeyboardLayout.of(language, Page.Symbols).rows.flatten()
                .map { it.action }.filterIsInstance<KeyAction.Letter>().single { it.char == currency }

            assertTrue(language.tag, key.options.containsAll(listOf('₽', '$', '€')))
        }
    }

    @Test
    fun `a long press offers the key's own char first and nothing when there are no alternates`() {
        assertEquals(listOf('е', 'ё'), KeyAction.Letter('е', listOf('ё')).options)
        assertEquals(emptyList<Char>(), KeyAction.Letter('ж').options)
    }

    @Test
    fun `a long press offers the hint before the alternates`() {
        assertEquals(listOf('е', '5', 'ё'), KeyAction.Letter('е', listOf('ё'), hint = '5').options)
        assertEquals(listOf('q', '1'), KeyAction.Letter('q', hint = '1').options)
    }

    @Test
    fun `a long press offers the symbols after the hint and before the alternates`() {
        assertEquals(listOf('ь', '?', 'ъ'), KeyAction.Letter('ь', listOf('ъ'), symbols = listOf('?')).options)
        assertEquals(
            listOf('ь', '1', '?', 'ъ'),
            KeyAction.Letter('ь', listOf('ъ'), hint = '1', symbols = listOf('?')).options,
        )
        assertEquals(listOf('р', '-', '—', '–'), KeyAction.Letter('р', symbols = "-—–".toList()).options)
    }

    @Test
    fun `a letter's symbol brings what its own long press offers`() {
        val options = { language: Language, row: Int, char: Char ->
            KeyboardLayout.of(language).rows[row].map { it.action }.filterIsInstance<KeyAction.Letter>()
                .single { it.char == char }.options.joinToString("")
        }

        assertEquals("в₽$€£¥", options(Language.Russian, 1, 'в'))
        assertEquals("ж/\\|", options(Language.Russian, 1, 'ж'))
        assertEquals("ч\"«»„“", options(Language.Russian, 2, 'ч'))
        assertEquals("ю%‰", options(Language.Russian, 2, 'ю'))
        assertEquals("э~≈", options(Language.Russian, 1, 'э'))
        assertEquals("ы#№", options(Language.Russian, 1, 'ы'))
        assertEquals("ь?ъ", options(Language.Russian, 2, 'ь'))
        assertEquals("d$¢€₽£¥", options(Language.English, 1, 'd'))
        assertEquals("x\"“”«»", options(Language.English, 2, 'x'))
    }

    @Test
    fun `the letters' middle and bottom rows offer the symbols page's keys at the same places, then more`() {
        val russian = KeyboardLayout.of(Language.Russian).rows
        val english = KeyboardLayout.of(Language.English).rows

        assertEquals("фывапролджэ".zip("@#₽_&-+()/~").toMap(), russian[1].letterSymbols())
        assertEquals("ячсмитьбю".zip("*\"':;!?=%").toMap(), russian[2].letterSymbols())
        assertEquals("asdfghjkl".zip($$"@#$_&-+()").toMap(), english[1].letterSymbols())
        assertEquals("zxcvbnm".zip("*\"':;!?").toMap(), english[2].letterSymbols())
    }

    @Test
    fun `the symbols stay off the top row and the bottom row, and off every other page`() {
        for (language in Language.entries) {
            val rows = KeyboardLayout.of(language).rows

            assertEquals(language.tag, emptyMap<Char, Char>(), (rows.first() + rows.last()).letterSymbols())
            for (page in Page.entries - Page.Letters) {
                val keys = KeyboardLayout.of(language, page).rows.flatten()

                assertEquals("${language.tag} $page", emptyMap<Char, Char>(), keys.letterSymbols())
            }
        }
    }

    @Test
    fun `the letters' top row hints at the digits from 1 to 0, the rest of it at nothing`() {
        for ((language, hinted) in listOf(Language.Russian to "йцукенгшщз", Language.English to "qwertyuiop")) {
            val layout = KeyboardLayout.of(language)
            val topRow = layout.rows.first().map { it.action as KeyAction.Letter }

            assertEquals(language.tag, hinted.zip("1234567890").toMap(), topRow.hints())
            assertEquals(language.tag, emptyMap<Char, Char>(), layout.rows.drop(1).flatten().letterHints())
        }
    }

    @Test
    fun `the symbol pages hint at nothing, as they have the digits on their keys`() {
        for (language in Language.entries) {
            for (page in listOf(Page.Symbols, Page.MoreSymbols)) {
                assertEquals(emptyMap<Char, Char>(), KeyboardLayout.of(language, page).rows.flatten().letterHints())
            }
        }
    }

    @Test
    fun `the digit row types the symbols page's top row and offers nothing more`() {
        val digits = digitRow().map { it.action as KeyAction.Letter }

        assertEquals("1234567890", digits.map { it.char }.joinToString(""))
        assertTrue(digits.all { it.options.isEmpty() })
        assertEquals(KeyboardLayout.of(Language.Russian, Page.Symbols).rows.first(), digitRow())
    }

    @Test
    fun `the emoji key goes right before the comma, and the space bar gives up its width`() {
        for (language in Language.entries) {
            val bottom = KeyboardLayout.of(language).rows.last()
            val withKey = withEmojiKey(bottom)

            assertEquals(language.tag, "?, .⏎", bottom.joinToString("") { it.action.sign() })
            assertEquals(language.tag, "?☺, .⏎", withKey.joinToString("") { it.action.sign() })
            val width = { row: List<Key> -> row.sumOf { it.width.toDouble() } }
            assertEquals(language.tag, width(bottom), width(withKey), 0.001)
            assertEquals(language.tag, bottom[1], withKey[2])
            assertEquals(language.tag, bottom[2].width - 1f, withKey[3].width)
            assertFalse(language.tag, withKey[1].standsOut)
        }
    }

    @Test
    fun `a part of the emoji key takes as much from the space bar`() {
        val bottom = KeyboardLayout.of(Language.English).rows.last()
        val withKey = withEmojiKey(bottom, width = 0.25f)

        assertEquals(0.25f, withKey[1].width)
        assertEquals(bottom[2].width - 0.25f, withKey[3].width)
    }

    @Test
    fun `a row without a comma or a space bar gets no emoji key`() {
        val top = KeyboardLayout.of(Language.Russian).rows.first()
        val noSpace = listOf(Key(KeyAction.Letter(',')), Key(KeyAction.Enter))

        assertEquals(top, withEmojiKey(top))
        assertEquals(noSpace, withEmojiKey(noSpace))
    }

    @Test
    fun `every long press shows in a corner but the period's, which shows its marks`() {
        val hinted = { language: Language, page: Page ->
            KeyboardLayout.of(language, page).rows.flatten()
                .mapNotNull { it.action as? KeyAction.Letter }
                .filter { it.alternates.isNotEmpty() && it.hint == null }
                .associate { it.char to it.optionsHint }
        }
        val symbols = mapOf(
            '#' to "№", '-' to "—", '+' to "±", '(' to "{", ')' to "}", '/' to "\\",
            '*' to "•", '\'' to "‘", '.' to null,
        )

        assertEquals(mapOf('ь' to "ъ", '.' to null), hinted(Language.Russian, Page.Letters))
        assertEquals(mapOf('.' to null), hinted(Language.English, Page.Letters))
        assertEquals(symbols + mapOf('₽' to "$", '"' to "«"), hinted(Language.Russian, Page.Symbols))
        assertEquals(symbols + mapOf('$' to "¢", '"' to "“"), hinted(Language.English, Page.Symbols))
        assertEquals(
            mapOf('~' to "≈", '§' to "¶", '%' to "‰", '.' to null),
            hinted(Language.Russian, Page.MoreSymbols),
        )
        assertEquals(mapOf('+' to "-()"), hinted(Language.Russian, Page.Phone))
    }

    @Test
    fun `e offers its digit under the finger and yo after it`() {
        val key = KeyboardLayout.of(Language.Russian).rows.first().map { it.action as KeyAction.Letter }
            .single { it.char == 'е' }

        assertEquals(listOf('е', '5', 'ё'), key.options)
    }

    private fun List<KeyAction.Letter>.hints(): Map<Char, Char> =
        mapNotNull { letter -> letter.hint?.let { letter.char to it } }.toMap()

    private fun List<Key>.letterHints(): Map<Char, Char> = mapNotNull { it.action as? KeyAction.Letter }.hints()

    private fun List<Key>.letterSymbols(): Map<Char, Char> = mapNotNull { it.action as? KeyAction.Letter }
        .mapNotNull { letter -> letter.symbols.firstOrNull()?.let { letter.char to it } }.toMap()

    @Test
    fun `both symbol pages lead to each other and back to letters`() {
        for (language in Language.entries) {
            val symbols = KeyboardLayout.of(language, Page.Symbols).actions()
            val moreSymbols = KeyboardLayout.of(language, Page.MoreSymbols).actions()

            assertTrue(language.tag, KeyAction.ShowPage(Page.MoreSymbols) in symbols)
            assertTrue(language.tag, KeyAction.ShowPage(Page.Symbols) in moreSymbols)
            assertTrue(language.tag, KeyAction.ShowPage(Page.Letters) in symbols)
            assertTrue(language.tag, KeyAction.ShowPage(Page.Letters) in moreSymbols)
        }
    }

    @Test
    fun `no symbol has a key on both symbol pages`() {
        for (language in Language.entries) {
            val symbols = KeyboardLayout.of(language, Page.Symbols).keyChars()
            val moreSymbols = KeyboardLayout.of(language, Page.MoreSymbols).keyChars()

            // The bottom row with comma and period is shared on purpose. Long presses may repeat
            // the other page's keys, as { [ < on the parenthesis.
            assertEquals(language.tag, setOf(',', '.'), symbols intersect moreSymbols)
        }
    }

    @Test
    fun `the number pad has the digits as on a calculator, the decimal marks and the minus`() {
        val layout = KeyboardLayout.of(Language.Russian, Page.Digits)

        assertEquals(
            listOf("123-", "456 ", "789⌫", ",0.⏎"),
            layout.rows.map { row -> row.joinToString("") { it.action.sign() } },
        )
    }

    @Test
    fun `the phone pad has the plus with the marks of a number, the star and the hash`() {
        val layout = KeyboardLayout.of(Language.Russian, Page.Phone)
        val plus = layout.rows.first().last().action as KeyAction.Letter

        assertEquals(
            listOf("123+", "456 ", "789⌫", "*0#⏎"),
            layout.rows.map { row -> row.joinToString("") { it.action.sign() } },
        )
        assertEquals(listOf('+', '-', '(', ')'), plus.options)
    }

    @Test
    fun `a keypad is the same in every language and leads nowhere`() {
        for (page in Page.entries.filter { it.keypad }) {
            val layouts = Language.entries.map { KeyboardLayout.of(it, page) }

            assertEquals(page.name, 1, layouts.map { it.rows }.distinct().size)
            assertTrue(page.name, layouts.first().actions().none { it is KeyAction.ShowPage || it == KeyAction.Shift })
        }
    }

    @Test
    fun `chars stand out from the function keys, but the comma and the period beside the space bar`() {
        val keys = KeyboardLayout.of(Language.Russian).rows.flatten()
        val (chars, others) = keys.partition { it.action is KeyAction.Letter }

        assertEquals(listOf(",", "."), chars.filterNot { it.standsOut }.map { it.action.sign() })
        assertEquals(listOf(KeyAction.Space), others.filter { it.standsOut }.map { it.action })
    }

    @Test
    fun `a keypad's digits and the marks around the zero stand out, its right column does not`() {
        for (page in Page.entries.filter { it.keypad }) {
            val rows = KeyboardLayout.of(Language.English, page).rows

            assertTrue(page.name, rows.all { row -> row.dropLast(1).all { it.standsOut } })
            assertTrue(page.name, rows.none { row -> row.last().standsOut })
        }
    }

    /** A key in one char: what it types, a space, ⌫, ⏎ or ☺ for the emoji's. */
    private fun KeyAction.sign(): String = when (this) {
        is KeyAction.Letter -> char.toString()
        KeyAction.Space -> " "
        KeyAction.Backspace -> "⌫"
        KeyAction.Enter -> "⏎"
        KeyAction.Emoji -> "☺"
        else -> "?"
    }

    private fun KeyboardLayout.actions(): Set<KeyAction> = rows.flatten().map { it.action }.toSet()

    /** What the keys type without a long press. */
    private fun KeyboardLayout.keyChars(): Set<Char> =
        rows.flatten().mapNotNull { (it.action as? KeyAction.Letter)?.char }.toSet()

    /** What the keys type, long presses included. */
    private fun KeyboardLayout.chars(): Set<Char> = rows.flatten()
        .mapNotNull { it.action as? KeyAction.Letter }
        .flatMap { listOf(it.char) + it.alternates }
        .toSet()
}
