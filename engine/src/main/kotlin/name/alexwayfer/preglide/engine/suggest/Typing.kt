package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.KeyPlace
import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language

/*
 * Costs are in nats, the natural log of how much less likely a reading of the taps is than the plain one, the word
 * as typed: a cost of 3 is a reading about 20 times less likely. A word's suggestion score is divided by e to the
 * power of its cost, so a word the user may have meant beats a word typed as it is only when it is that much likelier.
 */

/**
 * How far a finger strays from the middle of the key it aims at, in key widths: the spread of a bell curve. A tap in
 * a key's middle costs about 3 for meaning its neighbor, a tap on the edge between them nearly nothing.
 */
private const val FINGER_SPREAD = 0.4f

/** A tap read as another key always costs a little, so the key the finger hit wins a tap right on the edge. */
private const val LEAST_SLIP = 0.1f

/**
 * A tap read as a char of another key that lies far from it but is spelled alike (see [SPELLED_ALIKE]), as a spelling
 * mistake rather than a slip; or as a char whose key is not known.
 */
private const val OTHER_CHAR = 5.5f

/** A tap that belongs to no char of the word: a key hit twice, or one next to the key before or after it, brushed. */
private const val EXTRA_TAP = 4.5f

/** What a reading no finger makes costs, as a tap brushed far from the keys around it: more than any word may. */
private const val NO_READING = Float.POSITIVE_INFINITY

/**
 * The letters a spelling mixes up, in groups, as о for а: a tap read as one of its group costs at most [OTHER_CHAR]
 * however far apart their keys lie, and read as another far key, all its distance, as no finger slips that far. The
 * user asked for it (2026-10-28) after PreType found в read for д, two keys and a row away.
 */
private val SPELLED_ALIKE = mapOf(
    Language.Russian to listOf("ао", "еи", "ея", "иы", "еэ", "ий", "зс", "дт", "бп", "вф", "гк", "жш", "шщ", "ьъ"),
    Language.English to listOf("aeiou", "cks"),
)

/** Whether a spelling in [language] mixes up [a] and [b], folded chars. */
private fun areSpelledAlike(language: Language, a: Char, b: Char): Boolean =
    SPELLED_ALIKE[language].orEmpty().any { a in it && b in it }

/** A char of the word that no tap typed. */
private const val MISSED_CHAR = 4.5f

/** Two chars of the word typed in the other order. */
private const val SWAPPED = 4f

/** Up to this many taps, a word is read only as typed with slips to keys nearby: a short word fits too many others. */
private const val FEW_TAPS = 3

/** Up to this many taps, one mistake more is allowed. */
private const val SOME_TAPS = 4

/** The most a word typed with [FEW_TAPS] at most may cost: a slip to a neighbor, but no mistake. */
private const val SLIPS_ONLY = 3.5f

/** The most a word of [SOME_TAPS] may cost: one mistake, or two slips. */
private const val ONE_MISTAKE = 6.5f

/**
 * The most a longer word may cost: a mistake and a slip, or two swaps. Two mistakes would let a walk through the
 * dictionary reach nearly every start of two chars, and look at several times more words for each letter typed.
 */
private const val MISTAKE_AND_SLIP = 8f

/** The chars below this code, Latin and Cyrillic among them, keep their costs in an array rather than a map. */
private const val ARRAY_CHARS = 0x500

/** How tall a row of keys is, in key widths, unless the keyboard tells: 52 dp keys on a phone 400 dp wide. */
const val DEFAULT_ROW_HEIGHT = 1.4f

/**
 * Where the user touched [layout]'s keys for the chars of the word being typed: [points], by the index of the char
 * in the word, null where it is not known, as for a char from a long press popup or one typed before the keyboard
 * opened; and how tall a row of keys is, [rowHeight] key widths, so a tap's stray up or down weighs as far as it is.
 */
class Touches(val layout: KeyboardLayout, val points: List<KeyPoint?>, val rowHeight: Float = DEFAULT_ROW_HEIGHT)

/**
 * How likely the user meant a word when they typed [typed] by [touches], as a cost (see the costs above): what each
 * tap costs read as each char, and the most a word may cost to count as one they meant, [maxCost].
 */
