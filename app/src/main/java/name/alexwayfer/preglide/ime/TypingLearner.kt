package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.AFTER_NUMBER
import name.alexwayfer.preglide.engine.suggest.LONGEST_CONTEXT
import name.alexwayfer.preglide.engine.suggest.beginsField
import name.alexwayfer.preglide.engine.suggest.currentWord
import name.alexwayfer.preglide.engine.suggest.isNumberEndingWord
import name.alexwayfer.preglide.engine.suggest.nextWord
import name.alexwayfer.preglide.engine.suggest.wordContext
import name.alexwayfer.preglide.engine.suggest.wordRest
import java.util.Locale

/** How much text around the cursor to read for the word there and the word before it. */
private const val TEXT_AROUND = 64

/** The chars but letters and digits a name the app completes a word into may have, as "pepe_w" or "@user-name". */
private const val NAME_CHARS = "_-'"

/**
 * What one learning of a word was: the spelling kept, the words of the sentence before it, the nearest last, the
 * language it went to, whether the user [kept] the word as typed on purpose, and whether it [beganField].
 */
data class LearnedUse(
    val spelling: String,
    val context: List<String>,
    val language: Language,
    val kept: Boolean = false,
    val beganField: Boolean = false,
)

/** What [TypingLearner] learns and takes back. */
interface Teacher {
    /**
     * Learns [word] of [language] and that it came after [context], the words of the sentence before it, the nearest
     * last, [kept] as typed on purpose or not, and whether it [beganField], the first word of the field's text; tells
     * how, or null when it learned nothing.
     */
    fun learn(word: String, context: List<String>, language: Language, kept: Boolean, beganField: Boolean): LearnedUse?

    /** Takes back what [use] learned: the word and what it came after. */
    fun unlearn(use: LearnedUse)

    /**
     * Learns only that [word] of [language] came after [context], whose last word the word before it was changed
     * into.
     */
    fun learnNext(word: String, context: List<String>, language: Language)
}


/**
 * Follows the words the user types and changes in [field], and has [teacher] learn each once the user is done
 * with it: when a separator follows it, a suggestion replaces it, the cursor leaves it, or the field does.
 * [language] is the keyboard's.
 *
 * Changing the word typed last, while only spaces follow it, is a correction: the word it was is unlearned and the
 * word it became learned, so a typo fixed at once or a suggestion edited after it was chosen leaves only the word
 * as it ended up. A word after it erased whole, with the erasing gone on before it, leaves it the word typed last
 * again, so erasing back to a typo and fixing it is a correction too. Once a mark follows the word, or the next
 * word is learned, a change is the user changing their mind: the word it was stays learned with its pairs, and the
 * new word is learned too, with a pair with the word after it. Erasing learns nothing and takes nothing back: the
 * user did type the words. The one exception is a
 * suggestion chosen last and erased whole right away, a choice taken back, as after a tap on the wrong one. A word
 * left as it was is not learned again, but one erased whole and typed again is.
 */
