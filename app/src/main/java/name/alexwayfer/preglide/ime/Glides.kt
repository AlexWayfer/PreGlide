package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.glide.GlidedWord
import name.alexwayfer.preglide.engine.glide.WordsBefore
import name.alexwayfer.preglide.engine.glide.glidedScores
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.RANKED_CONTEXT
import name.alexwayfer.preglide.engine.suggest.wordContext

/**
 * The words glided into [field] in the [keyboard]'s language: read from a glide with [model], the language's model
 * once loaded, and [next], the prediction for the text before the word when it is ready; typed with the cursor right
 * after them; and, right after, erased whole, or chosen from the strip with the keyboard's [ownSpace] after it, as
 * the glided word or another one the glide may have meant, which leaves the glide done with.
 */
class Glides(
    private val keyboard: KeyboardState,
    private val field: SuggestionField,
    private val ownSpace: OwnSpace,
    private val model: () -> LanguageModel?,
    private val next: (LanguageModel, CharSequence) -> Next,
) {
    /** The word a glide typed last, while the strip offers others in its place; null otherwise. */
    private var glided: Glided? = null

    /**
     * The word a glide typed, put past swapping by the char typed right after it, while a backspace erasing that
     * char brings it back (see [backspacing]); null otherwise.
     */
    private var passed: Glided? = null

    /** Whether the strip offers the next words after the word glided last, and shows them yet. */
    val nextWords = GlideNextWords(::last) { keyboard.nextWordsTurns++ }

    /**
     * The text before the cursor right after a glided word was erased with the space typed before it, while
     * nothing else happened since; null otherwise.
     */
    private var erasedTo: String? = null

    /**
     * What reading a glide at the cursor now takes, gathered on this thread, for another to read it with (see
     * [GlideReading]); null where the field gets no glides (see [glidesIn]), or while the language's model loads.
     */
    fun reading(): GlideReading? {
        val model = (if (glidesIn(field.inputType)) model() else null) ?: return null
        val before = textBeforeCursor
        // The text the glided word goes after, with the space put before it.
        val context = if (glideNeedsSpace(before)) "$before " else before
        val previous = wordContext(context, RANKED_CONTEXT)
        // The user's words change on this thread as they type; the reading gets them as they are now.
        val personal = PersonalWords.snapshot(model.personal, previous)
        val snapshot = LanguageModel(model.dictionary, model.nextWords, personal)
        val answer = next(model, context)
        return GlideReading(
            snapshot,
            KeyboardLayout.of(keyboard.language),
            (answer as? Next.Ready)?.tokens,
            previous,
            contextComing = answer == Next.Waiting,
        )
    }

    /** [words] read from a glide, in the case the shift key sets now, or the field wants after a mark. */
    fun cased(words: List<String>): List<String> {
        val shift = shiftForNextWord(keyboard.shift, textBeforeCursor, field.inputType)
        return words.map { casedForShift(it, shift) }
    }

    /**
     * Types the first of [words] at the cursor, as glided, and offers the rest in its place, or, with [nextWords],
     * the words likely after it; it starts at [start] in the field. Returns the edit made.
     */
    fun type(words: List<String>, start: Int, nextWords: Boolean = false): SuggestionEdit {
        val edit = glideEdit(textBeforeCursor, words.first())
        field.replace(edit.deleteBefore, edit.text)
        // No space of the keyboard's own follows the word until one is chosen from the strip.
        ownSpace.forget()
        glided = Glided(edit.text, words.first(), start, words.drop(1), nextWords)
        this.nextWords.typed()
        erasedTo = null
        passed = null
        return edit
    }

    /** The word a glide typed last, while it is still what the cursor follows; null otherwise. */
    fun last(): Glided? = glided?.takeIf { it.isLast(textBeforeCursor) }

    /**
     * What the strip offers right after a glide, while the cursor [isPoint]: the glided word, in the middle as the
     * best, and the other words it may have meant beside it, as they showed while the finger went; null otherwise,
     * when the glide is forgotten, or while the strip offers the next words after it (see [GlideNextWords.due]).
     */
    fun offered(isPoint: Boolean): List<String>? {
        val last = if (isPoint) last() else null
        // The space an erased glide took stays owed: the strip suggests anew right after the erase.
        if (last == null) glided = null
        return last?.takeUnless(nextWords::dueAfter)?.let { listOf(it.word) + it.alternatives }
    }

    /**
     * Puts [word], one the strip offered, in place of the word glided last, with a space after it unless the
     * text after the cursor has one (see [swapNeedsSpace]); the strip then suggests the next word. Returns the edit
     * made, or null when there is no glided word to put it in place of.
     */
    fun swap(word: String): SuggestionEdit? {
        val edit = last()?.swappedFor(word, field.textAfterCursor(1) ?: "") ?: return null
        field.replace(edit.deleteBefore, edit.text)
        ownSpace.placed(edit.text.takeIf { it.endsWith(' ') })
        glided = null
        return edit
    }

    /** Erases the word glided last whole. Returns the edit made, or null when there is none to erase. */
    fun erase(): SuggestionEdit? {
        val last = last() ?: return null
        val edit = last.erased()
        field.replace(edit.deleteBefore, edit.text)
        ownSpace.forget()
        glided = null
        erasedTo = if (last.typedSpace) textBeforeCursor.toString() else null
        return edit
    }

    /**
     * Whether a letter typed now starts a word of its own, with a space before it: right after a glided word was
     * erased with the space typed before it, as the word before it is done and the user types the one they meant.
     */
    fun owesSpace(): Boolean = erasedTo?.let { it == textBeforeCursor.toString() } == true

    /** Something else was typed, or the cursor moved: the strip no longer offers words for the glide. */
    fun forget() {
        glided = null
        erasedTo = null
        passed = null
    }

    /**
     * A char goes right after the word glided last, as a space or a mark: the strip no longer offers words for the
     * glide, unless a backspace erases that char right away (see [backspacing]).
     */
    fun passOver() {
        val last = last()
        forget()
        passed = last
    }

    /**
     * Before a backspace: one that erases the char typed right after the word glided last brings the glide back, as
     * right after it, so the strip offers its words again, even where it offered the next words then: the user went
     * back to fix the word. The next backspace erases a char of it, not the word whole: the user may fix its end.
     * Any other backspace leaves it done.
     */
    fun backspacing() {
        val word = passed ?: return
        passed = null
        if (word.isLast(textBeforeCursor.dropLast(1))) glided = word.copy(nextWords = false, broughtBack = true)
    }

    // this.field: in a getter, a bare field is the property's own backing field.
    private val textBeforeCursor: CharSequence get() = this.field.textBeforeCursor(TEXT_BEFORE_CURSOR) ?: ""
}

