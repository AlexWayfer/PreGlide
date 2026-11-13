package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.engine.layout.Language
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long a start with no field keeps the page, waiting for the field the focus moves to. Chrome's comes within
 * 150 ms or so.
 */
internal val NO_FIELD_WAIT = 300.milliseconds

/**
 * What the keyboard does as a field starts, as its selection moves, as the language changes, and as the field
 * ends: sets [keyboard] up for the field, tells [learning] and [suggestions], and puts [glideTyping]'s word past
 * swapping. [autoCapitalize] turns shift on where the field wants a capital. [scope] runs on the main thread and
 * waits out a start that is no field, see [isNoField].
 */
class FieldSession(
    private val keyboard: KeyboardState,
    private val suggestions: WordSuggestions,
    private val learning: Learning,
    private val glideTyping: GlideTyping,
    private val autoCapitalize: () -> Unit,
    private val scope: CoroutineScope,
) {
    /** The letters a start with no field brings in once no field followed it; null when none waits. */
    private var lettersLater: Job? = null
    /** Where the selection the field reported last starts, before its end. */
    var selectionStart = 0
        private set

    /** Where the selection the field reported last ends. */
    var selectionEnd = 0
        private set

    /** A field of [inputType] starts, with the selection from [start] to [end], either way round. */
    fun start(inputType: Int, start: Int, end: Int) {
        showPage(inputType)
        keyboard.showing.shown()
        keyboard.shift = ShiftState.Off
        autoCapitalize()
        keyboard.removing = null
        // A field starts with its keys, the clipboard's and the emoji panels and their questions closed.
        keyboard.clipboardOpen = false
        keyboard.clearing = false
        keyboard.removingClip = null
        keyboard.emoji.open = false
        keyboard.emoji.query = null
        // A swipe from backspace cut short by the field's end leaves no strip standing still.
        keyboard.selectingWords = false
        keyboard.fieldGlides = glidesIn(inputType)
        selected(start, end)
        suggestions.log.fieldStarted()
        learning.startField(start, end)
        suggestions.ownSpace.forget()
        suggestions.choiceSpace.forget()
        suggestions.periodSpace.forget()
        suggestions.taps.clear()
        suggestions.corrections.clear()
        suggestions.wordAhead.placed()
        suggestions.update(cursorIsPoint = start == end)
    }

    /** The field's selection is now from [start] to [end], either way round: every edit and cursor move ends here. */
    fun selectionChanged(start: Int, end: Int) {
        val moved = start != selectionStart || end != selectionEnd
        selected(start, end)
        val ours = learning.selectionChanged(start, end)
        // Anything but the keyboard's own edit puts autocorrect's change past undoing, and a glide's past swapping,
        // and places the cursor anew.
        if (!ours) {
            if (moved) {
                suggestions.periodSpace.forget()
                suggestions.choiceSpace.forget()
            }
            suggestions.corrections.forget()
            suggestions.glides.forget()
            suggestions.wordAhead.placed()
        }
        // While a swipe from backspace selects words, the strip stays as it was; the field reports the swipe's end.
        if (!keyboard.selectingWords) suggestions.update(cursorIsPoint = start == end)
    }

    /**
     * Shows the page a field of [inputType] starts on. A start with no field keeps the page a while, as the field
     * the focus moves to likely follows it: from one number field to the next, the number pad does not flash the
     * letters in between.
     */
    private fun showPage(inputType: Int) {
        lettersLater?.cancel()
        lettersLater = null
        val page = fieldPage(inputType)
        if (!isNoField(inputType) || page == keyboard.page) {
            keyboard.page = page
        } else {
            lettersLater = scope.launch {
                delay(NO_FIELD_WAIT)
                keyboard.page = page
            }
        }
    }

    private fun selected(start: Int, end: Int) {
        selectionStart = minOf(start, end)
        selectionEnd = maxOf(start, end)
    }

    /**
     * The user switched the keyboard to another language: [language], or null for one the keyboard has not. The word
     * being typed goes on, its letters typed in either language.
     */
    fun languageChanged(language: Language?) {
        suggestions.periodSpace.forget()
        if (language != null) keyboard.language = language
        // The taps lie on the other language's keys, which are not where this one's are.
        suggestions.taps.clear()
        suggestions.update()
    }

    /** The field ends: what was learned in it is saved, and the strip empties. */
    fun finish() {
        glideTyping.drop()
        learning.finishField()
        suggestions.clear()
    }
}
