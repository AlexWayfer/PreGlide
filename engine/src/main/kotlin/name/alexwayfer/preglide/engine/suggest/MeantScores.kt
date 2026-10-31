package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.predict.NextTokens

/**
 * The words the user may have meant by [typing] without typing their start as it is (see [meantWords]), scored as
 * [predictedScores] scores the words with the typed start, and made less by their cost. Besides the dictionary's,
 * the model's likeliest words ([NextTokens.likeliest]) and the user's next words after [context], the words before,
 * are looked at, so a slip still finds the word the context calls for when the dictionary has more frequent ones
 * near it.
 */
fun meantPredicted(model: LanguageModel, next: NextTokens, context: List<String>, typing: Typing): List<Scored> {
    val mix = NextMix(model, next, context)
    val likely = next.likeliest.map { next.prediction.spellings[it] } + mix.after.map { it.spelling }
    return meantWords(model, typing, MEANT_WORDS + model.personal.removedCount, likely).map { meant ->
        val likely = mix.probability(meant.word, meant.frequency) * PER_BILLION
        Scored(meant.word, lessBy(withUses(likely, meant.frequency, usesOf(meant, model)), meant.cost))
    }
}

/**
 * The words the user may have meant by [typing] (see [meantWords]), scored as the completions are without the
 * prediction model: by their frequency and the user's uses, made less by their cost.
 */
internal fun meantCompletions(model: LanguageModel, typing: Typing): List<Scored> =
    meantWords(model, typing, MEANT_WORDS + model.personal.removedCount).map { meant ->
        val likely = withUses(meant.frequency.toDouble(), meant.frequency, usesOf(meant, model))
        Scored(meant.word, lessBy(likely, meant.cost))
    }

/** How many times the user typed the word [meant]. */
private fun usesOf(meant: Meant, model: LanguageModel): Int = model.personal.learned(meant.word)?.count ?: 0
