package name.alexwayfer.preglide.dictionary

import java.nio.file.Path
import java.time.LocalTime

/** The entry point of the training data build, beside [main] of the dictionary build in the same package. */
object TrainingDataMain {
    /** Builds the prediction model's training data of the languages named in [args], or of all for none. */
    @JvmStatic
    fun main(args: Array<String>) {
        val build = TrainingDataBuild.inProject(Path.of(""), httpClient()) {
            println(timestamped(it, LocalTime.now()))
        }
        corporaFor(args).forEach(build::run)
    }
}
