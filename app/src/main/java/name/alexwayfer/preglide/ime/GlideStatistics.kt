package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import name.alexwayfer.preglide.engine.glide.GlideTally

/** One application-owned writer: loads, counts, saves and report snapshots run in order off the UI thread. */
class GlideStatistics(
    private val store: GlideStatisticsStore,
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val log: (String) -> Unit,
) {
    private val actions = Channel<(GlideTally) -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch(io) {
            val tally = store.read()
            for (action in actions) action(tally)
        }
    }

    /** A settled choice is counted once; only numeric aggregates are written to disk. */
    fun record(slip: GlideSlip) {
        actions.trySend { tally ->
            val lines = count(tally, slip)
            lines.forEach(log)
            store.write(tally)
        }
    }

    /** Includes all earlier queued changes and does not clear any counters when sharing is canceled. */
    suspend fun lines(): List<String> {
        val result = CompletableDeferred<List<String>>()
        actions.send { tally -> result.complete(tally.lines()) }
        return result.await()
    }

    private fun count(tally: GlideTally, slip: GlideSlip): List<String> {
        if (slip.chosen.isEmpty()) return tally.erased(slip.language)
        val index = slip.offered.indexOf(slip.chosen)
        return slip.scores.getOrNull(index)?.let { score ->
            tally.meant(slip.language, slip.glide, slip.chosen, rank = index + 1, cost = score.cost)
        }.orEmpty()
    }
}
