package name.alexwayfer.preglide.ime

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shares the copies of images pasted from the strip with the apps they go to (see [ClipPaste]). Its paths are in
 * the manifest's meta-data: `FileProvider.getUriForFile` reads them there, whatever the provider is built with.
 */
class ClipFileProvider : FileProvider()

/** The folder in the cache the copies of pasted images go to, as `clip_paths.xml` shares it. */
const val CLIPS_FOLDER = "clips"

/** [file], a copy in [CLIPS_FOLDER], as [ClipFileProvider] of [context]'s app shares it. */
fun sharedClip(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.clips", file)
