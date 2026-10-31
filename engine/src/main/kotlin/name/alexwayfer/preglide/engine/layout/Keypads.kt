package name.alexwayfer.preglide.engine.layout

/** The number pad: the minus over the space bar, and the decimal comma and period around the zero. */
internal fun digits(language: Language) = keypad(
    language,
    Page.Digits,
    corner = Key(KeyAction.Letter('-'), standsOut = false),
    bottom = letters(",0."),
)

/**
 * The phone pad: the plus of an international number over the space bar, with the marks that set a number apart
 * behind its long press, and the star and the hash of the service codes around the zero, as on a phone's keypad.
 */
internal fun phone(language: Language) = keypad(
    language,
    Page.Phone,
    // The marks are few enough to show all in the corner.
    corner = Key(KeyAction.Letter('+', "-()".toList(), optionsHint = "-()"), standsOut = false),
    bottom = letters("*0#"),
)

/**
 * A keypad [page]: the digits as on a calculator, with [corner] above the space bar and backspace on the right and
 * [bottom] under the digits, left of Enter. The right column holds the function keys.
 */
private fun keypad(language: Language, page: Page, corner: Key, bottom: List<Key>) = KeyboardLayout(
    language = language,
    page = page,
    rows = listOf(
        letters("123") + corner,
        letters("456") + Key(KeyAction.Space, standsOut = false),
        letters("789") + Key(KeyAction.Backspace),
        bottom + Key(KeyAction.Enter),
    ),
)
