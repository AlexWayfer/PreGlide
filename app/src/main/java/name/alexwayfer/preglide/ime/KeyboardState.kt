package name.alexwayfer.preglide.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page

class KeyboardState {
    /**
     * The language of the letters. Switching it shows them and turns off caps lock.
     * A keypad stays, as its field takes no letters.
     */
    var language: Language
        get() = shownLanguage
        set(value) {
            if (value != shownLanguage) {
                if (!page.keypad) page = Page.Letters
                if (shift == ShiftState.Locked) shift = ShiftState.Off
                languageSwiped = swipingLanguage
                swipingLanguage = null
            }
            shownLanguage = value
        }
    private var shownLanguage by mutableStateOf(Language.Russian)

    /**
     * Which way a swipe along the space bar is switching the language, true for forward, while it has not switched
     * yet; null otherwise. The next language set takes it as [languageSwiped].
     */
    var swipingLanguage: Boolean? = null

    /**
     * Which way the swipe that brought the [language] went, true for forward, so the keys follow the finger; null when
     * it came otherwise, as from the system's picker or a field.
     */
    var languageSwiped by mutableStateOf<Boolean?>(null)
        private set

    /** Whether the user enabled another language to switch to, so the space bar's arrows show a swipe switches it. */
    var otherLanguages by mutableStateOf(false)

    var page: Page
        get() = shownPage
        set(value) {
            shownPage = value
            if (value == Page.Letters) symbolTyped = false
        }
    private var shownPage by mutableStateOf(Page.Letters)

    /** Whether a char was typed since the keyboard left the letters: the next space then returns to them. */
    private var symbolTyped = false

    var shift by mutableStateOf(ShiftState.Off)

    /** The words to finish the one being typed, the best first. */
    var suggestions by mutableStateOf(emptyList<String>())

    /**
     * The [suggestions] put on the start of the word being typed up to its last hyphen, as "помаленьку" on
     * "потихоньку-", which the strip shows after the hyphen (see [shownInStrip]); null when the word has no hyphen.
     */
    var gluedWords by mutableStateOf<GluedWords?>(null)

    /**
     * Whether the [suggestions] are the words the user often begins a field with, in an empty field: what the user
     * copied a moment ago goes before them.
     */
    var fieldStart by mutableStateOf(false)

    /**
     * The word being typed, in the user's or dictionary's spelling: the strip reserves it a slot even when known,
     * and picking it keeps it so, as the user's own word. Null otherwise.
     */
    var typedWord by mutableStateOf<String?>(null)

    /** Whether [typedWord] is known and not removed, so holding it can remove it from suggestions. */
    var typedWordRemovable by mutableStateOf(false)

    /**
     * The suggestion autocorrect puts in place of the word being typed when the user ends it, shown first and
     * marked; null when the word stays as typed.
     */
    var correction by mutableStateOf<String?>(null)

    /** The emoji the strip offers for the word right before the cursor, in its right slot (see [emojiForStrip]). */
    var stripEmoji by mutableStateOf(emptyList<String>())

    /**
     * The word a glide typed, while the strip offers the glide's other words in its place: marked among them as the
     * word in the text, so a tap reads as a swap. Null otherwise.
     */
    var glidedWord by mutableStateOf<String?>(null)

    /**
     * Counts the times the strip turned to the words likely after a glided word (see [GlideStrip]) or a word picked
     * from it (see [PickTurn]): they slide in from the right, onward, as the text goes on, so they read apart from the
     * words that were there for the word before.
     */
    var nextWordsTurns by mutableIntStateOf(0)

    /** The suggestion the user long pressed to remove, while the strip asks to confirm it; null otherwise. */
    var removing by mutableStateOf<String?>(null)

    /** Whether the field takes glided words: one with suggestions, as a password field has none. */
    var fieldGlides by mutableStateOf(false)

    /**
     * Whether a swipe from backspace selects words to erase: the strip stays as it was until the finger lifts, rather
     * than empty with every word the swipe takes.
     */
    var selectingWords by mutableStateOf(false)

    /** Whether the language's dictionary is still loading, so the strip has nothing to suggest yet. */
    var loading by mutableStateOf(false)

    /** What the user copied a moment ago and has not pasted from the strip yet; null otherwise. */
    var clip by mutableStateOf<OfferedClip?>(null)

    /** The types of content the field takes besides text, as `EditorInfo.contentMimeTypes` gives them. */
    var fieldContentTypes by mutableStateOf(emptyList<String>())

    /** Whether the clipboard's panel shows in place of the keys. */
    var clipboardOpen by mutableStateOf(false)

    /** Whether the strip asks to confirm clearing the clipboard's history. */
    var clearing by mutableStateOf(false)