/**
 * Whether the strip offers the words likely after the word glided [last], rather than the glide's others (see
 * [Glided.nextWords]), and whether it shows them yet: until the prediction for them comes, it shows the words it
 * showed as the finger went, and where no next word comes, the glide's others. As the strip turns to the next words,
 * once for each glide, [turned] hears of it.
 */
class GlideNextWords(private val last: () -> Glided?, private val turned: () -> Unit = {}) {
    private var shown = false

    /** Whether no next word came after the word glided last, so the strip offers the glide's others instead. */
    private var none = false

    /** A glide typed its word: the strip shows what it showed as the finger went. */
    fun typed() {
        shown = false
        none = false
    }

    /** Whether the strip offers the next words after the word glided last. */
    fun due(): Boolean = dueAfter(last())

    /** Whether the strip offers the next words after [glided], the word glided last, if any. */
    fun dueAfter(glided: Glided?): Boolean = !none && glided?.nextWords == true

    /**
     * The strip suggests [words]: the next words, when they are [due]. Returns whether it shows them: with none, it
     * offers the glide's others instead, from now on.
     */
    fun shown(words: List<String>): Boolean {
        val due = due()
        if (due && words.isEmpty()) {
            none = true
        } else {
            if (due && !shown) turned()
            shown = true
        }
        return !none
    }

    /** Whether the strip shows the next words, so a word from it goes after the glided one rather than in its place. */
    fun showing(): Boolean = shown && due()
}

/**
 * What reading a glide takes, apart from the keyboard's state, so any thread can read it: the [model], with a
 * snapshot of the user's words, the [layout], the prediction [next] for the text before the word, and the words
 * [previous] to it, the nearest last. [contextComing] tells that the prediction was still on its way: a reading
 * gathered again once it came reads with it.
 */
class GlideReading(
    private val model: LanguageModel,
    private val layout: KeyboardLayout,
    private val next: NextTokens?,
    private val previous: List<String>,
    val contextComing: Boolean = false,
) {
    /** The words [glide] may mean, the likeliest first, in the dictionary's spelling. */
    fun words(glide: Glide): List<String> = scores(glide).map { it.word }

    /** [words] with what each was scored by. */
    fun scores(glide: Glide): List<GlidedWord> =
        glidedScores(model, layout, glide, STRIP_SLOTS + 1, WordsBefore(previous, next))
}
