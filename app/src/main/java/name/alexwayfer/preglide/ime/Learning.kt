package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.AFTER_NUMBER
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.currentWord
import name.alexwayfer.preglide.engine.suggest.isLearnable
import name.alexwayfer.preglide.engine.suggest.learn
import name.alexwayfer.preglide.engine.suggest.learnedSpelling
import name.alexwayfer.preglide.engine.suggest.seen

/** How much text before the cursor to read for the word a separator ends. */
private const val WORD_AROUND = 64

/**
 * The words of [context], the nearest last, that a word is learned after in [model]: back to the nearest that is no
 * word to learn, as a letter of a smiley, which ends the context. A number before a hyphen, [AFTER_NUMBER], is one.
 */
fun learnableContext(context: List<String>, model: LanguageModel): List<String> =
    context.takeLastWhile { it == AFTER_NUMBER || isLearnable(it, model) }

/**
 * Whether [char], typed after [textBefore], the text before the cursor, goes in the word there: a letter, a hyphen
 * or an apostrophe does, and a digit after a word with letters, as "3" of "топ-3" or "mp3"; a digit after a space or
 * another digit is a number, no word.
 */
fun goesInWord(char: Char, textBefore: () -> CharSequence): Boolean =
    char.isLetter() || char == '-' || char == '\'' || char.isDigit() && currentWord(textBefore()).isNotEmpty()

/**
 * Teaches the model of the language each word was typed in, the keyboard's [language] as its letters went in, what
 * the user types in [field], where the field allows it, and the [emails] typed in email fields, whole; [personal]
 * saves both. [models] gives a language's model, and [emails] the addresses, while they are loaded; both hold the
 * words [personal] loaded. The keyboard tells about its edits and the field's selection changes.
 */
