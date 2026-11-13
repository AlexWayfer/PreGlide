package name.alexwayfer.preglide.ime

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.predict.NextTokens
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.SuggestionInput
import name.alexwayfer.preglide.engine.suggest.TapTrail
import name.alexwayfer.preglide.engine.suggest.Touches
import name.alexwayfer.preglide.engine.suggest.correction
import name.alexwayfer.preglide.engine.suggest.currentWord
import name.alexwayfer.preglide.engine.suggest.fieldStartWords
import name.alexwayfer.preglide.engine.suggest.hyphenSplit
import name.alexwayfer.preglide.engine.suggest.isEmptyField
import name.alexwayfer.preglide.engine.suggest.numberEndings
import name.alexwayfer.preglide.engine.suggest.predictedText
import name.alexwayfer.preglide.engine.suggest.preferredSpelling
import name.alexwayfer.preglide.engine.suggest.secondCapitalSuggestion
import name.alexwayfer.preglide.engine.suggest.wordRest
import name.alexwayfer.preglide.engine.suggest.suggestions

/**
 * Whether the field is empty around the cursor, where it [suggests]: see [isEmptyField]. A field that cannot tell
 * its text is not known to be empty.
 */
internal fun SuggestionField.isEmpty(suggests: Boolean): Boolean {
    val before = textBeforeCursor(TEXT_BEFORE_CURSOR)
    val after = textAfterCursor(1)
    return suggests && before != null && after != null && isEmptyField(before, after)
}

/**
 * The text the strip suggests for, where it [suggests]: [textBefore], the text before the cursor, with a space after
 * it right [afterGlide] whose strip offers the next words; none for a selection or a field that gets no suggestions.
 */
fun textToSuggestFor(textBefore: CharSequence, suggests: Boolean, afterGlide: Boolean): CharSequence = when {
    afterGlide -> "$textBefore "
    suggests -> textBefore
    else -> ""
}

/** The text field the keyboard types into, as far as suggestions need it. */
interface SuggestionField {
    /** The field's input type, as [android.text.InputType] spells it out. */
    val inputType: Int

    /** The field's options, as [android.view.inputmethod.EditorInfo.imeOptions] spells them out. */
    val imeOptions: Int

    /** Up to [length] chars before the cursor, or null when the field cannot tell. */
    fun textBeforeCursor(length: Int): CharSequence?

    /** Up to [length] chars after the cursor, or null when the field cannot tell. */
    fun textAfterCursor(length: Int): CharSequence?

    /** Deletes [deleteBefore] chars before the cursor and [deleteAfter] after it, and types [text], in one edit. */
    fun replace(deleteBefore: Int, text: String, deleteAfter: Int = 0)
}

/**
 * Where suggestions come from: the [models] of the languages, which load in their own time, the email addresses
 * [readEmails] loads, and, with [prediction], the language's prediction model, which the user may turn off.
 */
class WordSources(
    val models: LanguageModels,
    val readEmails: () -> PersonalWords = { PersonalWords() },
    val prediction: ((onReady: () -> Unit) -> ContextPrediction)? = null,
)

/** Where the lines about typing go: [line] gets each, and [summary] their tallies now and then (see [TypingLog]). */
class TypingLines(val line: (String) -> Unit = {}, val summary: (String) -> Unit = {})

/**
 * Suggests words in [field] into [KeyboardState.suggestions], from the model of the keyboard's language: to
 * finish the word being typed, or the next word after a space. The models of the [sources] load in their own time,
 * and the strip suggests anew as each comes; the email addresses load off the main thread, as the [work] loads. Its
 * scope must run on the thread that calls [update]. Nothing loads unless the work is available: a model holds what
 * the user's typing taught, which stays locked away until the user unlocks the device after it starts.
 *
 * With a prediction model, the next word comes from it; the pairs suggest while it loads. While [corrects],
 * autocorrect marks the suggestion it would put in place of a mistyped word, first. The [lines] get what the keyboard
 * logs of typing.
 */
