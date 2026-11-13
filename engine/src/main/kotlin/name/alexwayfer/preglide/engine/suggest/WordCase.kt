package name.alexwayfer.preglide.engine.suggest

/**
 * [word] in the case the user types it in: in caps when [typed] is in caps, with a capital first letter when
 * [typed] starts with one. Otherwise [word] keeps its own case, so a name stays capitalized; so it does when
 * [typed] is its own start, case and all, as "TD" of "TDLib", which only follows the word's capitals.
 */
fun casedLike(word: String, typed: String): String = when {
    word.startsWith(typed) -> word
    isInCaps(typed) -> word.uppercase()
    typed.firstOrNull { it.isLetter() }?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
    else -> word
}

/** Whether [word] is in caps: two letters at least, all capitals. */
internal fun isInCaps(word: String): Boolean {
    val letters = word.filter { it.isLetter() }
    return letters.length > 1 && letters.all { it.isUpperCase() }
}
