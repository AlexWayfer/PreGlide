package name.alexwayfer.preglide.ime

import android.content.Context
import android.graphics.Paint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.engine.emoji.EmojiHistory
import name.alexwayfer.preglide.engine.emoji.EmojiNames
import name.alexwayfer.preglide.engine.emoji.EmojiPalette
import name.alexwayfer.preglide.engine.emoji.readEmojiNames
import name.alexwayfer.preglide.engine.layout.Language
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The folder of the emoji panel's files in the app's assets. */
private const val EMOJI_FOLDER = "emoji"

/** The asset of the emoji by group (see [EmojiPalette.read]). */
const val PALETTE_ASSET = "$EMOJI_FOLDER/emoji.txt"

/** The asset of the emoji's names in [language] (see [readEmojiNames]). */
fun emojiNamesAsset(language: Language): String = "$EMOJI_FOLDER/${language.tag}.tsv"

/** The keyboard's languages, the one of [deviceLanguage], a language code as "ru", first, to name the emoji in. */
fun emojiLanguages(deviceLanguage: String): List<Language> = Language.entries.sortedBy { it.tag != deviceLanguage }

/** Where [EmojiData] reads the emoji from and keeps the history in. */
interface EmojiFiles {
    /** Reads the lines of the asset [name] with [read]. */
    fun <T> readAsset(name: String, read: (Sequence<String>) -> T): T

    /** Whether the phone's font draws [emoji] as one picture, not as its parts or a box. */
    fun renders(emoji: String): Boolean

    /** The history's file's text, or null for none yet. */
    fun readHistory(): String?

    fun writeHistory(text: String)
}

/**
 * Reads the emoji panel's emoji and names from [files] into [state] off the main thread, on [io], and keeps the
 * history there, once [available] says the app's storage is open, as it is not before the first unlock. The names
 * of [languages] come in their order, the first naming an emoji for a screen reader.
 */
class EmojiData(
    private val state: EmojiState,
    private val files: EmojiFiles,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val available: () -> Boolean,
    private val languages: List<Language>,
) {
    private var loading = false
    private var historyRead = false

    /** Reads what is not read yet: the emoji and their names once, and the history once the storage is open. */
    fun load() {
        val readsHistory = !historyRead && available()
        if (loading || (state.palette != null && !readsHistory)) return
        loading = true
        val readsEmoji = state.palette == null
        scope.launch {
            try {
                val read = withContext(io) { readAll(readsEmoji, readsHistory) }
                read.palette?.let { state.palette = it }
                read.names?.let { state.names = it }
                if (readsHistory) {
                    // What was typed while the file could not be read goes on top of it.
                    state.history = state.history.after(read.history ?: EmojiHistory())
                    historyRead = true
                    // A panel opened before the file was read shows the recent ones, with nothing to shift yet.
                    if (state.shownRecent.isEmpty()) state.shownRecent = state.history.recent
                }
            } finally {
                loading = false
            }
        }
    }

    private fun readAll(readsEmoji: Boolean, readsHistory: Boolean): ReadEmoji {
        val palette = if (readsEmoji) files.readAsset(PALETTE_ASSET) { EmojiPalette.read(it, files::renders) } else null
        val names = if (readsEmoji) readNames() else null
        val history = if (readsHistory) files.readHistory()?.let { EmojiHistory.read(it.lineSequence()) } else null
        return ReadEmoji(palette, names, history)
    }

    private fun readNames(): EmojiNames =
        EmojiNames(languages.flatMap { files.readAsset(emojiNamesAsset(it), ::readEmojiNames) })

    /** Keeps the history as it is now; before the file could be read, a save would lose what it holds. */
    fun save() {
        if (!historyRead) return
        val text = state.history.text()
        scope.launch(io) { files.writeHistory(text) }
    }
}

private class ReadEmoji(val palette: EmojiPalette?, val names: EmojiNames?, val history: EmojiHistory?)

/**
 * The emoji panel's files on the phone: the app's assets, the system's font, and the history in [historyFile], in
 * the app's storage, which Android opens only once the user unlocked the device.
 */
class AndroidEmojiFiles(private val context: Context, private val historyFile: () -> File) : EmojiFiles {
    // Asked for on the reading thread alone, where every check runs.
    private val paint by lazy { Paint() }

    override fun <T> readAsset(name: String, read: (Sequence<String>) -> T): T =
        context.assets.open(name).bufferedReader().useLines(read)

    override fun renders(emoji: String): Boolean = paint.hasGlyph(emoji)

    override fun readHistory(): String? = historyFile().takeIf(File::exists)?.readText()

    /** A new file takes the old one's place, so a save cut short leaves the old one whole. */
    override fun writeHistory(text: String) {
        val file = historyFile()
        file.parentFile?.mkdirs()
        val partial = File(file.path + ".part")
        partial.writeText(text)
        Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