class TypingLearner(
    private val field: SuggestionField,
    private val teacher: Teacher,
    private val language: () -> Language,
) {
    private var editing: Editing? = null

    private val trail = LearnedTrail()

    /** The word learned last before a mark cleared the trail: autocorrect's word right before the mark, if any. */
    private var beforeMark: Placed? = null

    /**
     * Before the user's letter or backspace at [cursor]: the word there is being typed or changed, and a letter goes
     * in typed in the keyboard's language.
     */
    fun beforeEdit(cursor: Int) {
        val current = editing
        if (current == null || cursor !in current.start..current.start + current.word.length) {
            val before = field.textBeforeCursor(TEXT_AROUND) ?: ""
            val after = field.textAfterCursor(TEXT_AROUND) ?: ""
            if (current?.appCompleted(cursor, before) == true) editing = null else finishWord()
            val head = currentWord(before)
            editing = editingFrom(trail.last, cursor - head.length, head + wordRest(after), before, after)
        }
        editing?.typedIn = language()
    }

    /** After the text or the cursor changed, the cursor now at [cursor]; [ours] for the keyboard's own edit. */
    fun updated(cursor: Int, ours: Boolean) {
        val current = editing ?: return
        val before = field.textBeforeCursor(TEXT_AROUND) ?: ""
        val head = currentWord(before)
        val inWord = cursor - head.length == current.start
        val word = head + wordRest(field.textAfterCursor(TEXT_AROUND) ?: "")
        when {
            inWord && ours -> {
                current.change(word)
                if (word.isEmpty()) current.erased = true
                // A suggestion chosen last and erased whole is taken back at once: typed again, the word is learned
                // anew, and a word typed in its place learned as the word there.
                val choice = current.correcting?.takeIf { current.erased && it.chose }
                if (choice != null) {
                    teacher.unlearn(choice.use)
                    current.correcting = null
                    trail.remove(choice)
                }
            }
            // The user put the cursor elsewhere in the word, as to fix a letter or put in a missed space: it is
            // still being typed.
            inWord && word == current.word -> Unit
            // The cursor moved away, the app changed the text, as when it sends and clears a message, or the
            // word was erased and the erasing went on before it: the word is done as it last was, unless the app
            // completed it.
            !ours && current.appCompleted(cursor, before) -> editing = null
            else -> finishWord()
        }
    }

    /**
     * Before [separator] is typed at the cursor: the word before the cursor is done, without what follows it.
     * Any separator but a space puts the word past correcting, but for autocorrect's taken back (see [reverted]).
     */
    fun beforeSeparator(separator: Char) {
        editing?.change(currentWord(field.textBeforeCursor(TEXT_AROUND) ?: ""))
        finishWord()
        beforeMark = null
        if (separator != ' ') {
            beforeMark = trail.last
            trail.clear()
        }
    }

    /**
     * The user chose [word], for the word being typed or as the next word, with the cursor at [cursor]; [kept] when
     * it is the word as typed, which the user keeps on purpose. The letters it puts in are of the keyboard's language,
     * as the suggestions are.
     */
    fun chose(word: String, cursor: Int, kept: Boolean = false) {
        val before = field.textBeforeCursor(TEXT_AROUND) ?: ""
        // With no word being changed, the choice goes after a space, or in place of the word the cursor came back to
        // the end of, as after erasing the space after it.
        val current = editing ?: run {
            val head = currentWord(before)
            editingFrom(trail.last, cursor - head.length, head, before, field.textAfterCursor(TEXT_AROUND) ?: "")
        } ?: return
        // Right after a mark, the choice goes a space after it, as [pick] puts it.
        if (choiceNeedsSpace(before)) current.start++
        editing = null
        current.choose(word, language())
        finish(current, kept, chose = true)
    }

    /**
     * The user glided [word] in at [cursor], a word of its own: the word being typed there, if any, ends as it is,
     * and a space goes between them, as [glideEdit] puts it. After a word that a hyphen or an apostrophe ends, the
     * glide goes on it instead, and the two are learned as one word, as "ИИ-вставка" (see [gluedPrefix]). Returns
     * where the word learned starts, to tell it by later.
     */
    fun glided(word: String, cursor: Int): Int {
        val before = field.textBeforeCursor(TEXT_AROUND) ?: ""
        val glued = gluedPrefix(before)
        // The start of the word the glide goes on is no word of its own; any other word typed there ends as it is.
        val joined = editing?.takeIf { glued.isNotEmpty() && it.start == cursor - glued.length }
        if (joined == null) {
            editing?.change(currentWord(before))
            finishWord()
        }
        editing = null
        val spaced = if (glideNeedsSpace(before)) "$before " else before
        val start = cursor + spaced.length - before.length - glued.length
        val current = joined ?: editingFrom(trail.last, start, glued, spaced, "") ?: return start
        current.change(glued + word, language())
        finish(current, chose = true, glued = glued)
        return current.start
    }

    /**
     * The user took back the word glided last, at [start], with nothing typed after it: erased it whole, or swapped
     * [instead] in for it. The glided word is unlearned, and [instead] learned in its place as chosen, on the start
     * of the word the glide went on, if any. Nothing changes when the words learned do not end with the glided one.
     */
    fun tookBackGlide(start: Int, instead: String?) {
        val glide = trail.takeLastChoice { it.start == start } ?: return
        teacher.unlearn(glide.use)
        if (instead != null) {
            val place = WordPlace(glide.use.context, glide.use.beganField, next = null)
            val swapped = Editing(start, "", place, original = "", correcting = null)
            swapped.change(glide.glued + instead, glide.use.language)
            finish(swapped, chose = true, glued = glide.glued)
        }
    }

    /**
     * The keyboard put back [typed], the word as the user typed it, in place of the word autocorrect chose for it,
     * which is the word typed last, with a space or a mark after it: the correction is taken back, and [typed] is
     * being typed again, to be learned as kept on purpose once the user ends it as it is.
     */
    fun reverted(typed: String) {
        val correction = trail.takeLastChoice { true } ?: beforeMark?.takeIf { it.chose } ?: return
        beforeMark = null
        teacher.unlearn(correction.use)
        // Being typed again: the user may still change it, and only ending it as it is keeps it.
        editing = with(correction.use) {
            val place = WordPlace(context, beganField, next = null)
            Editing(correction.start, typed, place, typed, null, revertedFrom = correction.use.language)
        }
    }

    /**
     * The user selects words before the cursor to erase them, the word being typed with them: it is not learned,
     * and a word typed there next is followed anew.
     */
    fun beforeSelecting() {
        editing = null
    }

    /** The user left the field: the word being typed is done, and no word here can be corrected anymore. */
    fun finishField() {
        finishWord()
        trail.clear()
    }

    /** The word being typed is done as it last was. */
    fun finishWord() {
        val current = editing ?: return
        editing = null
        finish(current)
    }

    /**
     * The word [current] was ends up as it reads now, [kept] as typed on purpose or not. Erased, or left as it was,
     * nothing changes, but erased and typed the same again, it is learned once more. A correction of the word typed
     * last takes it back; a change of a word typed before also pairs the new word with the word after it. A word
     * kept is learned as kept even left as it was, in place of what was learned of it when it is the word typed
     * last; and a correction of a kept word keeps the purpose, as the user fixed what they meant to keep. [chose]
     * when the user chose the word from the suggestions, and [glued] the start of it typed before a glide went on it.
     * A word put back in place of autocorrect's and ended as it was is kept too. The word is learned in the language
     * its letters were typed in; typed in two, it is learned in none, and only the correction is taken back.
     */
    private fun finish(current: Editing, kept: Boolean = false, chose: Boolean = false, glued: String = "") {
        // A hyphen or an apostrophe left at the end, as of "топ-" before a digit, is no part of the word, and a
        // number's ending is no word.
        val done = current.word.trimEnd('-', '\'').takeIf { it.any(Char::isLetter) && !current.isNumberEnding(it) }
        val language = current.language
        val same = done.equals(current.original, ignoreCase = true)
        val keep = kept || same && current.reverted
        if (same && !current.erased && !keep) return
        // Erased, the word is gone and takes nothing back.
        val correction = current.correcting?.use?.takeUnless { same && !keep }?.takeIf { done != null }
        correction?.let(teacher::unlearn)
        if (done == null || language == null) {
            // Typed in two languages, the word is no word of either, and with no letter typed now, of none known.
            // Either way, or erased, the word before it is the last again.
            current.correcting?.let(trail::remove)
            return
        }
        current.pairedAfter(done)?.let { teacher.learnNext(it, current.place.context + done, language) }
        val learnedKept = keep || correction?.kept == true
        val use = teacher.learn(done, current.place.context, language, learnedKept, current.place.beganField)
        if (use != null) trail.place(Placed(current.start, use, chose, glued))
    }
}

