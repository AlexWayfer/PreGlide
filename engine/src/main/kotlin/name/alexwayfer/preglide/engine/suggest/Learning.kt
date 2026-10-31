package name.alexwayfer.preglide.engine.suggest

/**
 * Whether [word], a word the user finished, is worth learning. A single letter is a word only when the dictionary
 * of [model] has it, as "и" or "я"; any other is part of an abbreviation, as "т" and "е" of "т.е.", a smiley, as
 * "D" of ":D", or an initial.
 */
fun isLearnable(word: String, model: LanguageModel): Boolean =
    word.length > 1 || model.dictionary.spellingOf(word) != null

/**
 * How to keep [typed], a word the user finished, so it is suggested in its usual case: the user's spelling of a
 * dictionary word takes priority over the dictionary's. Otherwise, within a sentence, as typed, which keeps
 * a name's capital. At a [sentenceStart] the capital tells nothing: the spelling the user taught before stays, and
 * a new word loses the capital, unless it is in caps all through, as an abbreviation. Nor do caps on a word the user
 * taught before, as "КРИНЖ" for emphasis: its spelling stays. An explicitly [kept] spelling overrides those choices,
 * except for a sentence's initial capital alone, which still tells nothing about the word's usual case.
 */
fun learnedSpelling(typed: String, sentenceStart: Boolean, model: LanguageModel, kept: Boolean = false): String {
    val dictionary = model.dictionary.spellingOf(typed)
    val taught = model.personal.learned(typed)?.spelling?.takeIf {
        sentenceStart || isInCaps(typed) || dictionary != null
    }
    val known = taught ?: dictionary
    return when {
        kept && (!sentenceStart || isInCaps(typed)) -> typed
        known != null -> known
        !sentenceStart || typed.drop(1).any { it.isUpperCase() } -> typed
        else -> typed.replaceFirstChar { it.lowercaseChar() }
    }
}

/**
 * The word to offer for [typed]: the user's spelling first, then the dictionary's, or the input when unknown.
 * Its case follows the input as suggestions do, so a sentence's capital and caps lock stay while "Ии" gets "ИИ".
 */
fun preferredSpelling(typed: String, model: LanguageModel): String {
    val spelling = model.personal.learned(typed)?.spelling ?: model.dictionary.spellingOf(typed) ?: typed
    return casedLike(spelling, typed)
}
