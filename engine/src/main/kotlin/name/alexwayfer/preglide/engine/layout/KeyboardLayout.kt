package name.alexwayfer.preglide.engine.layout

import kotlin.math.abs

/**
 * A keyboard layout as rows of keys. Widths are in key units, and every row adds up to the same total,
 * so the glide decoder can place key centers from this alone.
 */
data class KeyboardLayout(
    val language: Language,
    val page: Page,
    val rows: List<List<Key>>,
) {
    val rowWidth: Float = rows.first().sumOf { it.width.toDouble() }.toFloat()

    /** Where each key that types a char lies, by that char, as the layout has it: lowercase, so е and not ё. */
    val places: Map<Char, KeyPlace> by lazy { placesOf(rows) }

    /** Where a glide finds each letter: [places], and a letter of a key's long press, as ъ, at that key. */
    val glidePlaces: Map<Char, KeyPlace> by lazy { glidePlacesOf(rows) }

    init {
        require(rows.all { row -> abs(row.sumOf { it.width.toDouble() } - rowWidth) < ROW_WIDTH_TOLERANCE }) {
            "Every row of the ${language.tag} $page layout must add up to $rowWidth key units"
        }
    }

    companion object {
        /** Fractional widths do not add up exactly in floating point. */
        private const val ROW_WIDTH_TOLERANCE = 0.001

        private val layouts = Language.entries.associateWith { language ->
            Page.entries.associateWith { page ->
                when (page) {
                    Page.Letters -> when (language) {
                        Language.Russian -> russianLetters()
                        Language.English -> englishLetters()
                    }
                    Page.Symbols -> symbols(language)
                    Page.MoreSymbols -> moreSymbols(language)
                    Page.Digits -> digits(language)
                    Page.Phone -> phone(language)
                }
            }
        }

        fun of(language: Language, page: Page = Page.Letters): KeyboardLayout =
            layouts.getValue(language).getValue(page)
    }
}

/**
 * A key doing [action], [width] key units wide. A key that [standsOut] gets the bright background of the keys typed
 * most: by default a char's key and the space bar, while the function keys around them stay darker.
 */
data class Key(
    val action: KeyAction,
    val width: Float = 1f,
    val standsOut: Boolean = action is KeyAction.Letter || action == KeyAction.Space,
)

sealed interface KeyAction {
    /**
     * Types [char]. With [alternates], a long press offers [char] and then the alternates to choose from,
     * with the first alternate preselected, as ъ on ь. A [hint], as a digit on the top row, can be shown small in
     * the key's corner and offered right before the alternates, so the long press preselects what the corner shows,
     * as 5 on е with ё after it; the user may turn hints off. The [symbols], the symbols page's key at the same place
     * and what its long press offers, show and are offered by a setting of their own, off by default, the first of
     * them in the corner and all of them before the alternates, for the same reason, as ? on ь with ъ after it. An
     * [optionsHint] shows there while neither does, always, to tell of the long press: its first alternate, or all
     * of a few.
     */
    data class Letter(
        val char: Char,
        val alternates: List<Char> = emptyList(),
        val hint: Char? = null,
        val optionsHint: String? = null,
        val symbols: List<Char> = emptyList(),
    ) : KeyAction {
        /** What a long press offers, in order, the [hint], the [symbols], the [alternates]; empty for nothing. */
        val options: List<Char>
            get() {
                val more = listOfNotNull(hint) + symbols + alternates
                return if (more.isEmpty()) emptyList() else listOf(char) + more
            }
    }
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction

    /** Switches the keyboard to another [page]. */
    data class ShowPage(val page: Page) : KeyAction

    /** Opens the emoji panel in place of the keys. */
    data object Emoji : KeyAction

    /** Empty room that keeps a shorter row centered. */
    data object Gap : KeyAction
}

