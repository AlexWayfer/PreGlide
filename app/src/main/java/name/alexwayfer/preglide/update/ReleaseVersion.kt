package name.alexwayfer.preglide.update

/** A dotted release number; missing trailing parts are zero, so 1.0 equals 1.0.0. */
class ReleaseVersion private constructor(private val parts: List<Int>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is ReleaseVersion && compareTo(other) == 0
    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()
    override fun toString(): String = parts.joinToString(".")

    companion object {
        private val dotted = Regex("[0-9]+(?:\\.[0-9]+)*")

        fun parse(text: String): ReleaseVersion? {
            val parts = text.takeIf(dotted::matches)?.split('.')
            val numbers = parts?.mapNotNull(String::toIntOrNull)
            return numbers?.takeIf { it.size == parts.size }?.let(::ReleaseVersion)
        }
    }
}
