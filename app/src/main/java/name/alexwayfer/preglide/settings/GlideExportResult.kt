package name.alexwayfer.preglide.settings

import java.io.File
import java.io.IOException
import java.io.OutputStream

internal enum class GlideExportResult { Saved, Empty, Failed }

internal fun exportGlides(file: File, openDestination: () -> OutputStream?): GlideExportResult {
    return try {
        if (file.length() == 0L) {
            GlideExportResult.Empty
        } else {
            file.inputStream().use { source ->
                val destination = openDestination() ?: return GlideExportResult.Failed
                destination.use { source.copyTo(it) }
            }
            GlideExportResult.Saved
        }
    } catch (_: IOException) {
        GlideExportResult.Failed
    } catch (_: SecurityException) {
        GlideExportResult.Failed
    }
}
