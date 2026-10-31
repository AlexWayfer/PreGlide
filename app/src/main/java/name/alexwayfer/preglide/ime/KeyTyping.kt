package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Page

/** What [KeyTyping] asks of Android beyond the field's text. */
interface KeyOutput {
    /** Whether a field takes the keys now. */
    val connected: Boolean

    /** The field's options, as [android.view.inputmethod.EditorInfo.imeOptions] spells them out. */
    val imeOptions: Int

    /** The field's type, as [android.view.inputmethod.EditorInfo.inputType] spells it out. */
    val inputType: Int

    /**
     * Sends the delete key: a key event, not an edit of the text, so a selection is deleted whole and apps that
     * listen for the key itself (terminals, chats) see it.
     */
    fun delete()

    /** Types a line break, as the Enter key would. */
    fun newLine()

    /** Runs the field's own Enter [action], such as send or search. */
    fun editorAction(action: Int)

    /** Shows the system's list of keyboards to switch to. */
    fun pickKeyboard()

    /** Whether the field wants a capital at the cursor: true or false, or null with no field. */
    fun wantsCapital(): Boolean?

    /**
     * The text before the cursor the delete key would erase the last char of, at least two chars of it where the
     * field has them; null when the key would erase a selection instead, or with no field.
     */
    fun textBeforeDelete(): CharSequence?
}

/**
 * What the keys, the suggestion strip and a swipe from backspace do: types into the field through [suggestions]'
 * edits and [output], with [learning] told of each edit, autocorrect run on the words ended, and [glideTyping]'s
 * word swapped or erased right after a glide. [removal] forgets a word the strip is asked to remove.
 */