class Learning(
    private val field: SuggestionField,
    language: () -> Language,
    models: (Language) -> LanguageModel?,
    private val emails: () -> PersonalWords?,
    private val personal: PersonalData,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val teacher = object : Teacher {
        override fun learn(
            word: String,
            context: List<String>,
            language: Language,
            kept: Boolean,
            beganField: Boolean,
        ): LearnedUse? {
            val model = teachable(language)?.takeIf { isLearnable(word, it) } ?: return null
            val spelling = learnedSpelling(word, sentenceStart = context.isEmpty(), model, kept)
            val after = learnableContext(context, model)
            log?.learnedAfter(model.personal.seen(spelling, after))
            model.personal.learn(spelling, after, clock(), kept)
            if (beganField) model.personal.starts.learn(spelling, clock())
            personal.changed(personalName(language))
            return LearnedUse(spelling, after, language, kept, beganField)
        }

        override fun unlearn(use: LearnedUse) {
            val words = models(use.language)?.personal
            words?.unlearn(use.spelling, use.context, use.kept)
            if (use.beganField) words?.starts?.unlearn(use.spelling)
            personal.changed(personalName(use.language))
        }

        override fun learnNext(word: String, context: List<String>, language: Language) {
            val model = teachable(language)?.takeIf { isLearnable(word, it) } ?: return
            val after = learnableContext(context, model).ifEmpty { return }
            model.personal.learnNext(learnedSpelling(word, sentenceStart = false, model), after, clock())
            personal.changed(personalName(language))
        }

        /** The model of [language] to teach: loaded, in a field that allows learning; or null. */
        private fun teachable(language: Language): LanguageModel? =
            models(language)?.takeIf { learnsFrom(field.inputType, field.imeOptions) }
    }
    private val learner = TypingLearner(field, teacher, language)

    /** Where the words the user ends and the contexts they are learned after are counted, if anywhere. */
    var log: TypingLog? = null
    private val expectations = EditExpectations()
    private var selectionStart = 0
    private var selectionEnd = 0

    /**
     * Where the keyboard's next edit goes: after its own edits the field has not reported yet, as when typing runs
     * ahead of the reports, or where the field last reported the cursor.
     */
    private val cursor: Int get() = expectations.cursor ?: selectionStart

    /**
     * What an email field held when last seen: when the field is left, the app may no longer give its text, so
     * it is kept as the user types.
     */
    private var address = ""

    /** A field starts, with the selection from [start] to [end]. */
    fun startField(start: Int, end: Int) {
        selectionStart = start
        selectionEnd = end
        expectations.clear()
        address = ""
    }

    /**
     * The field's selection is now from [start] to [end], after the keyboard's edit or anything else. Returns
     * whether it was the keyboard's own edit.
     */
    fun selectionChanged(start: Int, end: Int): Boolean {
        val ours = expectations.reported(start)
        selectionStart = start
        selectionEnd = end
        // With later edits of the keyboard still to be reported, the field's text holds them already and is ahead of
        // this cursor: the word at the cursor is read at the last one's report.
        if (expectations.cursor == null) learner.updated(start, ours)
        if (isEmailField(field.inputType)) {
            val before = field.textBeforeCursor(EMAIL_LENGTH) ?: ""
            address = "$before${field.textAfterCursor(EMAIL_LENGTH) ?: ""}".trim()
        }
        return ours
    }

    /**
     * Before the keyboard types [char]: one that goes in a word goes into the word at the cursor (see [goesInWord]),
     * any other char ends it.
     */
    fun beforeTyping(char: Char) {
        // The field is read for a digit alone: whether a word with letters is typed before it.
        val inWord = goesInWord(char) { field.textBeforeCursor(WORD_AROUND) ?: "" }
        if (inWord) {
            learner.beforeEdit(cursor)
        } else {
            log?.wordEnded(currentWord(field.textBeforeCursor(WORD_AROUND) ?: ""))
            learner.beforeSeparator(char)
        }
    }

    /** Before the keyboard deletes back from the cursor, changing the word there. */
    fun beforeBackspace() {
        learner.beforeEdit(cursor)
        expectEdit(SuggestionEdit(deleteBefore = 1, text = ""))
    }

    /** Before a swipe from backspace selects words to erase: the word being typed is not learned. */
    fun beforeSelectingWords() {
        learner.beforeSelecting()
    }

    /**
     * The keyboard made [edit] at the cursor, or in place of the selection: the field reports it next. [reverted] when
     * the edit puts back the word as the user typed it, its text, in place of autocorrect's word right before: the
     * user meant it so.
     */
    fun expectEdit(edit: SuggestionEdit, reverted: Boolean = false) {
        if (reverted) learner.reverted(edit.text)
        expectations.expect(edit, selectionStart, selectionEnd)
    }

    /**
     * Before the user's chosen [word] goes in place of the word being typed; [kept] when it is that word as typed,
     * which the user keeps on purpose although the keyboard did not know it.
     */
    fun chose(word: String, kept: Boolean = false) {
        log?.wordEnded(word)
        learner.chose(word, cursor, kept)
    }

    /** Before the glided [word] goes in at the cursor as a word of its own. Returns where it starts. */
    fun glided(word: String): Int {
        log?.wordEnded(word, glided = true)
        return learner.glided(word, cursor)
    }

    /**
     * Before the word glided last, which starts at [start], goes: erased whole, or with [instead] in its place.
     */
    fun tookBackGlide(start: Int, instead: String?) {
        learner.tookBackGlide(start, instead)
    }

    /**
     * The user left the field: the word being typed is learned, and so is the email address an email field held;
     * what was learned is saved now.
     */
    fun finishField() {
        learner.finishField()
        val addresses = emails()
        if (addresses != null && isEmailAddress(address)) {
            addresses.learn(address, previous = null, clock())
            personal.changed(EMAILS)
        }
        address = ""
        personal.save()
    }
}

/**
 * Removes the words the user asks never to be suggested again from the model of the keyboard's [language], or the
 * [emails] in an email [field]; [models] gives a language's model while it is loaded. [personal] saves them once
 * [Learning] is done with the field.
 */
class WordRemoval(
    private val field: SuggestionField,
    private val language: () -> Language,
    private val models: (Language) -> LanguageModel?,
    private val emails: () -> PersonalWords?,
    private val personal: PersonalData,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Removes [word]: an address in an email field. */
    fun remove(word: String) {
        val email = isEmailField(field.inputType)
        val words = if (email) emails() else models(language())?.personal
        words?.remove(word, clock()) ?: return
        personal.changed(if (email) EMAILS else personalName(language()))
    }
}
