package name.alexwayfer.preglide.diagnostics

import androidx.activity.ComponentActivity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.R
import java.io.File
import java.io.IOException

private const val EXTRA_WITH_APP_LOG = "with_app_log"

/** Opens the share sheet with the report; [withAppLog] adds the whole log file. */
internal fun diagnosticReportIntent(context: Context, withAppLog: Boolean): Intent =
    Intent(context, DiagnosticReportActivity::class.java).putExtra(EXTRA_WITH_APP_LOG, withAppLog)

/** Opens the system share sheet with the report attached, then closes without showing anything. */
class DiagnosticReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java)?.cancel(DIAGNOSTIC_NOTIFICATION_ID)
        lifecycleScope.launch {
            val file = withContext((application as PreGlideApplication).dispatchers.io) { writeReport() }
            if (file != null) share(file)
            finish()
        }
    }

    private suspend fun writeReport(): File? {
        val folder = File(cacheDir, "reports")
        val name = diagnosticReportFileName(System.currentTimeMillis())
        val file = File(folder, name.removeSuffix(".txt") + ".zip")
        try {
            folder.mkdirs()
            // Each report has its own name; the ones shared before are no longer needed.
            folder.listFiles()?.forEach { it.delete() }
            val report = Diagnostics.report(this, intent.getBooleanExtra(EXTRA_WITH_APP_LOG, false))
            writeDiagnosticArchive(file.outputStream(), name, report)
        } catch (error: IOException) {
            Log.w(DIAGNOSTICS_TAG, "report write failed ${error.javaClass.simpleName}")
            return null
        }
        return file
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.diagnostics", file)
        val summary = Diagnostics.shareText(this)
        DiagnosticJournal.markSent(this)
        val send = diagnosticAttachmentIntent(this, uri, summary)
        startActivity(Intent.createChooser(send, getString(R.string.diagnostics_send)))
    }
}