/**
 * Where a word sits in the text: the words of the sentence before it, the nearest last, whether it [beganField],
 * the word [next] after it, and whether a colon goes right before it, as one that asks a chat for an emote.
 */
private class WordPlace(
    val context: List<String>,
    val beganField: Boolean,
    val next: String?,
    val afterColon: Boolean = false,
)

/**
 * The word being typed or changed: where it starts in the text, how it reads as the change begins, its [place], how
 * it read when the change began, what was learned of it when it is the word typed last, and whether it was [erased]
 * whole since.
 */
private class Editing(
    var start: Int,
    word: String,
    val place: WordPlace,
    val original: String,
    var correcting: Placed?,
    /**
     * The language of autocorrect's word the word was put back as typed in place of, the language it was typed in;
     * null for none. Put back so, it is kept on purpose, if the user ends it so.
     */
    private val revertedFrom: Language? = null,
) {
    var erased = false

    /** Whether the word was put back as typed in place of autocorrect's word. */
    val reverted: Boolean get() = revertedFrom != null

    /** How the word reads now. */
    var word = word
        private set

    /**
     * The language each letter of [word] was typed in, null for one typed in a language not known: the letters it
     * has as the change begins are of the learned word it is, if any.
     */
    private var languages = List(word.length) { correcting?.use?.language ?: revertedFrom }

    /** The keyboard's language as the user's latest edit began: the letters the edit puts in are typed in it. */
    var typedIn: Language? = null

    /** The one language the letters of the word were typed in, or null (see [soleLanguage]). */
    val language: Language? get() = soleLanguage(word, languages)

    /** The word reads [word] now, the letters that went in typed in [typedIn]. */
    fun change(word: String, typedIn: Language? = this.typedIn) {
        languages = retyped(this.word, languages, word, typedIn)
        this.word = word
    }

    /** A suggestion explicitly chosen belongs to its keyboard language, including an unchanged pasted word. */
    fun choose(word: String, language: Language) {
        languages = List(word.length) { language }
        this.word = word
    }

    /**
     * The word after this one, to pair with [done], the word it ends up as, when it is a change of a word typed
     * before, not of the word typed last; null otherwise.
     */
    /** Whether [done], the word this one ends up as, is the ending of the number before it, as "го" of "2-го". */
    fun isNumberEnding(done: String): Boolean = place.context == listOf(AFTER_NUMBER) && isNumberEndingWord(done)

    fun pairedAfter(done: String): String? =
        place.next.takeIf { correcting == null && original.isNotEmpty() && !done.equals(original, ignoreCase = true) }

    /**
     * Whether the app completed the word into a longer one, with the cursor at [cursor] after [before] now, as a chat
     * completes the name of an emote or a user (see [completedByApp]). The user did not type that word, and the part
     * of it they typed is no word: it is not learned.
     */
    fun appCompleted(cursor: Int, before: CharSequence): Boolean =
        completedByApp(word, start, cursor, before, place.afterColon)
}

