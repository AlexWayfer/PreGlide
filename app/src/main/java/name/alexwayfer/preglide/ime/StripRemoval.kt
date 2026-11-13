package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.suggest.LanguageModel
import java.util.Locale

/** Whether holding [word] may remove it: the [typedWord] only when [typedWordRemovable] tells it is known. */
fun isRemovable(word: String?, typedWord: String?, typedWordRemovable: Boolean = false): Boolean =
    word != null && (word != typedWord || typedWordRemovable)

/** Whether [word] is known whole in [model] and has not already been removed from its suggestions. */
fun typedWordRemovable(word: String?, model: LanguageModel?): Boolean =
    word != null && model != null && model.knowsWhole(word) &&
        word.lowercase(Locale.ROOT) !in model.personal.removedWords
