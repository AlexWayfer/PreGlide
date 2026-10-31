package name.alexwayfer.preglide.engine.suggest

/**
 * What [own] learned on this device read together with what [devices] learned on the others: the uses of a word add
 * up across the devices, but a use from before the word was forgotten or removed on any device is left out, as
 * forgetting it on one device takes its own uses. The devices, by their IDs, are never changed: a merge brings new
 * ones.
 */
internal class OtherDevices(private val own: PersonalWords, val devices: Map<String, PersonalWords>) {
    private val everyDevice get() = listOf(own) + devices.values

    /** What [device] learned, or this device when null, each word with its own uses there, past every cutoff. */
    fun allOn(device: String?): List<Learned> {
        val words = if (device == null) own.words else devices[device]?.words.orEmpty()
        return words.mapNotNull { (key, learned) -> visible(listOf(learned), cutoff(key)) }
    }

    fun learned(key: String): Learned? =
        if (devices.isEmpty()) own.words[key] else visible(everyDevice.mapNotNull { it.words[key] }, cutoff(key))

    fun all(): List<Learned> =
        if (devices.isEmpty()) own.words.values.toList()
        else everyDevice.flatMapTo(HashSet()) { it.words.keys }.mapNotNull(::learned)

    fun after(context: List<String>, length: Int): List<Learned> =
        if (devices.isEmpty()) own.next.after(context, length)
        else merged(everyDevice.flatMap { it.next.after(context, length) })

    fun starts(): List<Learned> =
        if (devices.isEmpty()) own.starts.all() else merged(everyDevice.flatMap { it.starts.all() })

    /** Whether [key] was removed on a device, and no device used it since. */
    fun isRemoved(key: String): Boolean {
        if (devices.isEmpty()) return key in own.removed
        val removed = everyDevice.mapNotNull { it.removed[key] }.maxOrNull()
        return removed != null && everyDevice.all { (it.words[key]?.lastUsed ?: 0) <= removed }
    }

    fun removed(): Set<String> =
        if (devices.isEmpty()) own.removed.keys.toSet()
        else everyDevice.flatMapTo(HashSet()) { it.removed.keys }.filterTo(HashSet(), ::isRemoved)

    /** Drops what [own] learned of each word before another device forgot or removed it. */
    fun dropForgottenFromOwn() {
        for (device in devices.values) {
            for ((key, time) in device.forgotten + device.removed) own.dropOlder(key, time)
        }
    }

    /** The latest time [key] was forgotten or removed on any device, or null when it never was. */
    private fun cutoff(key: String): Long? =
        everyDevice.flatMap { listOfNotNull(it.forgotten[key], it.removed[key]) }.maxOrNull()

    /** [learned], uses of words from all the devices, added up for each word. */
    private fun merged(learned: List<Learned>): List<Learned> =
        learned.groupBy { keyOf(it.spelling) }.mapNotNull { (key, uses) -> visible(uses, cutoff(key)) }
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
    if ((words[key]?.lastUsed ?: Long.MAX_VALUE) <= time) words.remove(key)
    next.forget(key, usedUpTo = time)
    starts.dropOlder(key, time)
}

/** [uses] of one word, this device's first, added up past [cutoff], or null when none is left. */
private fun visible(uses: List<Learned>, cutoff: Long?): Learned? =
    uses.filter { cutoff == null || it.lastUsed > cutoff }.reduceOrNull(::restored)
