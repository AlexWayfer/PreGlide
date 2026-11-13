package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.suggest.currentWord
import name.alexwayfer.preglide.engine.suggest.prefersCapitalWord
import name.alexwayfer.preglide.engine.suggest.startsSentence

/** A manual initial capital favors names; sentence capitals, word capitals and caps lock do not. */
internal fun prefersWordCapital(text: CharSequence, shift: ShiftState, inputType: Int): Boolean {
    val automatic = InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    if (shift == ShiftState.Locked || inputType and automatic != 0) return false
    return if (currentWord(text).isEmpty()) {
        shift == ShiftState.Once && !startsSentence(text)
    } else {
        prefersCapitalWord(text)
    }
}