class Typing(typed: String, private val touches: Touches) {
    private val points = ArrayList<KeyPoint?>(typed.length)

    /** The chars typed, as a search sees them: see [foldedKey]. */
    val chars: String = buildString {
        typed.forEachIndexed { index, char ->
            if (!isSkipped(char)) {
                append(fold(char))
                points += touches.points.getOrNull(index)
            }
        }
    }

    val maxCost: Float = when {
        chars.length <= FEW_TAPS -> SLIPS_ONLY
        chars.length <= SOME_TAPS -> ONE_MISTAKE
        else -> MISTAKE_AND_SLIP
    }

    // By char code, so a walk through a dictionary looks a char's costs up without boxing it.
    private val costs = arrayOfNulls<FloatArray>(ARRAY_CHARS)
    private val otherCosts = HashMap<Char, FloatArray>()

    /** What each tap costs as one that belongs to no char of the word, by its index: see [extraCost]. */
    internal val extraCosts = FloatArray(chars.length) { extraCost(it) }

    // One set of rows for every word costed, each spelled in and taken back out.
    private val rows by lazy { TypingRows(this) }

    /** What each tap costs read as [char], by the index of the tap. */
    fun costsOf(char: Char): FloatArray {
        if (char.code >= ARRAY_CHARS) return otherCosts.getOrPut(char) { costsAsRead(char) }
        return costs[char.code] ?: costsAsRead(char).also { costs[char.code] = it }
    }

    private fun costsAsRead(char: Char) = FloatArray(chars.length) { cost(it, char) }

    /**
     * What [word] costs, as one the user meant: the least any start of it costs, the rest of it being what the user
     * did not type yet; or null when it costs more than [maxCost].
     */
    fun costOf(word: String): Float? = spelling(word, whole = false)

    /**
     * What [word] costs read whole from the taps, each of its chars past them a missed one; or null when it costs
     * more than [maxCost]. What ending the word now with [word] in its place costs.
     */
    fun wholeCostOf(word: String): Float? = spelling(word, whole = true)

    /** What [word] costs as a start or [whole], spelled into the rows and taken back out. */
    private fun spelling(word: String, whole: Boolean): Float? {
        var spelled = 0
        var tooMuch = false
        for (char in foldedKey(word)) {
            val lowest = rows.push(char)
            spelled++
            // No longer start can cost less than lowest, nor the word read whole.
            tooMuch = lowest > maxCost && (whole || rows.cost > maxCost)
            if (tooMuch) break
        }
        val cost = if (whole) rows.whole else rows.cost
        repeat(spelled) { rows.pop() }
        return cost.takeIf { !tooMuch && it <= maxCost }
    }

    /**
     * What tap [index] costs read as [char]: nothing as the char it typed; as another key's char, by how much farther
     * the tap lies from that key's middle than from the middle of the key it hit, at most [OTHER_CHAR] for a char
     * spelled alike; as a char whose key is not known, [OTHER_CHAR].
     */
    private fun cost(index: Int, char: Char): Float {
        val typedChar = chars[index]
        val hit = touches.layout.places[typedChar]
        val point = points[index] ?: hit?.center
        val meant = touches.layout.places[char]
        return when {
            char == typedChar -> 0f
            point == null || meant == null -> OTHER_CHAR
            else -> slip(point, hit, meant).coerceAtMost(
                if (areSpelledAlike(touches.layout.language, typedChar, char)) OTHER_CHAR else NO_READING,
            )
        }
    }

    /** What a tap at [point] on the key at [hit], if known, costs read as the key at [meant]: at least [LEAST_SLIP]. */
    private fun slip(point: KeyPoint, hit: KeyPlace?, meant: KeyPlace): Float {
        val fromHit = hit?.let { squaredDistance(point, it) } ?: 0f
        val slip = (squaredDistance(point, meant) - fromHit) / (2 * FINGER_SPREAD * FINGER_SPREAD)
        return slip.coerceAtLeast(LEAST_SLIP)
    }