class KeyTyping(
    private val keyboard: KeyboardState,
    private val suggestions: WordSuggestions,
    private val learning: Learning,
    private val glideTyping: GlideTyping,
    private val removal: WordRemoval,
    private val output: KeyOutput,
) {
    private val autocorrect = Autocorrect(suggestions, learning)

    /**
     * A key's [action], by a [longPress] or not, and for a tap, where it landed on the key: once a glide still read
     * typed its word (see [AfterGlides.whenRead]).
     */
    fun key(action: KeyAction, longPress: Boolean, tap: KeyTap?) = glideTyping.afterGlides.whenRead {
        if (!output.connected) return@whenRead
        // Typing on is an answer too: the strip stops asking whether to remove a word.
        keyboard.removing = null
        keyboard.showing.typed()
        // Read before the key puts the glide past swapping.
        val owesSpace = suggestions.glides.owesSpace()
        val afterGlide = glideTyping.beforeKey(action)
        autocorrect.beforeKey(action, afterGlide)
        suggestions.choiceSpace.beforeKey(action)
        // The strip after a letter is not logged; a space or a backspace says otherwise below.
        suggestions.log.awaitStrip(null)
        when (action) {
            is KeyAction.Letter -> letter(action, tap, afterGlide || owesSpace)
            KeyAction.Shift -> {
                keyboard.shift = keyboard.shift.next()
                // Shift sets the case of a next word suggested before a letter is typed.
                suggestions.update()
            }
            KeyAction.Backspace -> backspace()
            KeyAction.Space -> if (longPress) output.pickKeyboard() else space()
            KeyAction.Enter -> enter()
            is KeyAction.ShowPage -> keyboard.page = action.page
            // The emoji panel takes its key before the field does (see EmojiActions.key).
            KeyAction.Emoji, KeyAction.Gap -> Unit
        }
    }

    /**
     * A char's key, or a char chosen from a long press popup, which arrives as its own [letter]; [afterGlide] when it
     * comes right after a glided word, or where one was erased with its space (see [Glides.owesSpace]), which a
     * letter goes a space after (see [startsWordAfterGlide]).
     */
    private fun letter(letter: KeyAction.Letter, tap: KeyTap?, afterGlide: Boolean) {
        val char = keyboard.typed(letter.char)
        val afterChoice = suggestions.choiceSpace.take(char)
        if (afterChoice || afterGlide && startsWordAfterGlide(char)) {
            // As if the space key came first: the chosen or glided word is done, and the letter starts the next.
            learning.beforeTyping(' ')
            learning.expectEdit(suggestions.ownSpace.type(' '))
        }
        // An edit of its own, before the letter: the word the letter goes in then starts with the word before it.
        suggestions.ownSpace.glueHyphen(char)?.let(learning::expectEdit)
        // Before the char goes into the trail: the word is read from the taps that typed it.
        autocorrect.beforeTyping(char)
        // A word is read on the letters' keys alone: a char from the symbols is no slip of a letter.
        val letters = KeyboardLayout.of(keyboard.language).takeIf { keyboard.page == Page.Letters }
        suggestions.taps.typed(char, letter.char, letters, tap)
        learning.beforeTyping(char)
        learning.expectEdit(suggestions.ownSpace.type(char))
        keyboard.afterTyping(char)
        if (keyboard.shift == ShiftState.Once) keyboard.shift = ShiftState.Off
        autoCapitalize()
    }

    private fun space() {
        suggestions.log.awaitStrip(StripTrigger.Space)
        if (autocorrect.beforeTyping(' ')) {
            // The correction brought its own space.
            keyboard.afterTyping(' ')
            autoCapitalize()
        } else if (!suggestions.ownSpace.dropsSpace()) {
            // A space right after the keyboard's own one is dropped, as typed out of habit; the next goes in.
            learning.beforeTyping(' ')
            learning.expectEdit(suggestions.ownSpace.type(' '))
            keyboard.afterTyping(' ')
            autoCapitalize()
        } else {
            // The field stays as it was, and so does the strip.
            suggestions.log.awaitStrip(null)
        }
    }

    private fun enter() {
        learning.beforeTyping('\n')
        suggestions.ownSpace.forget()
        val editorAction = enterAction(output.imeOptions)
        if (editorAction != null) {
            output.editorAction(editorAction)
            autoCapitalize()
        } else {
            output.newLine()
            learning.expectEdit(SuggestionEdit(0, "\n"))
            keyboard.afterTyping('\n')
            // The field would tell the capital of the line before: the line break is not in yet.
            val capital = wantsCapitalOnNewLine(output.inputType) ?: output.wantsCapital()
            keyboard.shift = shiftFor(capital, keyboard.shift)
        }
    }

    private fun backspace() {
        suggestions.log.awaitStrip(StripTrigger.Backspace)
        // Right after marks took the keyboard's own space, the backspace brings it back before them; whatever else
        // the backspace erases, the space is the user's from now on.
        val ownSpaceEdit = suggestions.ownSpace.backspace()
        // The shift a glided word the backspace erased was typed with.
        var erasedWith: ShiftState? = null
        // Whether the char erased was the capital a word began with.
        var erasedCapital = false
        val branch = when {
            ownSpaceEdit != null -> {
                learning.expectEdit(ownSpaceEdit)
                BackspaceBranch.OwnSpaceUndone
            }
            // Right after autocorrect changed a word, the backspace puts it back as typed instead.
            autocorrect.revert() -> BackspaceBranch.CorrectionUndone
            // Right after a glide, the backspace may erase its word whole.
            glideTyping.erase().also { erasedWith = it } != null -> BackspaceBranch.GlideErased
            else -> {
                learning.beforeBackspace()
                suggestions.taps.deleted()
                erasedCapital = output.textBeforeDelete()?.let { erasesWordCapital(it, it.length - 1) } == true
                output.delete()
                BackspaceBranch.Deleted
            }
        }
        suggestions.log.backspace(branch)
        autoCapitalize()
        erasedWith?.let { keyboard.shift = shiftAfterGlideErased(it, keyboard.shift) }
        if (erasedCapital) keyboard.shift = shiftAfterCapitalErased(keyboard.shift)
    }

    /** The user tapped [word] in the strip: once a glide still read typed its word (see [AfterGlides.whenRead]). */
    fun chose(word: String) = glideTyping.afterGlides.whenRead {
        suggestions.log.awaitStrip(StripTrigger.Choice)
        keyboard.showing.typed()
        // A word the glide may have meant swaps in for it; any other is chosen, after it where it is a next word.
        if (!glideTyping.swap(word)) {
            glideTyping.beforeNextWord()
            // Learned first: it reads the word the suggestion replaces.
            learning.chose(word, kept = keyboard.keepsTyped(word))
            learning.expectEdit(suggestions.choose(word))
        }
        // Either way the text goes on: the words after it slide in, as after a glide.
        suggestions.choiceSpace.chosen()
        suggestions.pickTurn.picked()
        autoCapitalize()
    }

    /** The user asked the strip never to suggest [word] again. */
    fun removed(word: String) {
        removal.remove(word)
        suggestions.update()
    }

    /**
     * Before a swipe from backspace selects the first word: the strip stops asking and stays as it is until the
     * swipe ends, and the word is not learned.
     */
    fun beforeSelectingWords() {
        keyboard.removing = null
        keyboard.selectingWords = true
        learning.beforeSelectingWords()
    }

    /**
     * A swipe from backspace that selected words ended, erasing them or not: the strip follows the field again, from
     * the selection the field reports the swipe left.
     */
    fun afterSelectingWords() {
        keyboard.selectingWords = false
    }

    /**
     * After a swipe from backspace erased words: the taps and the keyboard's own space were of the words erased. Shift
     * comes on where the first of them began with a [capital] (see [erasesWordCapital]).
     */
    fun afterErasingWords(capital: Boolean) {
        suggestions.taps.clear()
        suggestions.ownSpace.forget()
        autoCapitalize()
        if (capital) keyboard.shift = shiftAfterCapitalErased(keyboard.shift)
    }

    /** Turns shift on for the next letter where the field wants a capital, as at the start of a sentence. */
    fun autoCapitalize() {
        keyboard.shift = shiftFor(output.wantsCapital(), keyboard.shift)
    }
}