/** A language the keyboard types, by its BCP 47 [tag], written in [script]. */
enum class Language(val tag: String, val script: Character.UnicodeScript) {
    Russian("ru", Character.UnicodeScript.CYRILLIC),
    English("en", Character.UnicodeScript.LATIN),
    ;

    companion object {
        /** The language of a BCP 47 [languageTag], such as "ru-RU" or "en_US", or null for one the keyboard lacks. */
        fun forTag(languageTag: String): Language? {
            val language = languageTag.substringBefore('-').substringBefore('_').lowercase()
            return entries.firstOrNull { it.tag == language }
        }
    }
}

/** A page of keys; a [keypad] is the whole keyboard of a field that takes digits, with no way to the letters. */
enum class Page(val keypad: Boolean = false) {
    Letters,

    /** Digits and the common symbols. */
    Symbols,

    /** The rarer symbols. */
    MoreSymbols,

    /** Digits with the decimal marks and the minus, for a number field. */
    Digits(keypad = true),

    /** Digits with the marks of a phone number, for a phone field. */
    Phone(keypad = true),
}

/**
 * A key for each of [chars]; [alternates] maps a char to what its long press offers besides it, and the chars in
 * [hinted] show the first of those in the corner (see [KeyAction.Letter.optionsHint]).
 */
internal fun letters(
    chars: String,
    alternates: Map<Char, String> = emptyMap(),
    width: Float = 1f,
    hinted: String = "",
): List<Key> = chars.map { char ->
    val offered = alternates[char].orEmpty().toList()
    val hint = offered.firstOrNull()?.takeIf { char in hinted }?.toString()
    Key(KeyAction.Letter(char, offered, optionsHint = hint), width)
}

/** The digits a letters page hints at on its top row, in the order of the symbols page. */
private const val TOP_ROW_DIGITS = "1234567890"

/**
 * The row of digits the user may have above the letters, in place of the top row's hints: the same digits, over the
 * same keys, as the symbols page has. It is no part of a layout, so the letters' rows stay what a glide is read on.
 */
fun digitRow(): List<Key> = letters(TOP_ROW_DIGITS)

/**
 * [row] with the emoji key the user may have right before its comma, [width] key units wide, and its space bar as
 * much narrower, so the row keeps its width. As the [digitRow], it is no part of a layout, so the rows a glide is read
 * on stay as they are. A row without a comma or a space bar comes back as it is.
 */
fun withEmojiKey(row: List<Key>, width: Float = 1f): List<Key> {
    val comma = row.indexOfFirst { (it.action as? KeyAction.Letter)?.char == ',' }
    if (comma < 0 || row.none { it.action == KeyAction.Space }) return row
    val narrower = row.map { if (it.action == KeyAction.Space) it.copy(width = it.width - width) else it }
    return narrower.take(comma) + Key(KeyAction.Emoji, width) + narrower.drop(comma)
}

/** [row] with a digit hint on each of its first ten keys, as on the symbols page above the same keys. */
private fun withDigitHints(row: List<Key>): List<Key> = row.mapIndexed { index, key ->
    val action = key.action
    val digit = TOP_ROW_DIGITS.getOrNull(index)
    if (action is KeyAction.Letter && digit != null) key.copy(action = action.copy(hint = digit)) else key
}

/** The bottom row of every page: [left] opens another page, and the space takes what the row has left. */
private fun bottomRow(left: Page, rowWidth: Float): List<Key> = listOf(
    Key(KeyAction.ShowPage(left), width = 1.5f),
    // The comma and the period beside the space bar look like the function keys around them.
    Key(KeyAction.Letter(','), standsOut = false),
    Key(KeyAction.Space, width = rowWidth - 5.5f),
    Key(KeyAction.Letter('.', "…,;?!".toList()), standsOut = false),
    Key(KeyAction.Enter, width = 2f),
)

