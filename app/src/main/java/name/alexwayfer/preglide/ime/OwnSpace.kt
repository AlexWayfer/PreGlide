package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.suggest.CLOSING_BRACKETS
import name.alexwayfer.preglide.engine.suggest.STRAIGHT_QUOTES
import name.alexwayfer.preglide.engine.suggest.bracketIsOpen
import name.alexwayfer.preglide.engine.suggest.quoteIsOpen

/**
 * The space the keyboard puts in [field] after a chosen word: a mark typed right after it takes the space's place,
 * and the space moves after the mark, where a backspace erases it as any other; a space typed right after it out
 * of habit is dropped; a hyphen typed right after it stays after the space until a letter follows the hyphen, which
 * glues the hyphen to the word, as "-то" goes on "слово". [log] is told each of these.
 */
class OwnSpace(private val field: SuggestionField, private val log: (OwnSpaceEvent) -> Unit = {}) {
    /** What the keyboard last typed, ending with a space of its own, while nothing else was typed after it. */
    private var tail: String? = null

    /** What the keyboard last typed and a hyphen typed right after its own space, while nothing else followed. */
    private var hyphen: String? = null

    /** The marks that took the place of the keyboard's own space one after another, while nothing else followed. */
    private var marks: MarkRun? = null

    /**
     * The marks a backspace put back after the keyboard's own space, as [spaceBackBeforeMarks] does, while only
     * backspaces erasing them followed.
     */
    private var marksBack: MarkRun? = null

    /** The keyboard typed [text], which ends with a space of its own; null when it typed none. */
    fun placed(text: String?) {
        forget()
        tail = text
        if (text != null) log(OwnSpaceEvent.Placed)
    }

    /**
     * Types [char]. A mark that goes right after a word takes the place of the space after a chosen suggestion;
     * anything else typed after that space makes it the user's. Returns the edit it made.
     */
    fun type(char: Char): SuggestionEdit {
        val current = tail
        val run = marks
        forget()
        // Reads the field only right after the keyboard's own space.
        val before = if (current != null) textBeforeCursor() else ""
        val mark = markAfterOwnSpace(char, before, current)
        val edit = mark ?: SuggestionEdit(0, char.toString())
        field.replace(edit.deleteBefore, edit.text)
        // The space after the mark is the keyboard's own too, so the next mark takes its place in turn.
        if (mark != null) {
            tail = mark.text
            // A mark right after another one goes on with its run, as "?!" of "Чё?! ".
            marks = run?.let { MarkRun(it.tail, it.marks + char) } ?: MarkRun(current.orEmpty(), char.toString())
            log(OwnSpaceEvent.MarkTookPlace)
        } else if (hyphenAfterOwnSpace(char, before, current)) {
            // A dash goes between spaces: only a letter right after the hyphen tells it is part of a word.
            hyphen = "$current-"
        }
        return edit
    }

    /**
     * Before [char], a letter typed or the first of a glided word, goes in: right after a hyphen typed after the
     * keyboard's own space, the space goes, so the hyphen and [char] go on the word before it. Returns the edit
     * made, or null when it made none.
     */
    fun glueHyphen(char: Char): SuggestionEdit? {
        val current = hyphen
        hyphen = null
        // Reads the field only right after such a hyphen.
        val edit = current?.let { hyphenGlue(char, textBeforeCursor(), it) }
        if (edit != null) {
            field.replace(edit.deleteBefore, edit.text)
            log(OwnSpaceEvent.HyphenGlued)
        }
        return edit
    }

    /**
     * Space right after the keyboard's own space, typed out of habit: the field keeps the one space there, and
     * returns true. Either way, the space is the user's from now on, so a next space goes in.
     */
    fun dropsSpace(): Boolean {
        val current = tail
        forget()
        // Reads the field only right after the keyboard's own space.
        val drops = current?.let { spaceDoublesOwnSpace(textBeforeCursor(), it) } == true
        if (drops) log(OwnSpaceEvent.Dropped)
        return drops
    }

    /**
     * A backspace. Right after marks took the place of the keyboard's own space, it brings the space back before
     * them, as "Привет: " goes back to "Привет :" for an emote; a backspace after that erases the last of them,
     * and once all are gone the space is the keyboard's own again, as before the marks. Returns the edit made, or
     * null when it made none and the backspace deletes as usual: the space is the user's from then on.
     */
    fun backspace(): SuggestionEdit? {
        val run = marks
        val back = marksBack
        forget()
        // Reads the field only right after such marks.
        val before = if (run != null || back != null) textBeforeCursor() else ""
        val moved = run?.let { spaceBackBeforeMarks(before, it) }
        val erased = if (moved == null) back?.let { markErasedAfterSpace(before, it) } else null
        val edit = moved ?: erased ?: return null
        field.replace(edit.deleteBefore, edit.text)
        if (moved != null) {
            marksBack = run
            log(OwnSpaceEvent.BroughtBack)
        } else if (back != null) {
            val left = back.marks.dropLast(1)
            if (left.isEmpty()) tail = back.tail else marksBack = MarkRun(back.tail, left)
        }
        return edit
    }

