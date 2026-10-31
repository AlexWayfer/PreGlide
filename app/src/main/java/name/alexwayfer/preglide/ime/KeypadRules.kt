package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.layout.Page

/**
 * The page a field of [inputType] starts on: the number pad for a number, the phone pad for a phone number, and the
 * letters for the rest. A date or a time takes words too, as "завтра" or "5 pm", so it gets the letters.
 */
fun fieldPage(inputType: Int): Page = when (inputType and InputType.TYPE_MASK_CLASS) {
    InputType.TYPE_CLASS_NUMBER -> Page.Digits
    InputType.TYPE_CLASS_PHONE -> Page.Phone
    else -> Page.Letters
}

/**
 * What tells apart the keys that fade into each other as [page] comes: each keypad, which only a field brings, and
 * the letters with their symbols, which a key switches between while typing and which switch at once.
 */
fun pageFadeKey(page: Page): Page = if (page.keypad) page else Page.Letters

/**
 * Whether a field of [inputType] is no field at all, but raw keys, as `TYPE_NULL` asks. Chrome starts one between
 * two fields as the focus moves, for a tenth of a second or so, before the field the user tapped.
 */
fun isNoField(inputType: Int): Boolean = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
