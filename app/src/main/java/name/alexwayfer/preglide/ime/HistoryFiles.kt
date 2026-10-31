package name.alexwayfer.preglide.ime

import android.content.ContentResolver
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import name.alexwayfer.preglide.diagnostics.AppLog
import name.alexwayfer.preglide.diagnostics.Diagnostics
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.GeneralSecurityException
import java.security.ProviderException

/** The file in the history's folder that lists the copies. */
private const val HISTORY_FILE = "history"

/**
 * The clipboard's history on disk: the list of copies and the images, in [folder], each file encrypted with
 * [cipher]; images copied in through [resolver]. Every call reads or writes files: make it off the main thread.
 */
class HistoryFiles(private val folder: File, private val cipher: ClipCipher, private val resolver: ContentResolver) {
    /** The history the folder holds; an empty one when there is none, or it cannot be read, which drops its files. */
    fun read(): SavedHistory {
        val file = File(folder, HISTORY_FILE)
        if (!file.exists()) return SavedHistory(emptyList(), Long.MIN_VALUE)
        return try {
            DataInputStream(cipher.decrypting(file.inputStream()).buffered()).use(::readHistory)
                .also { dropUnlisted(it) }
        } catch (error: IOException) {
            unreadable(error)
        } catch (error: GeneralSecurityException) {
            unreadable(error)
        } catch (error: ProviderException) {
            // The keystore lost the key, as after the lock screen was removed on some phones.
            unreadable(error)
        }
    }

    /**
     * Writes [history] whole, through a file of its own, so a write cut short leaves the old one; and deletes the
     * images of [dropped].
     */
    fun write(history: SavedHistory, dropped: List<SavedClip>) {
        try {
            folder.mkdirs()
            val next = File(folder, "$HISTORY_FILE.next")
            DataOutputStream(cipher.encrypting(next.outputStream()).buffered()).use { writeHistory(it, history) }
            Files.move(next.toPath(), File(folder, HISTORY_FILE).toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
        } catch (error: IOException) {
            Diagnostics.reportFailure(CLIP_TAG, "The clipboard's history was not saved", error)
        } catch (error: GeneralSecurityException) {
            Diagnostics.reportFailure(CLIP_TAG, "The clipboard's history was not saved", error)
        }
        dropped.filterIsInstance<SavedClip.Image>().forEach { File(folder, it.file).delete() }
    }

    /** The image of [image], decrypted. */
    fun open(image: SavedClip.Image): InputStream = cipher.decrypting(File(folder, image.file).inputStream())

    /** [clip] copied into the folder, encrypted; null when it cannot be read. */
    fun copied(clip: Clip.Image): SavedClip.Image? {
        val saved = SavedClip.Image(savedImageName(clip.copiedAt), clip.mimeType, clip.copiedAt)
        val file = File(folder, saved.file)
        return try {
            folder.mkdirs()
            resolver.openInputStream(clip.uri)?.use { from ->
                cipher.encrypting(file.outputStream()).use { from.copyTo(it) }
                saved
            }
        } catch (error: IOException) {
            notCopied(file, error)
        } catch (error: SecurityException) {
            // The app that copied the image took access back.
            notCopied(file, error)
        } catch (error: GeneralSecurityException) {
            notCopied(file, error)
        }
    }

    /** A small picture of [image], read at a step that keeps it at least [size]; null when it cannot be read. */
    fun preview(image: SavedClip.Image, size: Int): ImageBitmap? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(image).use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = previewStep(bounds.outWidth, bounds.outHeight, size)
            }
            open(image).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
        } catch (_: IOException) {
            null
        } catch (_: GeneralSecurityException) {
            null
        }

    /** Starts the history anew when its file cannot be read: its copies are lost either way. */
    private fun unreadable(error: Exception): SavedHistory {
        Diagnostics.reportFailure(CLIP_TAG, "The clipboard's history is not readable", error)
        folder.listFiles()?.forEach(File::delete)
        return SavedHistory(emptyList(), Long.MIN_VALUE)
    }

    /** Deletes the images [history] does not list, as of a change cut short. */
    private fun dropUnlisted(history: SavedHistory) {
        val listed = history.clips.filterIsInstance<SavedClip.Image>().map { it.file }.toSet() + HISTORY_FILE
        folder.listFiles()?.filterNot { it.name in listed }?.forEach(File::delete)
    }

    private fun notCopied(file: File, error: Exception): SavedClip.Image? {
        file.delete()
        // The class alone: a message may hold the image's name.
        AppLog.w(CLIP_TAG, "A copied image was not kept: ${error.javaClass.simpleName}")
        return null
    }
}
