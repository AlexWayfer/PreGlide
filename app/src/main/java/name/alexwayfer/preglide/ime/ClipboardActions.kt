package name.alexwayfer.preglide.ime

/**
 * What the strip's clipboard button, its chip and the clipboard's panel do: open and close the panel in place of
 * the keys, paste through [paste] after [beforePaste], and change [history]. [watch] stops offering in the chip what
 * was pasted.
 */
class ClipboardActions(
    private val keyboard: KeyboardState,
    private val watch: ClipboardWatch,
    private val history: ClipHistory,
    private val paste: ClipPaste,
    private val beforePaste: () -> Unit,
) {
    /** Shows the panel in place of the keys, the copies grown old dropped. */
    fun open() {
        keyboard.removing = null
        keyboard.clipboardOpen = true
        history.refresh()
    }

    /** Shows the keys again, the panel's questions left unanswered. */
    fun close() {
        keyboard.clipboardOpen = false
        keyboard.clearing = false
        keyboard.removingClip = null
    }

    /** Pastes [clip], from the strip's chip. */
    fun paste(clip: Clip) {
        beforePaste()
        paste.paste(clip)
        watch.pasted(clip)
    }

    /** Pastes [clip], from the panel, and goes back to the keys to type on. */
    fun paste(clip: SavedClip) {
        beforePaste()
        paste.paste(clip, history::open)
        watch.pastedCopy(clip.copiedAt)
        close()
    }

    /** The user took [clip], a screenshot: the history keeps it, and the strip offers it, as if copied. */
    fun screenshotTaken(clip: Clip.Image) {
        history.saw(clip)
        watch.screenshot(clip)
    }

    private var backCloses = false

    /** The back key went down: true when it closes the panel rather than the keyboard. */
    fun backDown(): Boolean {
        backCloses = keyboard.clipboardOpen
        return backCloses
    }

    /** The back key came up: true when it closed the panel, so the keyboard stays. */
    fun backUp(): Boolean {
        if (!backCloses) return false
        backCloses = false
        close()
        return true
    }

    fun remove(clip: SavedClip) {
        keyboard.removingClip = null
        history.remove(clip)
    }

    fun clear() {
        keyboard.clearing = false
        history.clear()
    }
}