private fun russianLetters(): KeyboardLayout {
    val symbols = symbols(Language.Russian).rows
    val moreSymbols = moreSymbols(Language.Russian).rows.flatten()
    return KeyboardLayout(
        language = Language.Russian,
        page = Page.Letters,
        rows = listOf(
            // х, the eleventh, gets no digit.
            withDigitHints(letters("йцукенгшщзх", mapOf('е' to "ё"))),
            // The rows are longer than the symbols page's, so their last letters get symbols of the other page.
            withSymbols(letters("фывапролджэ"), symbols[1], more = "~", others = moreSymbols),
            // Eleven letters wide, like the rows above, so shift and backspace get their extra width from
            // slightly narrower letters.
            withSymbols(
                listOf(Key(KeyAction.Shift, width = 1.25f)) +
                    letters("ячсмитьбю", mapOf('ь' to "ъ"), width = 8.5f / 9, hinted = "ь") +
                    Key(KeyAction.Backspace, width = 1.25f),
                symbols[2],
                more = "=%",
                others = moreSymbols,
            ),
            bottomRow(Page.Symbols, rowWidth = 11f),
        ),
    )
}

private fun englishLetters(): KeyboardLayout {
    val symbols = symbols(Language.English).rows
    return KeyboardLayout(
        language = Language.English,
        page = Page.Letters,
        rows = listOf(
            withDigitHints(letters("qwertyuiop")),
            // Nine letters under ten symbols: the slash, the last, stays on the symbols page.
            withSymbols(
                listOf(Key(KeyAction.Gap, width = 0.5f)) + letters("asdfghjkl") + Key(KeyAction.Gap, width = 0.5f),
                symbols[1],
            ),
            withSymbols(
                listOf(Key(KeyAction.Shift, width = 1.5f)) +
                    letters("zxcvbnm") +
                    Key(KeyAction.Backspace, width = 1.5f),
                symbols[2],
            ),
            bottomRow(Page.Symbols, rowWidth = 10f),
        ),
    )
}

/** The currency key: the language's own currency, and the rest behind a long press. */
private fun currency(language: Language): Pair<Char, String> = when (language) {
    Language.Russian -> '₽' to "$€£¥"
    Language.English -> '$' to "¢€₽£¥"
}

/** Typographic double quotes, the language's own pair first. */
private fun doubleQuotes(language: Language): String = when (language) {
    Language.Russian -> "«»„“"
    Language.English -> "“”«»"
}

private fun symbols(language: Language) = KeyboardLayout(
    language = language,
    page = Page.Symbols,
    rows = listOf(
        letters("1234567890"),
        currency(language).let { (currency, others) ->
            letters(
                "@#${currency}_&-+()/",
                mapOf(
                    '#' to "№",
                    currency to others,
                    '-' to "—–",
                    '+' to "±",
                    '(' to "{[<",
                    ')' to "}]>",
                    '/' to "\\|",
                ),
                hinted = "#$currency-+()/",
            )
        },
        listOf(Key(KeyAction.ShowPage(Page.MoreSymbols), width = 1.5f)) +
            letters(
                "*\"':;!?",
                mapOf('*' to "•", '"' to doubleQuotes(language), '\'' to "‘’"),
                hinted = "*\"'",
            ) +
            Key(KeyAction.Backspace, width = 1.5f),
        bottomRow(Page.Letters, rowWidth = 10f),
    ),
)

private fun moreSymbols(language: Language) = KeyboardLayout(
    language = language,
    page = Page.MoreSymbols,
    rows = listOf(
        letters("~`|•√π÷×§∆", mapOf('~' to "≈", '§' to "¶"), hinted = "~§"),
        letters("©®™✓^°=<>\\"),
        listOf(Key(KeyAction.ShowPage(Page.Symbols), width = 1.5f)) +
            letters("%«»{}[]", mapOf('%' to "‰"), hinted = "%") +
            Key(KeyAction.Backspace, width = 1.5f),
        bottomRow(Page.Letters, rowWidth = 10f),
    ),
)
