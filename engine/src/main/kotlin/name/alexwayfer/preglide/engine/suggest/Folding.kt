package name.alexwayfer.preglide.engine.suggest

/*
 * A search for words ignores what the user does not type on purpose: case, ё typed as е, and apostrophes, so
 * "Ещ" finds "ещё" and "dont" finds "don't". Both the dictionary order and the prefix match fold words this way.
 */

/** [char] as a search sees it: lowercase, and е for ё. */
fun fold(char: Char): Char =
    // Most chars of the dictionaries are such already, and a walk through them folds many.
    if (char in 'а'..'я' || char in 'a'..'z') {
        char
    } else {
        when (val lower = char.lowercaseChar()) {
            'ё' -> 'е'
            else -> lower
        }
    }

/** Whether a search skips [char], as an apostrophe. */
internal fun isSkipped(char: Char): Boolean = char == '\''

/** The index of the first char of [text] at or after [from] that a search does not skip. */
private fun nextSearched(text: String, from: Int): Int {
    var index = from
    while (index < text.length && isSkipped(text[index])) index++
    return index
}

/** [word] as a search sees it, a key that every spelling of it shares: "Всё" and "все" are both "все". */
fun foldedKey(word: String): String = buildString(word.length) {
    for (char in word) if (!isSkipped(char)) append(fold(char))
}

/** The char of [foldedKey] of [word] at [position], or null past its end; quicker than making the key. */
fun foldedCharAt(word: String, position: Int): Char? {
    var searched = 0
    for (char in word) {
        if (isSkipped(char)) continue
        if (searched == position) return fold(char)
        searched++
    }
    return null
}

/** Whether [typed] is [word] with ё typed as е somewhere, whatever its case, and nothing else changed. */
fun isWithoutYo(typed: String, word: String): Boolean =
    typed.length == word.length && typed.indices.all { i ->
        val char = word[i].lowercaseChar()
        typed[i].lowercaseChar() == char || (typed[i].lowercaseChar() == 'е' && char == 'ё')
    }

/** Whether [a] and [b] differ only in case and in ё typed as е: unlike a search, it minds apostrophes. */
fun foldsTheSame(a: String, b: String): Boolean = a.length == b.length && a.indices.all { fold(a[it]) == fold(b[it]) }

/** Compares [a] and [b] as a search sees them; a word comes before every longer word it starts. */
fun compareFolded(a: String, b: String): Int {
    var i = nextSearched(a, 0)
    var j = nextSearched(b, 0)
    while (i < a.length && j < b.length) {
        val difference = fold(a[i]).compareTo(fold(b[j]))
        if (difference != 0) return difference
        i = nextSearched(a, i + 1)
        j = nextSearched(b, j + 1)
    }
    return (i < a.length).compareTo(j < b.length)
}

/**
 * Whether [word] has ё wherever [typed], laid over its start as a search sees them, has it: a ё the user typed is on
 * purpose. The dictionary spells ё where it is sure of it, and keeps both spellings where either may be meant, as
 * "чем" and "чём", so a word with е there is another word: "чё" is no start of "через".
 */
fun keepsTypedYo(word: String, typed: String): Boolean {
    var i = nextSearched(word, 0)
    var j = nextSearched(typed, 0)
    var keeps = true
    while (keeps && i < word.length && j < typed.length) {
        keeps = typed[j].lowercaseChar() != 'ё' || word[i].lowercaseChar() == 'ё'
        i = nextSearched(word, i + 1)
        j = nextSearched(typed, j + 1)
    }
    return keeps
}

/** Whether [word] starts with [typed] as a search sees them, with ё wherever [typed] has it: see [keepsTypedYo]. */
fun startsLikeTyped(word: String, typed: String): Boolean = startsWithFolded(word, typed) && keepsTypedYo(word, typed)

/** Whether [word] starts with [prefix] as a search sees them. */
fun startsWithFolded(word: String, prefix: String): Boolean {
    var i = nextSearched(word, 0)
    var j = nextSearched(prefix, 0)
    while (j < prefix.length) {
        if (i == word.length || fold(word[i]) != fold(prefix[j])) return false
        i = nextSearched(word, i + 1)
        j = nextSearched(prefix, j + 1)
    }
    return true
}
