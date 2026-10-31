package name.alexwayfer.preglide.dictionary

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

/** How a count in steps goes, with how long each part took, to see what is slow. */
sealed interface CountProgress {
    /** Step [done] of [steps] is counted, in [took]; null [took] for a step counted before a count stopped. */
    data class Step(val done: Int, val steps: Int, val took: Duration?) : CountProgress

    /** The steps' counts are summed into one file, in [took]. */
    data class Merged(val took: Duration) : CountProgress
}

/** A log line about [progress] of counting [what]. */
fun progressLine(what: String, progress: CountProgress): String = when (progress) {
    is CountProgress.Step -> "$what: step ${progress.done} of ${progress.steps} " +
        (progress.took?.let { "in ${duration(it)}" } ?: "counted before")
    is CountProgress.Merged -> "$what: steps summed in ${duration(progress.took)}"
}

/** [took] for a person to read: "44 s", "5 min 3 s", "1 h 2 min". */
fun duration(took: Duration): String = took.toComponents { hours, minutes, seconds, _ ->
    when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min $seconds s"
        else -> "$seconds s"
    }
}

/** [line] with the [time] it is logged at before it, so a log shows when each part ran. */
fun timestamped(line: String, time: LocalTime): String = "${time.format(clock)} $line"
