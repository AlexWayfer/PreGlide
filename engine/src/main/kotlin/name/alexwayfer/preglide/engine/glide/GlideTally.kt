package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.layout.KeyPlace
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.fold
import java.util.Locale
import java.util.TreeMap
import java.io.DataInput
import java.io.DataOutput

/** How many glided words of a language the user settles between its lines (see [GlideTally]). */
const val GLIDE_TALLY_EVERY = 25

/** An offset in the log's lines, signed, to a hundredth of a key or a row. */
private const val OFFSET = "%+.2f"

/**
 * Counts how the user settles glided words, for each language apart: left as typed, swapped for another word of the
 * strip at its rank, or erased whole; how much reading the glide as the word meant cost; and, by the key of the
 * word's first and last letters, how far from the key's middle the glide came down and lifted. It tells which of
 * the learning the user would gain from, as where the finger lifts by key, without a word: a letter's key and
 * counts alone. Every [every] glides of a language settled, it gives that language's lines so far.
 */
class GlideTally(private val every: Int = GLIDE_TALLY_EVERY) {
    private val languages = HashMap<Language, LanguageGlides>()

    /** All recorded totals, even before the next periodic log line is due. */
    fun lines(): List<String> = languages.entries.sortedBy { it.key.tag }.flatMap { it.value.lines() }

    /** Numeric aggregates only: words and individual paths never enter this snapshot. */
    fun write(output: DataOutput) {
        output.writeInt(FORMAT)
        output.writeInt(languages.size)
        for ((language, glides) in languages) {
            output.writeUTF(language.tag)
            glides.write(output)
        }
    }

    companion object {
        private const val FORMAT = 1

        fun read(input: DataInput, every: Int = GLIDE_TALLY_EVERY): GlideTally {
            require(input.readInt() == FORMAT) { "Unknown glide statistics format" }
            val tally = GlideTally(every)
            repeat(input.boundedSize(Language.entries.size)) {
                val language = requireNotNull(Language.forTag(input.readUTF())) { "Unknown statistics language" }
                tally.languages[language] = LanguageGlides(language).apply { read(input) }
            }
            return tally
        }
    }

    /** The user erased a glided word of [language] whole: its lines when due, or none. */
    fun erased(language: Language): List<String> = settled(language) { erased() }

    /**
     * The user left the glided word, or swapped another in, [word] at [rank] of the words offered for [glide], 1
     * for the word typed, with a [cost] of reading the glide as it: [language]'s lines when due, or none.
     */
    fun meant(language: Language, glide: Glide, word: String, rank: Int, cost: Float): List<String> =
        settled(language) { meant(glide, word, rank, cost) }

    private fun settled(language: Language, tally: LanguageGlides.() -> Unit): List<String> {
        val glides = languages.getOrPut(language) { LanguageGlides(language) }
        glides.tally()
        return if (glides.settled % every == 0L) glides.lines() else emptyList()
    }
}

/** What [GlideTally] counts of one language's glides. */
private class LanguageGlides(private val language: Language) {
    private var erased = 0L
    private var kept = 0L

    /** How many words were swapped in, by their rank among the words offered. */
    private val swapped = TreeMap<Int, Long>()

    /** The cost of reading each glide as the word meant, summed. */
    private var costs = 0.0

    private val starts = KeyOffsets()
    private val lifts = KeyOffsets()
    private val places = KeyboardLayout.of(language).glidePlaces

    private val meant: Long get() = kept + swapped.values.sum()
    val settled: Long get() = meant + erased

    fun write(output: DataOutput) {
        output.writeLong(kept)
        output.writeLong(erased)
        output.writeDouble(costs)
        output.writeInt(swapped.size)
        for ((rank, count) in swapped) {
            output.writeInt(rank)
            output.writeLong(count)
        }
        starts.write(output)
        lifts.write(output)
    }

    fun read(input: DataInput) {
        kept = input.readLong()
        erased = input.readLong()
        costs = input.readDouble()
        repeat(input.boundedSize(MAX_RANKS)) { swapped[input.readInt()] = input.readLong() }
        starts.read(input)
        lifts.read(input)
    }

    fun erased() {
        erased++
    }

    /** The user settled on [word] at [rank] of the words offered for [glide], read as it at [cost]. */
    fun meant(glide: Glide, word: String, rank: Int, cost: Float) {
        if (rank == 1) kept++ else swapped.merge(rank, 1L, Long::plus)
        costs += cost
        val letters = word.map(::fold).filter(places::containsKey)
        if (letters.isEmpty()) return
        val line = glide.line
        val last = line.size - 1
        starts.add(letters.first(), places.getValue(letters.first()), line.xs[0], line.ys[0] / glide.rowHeight)
        lifts.add(letters.last(), places.getValue(letters.last()), line.xs[last], line.ys[last] / glide.rowHeight)
    }

    fun lines(): List<String> = listOfNotNull(
        outcomeLine(),
        starts.line("Glide starts ${language.tag}"),
        lifts.line("Glide lifts ${language.tag}"),
    )

    private fun outcomeLine(): String {
        val ranks = if (swapped.isEmpty()) {
            ""
        } else {
            swapped.entries.joinToString(prefix = " (by rank ", postfix = ")") { "${it.key}: ${it.value}" }
        }
        val cost = if (meant > 0) {
            String.format(Locale.ROOT, "; the word meant cost %.2f on average", costs / meant)
        } else {
            ""
        }
        return "Glides ${language.tag} so far: kept $kept, swapped ${swapped.values.sum()}$ranks, " +
            "erased $erased, of $settled$cost"
    }
}

/** How far from the middle of each key the glides came, by the char the key types. */
private class KeyOffsets {
    private val keys = TreeMap<Char, Offset>()

    fun write(output: DataOutput) {
        output.writeInt(keys.size)
        for ((char, offset) in keys) {
            output.writeChar(char.code)
            output.writeLong(offset.count)
            output.writeDouble(offset.across)
            output.writeDouble(offset.down)
        }
    }

    fun read(input: DataInput) {
        repeat(input.boundedSize(MAX_KEYS)) {
            keys[input.readChar()] = Offset().apply {
                count = input.readLong()
                across = input.readDouble()
                down = input.readDouble()
            }
        }
    }

    /** A glide came at [x], [y] in key widths and rows for [char], whose key lies at [place]. */
    fun add(char: Char, place: KeyPlace, x: Float, y: Float) {
        val offset = keys.getOrPut(char) { Offset() }
        offset.count++
        offset.across += x - place.center.x
        offset.down += y - place.center.y
    }

    /** The line of each key's count and mean offset across and down, after [title]; null before any. */
    fun line(title: String): String? {
        if (keys.isEmpty()) return null
        val byKey = keys.entries.joinToString { (char, offset) ->
            String.format(Locale.ROOT, "%c %d $OFFSET/$OFFSET", char, offset.count, offset.meanAcross, offset.meanDown)
        }
        return "$title, count and mean offset across/down in keys/rows by key: $byKey"
    }
}

/** The sum of the offsets of [count] glides from a key's middle, in key widths [across] and rows [down]. */
private class Offset {
    var count = 0L
    var across = 0.0
    var down = 0.0

    val meanAcross: Double get() = across / count
    val meanDown: Double get() = down / count
}

/** Bounds on the small snapshot's collections, including when a damaged file is read. */
private const val MAX_KEYS = 256
private const val MAX_RANKS = 32

private fun DataInput.boundedSize(maximum: Int): Int = readInt().also {
    require(it in 0..maximum) { "Invalid glide statistics size" }
}
