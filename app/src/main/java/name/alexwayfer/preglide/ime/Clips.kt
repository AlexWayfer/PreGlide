package name.alexwayfer.preglide.ime

import android.content.ClipDescription
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import name.alexwayfer.preglide.R

/**
 * How long after the copy the strip offers what was copied: enough to switch to another app and find the field,
 * not so long that an old copy comes up out of the blue.
 */
const val CLIP_FRESH_MILLIS = 3 * 60_000L

/** How many chars of a copied text the strip reads; the chip shows a line of them at most. */
private const val CHIP_TEXT_READ = 200

/** What a copied text marked sensitive, such as a password, shows in place of itself. */
const val HIDDEN_TEXT = "••••••••"

/** What the user copied, as the strip offers to paste it. */
sealed interface Clip {
    /** When it was copied, on the clock of `System.currentTimeMillis`, as `ClipDescription.getTimestamp` tells. */
    val copiedAt: Long

    /** A text; a [sensitive] one, as a password that a password manager copied, does not show itself. */
    data class Text(val text: String, val sensitive: Boolean, override val copiedAt: Long) : Clip

    /**
     * An image the field may take whole, at [uri], of [mimeType]: copied, or a fresh [screenshot] the keyboard
     * offers as if copied.
     */
    data class Image(
        val uri: Uri,
        val mimeType: String,
        override val copiedAt: Long,
        val screenshot: Boolean = false,
    ) : Clip
}

/** The [clip] the strip offers, with the [preview] of an image. */
data class OfferedClip(val clip: Clip, val preview: ImageBitmap? = null)

/** Whether a clip copied at [copiedAt] is fresh enough to offer at [now]: no longer once [freshLeft] runs out. */
fun isFresh(copiedAt: Long, now: Long): Boolean = now - copiedAt in 0..<CLIP_FRESH_MILLIS

/** How long until a clip copied at [copiedAt] stops being fresh at [now]; zero once it has. */
fun freshLeft(copiedAt: Long, now: Long): Long = (copiedAt + CLIP_FRESH_MILLIS - now).coerceAtLeast(0)

/** What the chip shows of [text]: its start, on one line, with each run of spaces and line breaks as one space. */
fun chipText(text: String): String = text.take(CHIP_TEXT_READ).trim().replace(Regex("\\s+"), " ")

/** What the chip says of [clip]: the text, hidden when sensitive; null for an image, which [imageLabel] names. */
fun chipTextOf(clip: Clip): String? = when (clip) {
    is Clip.Text -> if (clip.sensitive) HIDDEN_TEXT else chipText(clip.text)
    is Clip.Image -> null
}

/**
 * What the chip calls [image]: a screenshot when the keyboard offers it fresh from the gallery, an image when it was
 * copied, a screenshot copied too, as Gboard does.
 */
@StringRes
fun imageLabel(image: Clip.Image): Int = if (image.screenshot) R.string.clip_screenshot else R.string.clip_image

/**
 * Whether a field that takes content of [fieldTypes] (`EditorInfo.contentMimeTypes`) can take [clip]: any field
 * takes a text, and an image only one that names its type or all images.
 */
fun takesClip(clip: Clip, fieldTypes: List<String>): Boolean = when (clip) {
    is Clip.Text -> clip.text.isNotBlank()
    is Clip.Image -> fieldTypes.any { ClipDescription.compareMimeTypes(clip.mimeType, it) }
}
