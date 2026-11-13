package name.alexwayfer.preglide.engine.suggest

import kotlin.time.Duration.Companion.days

val LEARNING_USES = 1..255
val CANDIDATE_DAYS = 1..3650
private const val DEFAULT_DAYS = 30

data class LearningPolicy(val uses: Int = 2, val days: Int = DEFAULT_DAYS) {
    init {
        require(uses in LEARNING_USES && days in CANDIDATE_DAYS)
    }

    internal fun fresh(time: Long, now: Long): Boolean = now - time <= days.days.inWholeMilliseconds
}

/** One occurrence, including the history to credit if the word reaches the threshold. */
data class WordOccurrence(
    val spelling: String,
    val context: List<String>,
    val time: Long,
    val kept: Boolean,
    val beganField: Boolean,
) {
    fun count(words: PersonalWords) {
        words.learn(spelling, context, time, kept)
        if (beganField) words.starts.learn(spelling, time)
    }
}

internal class WordCandidates {
    val pending = mutableMapOf<String, MutableList<WordOccurrence>>()
    val promoted = mutableMapOf<String, List<WordOccurrence>>()
    private var swept: Long? = null
    private var sweptPolicy: LearningPolicy? = null

    fun sweep(policy: LearningPolicy, now: Long) {
        if (sweptPolicy != policy || swept?.let { now - it >= 1.days.inWholeMilliseconds } != false) {
            pending.values.removeAll { !policy.fresh(it.last().time, now) }
            promoted.values.removeAll { !policy.fresh(it.last().time, now) }
            swept = now
            sweptPolicy = policy
        }
    }

    fun clear(key: String) {
        pending.remove(key)
        promoted.remove(key)
    }

    fun dropOlder(key: String, cutoff: Long) {
        pending[key]?.let { uses ->
            uses.removeAll { it.time <= cutoff }
            if (uses.isEmpty()) pending.remove(key)
        }
        if (promoted[key]?.any { it.time <= cutoff } == true) promoted.remove(key)
    }

    fun replaceWith(other: WordCandidates) {
        pending.clear()
        other.pending.forEach { (key, uses) -> pending[key] = uses.toMutableList() }
        promoted.clear()
        promoted.putAll(other.promoted)
        swept = null
    }
}

fun PersonalWords.isCandidate(word: String): Boolean = keyOf(word) in candidates.pending && learned(word) == null

fun PersonalWords.pendingSpelling(word: String, time: Long, policy: LearningPolicy): String? =
    candidates.pending[keyOf(word)]?.lastOrNull()?.takeIf { policy.fresh(it.time, time) }?.spelling

fun PersonalWords.observe(use: WordOccurrence, policy: LearningPolicy) {
    val key = keyOf(use.spelling)
    candidates.sweep(policy, use.time)
    candidates.promoted.remove(key)
    val previous = candidates.pending.remove(key)?.takeIf { policy.fresh(it.last().time, use.time) }.orEmpty()
    val known = learned(key) != null
    if (!known && previous.size + 1 < policy.uses) {
        candidates.pending[key] = (previous + use).toMutableList()
    } else {
        previous.forEach { it.count(this) }
        if (!known && previous.isNotEmpty()) candidates.promoted[key] = previous + use
        use.count(this)
    }
}

/** Undo the occurrence the user went back to, returning a just-promoted word to its earlier pending history. */
fun PersonalWords.undoOccurrence(word: String, context: List<String>, kept: Boolean, beganField: Boolean) {
    val key = keyOf(word)
    val pending = candidates.pending[key]
    if (pending != null) {
        pending.removeLast()
        if (pending.isEmpty()) candidates.pending.remove(key)
    } else {
        unlearn(word, context, kept)
        if (beganField) starts.unlearn(word)
        candidates.promoted.remove(key)?.dropLast(1)?.let { earlier ->
            for (use in earlier) {
                unlearn(use.spelling, use.context, use.kept)
                if (use.beganField) starts.unlearn(use.spelling)
            }
            if (earlier.isNotEmpty()) candidates.pending[key] = earlier.toMutableList()
        }
    }
}
