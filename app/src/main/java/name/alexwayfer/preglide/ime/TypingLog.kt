package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.suggest.ContextSeen

/** The tag of the log lines about typing: what the keyboard did, never what was typed. */
const val TYPING_TAG = "Typing"

/** The radix the field's flags are logged in, as Android's docs spell them out. */
private const val HEX = 16

/** How many words the user ends between the summaries of the tallies (see [TypingLog]). */
const val TYPING_SUMMARY_EVERY = 25

/** What the strip suggests anew after, as its log line names it. */
enum class StripTrigger(val label: String) {
    Glide("a glide"),
    Space("a space"),
    Backspace("a backspace"),
    Choice("a word chosen"),
}

/** Where the strip's words came from. */
enum class StripSource(val label: String) {
    GlideAlternatives("words for the glide"),
    GlideNextWords("next words after a glide"),
    Emails("email addresses"),
    Completions("completions"),
    NextWords("next words"),
    FieldStart("words to begin the field"),
    Selection("a selection"),
    FieldWithoutSuggestions("a field without suggestions"),
    NoModel("no model loaded"),
}

/** What a backspace did. */
enum class BackspaceBranch(val label: String) {
    CorrectionUndone("undid autocorrect"),
    OwnSpaceUndone("undid marks taking the own space"),
    PeriodUndone("undid double-space period"),
    GlideErased("erased the glided word"),
    Deleted("deleted"),
}

/** What became of the space the keyboard puts after a chosen word. */
enum class OwnSpaceEvent(val label: String) {
    Placed("placed"),
    MarkTookPlace("taken by a mark"),
    BroughtBack("brought back before the marks by a backspace"),
    Dropped("kept alone, a space typed after it dropped"),
    HyphenGlued("taken by a hyphen a letter followed"),
}

/**
 * The log line of a field that starts with [inputType] and [imeOptions]: both as the field set them, and what the
 * keyboard does in it, so a report tells why the strip or glides behave as they do there.
 */
fun fieldStartLine(inputType: Int, imeOptions: Int): String {
    val does = listOf(
        "suggestions" to offersSuggestions(inputType),
        "glides" to glidesIn(inputType),
        "learning" to learnsFrom(inputType, imeOptions),
        "autocorrect" to correctsIn(inputType),
    ).joinToString { (what, on) -> "$what ${if (on) "on" else "off"}" }
    return "Field started: inputType=0x${inputType.toString(HEX)} imeOptions=0x${imeOptions.toString(HEX)}, $does"
}

/** The log line of the strip showing [count] words from [source] after [trigger]. */
fun stripLine(trigger: StripTrigger, source: StripSource, count: Int): String =
    "Strip after ${trigger.label}: $count, ${source.label}"

/** The log line of a backspace that did [branch]. */
fun backspaceLine(branch: BackspaceBranch): String = "Backspace ${branch.label}"

/** The log line of the keyboard's own space, of [event]. */
fun ownSpaceLine(event: OwnSpaceEvent): String = "Own space ${event.label}"

/** Where the strip's words come from when it offers them over suggesting, in a field of [inputType]. */
fun offeredSource(inputType: Int): StripSource =
    if (isEmailField(inputType)) StripSource.Emails else StripSource.GlideAlternatives

/**
 * Where the strip's words come from, past glides and email fields: for a cursor that [isPoint], in a field that
 * [suggests], with the model [loaded], when [typed] is the word typed before the cursor, and the field is empty at
 * its [fieldStart].
 */
fun stripSource(isPoint: Boolean, suggests: Boolean, loaded: Boolean, typed: String, fieldStart: Boolean = false) =
    when {
        !isPoint -> StripSource.Selection
        !suggests -> StripSource.FieldWithoutSuggestions
        !loaded -> StripSource.NoModel
        fieldStart -> StripSource.FieldStart
        typed.isEmpty() -> StripSource.NextWords
        else -> StripSource.Completions
    }

/**
 * Writes the lines about typing with [write]: [field] starting, a backspace, the keyboard's own space, and the strip
 * once it shows its words after a glide, a space, a backspace or a word chosen, but not after each letter. Each word
 * the user ends tells whether the strip had it before (see [NextWordHits]), counted apart for the first word of an
 * empty field and for the word right after a glide, and each word learned whether the words before it came before
 * (see [ContextTally]). Every
 * [TYPING_SUMMARY_EVERY] words ended, [summarize] gets those tallies so far alone, a line each: the few lines that
 * tell how the next words fare without the trace of every key.
 */
class TypingLog(
    private val field: SuggestionField,
    private val write: (String) -> Unit,
    private val summarize: (String) -> Unit = {},
) {
    /** What the strip suggests anew after, while it has not shown its words yet; null to log none. */
    private var awaited: StripTrigger? = null

    private val hits = NextWordHits()
    private val startHits = NextWordHits(HitsPlace.FieldStart)
    private val glideHits = NextWordHits(HitsPlace.AfterGlide)
    private val allHits = listOf(hits, startHits, glideHits)
    private val contexts = ContextTally()

    /** How many words the user ended that the tallies counted. */
    private var wordsEnded = 0

    /** The field starts: the line tells what kind of field it is, and what the keyboard does in it. */
    fun fieldStarted() {
        awaited = null
        allHits.forEach(NextWordHits::forget)
        write(fieldStartLine(field.inputType, field.imeOptions))
    }

    /** The strip suggests anew after [trigger]: its words are logged once it shows them. Null logs none. */
    fun awaitStrip(trigger: StripTrigger?) {
        awaited = trigger
    }

    /**
     * The strip shows [words] from [source], for [typed], the word being typed, [byModel] or not; their count is
     * logged when it was awaited.
     */
    fun stripShown(source: StripSource, words: List<String>, typed: String = "", byModel: Boolean = false) {
        val following = when (source) {
            StripSource.FieldStart -> startHits
            StripSource.NextWords -> hits
            StripSource.GlideNextWords -> glideHits
            else -> null
        }
        for (each in allHits) {
            when {
                each === following -> each.shown(typed, words, byModel)
                // Completions go on with whichever follows the word: the others have nothing before it and skip them.
                source == StripSource.Completions -> each.shown(typed, words, byModel)
                else -> each.forget()
            }
        }
        val trigger = awaited ?: return
        awaited = null
        write(stripLine(trigger, source, words.size))
    }

    /** The user ended [word], typed or chosen, or [glided]: logged with what the strip had shown for it. */
    fun wordEnded(word: String, glided: Boolean = false) {
        // At most one follows the word: the others forgot what they showed before it.
        val line = allHits.firstNotNullOfOrNull { it.ended(word, glided) } ?: return
        write(line)
        if (++wordsEnded % TYPING_SUMMARY_EVERY == 0) {
            (allHits.mapNotNull(NextWordHits::summary) + listOfNotNull(contexts.summary())).forEach(summarize)
        }
    }

    /** A word was learned after the words before it, [contexts] of each length, seen before or not. */
    fun learnedAfter(contexts: List<ContextSeen>) {
        this.contexts.learned(contexts)?.let(write)
    }

    fun backspace(branch: BackspaceBranch) {
        write(backspaceLine(branch))
    }

    fun ownSpace(event: OwnSpaceEvent) {
        write(ownSpaceLine(event))
    }
}
