package name.alexwayfer.preglide.ime

/**
 * Types an emoji the strip offers after the word typed in [field] (see [emojiEdit]): once a glide still read typed its
 * word, with the keyboard's own space after it, as after a chosen word. [learning] takes the emoji for a mark, which
 * ends the word; [afterTyping] then sets the shift for what follows.
 */
class EmojiChoice(
    private val keyboard: KeyboardState,
    private val field: SuggestionField,
    private val suggestions: WordSuggestions,
    private val learning: Learning,
    private val glideTyping: GlideTyping,
    private val afterTyping: () -> Unit,
) {
    fun choose(emoji: String) = glideTyping.afterGlides.whenRead {
        suggestions.log.awaitStrip(StripTrigger.Choice)
        keyboard.showing.typed()
        // An emoji is no word: the next word is learned after none, as after a mark.
        learning.beforeTyping(emoji.first())
        val edit = emojiEdit(field.textBeforeCursor(TEXT_BEFORE_CURSOR) ?: "", emoji, field.textAfterCursor(1) ?: "")
        field.replace(edit.deleteBefore, edit.text)
        suggestions.ownSpace.placed(edit.text.takeIf { it.endsWith(' ') })
        learning.expectEdit(edit)
        afterTyping()
    }
}