    /** The user typed something else after the keyboard's own space, or selected text: the space is the user's. */
    fun forget() {
        tail = null
        hyphen = null
        marks = null
        marksBack = null
    }

    private fun textBeforeCursor(): CharSequence = field.textBeforeCursor(TEXT_BEFORE_CURSOR) ?: ""
}

/**
 * Marks written right after a word. A dash goes between spaces, a straight quote can open a quotation as well
 * as close one, and closing brackets with none open are an emote like `))`, so none of them is here.
 */
private val marksAfterWord = setOf('.', ',', '!', '?', ';', ':', '…', '»')

/**
 * Typing [char] when the text before the cursor, [textBefore], ends with [tail], whose last char is a space the
 * keyboard put there itself: a mark that goes right after a word takes the space's place, and the space moves
 * after it. So does a straight quote, a backtick or a closing bracket that closes one open before the space.
 * Null to type [char] as it is.
 */
fun markAfterOwnSpace(char: Char, textBefore: CharSequence, tail: String?): SuggestionEdit? =
    if (tail != null && textBefore.endsWith(tail) && goesRightAfterWord(char, textBefore.dropLast(1))) {
        SuggestionEdit(1, "$char ")
    } else {
        null
    }

/**
 * The [marks] typed one after another, each taking the place of the keyboard's own space, after [tail], what the
 * keyboard typed with that space before the first of them: "?!" after "Чё " made "Чё?! ".
 */
data class MarkRun(val tail: String, val marks: String)

/**
 * Bringing the keyboard's own space back before the marks of [run] that took its place, when the text before the
 * cursor, [textBefore], still ends with them and the space after them: "Привет: " becomes "Привет :". Null when the
 * text has changed since.
 */
fun spaceBackBeforeMarks(textBefore: CharSequence, run: MarkRun): SuggestionEdit? =
    if (textBefore.endsWith(run.tail.dropLast(1) + run.marks + " ")) {
        SuggestionEdit(run.marks.length + 1, " ${run.marks}")
    } else {
        null
    }

/**
 * Erasing the last of the marks of [run] that [spaceBackBeforeMarks] put after the space, when the text before the
 * cursor, [textBefore], still ends with them. Null when the text has changed since.
 */
fun markErasedAfterSpace(textBefore: CharSequence, run: MarkRun): SuggestionEdit? =
    if (textBefore.endsWith(run.tail + run.marks)) SuggestionEdit(1, "") else null

/**
 * Whether [char] typed when the text before the cursor, [textBefore], ends with [tail], whose last char is a space
 * the keyboard put there itself, is a hyphen that a letter typed next glues to the word before the space (see
 * [hyphenGlue]): right after a word, not after a mark, as a hyphen after "Привет. " opens a line of dialog.
 */
fun hyphenAfterOwnSpace(char: Char, textBefore: CharSequence, tail: String?): Boolean =
    char == '-' && tail != null && textBefore.endsWith(tail) &&
        textBefore.dropLast(1).lastOrNull()?.isLetterOrDigit() == true

/**
 * The edit that glues a hyphen typed after the keyboard's own space to the word before the space, when [char] goes
 * in right after the hyphen and the text before the cursor, [textBefore], still ends with [hyphen], what the
 * keyboard typed with its space and the hyphen: a letter makes "слово -" "слово-", as for "слово-то". Null for
 * anything else, as a space after a dash or a digit of "-5".
 */
fun hyphenGlue(char: Char, textBefore: CharSequence, hyphen: String?): SuggestionEdit? =
    if (hyphen != null && char.isLetter() && textBefore.endsWith(hyphen)) SuggestionEdit(2, "-") else null

/**
 * Whether a space typed when the text before the cursor, [textBefore], ends with [tail], whose last char is a space
 * the keyboard put there itself, would only double that space.
 */
fun spaceDoublesOwnSpace(textBefore: CharSequence, tail: String?): Boolean =
    tail != null && tail.endsWith(' ') && textBefore.endsWith(tail)

/**
 * Whether [char] typed after [text] goes right after its last word, with no space between: such a char takes the
 * place of the keyboard's own space, and ends the word for autocorrect.
 */
fun goesRightAfterWord(char: Char, text: CharSequence): Boolean =
    char in marksAfterWord ||
        char in STRAIGHT_QUOTES && quoteIsOpen(char, text) ||
        char in CLOSING_BRACKETS && bracketIsOpen(char, text)
