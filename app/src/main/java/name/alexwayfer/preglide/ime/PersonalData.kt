package name.alexwayfer.preglide.ime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.writeLocal
import name.alexwayfer.preglide.sync.refreshDevices
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * How long after the last change what was learned is saved: typing comes in bursts, and a save per word would
 * write the same file over and over.
 */
private val SAVE_DELAY = 5.seconds

/**
 * What the keyboard learned, shared by the keyboard and the app's screen, which run in one process. Each name, as
 * [personalName] or [EMAILS], loads from [store] once, so both change the same words, and only this saves them:
 * otherwise one would save its old copy over the other's change. Changes come on the thread of [scope], where
 * saves wait; they write in [writeContext], which must run one write at a time, in order, so an older save never
 * overwrites a newer one. Each name loads with what [others] gives of the other devices under it, by their IDs,
 * merged in, which only the premium edition syncs.
 */
class PersonalData(
    private val store: PersonalStore,
    private val scope: CoroutineScope,
    private val writeContext: CoroutineContext,
    private val others: (String) -> Map<String, PersonalWords> = { emptyMap() },
) {
    // Loads come from other threads, so the map is used only while holding it.
    private val loaded = HashMap<String, PersonalWords>()
    private val unsaved = HashSet<String>()
    private var saving: Job? = null
    private var writing: Job? = null

    /** Counts the changes, so a screen that shows what was learned knows when to show it anew. */
    val changes: StateFlow<Int>
        field = MutableStateFlow(0)

    /** What was learned under [name]. The first call reads it from [store], so call it off the main thread. */
    fun load(name: String): PersonalWords = synchronized(loaded) {
        loaded.getOrPut(name) {
            store.read(name).apply { others(name).takeIf { it.isNotEmpty() }?.let(::refreshDevices) }
        }
    }

    /** What was learned under [name] changed: it is saved a while after the last change. */
    fun changed(name: String) {
        unsaved += name
        changes.value++
        saving?.cancel()
        saving = scope.launch {
            delay(SAVE_DELAY)
            save()
        }
    }

    /** Saves what changed now: the text is made here, on the thread of [scope], and written in [writeContext]. */
    fun save() {
        saving?.cancel()
        val texts = unsaved.mapNotNull { name ->
            synchronized(loaded) { loaded[name] }?.let { name to buildString { it.writeLocal(this) } }
        }
        unsaved.clear()
        if (texts.isEmpty()) return
        writing = scope.launch(writeContext) { texts.forEach { (name, text) -> store.write(name, text) } }
    }

    /** Saves what changed now, and returns once it and every save before it are written, for a sync to read them. */
    suspend fun flush() {
        save()
        writing?.join()
    }

    /** Replays cached edits even for an unopened language or after an interrupted sync. */
    suspend fun refreshOthers(names: List<String>, downloadsChanged: Boolean) {
        for (name in names) {
            val devices = withContext(writeContext) { others(name) }
            val edited = devices.values.any { it.hasWordEdits }
            val existing = synchronized(loaded) { loaded[name] }
            if (edited || downloadsChanged && existing != null) {
                val words = existing ?: withContext(writeContext) { load(name) }
                words.refreshDevices(devices)
                changed(name)
            }
        }
    }

    /**
     * Reads each name loaded anew from [store], into the same words the keyboard and the screen hold, as after the
     * words of another account came in its place; what was not saved yet is dropped, so [flush] first.
     */
    fun reload() {
        saving?.cancel()
        unsaved.clear()
        for ((name, words) in synchronized(loaded) { loaded.toMap() }) {
            words.replaceWith(store.read(name).apply { refreshDevices(others(name)) })
        }
        changes.value++
    }
}
