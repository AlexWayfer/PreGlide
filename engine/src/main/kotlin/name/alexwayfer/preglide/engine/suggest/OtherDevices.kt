package name.alexwayfer.preglide.engine.suggest

/**
 * What [own] learned on this device read together with what [devices] learned on the others: the uses of a word add
 * up across the devices, but a use from before the word was forgotten or removed on any device is left out, as
 * forgetting it on one device takes its own uses. The devices, by their IDs, are never changed: a merge brings new
 * ones.
 */
internal class OtherDevices(val own: PersonalWords, val devices: Map<String, PersonalWords>) {
    private val everyDevice get() = listOf(own) + devices.values
    private val cutoffs by lazy {
        buildMap {
            for (device in everyDevice) {
                for ((key, time) in device.forgotten.asSequence() + device.removed.asSequence()) {
                    put(key, maxOf(get(key) ?: time, time))
                }
            }
        }
    }
    internal val frozen by lazy {
        everyDevice.filter { it.edits.commands.isNotEmpty() }.associateWith { it.edits.projected(cutoffs) }
    }

    /** What [device] learned, or this device when null, each word with its own uses there, past every cutoff. */
    fun allOn(device: String?): List<Learned> {
        val source = if (device == null) own else devices[device] ?: return emptyList()
        return usesOn(source) { it.words.values }
    }

    fun learned(key: String): Learned? =
        if (devices.isEmpty() && own.edits.commands.isEmpty()) own.words[key]
        else everyDevice.flatMap { device -> usesOn(device) { listOfNotNull(it.words[key]) } }.reduceOrNull(::restored)

    fun all(): List<Learned> =
        if (devices.isEmpty() && own.edits.commands.isEmpty()) own.words.values.toList() else merged { it.words.values }

    fun after(context: List<String>, length: Int): List<Learned> =
        merged { it.next.after(context, length) }

    fun starts(): List<Learned> =
        merged { it.starts.all() }

    /** Whether [key] was removed on a device, and no device used it since. */
    fun isRemoved(key: String): Boolean {
        val removed = everyDevice.mapNotNull { it.removed[key] }.maxOrNull() ?: return false
        return everyDevice.all { (it.words[key]?.lastUsed ?: 0) <= removed && frozen[it]?.words?.get(key) == null }
    }

    fun removed(): Set<String> =
        everyDevice.flatMapTo(HashSet()) { it.removed.keys }.filterTo(HashSet(), ::isRemoved)

    /** Drops what [own] learned of each word before another device forgot or removed it. */
    fun dropForgottenFromOwn() {
        for (device in devices.values) {
            for ((key, time) in device.forgotten + device.removed) own.dropOlder(key, time)
        }
    }

    /** Frozen uses have already passed the cutoffs under every spelling along their edit history. */
    internal fun usesOn(device: PersonalWords, rows: (PersonalWords) -> Collection<Learned>): List<Learned> {
        val active = rows(device).filter { visible(it) }
        return combined(active + frozen[device]?.let(rows).orEmpty())
    }

    internal fun visible(word: Learned): Boolean =
        cutoffs[keyOf(word.spelling)]?.let { word.lastUsed > it } ?: true

    private fun merged(rows: (PersonalWords) -> Collection<Learned>) =
        if (devices.isEmpty() && own.edits.commands.isEmpty()) rows(own).toList()
        else combined(everyDevice.flatMap { usesOn(it, rows) })
}

/** Only the owner's effective rows go in the prefix that older clients read. */
internal fun OtherDevices.materialized() = PersonalWords().also { result ->
    allOn(null).forEach { result.words[keyOf(it.spelling)] = it }
    usesOn(own) { it.starts.all() }.forEach(result.starts::put)
    own.next.entries.filter { visible(it.word) }.forEach { it.putIn(result) }
    frozen[own]?.next?.entries?.forEach { it.putIn(result) }
}

internal fun OtherDevices.seen(word: String, context: List<String>): List<ContextSeen> {
    val active = own.next.seen(word, context)
    val previous = frozen[own]?.next?.seen(word, context) ?: return active
    return active.zip(previous) { a, b ->
        ContextSeen(a.length, seen = a.seen || b.seen, withWord = a.withWord || b.withWord)
    }
}

/** Whether [word] was removed on any device, and not typed on any since. */
fun PersonalWords.isRemoved(word: String): Boolean = others.isRemoved(keyOf(word))

/** The IDs of the other devices merged in, in no order. */
fun PersonalWords.deviceIds(): Set<String> = others.devices.keys

/**
 * Every word [device] learned, or this device when null, with its uses there, in no order; what any device forgot
 * or removed after them is left out, as from [PersonalWords.all].
 */
fun PersonalWords.allOn(device: String?): List<Learned> = others.allOn(device)

/** The words the user began a field with, on all the devices, in no order. */
fun PersonalWords.allStarts(): List<Learned> = others.starts()

/** Drops what this device learned of [key] last used at [time] or before. */
private fun PersonalWords.dropOlder(key: String, time: Long) {
    candidates.dropOlder(key, time)
    if ((words[key]?.lastUsed ?: Long.MAX_VALUE) <= time) words.remove(key)
    next.forget(key, usedUpTo = time)
    starts.dropOlder(key, time)
}

/** Adds uses under the same spelling, retaining the first owner's spelling. */
private fun combined(uses: Collection<Learned>): List<Learned> =
    uses.groupBy { keyOf(it.spelling) }.values.map { it.reduce(::restored) }
