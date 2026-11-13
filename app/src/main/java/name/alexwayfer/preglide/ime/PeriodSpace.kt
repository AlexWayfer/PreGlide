package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.edit.DoubleSpacePeriod
import name.alexwayfer.preglide.engine.layout.KeyAction

/** Applies double-space periods through the same field edits as suggestions and learning. */
class PeriodSpace(private val field: SuggestionField) {
    private val period = DoubleSpacePeriod()

    /** Read at each key, so changing the setting takes effect without reopening the keyboard. */
    var enabled: () -> Boolean = { false }

    /** Only explicit space taps form a pair; a long press and all other keys break it. */
    fun beforeKey(action: KeyAction, longPress: Boolean) {
        if (!active()) {
            forget()
        } else if (action != KeyAction.Space || longPress) {
            period.otherKey(backspace = action == KeyAction.Backspace)
        }
    }

    /** Replaces the first space when a second consecutive tap follows it, regardless of the pause. */
    fun replaceSpace(): SuggestionEdit? {
        if (!active()) return null
        return if (period.space(textBefore())) replace(SuggestionEdit(1, ". ")) else null
    }

    /** Remembers only a handled space key, never a space inserted by a suggestion alone. */
    fun spaced() {
        if (active()) period.spaced(textBefore())
    }

    /** A backspace immediately after the replacement restores the two spaces. */
    fun backspace(): SuggestionEdit? {
        if (!active()) return null
        // The replacement adds one char to the context remembered before it.
        return if (period.undo(textBefore(TEXT_BEFORE_CURSOR + 1))) replace(SuggestionEdit(2, "  ")) else null
    }

    fun forget() = period.forget()

    private fun active(): Boolean = enabled() && periodsIn(field.inputType)

    private fun textBefore(length: Int = TEXT_BEFORE_CURSOR): String =
        field.textBeforeCursor(length)?.toString().orEmpty()

    private fun replace(edit: SuggestionEdit): SuggestionEdit {
        field.replace(edit.deleteBefore, edit.text)
        return edit
    }
}

/** Ordinary text gets sentence punctuation; passwords, addresses, URLs and keypads keep their spaces. */
fun periodsIn(inputType: Int): Boolean =
    offersSuggestions(inputType) && inputType and InputType.TYPE_MASK_VARIATION !in addressVariations
