package name.alexwayfer.preglide.spell

import android.view.textservice.SuggestionsInfo

/**
 * The [SuggestionsInfo] attributes of a word: none when it was not [checked], as while the language loads, so it
 * is not marked; in the dictionary when it has no [corrections], being spelled right; a typo otherwise.
 */
fun spellingAttributes(checked: Boolean, corrections: List<String>?): Int = when {
    !checked -> 0
    corrections == null -> SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
    else -> SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
}
