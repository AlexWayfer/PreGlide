package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.emoji.Emoji
import name.alexwayfer.preglide.engine.emoji.EmojiGroup
import name.alexwayfer.preglide.engine.emoji.SKIN_TONES

/** An emoji that Unicode's emoji-test.txt lists for a keyboard: its [group], the [emoji] and its English [name]. */
data class ListedEmoji(val group: EmojiGroup, val emoji: String, val name: String)

/** The groups of emoji-test.txt by their titles there; its components, as the skin tones alone, are not offered. */
private val groupsByTitle = mapOf(
    "Smileys & Emotion" to EmojiGroup.Smileys,
    "People & Body" to EmojiGroup.People,
    "Animals & Nature" to EmojiGroup.Animals,
    "Food & Drink" to EmojiGroup.Food,
    "Travel & Places" to EmojiGroup.Travel,
    "Activities" to EmojiGroup.Activities,
    "Objects" to EmojiGroup.Objects,
    "Symbols" to EmojiGroup.Symbols,
    "Flags" to EmojiGroup.Flags,
)
private const val COMPONENTS = "Component"
private const val GROUP_LINE = "# group:"

/** A fully qualified emoji's line: its code points, and after the emoji and the version it came in, its name. */
private val emojiLine = Regex("""^([0-9A-F]+(?: [0-9A-F]+)*)\s*;\s*fully-qualified\s*#\s*\S+\s+E\d+\.\d+\s+(.+)$""")

/**
 * The fully qualified emoji of the [lines] of emoji-test.txt, in its order, which Unicode recommends for a keyboard;
 * the forms an emoji may also be typed in, without its variation selectors, are left out.
 */
fun listedEmoji(lines: Sequence<String>): List<ListedEmoji> {
    val listed = mutableListOf<ListedEmoji>()
    var group: EmojiGroup? = null
    for (line in lines) {
        if (line.startsWith(GROUP_LINE)) {
            group = groupOf(line.removePrefix(GROUP_LINE).trim())
        } else {
            val match = emojiLine.matchEntire(line)
            if (match != null && group != null) {
                val (codes, name) = match.destructured
                listed += ListedEmoji(group, emojiOf(codes), name)
            }
        }
    }
    return listed
}

/** The group of [title]; null for the components, which are not offered. A group the builder does not know fails. */
private fun groupOf(title: String): EmojiGroup? {
    val group = groupsByTitle[title]
    check(group != null || title == COMPONENTS) { "A new emoji group: $title" }
    return group
}

/** The emoji of space-separated hex [codes]. */
private fun emojiOf(codes: String): String =
    codes.split(' ').joinToString("") { String(Character.toChars(it.toInt(HEX))) }

internal const val HEX = 16

/** The skin tone modifiers, lightest first. */
internal val TONE_MODIFIERS = 0x1F3FB..0x1F3FF

/** The emoji presentation selector, which CLDR's annotations leave out of their emoji. */
internal const val EMOJI_SELECTOR = 0xFE0F

/**
 * The [listed] emoji as the panel offers them, by group: each emoji in its base form, with its five forms of one
 * skin tone. The forms that mix tones, as a handshake of two hands of two tones, would make the long press a grid,
 * so they are left out.
 */
fun paletteOf(listed: List<ListedEmoji>): Map<EmojiGroup, List<Emoji>> {
    val bases = LinkedHashMap<String, ListedEmoji>()
    val tones = mutableMapOf<String, MutableList<String>>()
    for (each in listed) {
        val codes = each.emoji.codePoints().toArray()
        val toneCodes = codes.filter { it in TONE_MODIFIERS }.toSet()
        val key = codes.filter { it !in TONE_MODIFIERS && it != EMOJI_SELECTOR }.joinToString(" ")
        when (toneCodes.size) {
            0 -> bases.putIfAbsent(key, each)
            1 -> tones.getOrPut(key) { mutableListOf() } += each.emoji
        }
    }
    return bases.entries.groupBy({ it.value.group }) { (key, base) ->
        Emoji(base.emoji, tones[key]?.takeIf { it.size == SKIN_TONES }.orEmpty())
    }
}
