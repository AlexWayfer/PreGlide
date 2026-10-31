package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.predict.Vocabulary
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** How many bytes the writer gathers before each write to the file. */
private const val BUFFER_BYTES = 1 shl 20

/**
 * Writes token ids to the file at [path] as 32-bit little-endian integers, which numpy reads as int32 with no
 * parsing, and counts how many it wrote and how many of them are unknown words.
 */
class TokenWriter(path: Path) : AutoCloseable {
    private val channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
    )
    private val buffer = ByteBuffer.allocate(BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN)

    var written = 0L
        private set
    var unknown = 0L
        private set

    fun write(ids: IntArray) {
        for (id in ids) {
            if (buffer.remaining() < Int.SIZE_BYTES) flush()
            buffer.putInt(id)
            if (id == Vocabulary.UNKNOWN) unknown++
        }
        written += ids.size
    }

    private fun flush() {
        buffer.flip()
        while (buffer.hasRemaining()) channel.write(buffer)
        buffer.clear()
    }

    override fun close() {
        flush()
        channel.close()
    }
}
