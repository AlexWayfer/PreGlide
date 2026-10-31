package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.Language

/** The kind of number of four digits, a year mostly, among [russianEndings]. */
private const val YEAR = "year"

/** The kind of number that ends in 11 to 19, among [russianEndings]. */
private const val TEEN = "teen"

/** The kind of any number, among [russianEndings], for an ending none of its own kind's starts so. */
private const val ANY = "any"

/** How many digits a year has, as [YEAR] counts it. */
private const val YEAR_DIGITS = 4

/** The numbers whose last two digits make them [TEEN]. */
private val teens = 11..19

/**
 * The endings written after a number in digits and a hyphen in Russian, as "го" of "2-го", the most frequent first,
 * for each kind of number: by its last digit, [TEEN], [YEAR], and [ANY]. Counted in the Russian text of OPUS
 * OpenSubtitles v2024, the dictionary's corpus of spoken words: 2-х, but 10-ти and 90-е.
 */
private val russianEndings: Map<String, List<String>> = mapOf(
    "0" to "х ти е й го м ых ю ые я ый и",
    "1" to "й го м я ый ой е ом ю ого ая ое",
    "2" to "й го х ой м я ом е ю ая ух ого",
    "3" to "й го х м я ей ий е ем ю его мя",
    "4" to "й го х м я ый ой е ом ю ая ое",
    "5" to "й го ти м я ой ый ом ю е и ого",
    "6" to "й го м ти ой я ом ю е ая ого и",
    "7" to "й го ой м я ми ом е ю ого ая ое",
    "8" to "го й м ой ми ом я е ю ая ого и",
    "9" to "й го м ти ом я ый ой е ю мм ая",
    TEEN to "го й ти м е я ый ой ю ом ое ого",
    YEAR to "го м х й ом е ый ых ой ого му ем",
    ANY to "го й х м ти е ой я ом ый ю ого ых ая ое и",
).mapValues { it.value.split(' ') }

/**
 * The context a word typed right after a number and a hyphen is learned after, as "разовое" of "2-разовое": no word
 * the user types is spelled so.
 */
const val AFTER_NUMBER = "#"

/** Whether [word], typed right after a number and a hyphen, is one of its endings, as "го" of "2-го". */
fun isNumberEndingWord(word: String): Boolean = word.lowercase() in allEndings

/** The endings of [russianEndings] of every kind of number. */
private val allEndings: Set<String> = russianEndings.values.flatten().toSet()

/** The kind of the number [digits] spell, among [russianEndings]. */
internal fun numberKind(digits: String): String = when {
    digits.length == YEAR_DIGITS -> YEAR
    digits.takeLast(2).toInt() in teens -> TEEN
    else -> digits.takeLast(1)
}

/**
 * Up to [count] endings to suggest in [language] for the number at the end of [textBefore], the text before the
 * cursor, when what is typed there is a number's ending (see [isNumberEnding]): the ones that start as typed, the
 * most frequent after a number of its kind first, and between them the words of [personal] the user typed after a
 * number that start so, the most used first; all cased like what is typed. Russian alone writes them; none
 * otherwise, and none when no ending starts as typed, as then the strip suggests as for any word.
 */
fun numberEndings(
    textBefore: CharSequence,
    language: Language,
    count: Int,
    personal: PersonalWords = PersonalWords(),
): List<String> {
    val typed = currentWord(textBefore)
    val endings = if (language == Language.Russian && isNumberEnding(textBefore)) endingsOf(textBefore) else emptyList()
    if (endings.isEmpty()) return emptyList()
    return interleaved(endings, wordsAfterNumber(personal, typed))
        .take(count)
        .map { casedLike(it, typed) }
}

/**
 * The Russian endings for the number at the end of [textBefore], before the ending being typed there, that start as
 * typed, the most frequent after a number of its kind first.
 */
private fun endingsOf(textBefore: CharSequence): List<String> {
    val typed = currentWord(textBefore)
    // The hyphen comes between the number and its ending.
    val digits = textBefore.subSequence(0, textBefore.length - typed.length - 1).takeLastWhile(Char::isDigit)
    return (russianEndings.getValue(numberKind(digits.toString())) + russianEndings.getValue(ANY))
        .distinct()
        .filter { it.startsWith(typed, ignoreCase = true) }
}

/** The words of [personal] the user typed after a number that start like [typed], the most used first. */
private fun wordsAfterNumber(personal: PersonalWords, typed: String): List<String> =
    personal.after(AFTER_NUMBER)
        .filter { startsLikeTyped(it.spelling, typed) && !personal.isRemoved(it.spelling) }
        .sortedByDescending { it.count }
        .map { it.spelling }

/** [first] and [second] taken by turns, [first]'s first, and what is left of the longer after the other ends. */
internal fun <T> interleaved(first: List<T>, second: List<T>): List<T> =
    (0 until maxOf(first.size, second.size)).flatMap { listOfNotNull(first.getOrNull(it), second.getOrNull(it)) }
