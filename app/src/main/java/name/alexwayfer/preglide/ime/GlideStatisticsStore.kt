package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.diagnostics.Diagnostics
import name.alexwayfer.preglide.engine.glide.GlideTally
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Independent of the rotating problem journal. Call on the statistics writer's thread. */
class GlideStatisticsStore(
    private val fileOf: () -> File,
    private val failure: (String, Exception) -> Unit = { stage, error ->
        Diagnostics.reportFailure("GlideStats", "Glide statistics $stage failed", error)
    },
) {
    fun read(): GlideTally = try {
        val file = fileOf()
        if (file.exists()) DataInputStream(file.inputStream().buffered()).use { GlideTally.read(it) } else GlideTally()
    } catch (error: IOException) {
        unreadable(error)
    } catch (error: IllegalArgumentException) {
        unreadable(error)
    }

    /** Replace a complete snapshot atomically, retaining the previous one if writing is interrupted. */
    fun write(tally: GlideTally) {
        try {
            val file = fileOf()
            file.parentFile?.mkdirs()
            val next = File(file.path + ".next")
            DataOutputStream(next.outputStream().buffered()).use(tally::write)
            Files.move(next.toPath(), file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
        } catch (error: IOException) {
            failure("write", error)
        }
    }

    private fun unreadable(error: Exception): GlideTally {
        failure("read", error)
        return GlideTally()
    }
}
