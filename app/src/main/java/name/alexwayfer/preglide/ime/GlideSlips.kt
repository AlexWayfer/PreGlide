package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.diagnostics.AppLog
import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.glide.GlidedWord
import name.alexwayfer.preglide.engine.glide.glideTrace
import name.alexwayfer.preglide.engine.layout.Language
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor

private const val SLIPS_TAG = "GlideSlips"
internal const val GLIDE_SLIPS_FILE = "glide-slips.txt"

/** The most the file of slips grows to, a few thousand glides of 3 to 7 KB: past it, new ones are not kept. */
private const val MAX_SLIPS_BYTES = 16L * 1024 * 1024

/**
 * A glided word and what the user made of it: what [GlideSlips] keeps of it. A glide is kept as it typed its word,
 * and again with a later line of the same [number] and [at] each time the user swaps it or erases it whole: the
 * last line tells what the user meant.
 */
data class GlideSlip(
    /** Which glide it was since the keyboard started. */
    val number: Int,
    /** When the glide typed its word, in milliseconds since the epoch: which start of the keyboard it was in. */
    val at: Long,
    val language: Language,
    /** The word the glide typed. */
    val typed: String,
    /** The word the user swapped in for it, [typed] while none was, empty once the user erased it whole. */
    val chosen: String,
    /** The words the strip offered for the glide, the typed one first. */
    val offered: List<String>,
    /** What each of [offered], in order, was scored by. */
    val scores: List<GlidedWord>,
    val glide: Glide,
) {
    /** The same glide with [word] chosen for it. */
    fun swappedFor(word: String): GlideSlip = copy(chosen = word)

    /** The same glide erased whole. */
    fun erased(): GlideSlip = swappedFor("")
}

/**
 * The line [slip] is kept as: when, its words, what each offered word was scored by (see [glidedScoreText]), and the
 * glide as [glideTrace] tells it, for a test to read back. The time is the phone's own, to the second.
 */
internal fun glideSlipLine(slip: GlideSlip): String = with(slip) {
    String.format(Locale.ROOT, "glide=%d at=%tFT%<tT ", number, at) +
        "language=${language.tag} typed=$typed chose=$chosen offered=${offered.joinToString(",")} " +
        "scores=${scores.joinToString(";", transform = ::glidedScoreText)} " + glideTrace(glide)
}

/**
 * What [word] was scored by, as numbers alone: its frequency per billion, its likelihood by the context per billion
 * or "-" without the model, the user's uses of it, and the cost of reading the glide as it, in nats; with the model,
 * also what the context was mixed from: the model's likelihood alone per billion, and at each context the user typed
 * next words after, the shortest first, how many times this word and any word came there ("1:5+0:2").
 */
internal fun glidedScoreText(word: GlidedWord): String = with(word) {
    val byContext = context?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "-"
    val parts = contextParts?.let { parts ->
        String.format(Locale.ROOT, "/%.0f/", parts.model) + parts.levels.joinToString("+") { "${it.count}:${it.total}" }
    }.orEmpty()
    String.format(Locale.ROOT, "%d/%s/%d/%.2f", frequency, byContext, uses, cost) + parts
}

/**
 * Keeps the glides and what the user made of them (see [GlideSlip]) in [file], a line each (see [glideSlipLine]),
 * written on [writer]'s thread, while the file is under [maxBytes]: the glides he left as they were tell what reading
 * gets right, the ones he swapped or erased what it gets wrong. For tuning glides on the user's real paths in a
 * local build only: the lines hold the words he typed, so they never go to AppLog or a report.
 */
class GlideSlips(
    private val file: File,
    private val writer: Executor,
    private val maxBytes: Long = MAX_SLIPS_BYTES,
) {
    fun record(slip: GlideSlip) {
        val line = glideSlipLine(slip)
        writer.execute { append(line) }
    }

    private fun append(line: String) {
        try {
            if (file.length() < maxBytes) file.appendText(line + "\n")
        } catch (error: IOException) {
            AppLog.w(SLIPS_TAG, "slip not kept: ${error.javaClass.simpleName}")
        }
    }
}
