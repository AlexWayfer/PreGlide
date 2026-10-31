package name.alexwayfer.preglide.engine.emoji

/** The groups the emoji panel has a tab for, in the order of Unicode's emoji-test.txt. */
enum class EmojiGroup {
    Smileys,
    People,
    Animals,
    Food,
    Travel,
    Activities,
    Objects,
    Symbols,
    Flags,
}

/** How many skin tones an emoji that takes them comes in, lightest first. */
const val SKIN_TONES = 5

/**
 * An emoji as the panel offers it: its [base] form, the yellow one, and its [tones], one per skin tone from the
 * lightest, or none for an emoji that takes no tone.
 */
data class Emoji(val base: String, val tones: List<String> = emptyList()) {
    init {
        require(tones.isEmpty() || tones.size == SKIN_TONES) { "An emoji takes all $SKIN_TONES skin tones or none" }
    }

    /** The base form, then the tones: what a long press offers. */
    val forms: List<String> get() = listOf(base) + tones

    /** The form of [tone]: 0 for the base, 1 to [SKIN_TONES] for a skin tone; the base for one it does not take. */
    fun toned(tone: Int): String = tones.getOrNull(tone - 1) ?: base
}

/** The emoji the panel offers, by [groups], each in the order it shows them. */
class EmojiPalette(val groups: Map<EmojiGroup, List<Emoji>>) {
    private val byForm: Map<String, Emoji> = buildMap {
        for (emoji in groups.values.flatten()) emoji.forms.forEach { put(it, emoji) }
    }

    /** The emoji that [form], its base or one of its tones, is a form of; null for one the palette has not. */
    fun emojiOf(form: String): Emoji? = byForm[form]

    companion object {
        /**
         * Reads the palette from [lines] of [paletteText], keeping only what [renders]: an emoji whose base the
         * phone cannot draw is left out, and so are the tones of one whose tones it cannot all draw.
         */
        fun read(lines: Sequence<String>, renders: (String) -> Boolean): EmojiPalette {
            val groups = mutableMapOf<EmojiGroup, MutableList<Emoji>>()
            var group: EmojiGroup? = null
            for (line in lines) {
                if (line.startsWith(GROUP_MARK)) {
                    group = EmojiGroup.valueOf(line.drop(GROUP_MARK.length))
                    groups.getOrPut(group) { mutableListOf() }
                } else if (line.isNotBlank()) {
                    val emoji = readEmoji(line, renders) ?: continue
                    groups.getValue(checkNotNull(group) { "An emoji before any group" }).add(emoji)
                }
            }
            return EmojiPalette(groups)
        }
    }
}

private const val GROUP_MARK = "="

/** The emoji of a line: its base and its tones, apart by spaces; null when [renders] cannot draw its base. */
private fun readEmoji(line: String, renders: (String) -> Boolean): Emoji? {
    val forms = line.split(' ')
    val base = forms.first()
    if (!renders(base)) return null
    val tones = forms.drop(1)
    return Emoji(base, tones.takeIf { it.all(renders) }.orEmpty())
}

/** [groups] as the app's asset holds them: a line `=Group` before each group, then a line per emoji. */
fun paletteText(groups: Map<EmojiGroup, List<Emoji>>): String = buildString {
    for ((group, emoji) in groups) {
        append(GROUP_MARK).append(group.name).append('\n')
        for (each in emoji) append(each.forms.joinToString(" ")).append('\n')
    }
}
