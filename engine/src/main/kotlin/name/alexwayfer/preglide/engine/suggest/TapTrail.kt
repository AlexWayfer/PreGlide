package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.rowHeightOf

/** How many of the last chars typed the trail keeps: more than the longest word. */
private const val KEPT_CHARS = 64

/**
 * Where the finger touched the keys for the chars typed last, so the suggestions can tell a tap near a key's edge,
 * a likely slip to its neighbor, from one in its middle. The trail knows nothing of the field: [pointsFor] matches
 * it against the word being typed from its end, so a paste, a cursor move or an edit the trail missed only makes
 * the chars before the mismatch unknown. A wrong match costs little: a point kept for a char lies on its key.
 */
class TapTrail {
    private val chars = StringBuilder()
    private val points = ArrayList<KeyPoint?>()

    /** How tall a row of keys is, in key widths, as the keys were last tapped. */
    var rowHeight: Float = DEFAULT_ROW_HEIGHT
        private set

    /**
     * Notes that [char] was typed by the key for [keyChar] on [layout], with [tap] landing on it: null for a char
     * chosen from a long press popup, and [layout] null off the letters, whose keys a word is not read on.
     */
    fun typed(char: Char, keyChar: Char, layout: KeyboardLayout?, tap: KeyTap?) {
        val place = layout?.places?.get(keyChar)
        val point = if (place != null && tap != null) place.pointAt(tap.across, tap.down) else null
        if (place != null && tap != null && tap.width > 0) rowHeight = rowHeightOf(tap, place)
        chars.append(char)
        points += point
        if (chars.length > KEPT_CHARS) {
            chars.deleteCharAt(0)
            points.removeAt(0)
        }
    }

    /** Notes that a backspace deleted the char typed last. */
    fun deleted() {
        if (chars.isEmpty()) return
        chars.setLength(chars.length - 1)
        points.removeAt(points.lastIndex)
    }

    fun clear() {
        chars.setLength(0)
        points.clear()
    }

    /**
     * Where the finger touched for each char of [word], the word before the cursor: as far back from its end as the
     * trail's chars match it, whatever their case; null before that.
     */
    fun pointsFor(word: String): List<KeyPoint?> {
        val found = arrayOfNulls<KeyPoint>(word.length)
        var back = 1
        while (back <= word.length && back <= chars.length &&
            fold(word[word.length - back]) == fold(chars[chars.length - back])
        ) {
            found[word.length - back] = points[points.size - back]
            back++
        }
        return found.toList()
    }
}
