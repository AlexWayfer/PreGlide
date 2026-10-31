package name.alexwayfer.preglide.ime

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * The clipboard's history: what the user copied in the last hour, for the clipboard's panel, in
 * [KeyboardState.history], the latest first. The clipboard holds only the latest copy, so the keyboard keeps its own:
 * each copy the clipboard tells of is [saw], and kept but a sensitive one, as a password. The history lives in
 * [files], encrypted, images copied there whole, as the app that copied one takes access back once the clipboard
 * changes; previews of them are [previewSize] pixels at least.
 *
 * The files are read and written off the main thread, as the [work] loads, one change at a time. Nothing is read or
 * kept unless the work is available: before the first unlock the files cannot be read.
 */
class ClipHistory(
    private val keyboard: KeyboardState,
    private val files: HistoryFiles,
    work: KeyboardWork,
    private val previewSize: Int,
) {
    private val scope = work.scope
    private val loadContext = work.load
    private val available = work.available
    private val clock = work.clock
    private val lock = Mutex()

    /** The history as the files hold it; null until read. */
    private var history: SavedHistory? = null
    private val previews = HashMap<SavedClip.Image, ImageBitmap>()

    /** The latest copy [saw] was told of, so the same copy read again starts nothing. */
    private var lastSeen = Long.MIN_VALUE

    /** The clipboard holds [clip]: the history keeps it, unless it saw it before or the user removed it since. */
    fun saw(clip: Clip) {
        if (clip.copiedAt <= lastSeen || !available()) return
        lastSeen = clip.copiedAt
        change { before ->
            val saved = clip.takeIf { it.copiedAt > before.newestSeen && isSaved(it) }?.let { saved(it) }
            val clips = saved?.let { withClip(before.clips, it, clock()) } ?: before.clips
            SavedHistory(clips, maxOf(before.newestSeen, clip.copiedAt))
        }
    }

    /** Drops the copies grown old, as the panel opens. */
    fun refresh() {
        if (available()) change { it }
    }

    fun remove(clip: SavedClip) {
        change { it.copy(clips = it.clips - clip) }
    }

    fun clear() {
        change { it.copy(clips = emptyList()) }
    }

    /** The image of [image], decrypted; call it off the main thread. */
    fun open(image: SavedClip.Image): InputStream = files.open(image)

    /** Changes the history by [block], one change at a time, with the copies grown old dropped, and shows it. */
    private fun change(block: suspend (SavedHistory) -> SavedHistory) {
        scope.launch {
            lock.withLock {
                val before = history ?: withContext(loadContext) { files.read() }
                val changed = block(before)
                val after = changed.copy(clips = changed.clips.filter { isKept(it, clock()) })
                if (after != before) {
                    withContext(loadContext) { files.write(after, dropped = before.clips - after.clips.toSet()) }
                }
                history = after
                show(after)
            }
        }
    }

    /** [clip] as the history keeps it: an image copied into the files; null when it cannot be read. */
    private suspend fun saved(clip: Clip): SavedClip? = when (clip) {
        is Clip.Text -> SavedClip.Text(clip.text, clip.copiedAt)
        is Clip.Image -> withContext(loadContext) { files.copied(clip) }
    }

    /** Shows [history] in the panel, with the previews of its images, read the first time. */
    private suspend fun show(history: SavedHistory) {
        val images = history.clips.filterIsInstance<SavedClip.Image>()
        previews.keys.retainAll(images.toSet())
        for (image in images.filterNot { it in previews }) {
            withContext(loadContext) { files.preview(image, previewSize) }?.let { previews[image] = it }
        }
        keyboard.history = history.clips.map { HistoryItem(it, (it as? SavedClip.Image)?.let(previews::get)) }
    }
}
