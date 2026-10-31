package name.alexwayfer.preglide.ime

import kotlinx.coroutines.Job

/**
 * What waits for a lifted glide's word: the read of it under way, if any, and what the user did meanwhile, a key, a
 * choice from the strip or another glide, done in order once its word went in (see [whenRead]). The glide came
 * first, so its word goes first, and the keys never stop for a read: reading the glide over again on the keys'
 * thread, as was done before, stopped them for most of a second.
 */
class AfterGlides {
    /** The read of a lifted glide whose word has not gone in yet; null when none is. */
    private var read: Job? = null

    /** What the user did while a glide was read, in order. */
    private val waiting = ArrayDeque<() -> Unit>()

    /** Whether a lifted glide is being read. */
    val reading: Boolean get() = read != null

    /** Does [action] now, or, while a glide is being read or other actions wait, after them. */
    fun whenRead(action: () -> Unit) {
        if (read == null && waiting.isEmpty()) action() else waiting.addLast(action)
    }

    /** A glide's read started as [job], unless it finished at once. */
    fun started(job: Job) {
        if (job.isActive) read = job
    }

    /** The glide's word went in: what waited for it is done, up to another glide being read. */
    fun done() {
        read = null
        while (read == null) {
            val next = waiting.removeFirstOrNull() ?: return
            next()
        }
    }

    /** The field is left: the glide being read types nothing, and nothing that waited for it is done. */
    fun drop() {
        read?.cancel()
        read = null
        waiting.clear()
    }
}