    /**
     * What tap [index] costs as one that belongs to no char of the word: [EXTRA_TAP] when its key is the one of the
     * tap before or after it, or next to it, or is not known; otherwise [NO_READING], as no finger brushes a key that
     * far from the ones it aims at.
     */
    private fun extraCost(index: Int): Float {
        val char = chars[index]
        val place = touches.layout.places[char] ?: return EXTRA_TAP
        val beside = listOfNotNull(chars.getOrNull(index - 1), chars.getOrNull(index + 1)).any { other ->
            other == char || touches.layout.places[other]?.let(place::isNextTo) == true
        }
        return if (beside) EXTRA_TAP else NO_READING
    }

    /** How far [point] lies from [place]'s middle, squared, in key widths. */
    private fun squaredDistance(point: KeyPoint, place: KeyPlace): Float {
        val across = point.x - place.center.x
        val down = (point.y - place.center.y) * touches.rowHeight
        return across * across + down * down
    }
}

/**
 * The costs of reading the taps of a [Typing] as the start of a word, spelled into it a char at a time, as
 * [EditRows] counts edits: a tap reads as a char at its cost, and a tap with no char, a char with no tap, or two
 * chars swapped, at theirs. [push] adds a char of the word and [pop] takes the last one back, so words that start
 * alike share the work for their start.
 */
internal class TypingRows(private val typing: Typing) {
    private val taps = typing.chars.length

    private val extra = typing.extraCosts

    // rows[depth][tap]: the least cost of reading the first tap taps as the first depth chars spelled; before any char,
    // each tap is an extra one.
    private val rows = arrayListOf(FloatArray(taps + 1).also { first ->
        for (tap in 1..taps) first[tap] = first[tap - 1] + extra[tap - 1]
    })

    // The least of each row, and the least cost of all the taps read as a start of the word, up to each depth.
    private var least = FloatArray(INITIAL_DEPTH)
    private var costs = FloatArray(INITIAL_DEPTH).also { it[0] = rows[0][taps] }
    private val spelled = StringBuilder()

    /** The least cost of all the taps read as a start of the word spelled: what the word costs, as one meant. */
    val cost: Float get() = costs[spelled.length]

    /** The cost of all the taps read as the whole of what is spelled. */
    val whole: Float get() = rows[spelled.length][taps]

    /**
     * Spells [char] next. Returns the least cost the taps can have read as a longer start of the word, at best: once
     * that and [cost] are both too high, so is every word that starts with what is spelled; and once it is no lower
     * than [cost], every such word costs [cost].
     */
    fun push(char: Char): Float {
        val depth = spelled.length
        val row = rows[depth]
        if (rows.size == depth + 1) rows += FloatArray(taps + 1)
        val next = rows[depth + 1]
        val read = typing.costsOf(char)
        next[0] = row[0] + MISSED_CHAR
        var lowest = next[0]
        for (tap in 1..taps) {
            var cost = minOf(row[tap - 1] + read[tap - 1], row[tap] + MISSED_CHAR, next[tap - 1] + extra[tap - 1])
            if (swapped(char, tap)) cost = minOf(cost, rows[depth - 1][tap - 2] + SWAPPED)
            next[tap] = cost
            lowest = minOf(lowest, cost)
        }
        spelled.append(char)
        if (depth + 1 == least.size) {
            least = least.copyOf(least.size * 2)
            costs = costs.copyOf(costs.size * 2)
        }
        least[depth + 1] = lowest
        costs[depth + 1] = minOf(costs[depth], next[taps])
        // The rows to come grow from this one, and a swap reaches back to the one before it.
        return minOf(lowest, least[depth] + SWAPPED)
    }

    /** Takes back the last [push]. */
    fun pop() {
        spelled.setLength(spelled.length - 1)
    }

    /** Whether [char], after the last char spelled, is the two taps that end at [tap] in the other order. */
    private fun swapped(char: Char, tap: Int): Boolean {
        val chars = typing.chars
        return spelled.isNotEmpty() && tap >= 2 && char == chars[tap - 2] && spelled.last() == chars[tap - 1]
    }

    private companion object {
        /** How long a start the rows make room for at first: most words are shorter. */
        const val INITIAL_DEPTH = 16
    }
}
