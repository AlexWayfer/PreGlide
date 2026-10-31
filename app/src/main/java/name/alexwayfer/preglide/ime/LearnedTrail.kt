package name.alexwayfer.preglide.ime

/** How many words back erasing can return to and still correct the word there. */
private const val TRAIL_WORDS = 16

/**
 * A word learned, where it starts in the text, whether the user [chose] it from the suggestions, and the start of it
 * typed before the glide that went on it, as "ИИ-" before "вставка", or empty (see [gluedPrefix]).
 */
internal data class Placed(val start: Int, val use: LearnedUse, val chose: Boolean = false, val glued: String = "")

/**
 * The words learned since the last mark, in the order they follow one another. The last of them, while only
 * spaces follow it, is the word typed last: changing it is a correction.
 */
internal class LearnedTrail {
    private val words = ArrayDeque<Placed>()

    /** The word learned last, or null. */
    val last: Placed? get() = words.lastOrNull()

    /** Adds [placed] in place of the words from where it starts on: gone, or past correcting. */
    fun place(placed: Placed) {
        words.removeAll { it.start >= placed.start }
        words.addLast(placed)
        if (words.size > TRAIL_WORDS) words.removeFirst()
    }

    fun remove(placed: Placed) {
        words.remove(placed)
    }

    /** Takes off the word learned last when the user chose it and it [matches]; returns it, or null otherwise. */
    fun takeLastChoice(matches: (Placed) -> Boolean): Placed? =
        words.lastOrNull()?.takeIf { it.chose && matches(it) }?.also { words.removeLast() }

    fun clear() {
        words.clear()
    }
}
