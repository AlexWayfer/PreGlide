package name.alexwayfer.preglide.ime

/**
 * The strip turning to the words after a word the user picked from it, in [field]: the first words it shows for the
 * text the pick left slide in, as after a glide (see [KeyboardState.nextWordsTurns]), and [turned] hears of it.
 */
class PickTurn(private val field: SuggestionField, private val turned: () -> Unit) {
    /** The text before the cursor a pick left, while the strip shows no words for it yet; null otherwise. */
    private var pickedTo: String? = null

    /** A word from the strip went in: the strip turns once words come for the text it left. */
    fun picked() {
        pickedTo = field.textBeforeCursor(TEXT_BEFORE_CURSOR)?.toString()
    }

    /** The strip shows words for [text]: they turn in when they are the first for the text a pick left. */
    fun shown(text: CharSequence) {
        if (pickedTo == text.toString()) turned()
        pickedTo = null
    }

    /** The strip was cleared: no turn is due anymore. */
    fun forget() {
        pickedTo = null
    }
}
