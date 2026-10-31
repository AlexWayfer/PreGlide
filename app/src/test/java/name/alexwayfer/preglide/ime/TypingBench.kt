package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.layout.Language

/**
 * A [field] holding [text] at first, typed into the way the keyboard types, with [learner] following it, and what it
 * learns and takes back written down (see [Recorder]); [learns] false for a teacher that learns nothing. [language] is
 * the keyboard's as the user types.
 */
internal class TypingBench(text: String = "", learns: Boolean = true) {
    /** "+word after the words before", "-word", "~word after the words before", as [Recorder] writes them. */
    val events = mutableListOf<String>()

    /** "+word" and "-word" for a word that began the field. */
    val starts = mutableListOf<String>()

    var language = Language.Russian
    val field = Typist(text)
    val learner = TypingLearner(field, Recorder(learns)) { language }

    /** A field with a cursor, edited the way the keyboard edits it, telling the learner as the keyboard does. */
    inner class Typist(var text: String) : SuggestionField {
        var cursor = text.length
        override val inputType = InputType.TYPE_CLASS_TEXT
        override val imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.substring(0, cursor).takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = text.substring(cursor).take(length)

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.substring(0, cursor - deleteBefore) + text + this.text.substring(cursor + deleteAfter)
            cursor += text.length - deleteBefore
        }

        fun type(typed: String) {
            for (char in typed) {
                if (goesInWord(char) { text.substring(0, cursor) }) {
                    learner.beforeEdit(cursor)
                } else {
                    learner.beforeSeparator(char)
                }
                replace(0, char.toString())
                learner.updated(cursor, ours = true)
            }
        }

        fun backspace(times: Int = 1) = repeat(times) {
            learner.beforeEdit(cursor)
            replace(1, "")
            learner.updated(cursor, ours = true)
        }

        fun choose(word: String, kept: Boolean = false) {
            learner.chose(word, cursor, kept)
            val edit = pick(text.substring(0, cursor), word)
            replace(edit.deleteBefore, edit.text)
            learner.updated(cursor, ours = true)
        }

        fun moveTo(position: Int) {
            cursor = position
            learner.updated(cursor, ours = false)
        }

        /** The app clears the field, as after sending a message with its own button. */
        fun clear() {
            text = ""
            moveTo(0)
        }
    }

    /** Glides [word] in at the cursor as the keyboard does, a space before it after a letter. */
    fun glide(word: String): Int {
        val start = learner.glided(word, field.cursor)
        val spaced = field.text.substring(0, field.cursor).lastOrNull()?.isLetter() == true
        field.replace(0, (if (spaced) " " else "") + "$word ")
        learner.updated(field.cursor, ours = true)
        return start
    }

    /**
     * Writes down what is learned and taken back: "+word after the words before", "-word", "~word after the words
     * before", "!" after a word kept as typed, and "in en" after a word of another language than Russian; and apart,
     * in [starts], "+word" and "-word" for a word that began the field.
     */
    private inner class Recorder(private val learns: Boolean) : Teacher {
        override fun learn(
            word: String,
            context: List<String>,
            language: Language,
            kept: Boolean,
            beganField: Boolean,
        ): LearnedUse? {
            if (!learns) return null
            events += "+$word${mark(kept)}${tag(language)}${after(context)}"
            if (beganField) starts += "+$word"
            return LearnedUse(word, context, language, kept, beganField)
        }

        override fun unlearn(use: LearnedUse) {
            events += "-${use.spelling}${mark(use.kept)}${tag(use.language)}${after(use.context)}"
            if (use.beganField) starts += "-${use.spelling}"
        }

        override fun learnNext(word: String, context: List<String>, language: Language) {
            events += "~$word${tag(language)}${after(context)}"
        }

        private fun after(context: List<String>) = if (context.isEmpty()) "" else " after ${context.joinToString(" ")}"

        private fun mark(kept: Boolean) = if (kept) "!" else ""

        private fun tag(language: Language) = if (language == Language.Russian) "" else " in ${language.tag}"
    }
}
