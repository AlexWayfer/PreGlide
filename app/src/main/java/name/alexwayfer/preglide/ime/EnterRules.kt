package name.alexwayfer.preglide.ime

import android.view.inputmethod.EditorInfo

/**
 * What Enter does in a field with [imeOptions]: the field's action, such as send or search, or null for a new
 * line, when the field asks for none or for Enter to stay a line break.
 */
fun enterAction(imeOptions: Int): Int? {
    val action = imeOptions and EditorInfo.IME_MASK_ACTION
    val wanted = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
        action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
    return if (wanted) action else null
}
