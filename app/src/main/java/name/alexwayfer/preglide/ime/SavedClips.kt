package name.alexwayfer.preglide.ime

import androidx.compose.ui.graphics.ImageBitmap
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/** How long the clipboard's history keeps a copy, from when it was copied. */
const val HISTORY_KEEP_MILLIS = 60 * 60_000L

/** How many copies the history keeps at most, however fast they come. */
const val HISTORY_SIZE = 50

/** The longest text the history keeps; a longer one is offered in the strip alone. */
const val HISTORY_TEXT_LIMIT = 100_000

/** How many chars of a text a history card reads; it shows a few lines of them at most. */
private const val CARD_TEXT_READ = 300

/** The version of the history file's layout, written first. */
private const val HISTORY_VERSION = 1
private const val KIND_TEXT = 0
private const val KIND_IMAGE = 1

/** A copy the clipboard's history keeps. */
sealed interface SavedClip {
    /** When it was copied, on the clock of `System.currentTimeMillis`; it tells copies apart. */
    val copiedAt: Long

    data class Text(val text: String, override val copiedAt: Long) : SavedClip

    /** An image, in a [file] of the history's own folder, encrypted. */
    data class Image(val file: String, val mimeType: String, override val copiedAt: Long) : SavedClip
}

/** A copy as the clipboard's panel shows it, with the [preview] of an image when it could be read. */
data class HistoryItem(val clip: SavedClip, val preview: ImageBitmap? = null)

/** What the history file holds: the copies, the latest first, and when the [newestSeen] copy was made. */
data class SavedHistory(val clips: List<SavedClip>, val newestSeen: Long)

/** Whether [clip] is still in the history at [now]: copied less than [HISTORY_KEEP_MILLIS] before. */
fun isKept(clip: SavedClip, now: Long): Boolean = now - clip.copiedAt in 0..<HISTORY_KEEP_MILLIS

/** Whether the history takes [clip], read off the clipboard: not a sensitive text, nor a very long one. */
fun isSaved(clip: Clip): Boolean = when (clip) {
    is Clip.Text -> !clip.sensitive && clip.text.isNotBlank() && clip.text.length <= HISTORY_TEXT_LIMIT
    is Clip.Image -> true
}

/**
 * [clips] with [clip] first, at [now]: the same text copied before leaves its old place, and copies grown old or
 * beyond [HISTORY_SIZE] drop out.
 */
fun withClip(clips: List<SavedClip>, clip: SavedClip, now: Long): List<SavedClip> {
    val others = if (clip is SavedClip.Text) clips.filterNot { it is SavedClip.Text && it.text == clip.text } else clips
    return (listOf(clip) + others).filter { isKept(it, now) }.take(HISTORY_SIZE)
}

/** What a history card shows of [text]: its start, with its own line breaks, the ends trimmed. */
fun cardText(text: String): String = text.take(CARD_TEXT_READ).trim()

/** The name of the file a copied image of [copiedAt] is kept in. */
fun savedImageName(copiedAt: Long): String = "image-$copiedAt"

/**
 * The step to read an image of [width] by [height] pixels at, to make a preview at least [size] on its shorter
 * side: a power of two, as `BitmapFactory.Options.inSampleSize` takes, 1 for a small image.
 */
fun previewStep(width: Int, height: Int, size: Int): Int {
    var step = 1
    while (minOf(width, height) / (step * 2) >= size) step *= 2
    return step
}

fun writeHistory(output: DataOutput, history: SavedHistory) {
    output.writeInt(HISTORY_VERSION)
    output.writeLong(history.newestSeen)
    output.writeInt(history.clips.size)
    for (clip in history.clips) {
        when (clip) {
            is SavedClip.Text -> {
                output.writeByte(KIND_TEXT)
                output.writeLong(clip.copiedAt)
                val bytes = clip.text.toByteArray()
                output.writeInt(bytes.size)
                output.write(bytes)
            }
            is SavedClip.Image -> {
                output.writeByte(KIND_IMAGE)
                output.writeLong(clip.copiedAt)
                output.writeUTF(clip.file)
                output.writeUTF(clip.mimeType)
            }
        }
    }
}

/** The history [writeHistory] wrote; throws [IOException] for a file of another layout or a broken one. */
fun readHistory(input: DataInput): SavedHistory {
    val version = input.readInt()
    if (version != HISTORY_VERSION) throw IOException("History version $version")
    val newestSeen = input.readLong()
    val clips = List(input.readInt()) {
        when (val kind = input.readByte().toInt()) {
            KIND_TEXT -> {
                val copiedAt = input.readLong()
                val bytes = ByteArray(input.readInt())
                input.readFully(bytes)
                SavedClip.Text(String(bytes), copiedAt)
            }
            KIND_IMAGE -> {
                val copiedAt = input.readLong()
                SavedClip.Image(input.readUTF(), input.readUTF(), copiedAt)
            }
            else -> throw IOException("History clip kind $kind")
        }
    }
    return SavedHistory(clips, newestSeen)
}
