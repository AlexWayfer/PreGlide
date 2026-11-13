package name.alexwayfer.preglide.engine.suggest

/** Shift stayed down for the second letter. Every other character must already match exactly. */
fun fixesSecondCapital(typed: String, word: String): Boolean =
    typed.length == word.length && startsWithTwoCapitals(typed) && typed[0] == word[0] &&
        word[1].isLowerCase() && typed[1].lowercaseChar() == word[1] &&
        typed.regionMatches(2, word, 2, typed.length - 2)

private fun startsWithTwoCapitals(word: String): Boolean =
    word.length >= 2 && word[0].isUpperCase() && word[1].isUpperCase()

/** The known spelling of a whole word like WOkege, even if more frequent completions fill the strip. */
fun secondCapitalSuggestion(typed: String, model: LanguageModel): String? {
    if (!startsWithTwoCapitals(typed) || isInCaps(typed)) return null
    val spelling = model.personal.learned(typed)?.spelling ?: model.dictionary.spellingOf(typed)
    return spelling?.let { casedLike(it, typed) }?.takeIf {
        fixesSecondCapital(typed, it) && !model.personal.isRemoved(it) && model.dictionary.find(typed) == null
    }
}
