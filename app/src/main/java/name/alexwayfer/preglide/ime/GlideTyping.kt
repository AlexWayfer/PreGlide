package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.diagnostics.AppLog
import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.glide.GlidedWord
import name.alexwayfer.preglide.engine.glide.glideIsSure
import name.alexwayfer.preglide.engine.layout.KeyAction
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

private const val GLIDE_TAG = "Glide"

/**
 * The log line of a read of a lifted glide [length] key widths long that took [nanos]: how long and how far alone,
 * nothing of the word.
 */
internal fun glideReadLine(nanos: Long, length: Float): String {
    val millis = TimeUnit.NANOSECONDS.toMillis(nanos)
    return "Glide of ${length.roundToInt()} key widths read in $millis ms"
}

/**
 * The words [glide], lifted, may mean, with what each was scored by, read with [reading], with how long it took
 * written to [log] (see [glideReadLine]).
 */
private fun timedScores(reading: GlideReading, glide: Glide, log: (String) -> Unit): List<GlidedWord> {
    val start = System.nanoTime()
    val scores = reading.scores(glide)
    log(glideReadLine(System.nanoTime() - start, glide.length))
    return scores
}

/**
 * Glide typing at the keys: while the finger goes, the strip shows the words the glide may mean so far; once it
 * lifts, the word it likely meant is read, both in [contexts], off the thread the keys and the field run on, and
 * goes in through [suggestions] there, learned by [learning] as a chosen suggestion; then the [hooks] hear of it,
 * whether a word went in or not. What the user does meanwhile waits for it ([afterGlides]), so
 * the keys never stop for a read. Right after, a word from the strip swaps in for it, or goes after it where the
 * strip offers the next words, and a backspace may erase it whole (see [GlideOptions]); a swap or an erase [learning]
 * takes back.
 */
