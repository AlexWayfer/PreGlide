package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.predict.NextTokens
import java.util.Locale

/**
 * The word being typed split at its last hyphen: its [head], up to the hyphen and with it, as "потихоньку-" of
 * "потихоньку-п", and the text before the cursor with a space in place of that hyphen, [spaced], where the part after
 * it is a word of its own after the head.
 */
data class HyphenSplit(val head: String, val spaced: String)

/**
 * The word being typed at the end of [textBefore] split at its last hyphen (see [HyphenSplit]), or null: for a word
 * with no hyphen, one with nothing but a hyphen before it, or one with a digit after it, as "топ-3".
 */
fun hyphenSplit(textBefore: CharSequence): HyphenSplit? {
    val typed = currentWord(textBefore)
    val hyphen = typed.lastIndexOf('-')
    val tail = typed.substring(hyphen + 1)
    if (hyphen < 1 || !typed[hyphen - 1].isLetterOrDigit() || !tail.all(Char::isLetter)) return null
    val before = textBefore.subSequence(0, textBefore.length - tail.length - 1)
    return HyphenSplit(typed.substring(0, hyphen + 1), "$before $tail")
}

/**
 * The text the prediction model reads for [textBefore], the text before the cursor, to [suggestions]: the text
 * itself, or for a word a hyphen splits its [HyphenSplit.spaced] text.
 */
fun predictedText(textBefore: CharSequence): CharSequence = hyphenSplit(textBefore)?.spaced ?: textBefore

/**
 * Up to [count] words for [input]'s typed word, which [split] splits at its last hyphen:
 * the whole words the dictionary and the user know that start so, as "кто-нибудь" for "кто-", then the next words
 * after the part before the hyphen that start as the part after it, put on it, as "потихоньку-помаленьку" for
 * "потихоньку-"; all cased like the word typed. [next] is what the prediction model gives for [HyphenSplit.spaced].
 */
internal fun hyphenSuggestions(
    input: SuggestionInput,
    split: HyphenSplit,
    model: LanguageModel,
    count: Int,
    next: NextTokens?,
): List<String> {
    val typed = input.typed
    val after = suggestions(SuggestionInput(split.spaced, input.preferCapital), model, count, next)
        .map { split.head + it }
    val particles = particleCompletions(typed, split.head)
    // Even a known -с follows -то when both particles fit.
    val defer = particles.lastOrNull().takeIf { particles.size == 2 }
    val completed = completions(model, typed, count, typing = null, preferCapital = input.preferCapital)
    val ordered = if (input.preferCapital) capitalWordsFirst(completed, typed, model.personal) else completed
    val known = ordered.filterNot {
        defer != null && it.equals(defer, ignoreCase = true)
    }
    return (known + particles + after)
        .filterNot { model.personal.isRemoved(it) }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(count)
        .map { casedLike(it, typed) }
}