    /** What the user copied in the last hour, the latest first, for the clipboard's panel. */
    var history by mutableStateOf(emptyList<HistoryItem>())

    /** The panel's copy the user long pressed to remove, while it asks to confirm; null otherwise. */
    var removingClip by mutableStateOf<SavedClip?>(null)

    /** The emoji panel and its search. */
    val emoji = EmojiState()

    /** When the keyboard showed, and whether the user typed since. */
    val showing = KeyboardShowing()

    /** What a key with [char] types now. Shift applies to letters only: on a symbols page π stays π. */
    fun typed(char: Char): Char = if (shift != ShiftState.Off && page == Page.Letters) char.uppercaseChar() else char

    /**
     * Notes that a key typed [char]. On a symbols page a space or a line break after a typed char returns to the
     * letters, as after "100% " or "(да)\n"; one typed before any char keeps the page. A keypad is the field's whole
     * keyboard and stays.
     */
    fun afterTyping(char: Char) {
        when {
            page == Page.Letters || page.keypad -> Unit
            char != ' ' && char != '\n' -> symbolTyped = true
            symbolTyped -> page = Page.Letters
        }
    }

    /** Whether picking [word] keeps the word being typed as it is: see [typedWord]. */
    fun keepsTyped(word: String): Boolean = word == typedWord
}

/** What the strip above the keys shows. */
sealed interface StripContent {
    data object Suggestions : StripContent

    /** The dictionary is loading: the strip says so rather than stay blank. */
    data object Loading : StripContent

    /** Whether to remove [word] from suggestions. */
    data class Removing(val word: String) : StripContent

    /**
     * What the user copied, to paste, with the button of the clipboard's panel: in a strip with no word to show, as
     * at the start of a field.
     */
    data class Clip(val clip: OfferedClip) : StripContent

    /** No word to show: the button of the clipboard's panel alone. */
    data object Empty : StripContent

    /** The clipboard's panel shows in place of the keys: the way back to them, and the button to clear it. */
    data object Clipboard : StripContent

    /** Whether to clear the clipboard's history. */
    data object Clearing : StripContent

    /** The emoji panel shows in place of the keys: the way back to them, the search, and backspace. */
    data object Emoji : StripContent

    /** The emoji search shows over the keys: the way back to the panel, the words typed, and what they find. */
    data object EmojiSearch : StripContent
}

/**
 * What the strip shows for [state]: the question to remove a word over all, then the clipboard's panel's own row,
 * or the emoji panel's or its search's; then, with no word to show, what the user copied, if the field takes it, or
 * the loading, or the clipboard's button alone; then the words. A clip goes before the words an empty field offers
 * to begin it with too: right after a copy, pasting is likelier than any first word.
 */
fun stripContent(state: KeyboardState): StripContent {
    val removing = state.removing
    val empty = showsNoWord(state)
    val clip = state.clip?.takeIf { (empty || state.fieldStart) && takesClip(it.clip, state.fieldContentTypes) }
    return when {
        removing != null -> StripContent.Removing(removing)
        state.clipboardOpen -> if (state.clearing) StripContent.Clearing else StripContent.Clipboard
        state.emoji.open -> if (state.emoji.searching) StripContent.EmojiSearch else StripContent.Emoji
        clip != null -> StripContent.Clip(clip)
        state.loading && state.suggestions.isEmpty() -> StripContent.Loading
        empty -> StripContent.Empty
        else -> StripContent.Suggestions
    }
}

/** Whether the strip has nothing for the text typed: no word, as typed or suggested, and no emoji for it. */
private fun showsNoWord(state: KeyboardState): Boolean =
    state.suggestions.isEmpty() && state.typedWord == null && state.stripEmoji.isEmpty()

/**
 * The row of the strip that shows [content]:one per kind, but for a clip, which shows in the row of the clipboard's
 * button with no clip, so the button stays as the clip comes and goes.
 */
fun stripRow(content: StripContent): Any =
    if (content is StripContent.Clip) StripContent.Empty::class else content::class

/** What shows below the strip: the keys, or a panel in their place. */
enum class KeysPanel { Keys, Clipboard, Emoji }

/** What shows below the strip for [state]: the emoji search types on the keys, so they show with it. */
fun shownPanel(state: KeyboardState): KeysPanel = when {
    state.clipboardOpen -> KeysPanel.Clipboard
    state.emoji.open && !state.emoji.searching -> KeysPanel.Emoji
    else -> KeysPanel.Keys
}

enum class ShiftState {
    Off,

    /** Capitalizes the next letter only. */
    Once,

    /** Caps lock. */
    Locked,
    ;

    fun next(): ShiftState = when (this) {
        Off -> Once
        Once -> Locked
        Locked -> Off
    }
}
