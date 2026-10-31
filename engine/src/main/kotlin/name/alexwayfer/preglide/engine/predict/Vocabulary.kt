package name.alexwayfer.preglide.engine.predict

/**
 * The tokens the prediction model knows, each with its id, the model's input and output index: the fixed ones
 * ([FIXED]: padding, a text's start, an unknown word, a number, a line break, then the [MARKS]) and then [words],
 * lowercase, the most frequent first. A model ships with its vocabulary file, which lists every token in id order;
 * changing the fixed tokens takes a new model.
 */
class Vocabulary(val words: List<String>) {
    private val wordIds = HashMap<String, Int>(words.size * 2).apply {
        words.forEachIndexed { index, word -> putIfAbsent(word, FIXED.size + index) }
        // Texts often write е for ё, and the dictionary has ё put back: "еще" is the token of "ещё". A word whose
        // е spelling is a word of its own, as "все" for "всё", keeps that one.
        words.forEachIndexed { index, word -> if ('ё' in word) putIfAbsent(word.replace('ё', 'е'), FIXED.size + index) }
    }

    /** Every token in id order, as the vocabulary file lists them. */
    val tokens: List<String> get() = FIXED + words

    val size: Int get() = FIXED.size + words.size

    fun id(token: Token): Int =
        when (token) {
            is Token.Word -> wordIds[token.text] ?: UNKNOWN
            is Token.Mark -> FIRST_MARK + MARKS.indexOf(token.mark)
            Token.Number -> NUMBER
            Token.LineBreak -> LINE_BREAK
        }

    /** The word with [id], or null for a fixed token. */
    fun word(id: Int): String? = words.getOrNull(id - FIXED.size)

    /** The ids of [text] as a text of its own: [START], then the id of each of its [tokens]. */
    fun textIds(text: CharSequence): IntArray {
        val found = tokens(text)
        return IntArray(found.size + 1) { if (it == 0) START else id(found[it - 1]) }
    }

    /** The vocabulary file: a line per token, in id order. */
    fun text(): String = tokens.joinToString("") { "$it\n" }

    companion object {
        const val PADDING = 0
        const val START = 1
        const val UNKNOWN = 2
        const val NUMBER = 3
        const val LINE_BREAK = 4
        private const val FIRST_MARK = 5

        /** The tokens every vocabulary starts with, in id order; no word can look like one of them. */
        val FIXED: List<String> = listOf("<pad>", "<s>", "<unk>", "<num>", "<br>") + MARKS.map { it.toString() }

        /** The vocabulary from the [lines] of its file, which must start with the [FIXED] tokens. */
        fun read(lines: Sequence<String>): Vocabulary {
            val all = lines.filter { it.isNotEmpty() }.toList()
            require(all.take(FIXED.size) == FIXED) { "A vocabulary file starts with ${FIXED.joinToString(" ")}" }
            return Vocabulary(all.drop(FIXED.size))
        }
    }
}