class WordSuggestions(
    private val keyboard: KeyboardState,
    private val field: SuggestionField,
    work: KeyboardWork,
    sources: WordSources,
    private val corrects: () -> Boolean = { false },
    lines: TypingLines = TypingLines(),
) {
    private val scope = work.scope
    private val loadContext = work.load
    private val available = work.available
    private val models = sources.models
    private val readEmails = sources.readEmails
    private var cursorIsPoint = true
    private var emails: PersonalWords? = null
    private var emailsLoading = false

    /** What the keyboard logs of typing: what it did, never the text. */
    val log = TypingLog(field, lines.line, lines.summary)

    /** The space the keyboard puts after a chosen word; the keyboard types marks and backspaces through it. */
    val ownSpace = OwnSpace(field, log::ownSpace)

    /** A space deferred while a chosen word is followed by an existing closing mark. */
    val choiceSpace = ChoiceSpace(field)

    /** The period placed by two explicit space taps, independently of the keyboard's automatic spaces. */
    val periodSpace = PeriodSpace(field)

    /** Where the keys were tapped for the chars typed last, so a slip to a key nearby still gets its word. */
    val taps = TapTrail()

    /** What autocorrect changes, and undoes on a backspace right after. */
    val corrections = Corrections(field, ownSpace) { choose(it, spaceTyped = true) }

    /** The word the cursor was put right before, which a word typed in front of it leaves in place. */
    val wordAhead = WordAhead(field)

    /** The strip turning to the words after a word picked from it. */
    val pickTurn = PickTurn(field) { keyboard.nextWordsTurns++ }

    private val prediction = sources.prediction?.invoke { update() }

    /** The words glided in, which the strip offers others for right after. */
    val glides = Glides(
        keyboard,
        field,
        ownSpace,
        model = { if (available()) models[keyboard.language] else null },
        next = ::nextFor,
    )

    init {
        // The count as it is now tells nothing new: only a model loaded later is one the strip may wait for.
        scope.launch { models.loaded.drop(1).collect { update() } }
    }

    /** The model of [language] if it is loaded, or null. */
    fun model(language: Language): LanguageModel? = models[language]

    /**
     * Loads the models of [languages], in order, and the email addresses ahead of need, as soon as they can load,
     * so the keyboard rarely waits for them, when it opens or switches language.
     */
    fun preload(languages: List<Language>) {
        if (!available()) return
        emails()
        models.load(languages)
    }

    /** The email addresses the user typed, or null while they load; the first call starts loading them. */
    fun emails(): PersonalWords? {
        if (emails == null && !emailsLoading && available()) {
            emailsLoading = true
            scope.launch {
                emails = withContext(loadContext) { readEmails() }
                update()
            }
        }
        return emails
    }

    /**
     * Suggests anew: after an edit, a cursor move, a shift or a language change. [cursorIsPoint] is false for a
     * selection, which is not a word being typed. An email field gets the addresses the user typed before.
     */
    fun update(cursorIsPoint: Boolean = this.cursorIsPoint) {
        this.cursorIsPoint = cursorIsPoint
        keyboard.fieldStart = false
        // A mark or a backspace takes the place of a selection, not of the space.
        if (!cursorIsPoint) {
            ownSpace.forget()
            choiceSpace.forget()
            periodSpace.forget()
        }
        if (showOffered(cursorIsPoint)) return
        val model = if (available()) models.loadedOrLoading(keyboard.language) else null
        // Locked, nothing loads, and there is nothing to wait for.
        keyboard.loading = available() && model == null
        val suggests = cursorIsPoint && offersSuggestions(field.inputType)
        // Right after a glide whose strip offers the next words: in a field that gets suggestions, as any field that
        // gets glides does, with the cursor a point, as the glide is forgotten otherwise.
        val afterGlide = glides.nextWords.due()
        val text = textToSuggestFor(textBeforeCursor, suggests, afterGlide)
        // A prediction on its way leaves the strip as it is for the moment it takes; it suggests anew when done.
        val suggested = suggestedFor(text, model, field.isEmpty(suggests)) ?: return
        if (!glides.nextWords.shown(suggested.words)) {
            // With no next words to offer after the glide, the strip offers its own words instead.
            showOffered(cursorIsPoint)
        } else {
            val typed = currentWord(text)
            val typedWord = model?.let { loaded -> typedToKeep(text)?.let { preferredSpelling(it, loaded) } }
            keyboard.typedWord = typedWord
            keyboard.typedWordRemovable = typedWordRemovable(typedWord, model)
            keyboard.glidedWord = null
            keyboard.correction = suggested.correction
            keyboard.fieldStart = suggested.fieldStart
            keyboard.stripEmoji = emojiForStrip(text, charAfterCursor, keyboard.emoji)
            // With no letter typed yet, the shift key decides the case of the next word.
            keyboard.suggestions = if (typed.isEmpty()) {
                val shift = shiftForNextWord(keyboard.shift, text, field.inputType)
                suggested.words.map { casedForShift(it, shift) }
            } else {
                withoutTyped(suggested.words, typedWord).map { casedForCapsLock(it, keyboard.shift) }
            }
            keyboard.gluedWords = gluedWords(text, keyboard.suggestions, model)
            pickTurn.shown(text)
            val source = if (afterGlide) {
                StripSource.GlideNextWords
            } else {
                stripSource(cursorIsPoint, suggests, model != null, typed, suggested.fieldStart)
            }
            log.stripShown(source, keyboard.suggestions, typed, suggested.byModel)
        }
    }

    /**
     * The word autocorrect puts in place of the word being typed when the user types [ending] now, read afresh
     * from the field, as the strip may lag behind fast typing; null to leave the word as typed: when [ending] does
     * not end it (see [endsWordForCorrection]), or while its prediction is on its way.
     */
    fun correctionNow(ending: Char): String? {
        val typing = cursorIsPoint && offersSuggestions(field.inputType) && available()
        // A letter goes on with the word: the field is not read for one.
        val text = (if (typing && !ending.isLetter()) textBeforeCursor else null)
            ?.takeIf { endsWordForCorrection(ending, it) }
        val model = text?.let { models[keyboard.language] }
        return if (text != null && model != null) suggestedFor(text, model)?.correction else null
    }

    fun clear() {
        keyboard.suggestions = emptyList()
        keyboard.fieldStart = false
        keyboard.typedWord = null
        keyboard.typedWordRemovable = false
        keyboard.gluedWords = null
        keyboard.glidedWord = null
        keyboard.correction = null
        keyboard.stripEmoji = emptyList()
        ownSpace.forget()
        periodSpace.forget()
        taps.clear()
        corrections.clear()
        glides.forget()
        choiceSpace.forget()
        wordAhead.forget()
        pickTurn.forget()
    }

    /**
     * Puts [word] in place of the word the cursor is in, or after the space when none is, with a space after it
     * unless text follows (see [pick]). [spaceTyped] when the user typed that space, as autocorrect's word ends with
     * one, which goes in whatever follows. In an email field the address takes the place of all that is typed, and
     * nothing follows it. Returns the edit made.
     */
    fun choose(word: String, spaceTyped: Boolean = false): SuggestionEdit {
        val email = isEmailField(field.inputType)
        val edit = if (email) {
            SuggestionEdit(field.textBeforeCursor(EMAIL_LENGTH)?.length ?: 0, word)
        } else {
            // Autocorrect changes a word that ends at the cursor: what follows it changes nothing.
            val after = if (spaceTyped) "" else field.textAfterCursor(WORD_AFTER_CURSOR) ?: ""
            pick(textBeforeCursor, word, after, wordAhead.word)
        }
        field.replace(edit.deleteBefore, edit.text, edit.deleteAfter)
        ownSpace.placed(edit.text.takeIf { !email && it.endsWith(' ') })
        return edit
    }

    // this.field: in a getter, a bare field is the property's own backing field.
    private val textBeforeCursor: CharSequence get() = this.field.textBeforeCursor(TEXT_BEFORE_CURSOR) ?: ""

    /** The char after the cursor, or none: enough to tell whether the cursor is inside a word. */
    private val charAfterCursor: CharSequence get() = this.field.textAfterCursor(1) ?: ""

    /**
     * What the strip suggests for [text] with [model], loaded or not, autocorrect's word first; null while the
     * prediction for it is on its way. An [emptyField] gets the words the user often begins one with, if any (see
     * [fieldStartWords]): the prediction model alone guesses a first word too rarely to ask it. After a number and a
     * hyphen come its endings, which neither the prediction nor autocorrect knows, with the user's words typed there
     * (see [numberEndings]). For a word a hyphen splits, the prediction reads the text as [predictedText] has it.
     */
    private fun suggestedFor(text: CharSequence, model: LanguageModel?, emptyField: Boolean = false): Suggested? {
        val endings = numberEndings(text, keyboard.language, STRIP_SLOTS, model?.personal ?: PersonalWords())
        // Asked for only when the strip needs it: a prediction takes its time.
        val next by lazy { nextFor(model, predictedText(text)) }
        val capital = model?.let { secondCapitalSuggestion(currentWord(text), it) }
        return when {
            endings.isNotEmpty() -> Suggested(endings, null)
            model != null && emptyField ->
                Suggested(fieldStartWords(model.personal, STRIP_SLOTS), null, fieldStart = true)
            next == Next.Waiting && capital == null -> null
            model == null -> Suggested(emptyList(), null)
            else -> suggestedWith(text, model, (next as? Next.Ready)?.tokens, capital)
        }
    }

    /** What the strip suggests for [text] with [model] and [tokens], the prediction for it, if any. */
    private fun suggestedWith(
        text: CharSequence,
        model: LanguageModel,
        tokens: NextTokens?,
        capital: String?,
    ): Suggested {
        val typed = currentWord(text)
        val touches = Touches(KeyboardLayout.of(keyboard.language), taps.pointsFor(typed), taps.rowHeight)
        val input = SuggestionInput(text, preferCapital = prefersWordCapital(text, keyboard.shift, field.inputType))
        val words = suggestions(input, model, STRIP_SLOTS, tokens, touches)
        // A space inside a word splits it: the half before the cursor is no word typed to its end.
        val atWordEnd = wordRest(charAfterCursor).isEmpty()
        val corrects = corrects() && correctsIn(field.inputType) && atWordEnd && !corrections.keeps(typed)
        // The prediction for a word a hyphen splits is for the part after the hyphen, not for the whole word.
        val wholeNext = tokens?.takeIf { hyphenSplit(text) == null }
        val correction = if (corrects) correction(text, words, model, wholeNext, touches) else null
        return Suggested(firstOf(words, correction ?: capital).take(STRIP_SLOTS), correction, byModel = tokens != null)
    }

    /**
     * What the prediction model gives for [text], with [model] loaded and the prediction on. Text shorter than what
     * was asked for is all the field holds before the cursor.
     */
    private fun nextFor(model: LanguageModel?, text: CharSequence): Next {
        val wholeText = text.length < TEXT_BEFORE_CURSOR
        return model?.let { prediction?.nextFor(keyboard.language, it.dictionary, text, wholeText) } ?: Next.None
    }

    /**
     * Shows what the strip offers over suggesting, where it offers anything, and returns whether: the email addresses
     * the user typed before in an email field, or right after a glide the other words it may have meant (see
     * [Glides.offered]), unless it offers the next words.
     */
    private fun showOffered(cursorIsPoint: Boolean): Boolean {
        val email = isEmailField(field.inputType)
        val offered = if (email) {
            val typed = if (cursorIsPoint) field.textBeforeCursor(EMAIL_LENGTH) ?: "" else ""
            emails()?.let { emailSuggestions(typed, it, STRIP_SLOTS) }.orEmpty()
        } else {
            glides.offered(cursorIsPoint)
        } ?: return false
        keyboard.suggestions = offered
        // A glide's own word comes first, in the middle, marked as the one in the text.
        keyboard.glidedWord = if (email) null else offered.first()
        keyboard.typedWord = null
        keyboard.typedWordRemovable = false
        keyboard.gluedWords = null
        keyboard.correction = null
        keyboard.stripEmoji = emptyList()
        keyboard.loading = false
        log.stripShown(offeredSource(field.inputType), offered)
        return true
    }
}
