package name.alexwayfer.preglide.engine.layout

/**
 * [row] with symbols on each of its letters, in order: the keys of the symbols page's [symbolsRow], so a letter
 * offers the symbol at its place there, as the top row does its digit, and then the [more] for the letters past the
 * end of that row, as the [others] keys have them. Each symbol comes with what its own long press offers.
 */
internal fun withSymbols(
    row: List<Key>,
    symbolsRow: List<Key>,
    more: String = "",
    others: List<Key> = emptyList(),
): List<Key> {
    val symbols = symbolsRow.mapNotNull { it.action as? KeyAction.Letter } + more.map { symbolKey(it, others) }
    var next = 0
    return row.map { key ->
        val action = key.action
        if (action is KeyAction.Letter) {
            val symbol = symbols.getOrNull(next++)
            key.copy(action = action.copy(symbols = symbol?.let { listOf(it.char) + it.alternates }.orEmpty()))
        } else {
            key
        }
    }
}

/** The key among [keys] that types [char], or one of [char] alone where none does. */
private fun symbolKey(char: Char, keys: List<Key>): KeyAction.Letter =
    keys.firstNotNullOfOrNull { key -> (key.action as? KeyAction.Letter)?.takeIf { it.char == char } }
        ?: KeyAction.Letter(char)
