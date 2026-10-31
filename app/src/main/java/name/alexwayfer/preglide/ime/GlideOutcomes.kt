package name.alexwayfer.preglide.ime

/** The tag of the log lines of how glides are settled (see [GlideOutcomes]). */
const val GLIDE_TALLY_TAG = "GlideTally"

/**
 * Tells [settle] what the user made of each glided word, once it is past changing: a glide is told as it typed its
 * word and again as the word is swapped or erased (see [GlideSlip]), and the next glide's word settles the one
 * before, or leaving the keyboard settles the last choice.
 */
class GlideOutcomes(private val settle: (GlideSlip) -> Unit) {
    /** The latest of the last glide, not yet settled; null before any. */
    private var pending: GlideSlip? = null

    fun record(slip: GlideSlip) {
        pending?.takeIf { it.number != slip.number }?.let(settle)
        pending = slip
    }

    fun finish() {
        val slip = pending ?: return
        pending = null
        settle(slip)
    }
}
