package name.alexwayfer.preglide.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * The extra a clip's description carries, set by password managers and Android's own copies of passwords, when
 * what was copied must not show: `ClipDescription.EXTRA_IS_SENSITIVE`, which Android 13 named, set the same before.
 */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * Puts what the user copied a moment ago in [KeyboardState.clip], for the strip to offer, and takes it away once
 * it is pasted from there or is no longer fresh (see [CLIP_FRESH_MILLIS]). [clipboard] tells of each copy while the
 * keyboard is the default one; [check] reads it anew, as a field starts. An image's preview of [previewSize] is
 * read through [resolver] off the main thread, as the [work] loads, before the strip offers it. [onCopy] is told what
 * the clipboard holds each time it is read, fresh or not.
 */
class ClipboardWatch(
    private val keyboard: KeyboardState,
    private val clipboard: ClipboardManager,
    private val resolver: ContentResolver,
    work: KeyboardWork,
    private val previewSize: Size,
    private val onCopy: (Clip) -> Unit = {},
) {
    private val scope = work.scope
    private val loadContext = work.load

    // The clock of ClipDescription.getTimestamp.
    private val now = work.clock

    private val changed = ClipboardManager.OnPrimaryClipChangedListener { check() }

    /** The clip read last, which the strip offers or soon will. */
    private var read: Clip? = null

    /** The clips pasted from the strip while fresh: they are not offered again. */
    private val pasted = mutableSetOf<Clip>()

    /** The fresh screenshot the keyboard was told of last, offered as if copied; null with none. */
    private var screenshot: Clip.Image? = null
    private var loading: Job? = null
    private var expiring: Job? = null

    /** Starts listening to the clipboard and reads it. */
    fun start() {
        clipboard.addPrimaryClipChangedListener(changed)
        check()
    }

    fun stop() {
        clipboard.removePrimaryClipChangedListener(changed)
    }

    /**
     * Reads the clipboard anew: tells [onCopy] what it holds, offers the latest fresh clip not pasted yet, copied or
     * a screenshot, and drops one that is not there anymore.
     */
    fun check() {
        val current = currentClip()
        current?.let(onCopy)
        val now = now()
        pasted.retainAll { isFresh(it.copiedAt, now) }
        val clip = listOfNotNull(current, screenshot)
            .filter { it !in pasted && isFresh(it.copiedAt, now) }
            .maxByOrNull { it.copiedAt }
        if (clip == read) return
        read = clip
        loading?.cancel()
        expiring?.cancel()
        keyboard.clip = null
        when (clip) {
            null -> Unit
            is Clip.Text -> offer(OfferedClip(clip))
            is Clip.Image -> loading = scope.launch { offer(withContext(loadContext) { imageOffer(clip) }) }
        }
    }

    /** The user pasted [clip] from the strip, which stops offering it. */
    fun pasted(clip: Clip) {
        pasted += clip
        check()
    }

    /** The user took [clip], a screenshot: the strip offers it while it is the latest clip and fresh. */
    fun screenshot(clip: Clip.Image) {
        screenshot = clip
        check()
    }

    /** The user pasted the copy made at [copiedAt] from the clipboard's panel: the strip stops offering it too. */
    fun pastedCopy(copiedAt: Long) {
        read?.takeIf { it.copiedAt == copiedAt }?.let(::pasted)
    }

    private fun offer(offered: OfferedClip) {
        keyboard.clip = offered
        expiring = scope.launch {
            delay(freshLeft(offered.clip.copiedAt, now()).milliseconds)
            check()
        }
    }

    /** The clip on the clipboard as the strip would offer it: its first item, a text or an image; or null. */
    private fun currentClip(): Clip? {
        // The description alone, cheap, says whether there is anything worth reading.
        val description = clipboard.primaryClipDescription ?: return null
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.let { clipOf(description, it.getItemAt(0)) }
    }

    /** What [item] holds, as [description] tells: an image, a text, or null for anything else. */
    private fun clipOf(description: ClipDescription, item: ClipData.Item): Clip? {
        val copiedAt = description.timestamp
        val image = (0 until description.mimeTypeCount)
            .map { description.getMimeType(it) }
            .firstOrNull { it.startsWith("image/") }
        return when {
            image != null && item.uri != null -> Clip.Image(item.uri, image, copiedAt)
            item.text != null -> {
                val sensitive = description.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true
                Clip.Text(item.text.toString(), sensitive, copiedAt)
            }
            else -> null
        }
    }

    /** [clip] as the strip offers it, with its preview when one can be read. */
    private fun imageOffer(clip: Clip.Image): OfferedClip = OfferedClip(clip, preview(clip.uri)?.asImageBitmap())

    /** A small picture of the image at [uri], or null when it cannot be read, as after its app took access back. */
    private fun preview(uri: Uri): Bitmap? =
        try {
            resolver.loadThumbnail(uri, previewSize, null)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
}
