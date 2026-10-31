package name.alexwayfer.preglide.ime

import android.text.InputType
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.testDispatchers
import java.io.File

/**
 * The keyboard as the service puts it together, on a field that holds its text around the cursor, and
 * Android's side played by [output]; what it learns is kept in [folder]. Everything runs at once, on the caller's
 * thread, but for what the field session waits out, in [sessionScope].
 */
internal class KeyboardBench(
    sessionScope: CoroutineScope = CoroutineScope(testDispatchers.now),
    folder: () -> File,
) {
    /**
     * A field that holds its text before the cursor and [after] it, none unless a test puts some there, which asks
     * for corrections unless told otherwise.
     */
    class Field : SuggestionField {
        var text = ""
        var after = ""
        override var inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        override var imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = after.take(length)

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
            after = after.drop(deleteAfter)
        }
    }

    /** Android's side: the delete key and line breaks go into the field, and the rest is written down. */
    inner class Output : KeyOutput {
        override var connected = true
        override val imeOptions: Int get() = this@KeyboardBench.field.imeOptions
        override val inputType: Int get() = this@KeyboardBench.field.inputType

        /** The field's own Enter actions run. */
        val actions = mutableListOf<Int>()

        /** How many times the list of keyboards showed. */
        var picked = 0

        override fun delete() {
            field.text = field.text.dropLast(1)
        }

        override fun newLine() {
            field.text += "\n"
        }

        override fun editorAction(action: Int) {
            actions += action
        }

        override fun pickKeyboard() {
            picked++
        }

        // As a field that asks for sentences' capitals: at its start, and after a sentence's end and a space.
        override fun wantsCapital(): Boolean? =
            if (connected) field.text.isEmpty() || field.text.endsWith(". ") else null

        override fun textBeforeDelete(): CharSequence? = if (connected) field.text.takeLast(2) else null
    }

    val field = Field()
    val output = Output()
    private val now = testDispatchers.now
    private val personal = PersonalData(PersonalStore { File(folder(), "personal") }, CoroutineScope(now), now)

    // Lazy: the words load from the folder, which a test's rule makes only once the test starts.
    val model by lazy {
        val words = sequenceOf("привет\t100000", "приват\t5000", "как\t50000", "ну\t40000")
        LanguageModel(Dictionary.parse(words), personal = personal.load(personalName(Language.Russian)))
    }
    private val models = LanguageModels(CoroutineScope(now), now) { model }
    val keyboard = KeyboardState()

    /** The lines logged about typing. */
    val logged = mutableListOf<String>()
    val suggestions = WordSuggestions(
        keyboard,
        field,
        KeyboardWork(CoroutineScope(now), now),
        WordSources(models),
        corrects = { true },
        TypingLines(line = { logged += it }),
    )
    val learning = Learning(
        field,
        language = { Language.Russian },
        models = { suggestions.model(it) },
        emails = { null },
        personal = personal,
        clock = { 42 },
    )
    private val removal = WordRemoval(
        field,
        language = { Language.Russian },
        models = { suggestions.model(it) },
        emails = { null },
        personal = personal,
        clock = { 42 },
    )
    /** Whether a backspace through the space or mark after a glided word brings its words back. */
    var bringsBackGlides = true

    /** What the strip offers right after a glide: the glide's words, unless a test says otherwise. */
    var glideStrip = GlideStrip.GlideWords

    val glideTyping: GlideTyping = GlideTyping(
        keyboard,
        suggestions,
        learning,
        GlideContexts(CoroutineScope(now), now),
        GlideOptions(erasesWord = { true }, bringsBackWords = { bringsBackGlides }, strip = { glideStrip }),
        // Android's log does not run on the JVM.
        GlideHooks(log = {}, afterGlide = { typing.autoCapitalize() }),
    )
    val typing = KeyTyping(keyboard, suggestions, learning, glideTyping, removal, output)
    val emojiChoice = EmojiChoice(keyboard, field, suggestions, learning, glideTyping) { typing.autoCapitalize() }
    val session = FieldSession(keyboard, suggestions, learning, glideTyping, typing::autoCapitalize, sessionScope)

    /** Presses the key of [action], by a [longPress] or not, and reports the edit back as the field does. */
    fun press(action: KeyAction, longPress: Boolean = false) {
        typing.key(action, longPress, tap = null)
        reported()
    }

    /** Types [text] a key at a time: its letters in lowercase, as on the keys, which shift capitalizes. */
    fun type(text: String) {
        for (char in text) press(if (char == ' ') KeyAction.Space else KeyAction.Letter(char.lowercaseChar()))
    }

    /** Glides [word] in. */
    fun glide(word: String) {
        glideTyping.glided(glideThrough(word))
        reported()
    }

    /** The field tells where its cursor is now, after an edit or a move. */
    fun reported() {
        session.selectionChanged(field.text.length, field.text.length)
    }
}
