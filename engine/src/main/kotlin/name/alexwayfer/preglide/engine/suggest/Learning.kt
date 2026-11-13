package name.alexwayfer.preglide.engine.suggest

/**
 * Whether [word], a word the user finished, is worth learning. A single letter is a word only when the dictionary
 * of [model] has it, as "и" or "я"; any other is part of an abbreviation, as "т" and "е" of "т.е.", a smiley, as
 * "D" of ":D", or an initial.
 */
fun isLearnable(word: String, model: LanguageModel): Boolean =
    !hasProductiveParticle(word) && (word.length > 1 || model.dictionary.spellingOf(word) != null)

/**
 * How to keep [typed], a word the user finished, so it is suggested in its usual case: the user's spelling of a
 * dictionary word takes priority over the dictionary's. Otherwise, within a sentence, as typed, which keeps
 * a name's capital. A new word keeps its first spelling. A sentence's capital or caps for emphasis preserve a
 * spelling already seen, including [candidate]. A deliberate lowercase initial can teach a different spelling.
 */
fun learnedSpelling(
    typed: String,
    sentenceStart: Boolean,
    model: LanguageModel,
    kept: Boolean = false,
    candidate: String? = null,
): String {
    val dictionary = model.dictionary.spellingOf(typed)
    val taught = (model.personal.learned(typed)?.spelling ?: candidate)?.takeIf {
        sentenceStart && typed.firstOrNull()?.isUpperCase() == true || isInCaps(typed) || dictionary != null
    }
    val known = taught ?: dictionary
    model.personal.editedSpelling(typed)?.let { return it }
    return when {
        kept && (!sentenceStart || isInCaps(typed)) -> typed
        else -> known ?: typed
    }
}

/**
 * The word to offer for [typed]: the user's spelling first, then the dictionary's, or the input when unknown.
 * Its case follows the input as suggestions do, so a sentence's capital and caps lock stay while "Ии" gets "ИИ".
 */
fun preferredSpelling(typed: String, model: LanguageModel): String {
    val spelling = model.personal.learned(typed)?.spelling ?: model.dictionary.spellingOf(typed) ?: typed
    val preferred = casedLike(spelling, typed)
    return if (fixesSecondCapital(typed, preferred)) typed else preferred
}
