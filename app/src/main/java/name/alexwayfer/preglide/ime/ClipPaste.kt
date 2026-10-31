package name.alexwayfer.preglide.ime

import android.content.ClipDescription
import android.content.ContentResolver
import android.net.Uri
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.webkit.MimeTypeMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.diagnostics.AppLog
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException

/** The tag of the clipboard's log lines. */
const val CLIP_TAG = "Clipboard"

/**
 * How long a copy of a pasted image is kept: the app it went to may read it only as the message is sent, so it
 * outlives the paste.
 */
const val PASTED_IMAGE_KEEP_MILLIS = 24 * 60 * 60_000L

/**
 * Pastes what the user copied, from the clipboard or its history, into the field of [connection]. A text goes in as
 * typed. An image goes in as content,
 * in a copy the keyboard keeps in [folder] and shares as [shared] gives: the app that copied it may take access back
 * when the clipboard changes, and the field may read it later than that. The copy is made through [resolver] off the
 * main thread, as the [work] loads; its clock tells when copies grow old.
 */
class ClipPaste(
    private val resolver: ContentResolver,
    private val folder: File,
    private val shared: (File) -> Uri,
    private val connection: () -> InputConnection?,
    work: KeyboardWork,
) {
    private val scope = work.scope
    private val loadContext = work.load
    private val clock = work.clock

    fun paste(clip: Clip) {
        when (clip) {
            is Clip.Text -> pasteText(clip.text)
            is Clip.Image -> pasteImage(clip.mimeType) { resolver.openInputStream(clip.uri) }
        }
    }

    /** Pastes [clip] from the clipboard's history, whose images [open] reads. */
    fun paste(clip: SavedClip, open: (SavedClip.Image) -> InputStream) {
        when (clip) {
            is SavedClip.Text -> pasteText(clip.text)
            is SavedClip.Image -> pasteImage(clip.mimeType) { open(clip) }
        }
    }

    private fun pasteText(text: String) {
        connection()?.commitText(text, 1)
    }

    /** Pastes the image of [mimeType] that [open] reads, off the main thread. */
    private fun pasteImage(mimeType: String, open: () -> InputStream?) {
        val field = connection() ?: return
        scope.launch {
            val file = withContext(loadContext) { copied(mimeType, open) } ?: return@launch
            // The user may have left the field while the image was copied.
            if (connection() === field) commit(field, file, mimeType)
        }
    }

    private fun commit(field: InputConnection, file: File, mimeType: String) {
        val content = InputContentInfo(shared(file), ClipDescription(file.name, arrayOf(mimeType)))
        if (!field.commitContent(content, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)) {
            AppLog.w(CLIP_TAG, "The field did not take the pasted image")
        }
    }

    /**
     * A copy of its own in [folder] of the image of [mimeType] that [open] reads, old copies dropped; null when the
     * image cannot be read.
     */
    private fun copied(mimeType: String, open: () -> InputStream?): File? {
        val now = clock()
        folder.listFiles()?.filter { now - it.lastModified() > PASTED_IMAGE_KEEP_MILLIS }?.forEach(File::delete)
        val file = File(folder, pastedImageName(now, mimeType))
        return try {
            folder.mkdirs()
            open()?.use { from ->
                file.outputStream().use { from.copyTo(it) }
                file
            }
        } catch (error: IOException) {
            failed(file, error)
        } catch (error: SecurityException) {
            // The app that copied the image took access back.
            failed(file, error)
        } catch (error: GeneralSecurityException) {
            // The history's copy, which did not decrypt.
            failed(file, error)
        }
    }

    private fun failed(file: File, error: Exception): File? {
        file.delete()
        // The class alone: a message may hold the image's name.
        AppLog.w(CLIP_TAG, "The pasted image was not read: ${error.javaClass.simpleName}")
        return null
    }
}

/** The name of the copy of an image of [mimeType] pasted at [now]: `clip-<now>.png` for a PNG. */
fun pastedImageName(now: Long, mimeType: String): String =
    "clip-$now." + (MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "img")
