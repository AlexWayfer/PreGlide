package name.alexwayfer.preglide.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import name.alexwayfer.preglide.engine.layout.Language

/**
 * How much text before the cursor to read for the word being typed and the prediction model's context: room for
 * its 32 tokens at some 8 chars each, which is far more than the longest dictionary word.
 */
const val TEXT_BEFORE_CURSOR = 300

internal val passwordVariations = setOf(
    InputType.TYPE_TEXT_VARIATION_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
)

/**
 * Whether a field of [inputType] gets suggestions: a text field. A password gets none, so it never shows on screen
 * or shapes what the keyboard suggests. A field that asks for no suggestions gets them all the same: apps such as
 * YouTube ask so for their comments, where the user types as anywhere else.
 */
fun offersSuggestions(inputType: Int): Boolean =
    inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        inputType and InputType.TYPE_MASK_VARIATION !in passwordVariations

/** Addresses are not words of a language: learning them would teach the dictionary their parts. */
internal val addressVariations = setOf(
    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_URI,
)

/**
 * Whether the keyboard learns from what is typed in a field of [inputType] with [imeOptions]: a field with
 * suggestions, but no address, and none that asks for no learning, as a browser's incognito tab does.
 */
fun learnsFrom(inputType: Int, imeOptions: Int): Boolean =
    offersSuggestions(inputType) &&
        inputType and InputType.TYPE_MASK_VARIATION !in addressVariations &&
        imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0

/**
 * Whether autocorrect may change what is typed in a field of [inputType]: one with suggestions, and no address,
 * that asks for corrections or takes many lines, as a comment or a message does; AOSP's keyboard corrects in both.
 * A single line that does not ask, as a search or a name, is left as typed.
 */
fun correctsIn(inputType: Int): Boolean =
    offersSuggestions(inputType) &&
        inputType and InputType.TYPE_MASK_VARIATION !in addressVariations &&
        inputType and (InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

/**
 * Whether typing [char] after [textBefore], the text before the cursor, ends its last word, so autocorrect may
 * change the word first: a space, or a mark that goes right after a word, as one that takes the keyboard's own
 * space does, a closing quote or bracket among them.
 */
fun endsWordForCorrection(char: Char, textBefore: CharSequence): Boolean =
    char == ' ' || goesRightAfterWord(char, textBefore)

/** The order the dictionaries load ahead of need in: the [current] language first, then the other [enabled] ones. */
fun preloadOrder(current: Language, enabled: List<Language>): List<Language> = (listOf(current) + enabled).distinct()

/** The asset the dictionary of [language] is in. */
fun dictionaryAsset(language: Language): String = "dictionaries/${language.tag}.tsv"

/** The asset the next words of [language] are in. */
fun nextWordsAsset(language: Language): String = "dictionaries/${language.tag}.next.tsv"

/** The folder of the prediction models' assets. */
const val PREDICTION_FOLDER = "prediction"

/** The asset the prediction model of [language] is in, written by model-trainer's export. */
fun predictionAsset(language: Language): String = "$PREDICTION_FOLDER/${language.tag}.tflite"

/** The asset the vocabulary of the prediction model of [language] is in, beside the model. */
fun vocabularyAsset(language: Language): String = "$PREDICTION_FOLDER/${language.tag}.vocabulary.txt"

/** A next word suggested before a letter is typed, in the case [shift] would type its first letter in. */
fun casedForShift(word: String, shift: ShiftState): String = when (shift) {
    ShiftState.Off -> word
    ShiftState.Once -> word.replaceFirstChar { it.uppercaseChar() }
    ShiftState.Locked -> word.uppercase()
}

/**
 * A word suggested for the word being typed, in caps with [shift] locked, as the rest of the word goes in; as it is
 * otherwise, since shift pressed within the word capitalizes the next letter, not the word's first.
 */
fun casedForCapsLock(word: String, shift: ShiftState): String =
    if (shift == ShiftState.Locked) word.uppercase() else word
