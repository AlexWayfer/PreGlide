package name.alexwayfer.preglide.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import name.alexwayfer.preglide.R
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Keep the report as an attachment even when a share target treats text files as message text. */
internal fun writeDiagnosticArchive(output: OutputStream, name: String, report: String) {
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(report.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}

internal fun diagnosticAttachmentIntent(context: Context, uri: Uri, summary: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_EMAIL, arrayOf("preglide@alexwayfer.name"))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostics_subject))
        putExtra(Intent.EXTRA_TEXT, summary)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
