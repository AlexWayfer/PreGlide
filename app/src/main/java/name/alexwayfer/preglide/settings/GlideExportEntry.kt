package name.alexwayfer.preglide.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ime.GLIDE_SLIPS_FILE

@Composable
internal fun GlideExportEntry() {
    val context = LocalContext.current
    val app = context.applicationContext as PreGlideApplication
    val file = app.noBackupFilesDir.resolve(GLIDE_SLIPS_FILE)
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(app.dispatchers.io) {
                exportGlides(file) { app.contentResolver.openOutputStream(uri) }
            }
            showGlideExportResult(context, result)
        }
    }
    ActionEntry(
        R.drawable.ic_gesture,
        stringResource(R.string.glide_export),
        stringResource(R.string.glide_export_hint),
    ) {
        scope.launch {
            if (withContext(app.dispatchers.io) { file.length() > 0 }) save.launch(GLIDE_SLIPS_FILE)
            else showGlideExportResult(context, GlideExportResult.Empty)
        }
    }
}

private fun showGlideExportResult(context: Context, result: GlideExportResult) {
    val message = when (result) {
        GlideExportResult.Saved -> R.string.glide_export_saved
        GlideExportResult.Empty -> R.string.glide_export_empty
        GlideExportResult.Failed -> R.string.glide_export_failed
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
