package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.currentWord
import name.alexwayfer.preglide.engine.suggest.endsSentenceBeforeSpace
import name.alexwayfer.preglide.engine.suggest.endsWithMarkBeforeSpace
import name.alexwayfer.preglide.engine.suggest.fixesSecondCapital
import name.alexwayfer.preglide.engine.suggest.hyphenSplit
import name.alexwayfer.preglide.engine.suggest.isNumberEnding

/** How many suggestions the strip above the keys shows. */
const val STRIP_SLOTS = 3

/** The slot of the best suggestion: the middle one, where the eyes rest. */
const val BEST_SLOT = 1

/**
 * The strip's slots, left to right: the best suggestion in the middle, the second on its left, the third right.
 * The [typedWord], when the strip shows it, takes the left slot, and the second suggestion goes right; with no
 * suggestion beside it, it takes the middle, so it does not hang alone at the side.
 */
fun stripSlots(suggestions: List<String>, typedWord: String? = null): List<String?> =
    when {
        typedWord == null -> listOf(suggestions.getOrNull(1), suggestions.getOrNull(0), suggestions.getOrNull(2))
        suggestions.isEmpty() -> listOf(null, typedWord, null)
        else -> listOf(typedWord, suggestions[0], suggestions.getOrNull(1))
    }

/**
 * The word being typed at the end of [textBefore], the text before the cursor, for the strip to always show as it
 * is, so the user can keep it even when completions or corrections rank higher. Null with nothing typed or for a
 * number's ending, as "го" of "2-го", which is no word (see [isNumberEnding]).
 */
fun typedToKeep(textBefore: CharSequence): String? = currentWord(textBefore).takeIf {
    it.isNotEmpty() && !isNumberEnding(textBefore)
}

/**
 * What the strip suggests for the word being typed: the [words], and the [correction] autocorrect would put in place
 * of the word as the user ends it, first among them; null when it would leave the word as typed. [byModel] when the
 * prediction model, not the pairs, gave the words; [fieldStart] when the field is empty and they are the words the
 * user often begins one with.
 */
class Suggested(
    val words: List<String>,
    val correction: String?,
    val byModel: Boolean = false,
    val fieldStart: Boolean = false,
)

/**
 * What the strip's row of words shows: the [slots], with the [typedWord], the [correction] and the [glidedWord] that
 * set how its words look (see [slotLook]), and the [turn] of the next words after a glide or a pick it came with (see
 * [KeyboardState.nextWordsTurns]): a row of a new turn slides in.
 */
data class StripWords(
    val slots: List<SlotContent?>,
    val typedWord: String?,
    val correction: String?,
    val glidedWord: String?,
    val turn: Int,
    val typedWordRemovable: Boolean = false,
)

/** What the strip's row of words shows for [state]. */
fun stripWords(state: KeyboardState): StripWords = StripWords(
    stripSlotContents(state.suggestions, state.typedWord, state.stripEmoji, state.gluedWords),
    state.typedWord,
    state.correction,
    state.glidedWord,
    state.nextWordsTurns,
    state.typedWordRemovable,
)

/**
 * How a slot's word looks: the [best] a little bolder, the one autocorrect puts in, [correcting], marked, and the
 * [glided] word among the glide's others marked apart, as the word in the text.
 */
data class SlotLook(val best: Boolean, val correcting: Boolean, val glided: Boolean = false)

/**
 * How [word] looks in [slot] of the strip, with [correction] the word autocorrect puts in, and [glided] the word a
 * glide typed while the strip offers its others, or null.
 */
fun slotLook(slot: Int, word: String?, correction: String?, glided: String? = null): SlotLook =
    SlotLook(
        best = slot == BEST_SLOT,
        correcting = word != null && word == correction,
        glided = word != null && word == glided,
    )

/** [words] with [first] moved to the front, where the strip shows the best suggestion; as they are without it. */
fun firstOf(words: List<String>, first: String?): List<String> =
    if (first == null) words else listOf(first) + words.filterNot { it == first }

/**
 * The shift a next word chosen or glided right after [textBefore], the text before the cursor, takes, with [shift]
 * on the keys, in a field of [inputType]: right after a mark, the word goes a space after it, where the field wants
 * a capital as it would after that space. Android tells a sentence's start only once the space is there, so the keys
 * show no capital right after "Привет.".
 */
fun shiftForNextWord(shift: ShiftState, textBefore: CharSequence, inputType: Int): ShiftState {
    val afterMark = shift == ShiftState.Off && endsWithMarkBeforeSpace(textBefore)
    return if (afterMark && wantsCapitalAfterSpace(textBefore, inputType)) ShiftState.Once else shift
}

/**
 * Whether a field of [inputType] wants a capital once a space follows [textBefore], as Android's
 * `TextUtils.getCapsMode` tells it with the space typed: always for capitals, after any space for words' capitals,
 * and for sentences' capitals at a sentence's start alone, as words' capitals then go unasked.
 */
fun wantsCapitalAfterSpace(textBefore: CharSequence, inputType: Int): Boolean = when {
    inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> true
    inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> endsSentenceBeforeSpace(textBefore)
    else -> inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0
}

/**
 * The suggestions put on the [head] of the word being typed, its start up to its last hyphen: the [words] guessed as
 * the next word after the head, as "потихоньку-помаленьку" for "потихоньку-", not known whole.
 */
data class GluedWords(val head: String, val words: Set<String>)

/**
 * The [suggestions] for the word being typed at the end of [textBefore] that go on from its start up to its last
 * hyphen (see [hyphenSplit]) and that neither the dictionary of [model] nor the user knows whole; null for a word
 * with no such hyphen.
 */
fun gluedWords(textBefore: CharSequence, suggestions: List<String>, model: LanguageModel?): GluedWords? {
    val head = hyphenSplit(textBefore)?.head ?: return null
    val glued = suggestions.filter { word ->
        word.length > head.length && word.startsWith(head, ignoreCase = true) && model?.knowsWhole(word) != true
    }
    return GluedWords(head, glued.toSet())
}

/** Whether the dictionary or the user's own words have [word] as one word. */
internal fun LanguageModel.knowsWhole(word: String): Boolean =
    dictionary.spellingOf(word) != null || personal.learned(word) != null

/** [suggestions] without [typedWord], which the strip shows on its own, in whatever case. */
fun withoutTyped(suggestions: List<String>, typedWord: String?): List<String> =
    if (typedWord == null) suggestions else suggestions.filterNot {
        it.equals(typedWord, ignoreCase = true) && !fixesSecondCapital(typedWord, it)
    }
