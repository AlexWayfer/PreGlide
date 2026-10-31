package name.alexwayfer.preglide.ime

import android.text.InputType

/** The flags of a field's input type that ask for capitals of any kind. */
private const val CAP_FLAGS =
    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or
        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

/**
 * The shift where the field wants a [capital] at the cursor, with [shift] on the keys: on for the next letter or off,
 * but caps lock stays, and so does any shift with no field to tell (null).
 */
fun shiftFor(capital: Boolean?, shift: ShiftState): ShiftState = when {
    shift == ShiftState.Locked || capital == null -> shift
    capital -> ShiftState.Once
    else -> ShiftState.Off
}

/**
 * Whether a field of [inputType] wants a capital at the start of a line the Enter key began, as Android's
 * `TextUtils.getCapsMode` tells it once the line break is in: a line's start is a sentence's and a word's, so any
 * capitals the field asks for come. The field takes the line break only after the key's events, too late to ask it
 * at once. Null for a field of one line, where the key types no line break.
 */
fun wantsCapitalOnNewLine(inputType: Int): Boolean? =
    if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) null else inputType and CAP_FLAGS != 0

/**
 * Whether erasing [text] from [from] on, [text] being what stood before the cursor, takes away the capital a word
 * began with: the cursor is left where that word started, as "город Москва" erased back to "город ", and the word
 * typed anew there likely wants its capital again. A word starts after no text, or after anything but a letter or a
 * digit, so the capital after a quote or a hyphen counts too, as in "«Москва»" or "Нью-Йорк".
 */
fun erasesWordCapital(text: CharSequence, from: Int): Boolean =
    from in text.indices && text[from].isUpperCase() && (from == 0 || !text[from - 1].isLetterOrDigit())

/** The shift once a word's capital was erased (see [erasesWordCapital]): on for one letter, but caps lock stays. */
fun shiftAfterCapitalErased(shift: ShiftState): ShiftState =
    if (shift == ShiftState.Locked) shift else ShiftState.Once
