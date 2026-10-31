package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page
import name.alexwayfer.preglide.engine.suggest.currentWord
import name.alexwayfer.preglide.engine.suggest.endsWithMarkBeforeSpace

/**
 * Whether a finger gliding over the letters types a word in a field of [inputType]: a text field, but no password,
 * whose text never shows, nor an email address, which is no word of a language. A field that asks for no
 * suggestions still gets glides: apps set that flag on fields of plain words, as YouTube does on its own.
 */
fun glidesIn(inputType: Int): Boolean =
    inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        inputType and InputType.TYPE_MASK_VARIATION !in passwordVariations &&
        !isEmailField(inputType)

/**
 * What typing the glided [word] after [textBefore], the text before the cursor, types: the word with the cursor
 * right after it, so a mark goes on right after it and the strip offers the other words the glide may have meant,
 * and a space before it right after a letter, a digit or a mark that ends a phrase. After a space, an opening
 * bracket or quote, or a dash it goes right on.
 */
fun glideEdit(textBefore: CharSequence, word: String): SuggestionEdit =
    SuggestionEdit(0, (if (glideNeedsSpace(textBefore)) " " else "") + word)

/** Whether a glided word after [textBefore], the text before the cursor, gets a space before it: see [glideEdit]. */
fun glideNeedsSpace(textBefore: CharSequence): Boolean {
    val last = textBefore.lastOrNull()
    return last != null && last.isLetterOrDigit() || endsWithMarkBeforeSpace(textBefore)
}

/**
 * Whether a word chosen from the strip after [textBefore], the text before the cursor, gets a space before it: with
 * no word typed at its end, where a glided word would, as right after "Привет." the strip offers the next words.
 */
fun choiceNeedsSpace(textBefore: CharSequence): Boolean =
    currentWord(textBefore).isEmpty() && glideNeedsSpace(textBefore)

/**
 * The start of the word a glide after [textBefore], the text before the cursor, goes on: the word typed at its end
 * when a hyphen or an apostrophe ends it, as "ИИ-" before "вставка" or "don'" before "t"; empty otherwise.
 */
fun gluedPrefix(textBefore: CharSequence): String =
    currentWord(textBefore).takeIf { it.endsWith('-') || it.endsWith('\'') }.orEmpty()

/**
 * The word a glide typed last, while nothing else was typed after it: the [text] typed for it, the [word], which
 * starts at [start] in the field, and the [alternatives] the strip offers in its place, or, with [nextWords], the
 * words likely after it instead (see [GlideStrip]). Once [broughtBack] by a backspace through the char typed after
 * it, it still swaps, but no backspace erases it whole anymore: only the one right after the glide does.
 */
data class Glided(
    val text: String,
    val word: String,
    val start: Int,
    val alternatives: List<String>,
    val nextWords: Boolean = false,
    val broughtBack: Boolean = false,
) {
    /**
     * The edit that puts [other], the glided word or one of the [alternatives], in its place with the keyboard's own
     * space after it, as a chosen suggestion goes in, unless [textAfter], the text after the cursor, starts with a
     * space or a mark of its own (see [swapNeedsSpace]).
     */
    fun swappedFor(other: String, textAfter: CharSequence): SuggestionEdit {
        val spaceAfter = if (swapNeedsSpace(textAfter)) " " else ""
        return SuggestionEdit(text.length, text.takeWhile { it == ' ' } + other + spaceAfter)
    }

    /** Whether the glide typed a space before its word. */
    val typedSpace: Boolean get() = text.startsWith(' ')

    /** The edit that erases the glided word whole, with the space typed before it. */
    fun erased(): SuggestionEdit = SuggestionEdit(text.length, "")

    /** Whether [textBefore], the text before the cursor, still ends with what the glide typed. */
    fun isLast(textBefore: CharSequence): Boolean = textBefore.endsWith(text)
}

/**
 * Whether a word chosen for a glided one gets the keyboard's own space after it, [textAfter] being the text after
 * the cursor: when nothing follows, or a word that would run on into it. Where the user moved the cursor back into
 * the text, a space, a line break or a mark after it is there already.
 */
fun swapNeedsSpace(textAfter: CharSequence): Boolean = textAfter.firstOrNull()?.isLetterOrDigit() ?: true

/**
 * Whether [char], typed right after a glided word, starts a word of its own, with a space before it: a letter does,
 * as the cursor after the word is left there to change it from the strip, not to type on. A mark, a digit, a
 * hyphen or an apostrophe goes right after the word.
 */
fun startsWordAfterGlide(char: Char): Boolean = char.isLetter()

/**
 * Whether a glided word that [action] came right after comes back on a backspace that erases what it typed, as a
 * space or a mark ends the word as it is and the word meant may be among the glide's others; a letter starts the
 * next word, and Enter may send the text.
 */
fun glideComesBackAfter(action: KeyAction): Boolean =
    action == KeyAction.Space || action is KeyAction.Letter && !action.char.isLetter()

/**
 * The shift once the user erased a glided word typed with [typedWith], [wanted] being what the field wants at the
 * cursor now: a word typed with shift for one letter gets it back, so the glide made again types it as it did, as
 * after "Cursor" erased to glide it anew; otherwise the field's wish stands.
 */
fun shiftAfterGlideErased(typedWith: ShiftState, wanted: ShiftState): ShiftState =
    if (typedWith == ShiftState.Once) ShiftState.Once else wanted

/**
 * The words [next] of a glide still going, the likeliest first, in the order the strip shows them in after [shown],
 * the ones it showed for the glide so far: a word still among them keeps its place, so its slot stays as it is, and
 * the new ones take the places left, the likeliest first. Every word moving to put the best in the middle made the
 * strip flicker as the finger went.
 */
fun steadyPreview(shown: List<String>, next: List<String>): List<String> {
    val places = arrayOfNulls<String>(next.size)
    for (word in next) shown.indexOf(word).takeIf { it in places.indices }?.let { places[it] = word }
    val newcomers = next.filter { it !in places }.iterator()
    return places.map { it ?: newcomers.next() }
}

/**
 * Whether a glide may start on a key with [action] on [page]: a letter's, on the letters, or the space bar's there,
 * which a finger aimed at a letter of the row above may hit.
 */
fun glidesFrom(action: KeyAction, page: Page): Boolean =
    page == Page.Letters && (action is KeyAction.Letter && action.char.isLetter() || action == KeyAction.Space)
