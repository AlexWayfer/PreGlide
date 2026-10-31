package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.glide.glideIsSure

/** What the strip offers right after a glide typed its word, as the user chose. */
enum class GlideStrip {
    /** The other words the glide may have meant: a tap swaps one in for the glided word. */
    GlideWords,

    /** The words likely next: a tap puts one after the glided word and a space. */
    NextWords,

    /** The next words after a glide sure of its word (see [glideIsSure]), the glide's other words after any other. */
    Auto,
    ;

    /** Whether the strip offers the next words after a glide [sure] of its word or not. */
    fun showsNextWords(sure: Boolean): Boolean = this == NextWords || this == Auto && sure

    companion object {
        /** The choice saved under [name], or the default, [Auto], when none or unknown. */
        fun parse(name: String?): GlideStrip = entries.firstOrNull { it.name == name } ?: Auto
    }
}
