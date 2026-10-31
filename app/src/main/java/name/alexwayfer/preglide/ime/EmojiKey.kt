package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.Key
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.withEmojiKey

/**
 * [layout]'s rows, the bottom one with as much of the emoji key as [look] shows (see [withEmojiKey]): on every page
 * but a keypad, whose field takes digits, and none at all while the key is gone, as a key of no width cannot be laid
 * out.
 */
internal fun emojiKeyRows(layout: KeyboardLayout, look: KeyLook): List<List<Key>> =
    if (layout.page.keypad || look.emojiKey <= 0f) {
        layout.rows
    } else {
        layout.rows.dropLast(1) + listOf(withEmojiKey(layout.rows.last(), look.emojiKey))
    }

/** How much of the smile over the comma shows: none with the emoji key, which opens the panel in its place. */
internal fun emojiHintAlpha(look: KeyLook): Float = 1f - look.emojiKey
