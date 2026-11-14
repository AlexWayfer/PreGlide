package name.alexwayfer.preglide.diagnostics

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DiagnosticAttachmentTest {
    @Test
    fun shareOffersTheArchiveWithReadAccessAndOnlyASummaryAsText() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val uri = Uri.parse("content://name.alexwayfer.preglide.diagnostics/reports/report.zip")
        val summary = "PreGlide 1.1.0 (7)\nProblems: 1"
        val intent = diagnosticAttachmentIntent(context, uri, summary)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals(uri, intent.extras?.getParcelable(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(summary, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(listOf("preglide@alexwayfer.name"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList())
    }

    @Test
    fun archiveKeepsTheCompleteUtf8ReportAsOneNamedTextFile() {
        val report = "PreGlide problem report\nAndroid: 11\nОшибка\n"
        val output = ByteArrayOutputStream()
        writeDiagnosticArchive(output, "preglide-report.txt", report)

        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            assertEquals("preglide-report.txt", zip.nextEntry.name)
            assertEquals(report, zip.readBytes().toString(Charsets.UTF_8))
            assertNull(zip.nextEntry)
        }
    }
}
