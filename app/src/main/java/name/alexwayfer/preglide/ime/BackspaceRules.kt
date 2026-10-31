package name.alexwayfer.preglide.ime

/**
 * Whether a backspace deletes anything in a field with [textBefore], the char before the cursor, and [selected],
 * the selected text: something is selected, or a char is there. A field that does not tell its text (null) may
 * still take the key, as a terminal does, so it counts as deleting.
 */
fun backspaceDeletes(textBefore: CharSequence?, selected: CharSequence?): Boolean =
    textBefore == null || textBefore.isNotEmpty() || !selected.isNullOrEmpty()