class GlideTyping(
    private val keyboard: KeyboardState,
    private val suggestions: WordSuggestions,
    private val learning: Learning,
    private val contexts: GlideContexts,
    private val options: GlideOptions,
    private val hooks: GlideHooks = GlideHooks(),
) : GlideListener {
    private val glides get() = suggestions.glides

    /** How many glides typed a word since the keyboard started. */
    private var typedGlides = 0

    /** The shift the glide that typed a word last typed it with. */
    private var typedShift = ShiftState.Off

    /** The glide that typed a word last, as a swap or an erase of it is recorded; null before any. */
    private var lastTyped: GlideSlip? = null

    /** What waits for a lifted glide's word, the user's keys included (see [AfterGlides.whenRead]). */
    val afterGlides = AfterGlides()

    /** What the glide under the finger is read with, gathered as it began; null while no finger glides. */
    private var going: GlideReading? = null

    /** The read of the glide so far, for the strip; one at a time, so a slow one is not queued behind. */
    private var previewing: Job? = null

    /** The words the strip shows for the glide under the finger so far, in their order; empty before any. */
    private var previewed = emptyList<String>()

    /**
     * Before the user's key with [action]: anything but a backspace or shift puts the glided word past swapping,
     * while [GlideOptions.bringsBackWords] a space or a mark only till a backspace erases it (see
     * [glideComesBackAfter]). Returns whether the key comes right after a glided word, which autocorrect leaves as it
     * is.
     */
    fun beforeKey(action: KeyAction): Boolean {
        val afterGlide = glides.last() != null
        when {
            action == KeyAction.Backspace -> glides.backspacing()
            action == KeyAction.Shift -> Unit
            options.bringsBackWords() && glideComesBackAfter(action) -> glides.passOver()
            else -> glides.forget()
        }
        return afterGlide
    }

    /**
     * The finger glides on over the letters: the strip shows the words [glide], the way so far, may mean, the best
     * in the middle, unless a read of an earlier way is still going.
     */
    override fun preview(glide: Glide) {
        if (previewing?.isActive == true) return
        val reading = currentReading() ?: return
        previewing = contexts.scope.launch {
            val words = withContext(contexts.preview) { reading.words(glide) }
            // Words read for a glide that ended since are not worth showing.
            if (going === reading) {
                val next = glides.cased(words).take(STRIP_SLOTS)
                keyboard.suggestions = steadyPreview(previewed, next).also { previewed = it }
                // The words of a glide still going: none of them is in the text yet.
                keyboard.glidedWord = null
            }
        }
    }

    /**
     * The finger glided over the letters: the word it likely meant is read, and goes in once it is. While a glide
     * before it is still read, it waits (see [AfterGlides.whenRead]), and what it is read with is gathered once that
     * word went in.
     */
    override fun glided(glide: Glide) {
        suggestions.periodSpace.forget()
        suggestions.choiceSpace.forget()
        keyboard.removing = null
        val reading = if (afterGlides.reading) null else currentReading()
        stopPreviews()
        afterGlides.whenRead { read(glide, reading ?: glides.reading()) }
    }

    /** Reads [glide] with [reading] off the keys' thread, and types its word there once it is read. */
    private fun read(glide: Glide, reading: GlideReading?) {
        if (reading == null) {
            // Nothing to read it with here: it types nothing.
            hooks.afterGlide()
            return
        }
        val job = contexts.scope.launch {
            val scores = withContext(contexts.read) { timedScores(reading, glide, hooks.log) }
            type(scores, glide)
            afterGlides.done()
        }
        // A read that finished at once has typed its word already.
        afterGlides.started(job)
    }

    /**
     * The user tapped [word] in the strip: one the glide may have meant swaps in for its word, and the glided word
     * itself, in the middle, stays; either way with a space after it, as a chosen suggestion. Returns whether. Where
     * the strip shows the next words, none swaps in.
     */
    fun swap(word: String): Boolean {
        val glide = glides.last()
            ?.takeIf { !glides.nextWords.showing() && (word == it.word || word in it.alternatives) }
            ?: return false
        if (word != glide.word) {
            learning.tookBackGlide(glide.start, word)
            lastTyped?.let { hooks.recorded(it.swappedFor(word)) }
        }
        glides.swap(word)?.let(learning::expectEdit)
        return true
    }

    /**
     * Before a word the user tapped in the strip goes in: right after a glide whose strip shows the next words, the
     * glided word is done, a space typed after it as the space key types one, and the word goes after that space.
     */
    fun beforeNextWord() {
        if (!glides.nextWords.showing()) return
        glides.forget()
        learning.beforeTyping(' ')
        learning.expectEdit(suggestions.ownSpace.type(' '))
    }

    /**
     * A backspace: right after a glide, while [GlideOptions.erasesWord], its word goes whole, but not once a
     * backspace brought it back (see [Glided.broughtBack]). Returns the shift the word was typed with, for a glide
     * made again (see [shiftAfterGlideErased]), or null when no word went.
     */
    fun erase(): ShiftState? {
        val glide = (if (options.erasesWord()) glides.last() else null)?.takeUnless { it.broughtBack } ?: return null
        learning.tookBackGlide(glide.start, instead = null)
        lastTyped?.let { hooks.recorded(it.erased()) }
        glides.erase()?.let(learning::expectEdit)
        return typedShift
    }

    /**
     * The field is left: a glide still being read types nothing, as its field may be gone, and what waited for it
     * (see [AfterGlides.whenRead]) is not done either.
     */
    fun drop() {
        afterGlides.drop()
        stopPreviews()
    }

    /**
     * What the glide under the finger is read with: gathered as it began, or gathered again while the prediction for
     * the text before it was still on its way then, so a glide begun right after a word still reads with it. Null
     * where the field gets no glides or the model loads.
     */
    private fun currentReading(): GlideReading? {
        val known = going
        if (known != null && !known.contextComing) return known
        return (glides.reading() ?: known).also { going = it }
    }

    /** No finger glides anymore: what the strip was shown for it is done with. */
    private fun stopPreviews() {
        going = null
        previewing?.cancel()
        previewing = null
        previewed = emptyList()
    }

    /**
     * Types the first of the words in [scores], read from [glide], and offers the others in its place, or the words
     * likely after it, as [GlideOptions.strip] tells for a glide as sure of its word as the scores make it.
     */
    private fun type(scores: List<GlidedWord>, glide: Glide) {
        suggestions.log.awaitStrip(StripTrigger.Glide)
        if (scores.isNotEmpty()) {
            keyboard.showing.typed()
            val cased = glides.cased(scores.map { it.word })
            val typed = cased.first()
            val slip = GlideSlip(++typedGlides, hooks.clock(), keyboard.language, typed, typed, cased, scores, glide)
            lastTyped = slip
            hooks.recorded(slip)
            // The taps before it typed no part of it, and nothing is left for autocorrect to undo.
            suggestions.taps.clear()
            suggestions.corrections.forget()
            // A hyphen typed after the keyboard's own space goes on the word before it, and the glide on the hyphen.
            suggestions.ownSpace.glueHyphen(typed.first())?.let(learning::expectEdit)
            // Learned first: it reads the word the glide ends.
            val start = learning.glided(cased.first())
            // Sure or not of the words the strip would offer in its place.
            val sure = glideIsSure(scores.take(STRIP_SLOTS))
            learning.expectEdit(glides.type(cased, start, options.strip().showsNextWords(sure)))
            typedShift = keyboard.shift
            if (keyboard.shift == ShiftState.Once) keyboard.shift = ShiftState.Off
        } else {
            // The strip showed the glide's words as it went: with none read whole, it suggests as before.
            suggestions.update()
        }
        hooks.afterGlide()
    }
}

/**
 * Where glides run: on [scope], the thread of the keys and the field, and read off it, a [preview] of the words as
 * the finger goes, and the [read] of the whole glide as it lifts, apart, so the read of the whole glide never waits
 * behind a preview.
 */
class GlideContexts(val scope: CoroutineScope, val preview: CoroutineContext, val read: CoroutineContext) {
    constructor(scope: CoroutineScope, both: CoroutineContext) : this(scope, both, both)
}

/**
 * What follows a glide, as the user chose: right after it, while [erasesWord], a backspace erases the glided word
 * whole; while [bringsBackWords], one that erases the space or mark typed right after the word brings the glide back,
 * so the strip offers its words again and one swaps in for it; and [strip] tells whether the strip offers the glide's
 * words or the next ones right after it.
 */
class GlideOptions(
    val erasesWord: () -> Boolean,
    val bringsBackWords: () -> Boolean,
    val strip: () -> GlideStrip = { GlideStrip.GlideWords },
)

/**
 * What hears of glides besides the field: [log] gets a line of how long each read of a lifted glide took, to tell how
 * fast reading runs on a phone; [recorded] gets each glided word as it goes in, and again as it is swapped or erased
 * whole, at the time [clock] tells, for tuning glides in a debug build; [afterGlide] runs once a glide is done,
 * whether a word went in or not.
 */
class GlideHooks(
    val log: (String) -> Unit = { AppLog.i(GLIDE_TAG, it) },
    val recorded: (GlideSlip) -> Unit = {},
    val afterGlide: () -> Unit = {},
    val clock: () -> Long = System::currentTimeMillis,
)
