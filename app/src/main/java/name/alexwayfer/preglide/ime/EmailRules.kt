package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.suggest.Learned
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.isRemoved

/** How much of an email field to read: more than any address the user is likely to have. */
const val EMAIL_LENGTH = 128

private val emailVariations = setOf(
    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
)

private val emailAddress = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")

/** Whether a field of [inputType] is for an email address, which the keyboard learns and suggests whole. */
fun isEmailField(inputType: Int): Boolean =
    inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        inputType and InputType.TYPE_MASK_VARIATION in emailVariations

/** Whether [text] is one email address, as "name@example.com", and nothing else. */
fun isEmailAddress(text: String): Boolean = emailAddress.matches(text)

/**
 * Up to [count] of the [addresses] the user typed before that start with [typed], what is in the field before the
 * cursor, whatever its case: the most used first, then the latest. With nothing typed, the most used of all; the
 * address already typed in full is not suggested again.
 */
fun emailSuggestions(typed: CharSequence, addresses: PersonalWords, count: Int): List<String> {
    val start = typed.trim().toString()
    return addresses.all().asSequence()
        .filter { it.spelling.startsWith(start, ignoreCase = true) && !it.spelling.equals(start, ignoreCase = true) }
        .filterNot { addresses.isRemoved(it.spelling) }
        .sortedWith(compareByDescending<Learned> { it.count }.thenByDescending { it.lastUsed }.thenBy { it.spelling })
        .take(count)
        .map { it.spelling }
        .toList()
}
