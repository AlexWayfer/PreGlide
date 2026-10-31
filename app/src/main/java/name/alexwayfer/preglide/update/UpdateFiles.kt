package name.alexwayfer.preglide.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

private const val COPY_BUFFER = 64 * 1024

/** A cached APK counts only while it still matches the currently offered release. */
internal fun checkedUpdate(file: File, release: UpdateRelease): Boolean {
    if (!file.isFile || file.length() != release.size) return false
    return release.sha256?.let { updateDigest(file) == it } ?: true
}

private fun updateDigest(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(COPY_BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

/** A partial or interrupted download never gets the installable name. */
internal fun saveUpdate(
    input: InputStream,
    target: File,
    release: UpdateRelease,
    progress: (Float) -> Unit,
): Boolean {
    val part = target.resolveSibling(target.name + ".part")
    try {
        copyUpdate(input, part, release.size, progress)
        return checkedUpdate(part, release) && part.renameTo(target)
    } finally {
        part.delete()
    }
}

private fun copyUpdate(input: InputStream, target: File, size: Long, progress: (Float) -> Unit) {
    target.outputStream().use { output ->
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            if (total > size) throw java.io.IOException("Update exceeds the offered size")
            val fraction = if (size > 0) (total.toDouble() / size).toFloat().coerceIn(0f, 1f) else 0f
            progress(fraction)
        }
    }
}