/**
 * The word from [start] that reads [word] now, between [before] and [after], the text around the cursor in it, as
 * it begins to change; a correction of [last], the word learned last, when it is that word in its place, or that
 * word with a hyphen glued on, as "слово-" goes on to "слово-то" (see [goesOn]). Right after a number and a hyphen
 * it comes after a number (see [wordContext]), unless it ends up as an ending, as "го" of "2-го", which is no word
 * to learn.
 */
private fun editingFrom(last: Placed?, start: Int, word: String, before: CharSequence, after: CharSequence): Editing? {
    val correcting = last?.takeIf { it.start == start && goesOn(word, it.use.spelling) }
    val context = wordContext(before, LONGEST_CONTEXT)
    val afterColon = before.dropLast(currentWord(before).length).lastOrNull() == ':'
    val place = WordPlace(context, beginsField(before, start), nextWord(after), afterColon)
    return Editing(start, word, place, original = word, correcting)
}

/**
 * The languages the letters of [word] were typed in, when it read [was] before, its letters typed in [languages]:
 * what the two share at the start and at the end keeps its languages, and what went in between was typed in
 * [typedIn].
 */
internal fun retyped(was: String, languages: List<Language?>, word: String, typedIn: Language?): List<Language?> {
    val head = was.commonPrefixWith(word).length
    val tail = minOf(was.commonSuffixWith(word).length, was.length - head, word.length - head)
    return languages.take(head) + List(word.length - head - tail) { typedIn } + languages.takeLast(tail)
}

/**
 * The one language the letters of [word] were typed in, by [languages], a letter's null for a language not known;
 * null when they were typed in two, or in none known. A digit, a hyphen and an apostrophe do not count: they are the
 * same in either, as in "топ-3".
 */
internal fun soleLanguage(word: String, languages: List<Language?>): Language? =
    word.indices.filter { word[it].isLetter() }.mapNotNull { languages[it] }.distinct().singleOrNull()

private fun keyOf(word: String): String = word.lowercase(Locale.ROOT)

/**
 * Whether [word], in the place of [learned], is that word, in any case, or goes on from it after a hyphen, as
 * "слово-т" from "слово": the learned word is then only the start of the word.
 */
internal fun goesOn(word: String, learned: String): Boolean =
    keyOf(word) == keyOf(learned) || keyOf(word).startsWith(keyOf(learned) + "-")

/**
 * Whether the app completed [typed], the word being typed from [start], into a longer one: [before], the text before
 * the cursor at [cursor], has a longer name at [start], of letters, digits, underscores, hyphens and apostrophes, that
 * holds [typed], in any case, as "PE" becomes "PepeLaugh" when a chat completes an emote's name, or "shr" "catShrug"
 * when it finds the emote by any part of its name. [afterColon] when a colon goes right before the word: a chat puts
 * the name in place of the colon too. A mark after the word is no part of a name.
 */
internal fun completedByApp(
    typed: String,
    start: Int,
    cursor: Int,
    before: CharSequence,
    afterColon: Boolean = false,
): Boolean {
    val offset = before.length - (cursor - start) - if (afterColon) 1 else 0
    if (typed.isEmpty() || offset !in before.indices) return false
    val now = before.subSequence(offset, before.length).takeWhile { it.isLetterOrDigit() || it in NAME_CHARS }
    return now.length > typed.length && now.contains(typed, ignoreCase = true)
}
