package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.suggest.ContextSeen
import name.alexwayfer.preglide.engine.suggest.LONGEST_CONTEXT
import java.util.Locale

/** One decimal place, for the mean letters in a log line. */
private const val ONE_DECIMAL = "%.1f"

/** Where the words a [NextWordHits] counts come, as its lines name it. */
enum class HitsPlace(val label: String) {
    MidText("mid-text"),

    /** The first word of an empty field, for the words the user often begins with. */
    FieldStart("field start"),

    /** The word right after a glide whose strip offered the next words, while the glided word was still being typed. */
    AfterGlide("after a glide"),
}

/**
 * Counts how often the strip had the word the user ended before they typed it whole: among the next words it showed
 * before the word's first letter, and at which rank, or among its completions before its last letter, and after how
 * many letters. A word the strip showed only once it was typed whole saved nothing and counts apart, as does a
 * glided one; a word it never showed is missed. The strip's words are held only while the word is typed, and the
 * lines tell counts and lengths alone, never a word. It counts the words at [place].
 */
class NextWordHits(private val place: HitsPlace = HitsPlace.MidText) {
    /** The next words the strip showed before the word's first letter, in lowercase; null when it is not followed. */
    private var before: List<String>? = null

    /** Whether those next words came from the prediction model, not from the pairs. */
    private var byModel = false

    /**
     * Whether the strip showed the next words while the word before them was still there to end, as the glided one is
     * [HitsPlace.AfterGlide]: the first word that ends, unless glided, is that one.
     */
    private var wordBefore = false

    /** The word typed so far, as the strip last showed completions for it. */
    private var typed = ""

    /** Each word the strip completed the word being typed to, in lowercase, with how many letters were typed then. */
    private val completions = HashMap<String, Int>()

    private val ranks = IntArray(STRIP_SLOTS)
    private var completed = 0
    private var lettersTyped = 0
    private var lettersInCompleted = 0
    private var shownWhole = 0
    private var glided = 0
    private var missed = 0

    /**
     * The strip shows [words] for [typed], the word being typed, [byModel] or from the pairs: with nothing typed,
     * the next words, which the word is followed from.
     */
    fun shown(typed: String, words: List<String>, byModel: Boolean) {
        val continues = typed.startsWith(this.typed, ignoreCase = true) || this.typed.startsWith(typed, true)
        when {
            typed.isEmpty() -> {
                before = words.map(::keyOf)
                this.byModel = byModel
                wordBefore = place == HitsPlace.AfterGlide
                this.typed = ""
                completions.clear()
            }
            before == null -> Unit
            // The cursor went into another word: what the strip showed before is no longer about it.
            !continues -> forget()
            else -> {
                this.typed = typed
                for (word in words) completions.putIfAbsent(keyOf(word), typed.length)
            }
        }
    }

    /** The strip shows no next words or completions now: the word being typed is not followed. */
    fun forget() {
        before = null
        wordBefore = false
        typed = ""
        completions.clear()
    }

    /**
     * The user ended [word], typed or chosen, or [glided]: the log line of how it came, or null when it was not
     * followed, is no word, or is the word the next words were shown after.
     */
    fun ended(word: String, glided: Boolean = false): String? {
        val isWordBefore = endsWordBefore(glided)
        val shown = before?.takeIf { !isWordBefore && word.any(Char::isLetter) } ?: return null
        val key = keyOf(word)
        val rank = shown.indexOf(key) + 1
        val letters = completions[key]
        forget()
        val came = when {
            rank in 1..STRIP_SLOTS -> "next word at rank $rank".also { ranks[rank - 1]++ }
            letters != null && letters < word.length -> "completed after $letters of ${word.length} letters".also {
                completed++
                lettersTyped += letters
                lettersInCompleted += word.length
            }
            letters != null -> "shown only typed whole, ${word.length} letters".also { shownWhole++ }
            glided -> "glided".also { this.glided++ }
            else -> "missed, ${word.length} letters".also { missed++ }
        }
        return "Word ended, ${sourceLabel()}: $came; so far ${tally()}"
    }

    /**
     * Whether the word ending now, [glided] or not, is the one the next words were shown after (see [wordBefore]),
     * which goes uncounted: the first to end, unless a glide right after types the next word itself, as the word
     * before it then ends with no line of its own.
     */
    private fun endsWordBefore(glided: Boolean): Boolean {
        val ends = wordBefore && !glided
        wordBefore = false
        return ends
    }

    /** The line of what the words ended so far came by, or null before any. */
    fun summary(): String? {
        if (counted() == 0) return null
        return "Words so far, ${place.label}: ${tally()}"
    }

    private fun sourceLabel(): String {
        val source = if (byModel) "model" else "pairs"
        return when (place) {
            HitsPlace.MidText -> source
            HitsPlace.FieldStart -> place.label
            HitsPlace.AfterGlide -> "${place.label}, $source"
        }
    }

    private fun counted(): Int = ranks.sum() + completed + shownWhole + glided + missed

    private fun tally(): String {
        val all = counted()
        val mean = if (completed > 0) {
            " after ${meanOf(lettersTyped)} of ${meanOf(lettersInCompleted)} letters"
        } else {
            ""
        }
        return "next words by rank ${ranks.joinToString("/")}, completed $completed$mean, " +
            "shown only whole $shownWhole, glided $glided, missed $missed, of $all"
    }

    private fun meanOf(letters: Int): String = ONE_DECIMAL.format(Locale.ROOT, letters.toDouble() / completed)
}

/**
 * Counts how often the words before a word the user ended had come before in what they typed, for each length of
 * context: with any next word, and with this one. It tells how much a longer context would add before it is used.
 */
class ContextTally {
    private val seenCounts = IntArray(LONGEST_CONTEXT)
    private val withWordCounts = IntArray(LONGEST_CONTEXT)
    private val allCounts = IntArray(LONGEST_CONTEXT)

    /** The log line of a word learned after [contexts], each seen or not; null when it had no word before it. */
    fun learned(contexts: List<ContextSeen>): String? {
        if (contexts.isEmpty()) return null
        for ((length, seen, withWord) in contexts) {
            val index = length - 1
            allCounts[index]++
            if (seen) seenCounts[index]++
            if (withWord) withWordCounts[index]++
        }
        val now = contexts.joinToString { "${it.length} ${stateOf(it)}" }
        return "Learned after words: $now; so far seen/with word/of ${sofar()}"
    }

    /** The line of how often the words before the words learned so far had come before, or null before any. */
    fun summary(): String? =
        if (allCounts.all { it == 0 }) null else "Contexts so far, seen/with word/of ${sofar()}"

    private fun sofar(): String = (0 until LONGEST_CONTEXT).joinToString {
        "${it + 1}: ${seenCounts[it]}/${withWordCounts[it]}/${allCounts[it]}"
    }

    private fun stateOf(context: ContextSeen): String = when {
        context.withWord -> "seen with word"
        context.seen -> "seen"
        else -> "new"
    }
}

private fun keyOf(word: String): String = word.lowercase(Locale.ROOT)
