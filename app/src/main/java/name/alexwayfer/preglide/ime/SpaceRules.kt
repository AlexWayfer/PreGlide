package name.alexwayfer.preglide.ime

/** What the space bar does besides typing a space, as the user chose. */
enum class SpaceGestures {
    /** A swipe along it switches the language, and a long press moves the cursor after the finger. */
    SwipeLanguage,

    /** A swipe along it moves the cursor, and a long press opens the system's keyboard picker. */
    SwipeCursor,

    /** It only types a space, and a long press opens the picker. */
    Off,
    ;

    companion object {
        /** The gestures saved under [name], or the default, the language on a swipe, when none or unknown. */
        fun parse(name: String?): SpaceGestures = entries.firstOrNull { it.name == name } ?: SwipeLanguage
    }
}

/** What a swipe along the space bar does. */
enum class SpaceSwipe { Language, Cursor }

/** What a swipe along the space bar does by [gestures] when a [cursor] is there to move, or null for nothing. */
fun spaceSwipe(gestures: SpaceGestures, cursor: Boolean): SpaceSwipe? = when (gestures) {
    SpaceGestures.SwipeLanguage -> SpaceSwipe.Language
    SpaceGestures.SwipeCursor -> SpaceSwipe.Cursor.takeIf { cursor }
    SpaceGestures.Off -> null
}

/**
 * Whether a long press of the space bar by [gestures] moves the cursor after the finger, when a [cursor] is there to
 * move, rather than opening the keyboard picker.
 */
fun holdMovesCursor(gestures: SpaceGestures, cursor: Boolean): Boolean =
    gestures == SpaceGestures.SwipeLanguage && cursor

/** What a long press of the space bar does: moves the cursor after the finger, or opens the keyboard picker. */
enum class SpaceHold { Cursor, Picker }

/** What a long press of [space] does: without its gestures, as on a key that has none, it opens the picker. */
fun spaceHold(space: SpaceBar?): SpaceHold = if (space?.holdCursor != null) SpaceHold.Cursor else SpaceHold.Picker

/** Whether a swipe [dx] pixels to the right goes forward through the languages: to the right it does. */
fun swipesForward(dx: Float): Boolean = dx > 0

/**
 * The language after [current] among the [enabled] ones, [forward] or back, round from the last to the first; the
 * first when [current] is none of them, and null when there is no other to switch to.
 */
fun <T> nextLanguage(enabled: List<T>, current: T?, forward: Boolean): T? {
    val index = enabled.indexOf(current)
    return when {
        enabled.size < 2 -> null
        index < 0 -> enabled.first()
        else -> enabled[(index + if (forward) 1 else -1).mod(enabled.size)]
    }
}

/**
 * Where a new language's name, [width] pixels wide, comes onto the space bar from, in pixels to the right: behind the
 * finger that swiped [forward], which went right, so from the left, as a page follows the finger; the old name leaves
 * the other way.
 */
fun languageEntryOffset(forward: Boolean, width: Int): Int = if (forward) -width else width

/**
 * Whether the space bar shows arrows beside the language's name, for a swipe that switches it by the user's
 * [gestures]: only with [otherLanguages] to switch to, and not on a [keypad], whose bar is too narrow for them.
 */
fun showsLanguageArrows(gestures: SpaceGestures?, otherLanguages: Boolean, keypad: Boolean): Boolean =
    gestures == SpaceGestures.SwipeLanguage && otherLanguages && !keypad

/**
 * How far each of the space bar's arrows, [arrowWidth] pixels wide, stands in from its end of the bar, [barWidth]
 * pixels wide, in pixels: right beside the language's name, [labelWidth] wide, and as the cursor moves, [toEdges] of
 * the way out to [edge] pixels from the ends. It never goes nearer the ends than that, as by a name too long.
 */
fun arrowInset(barWidth: Int, labelWidth: Int, arrowWidth: Int, edge: Int, toEdges: Float): Float {
    val beside = ((barWidth - labelWidth) / 2f - arrowWidth).coerceAtLeast(edge.toFloat())
    return beside + (edge - beside) * toEdges
}

/** Switches the keyboard's language, as a swipe along the space bar asks. */
fun interface LanguageSwitch {
    /** Switches to the next language [forward], or to the one before. Returns whether there was another one. */
    fun switch(forward: Boolean): Boolean
}

/**
 * Switches the language through [languages] and tells [state] which way the swipe went, so the language it brings
 * comes in from there; when there is no other language, nothing comes.
 */
class SwipedLanguageSwitch(private val state: KeyboardState, private val languages: LanguageSwitch) : LanguageSwitch {
    override fun switch(forward: Boolean): Boolean {
        state.swipingLanguage = forward
        val switched = languages.switch(forward)
        if (!switched) state.swipingLanguage = null
        return switched
    }
}

/**
 * What the space bar does besides typing a space: the user's [gestures], with the [cursor] in the field they move, if
 * any, and the [languages] they switch.
 */
data class SpaceBar(val gestures: SpaceGestures, val cursor: Cursor?, val languages: LanguageSwitch) {
    /** What a swipe along the bar does, if anything. */
    val swipe: SpaceSwipe? get() = spaceSwipe(gestures, cursor != null)

    /** The cursor a long press moves, or null when it opens the keyboard picker. */
    val holdCursor: Cursor? get() = cursor.takeIf { holdMovesCursor(gestures, cursor != null) }
}
