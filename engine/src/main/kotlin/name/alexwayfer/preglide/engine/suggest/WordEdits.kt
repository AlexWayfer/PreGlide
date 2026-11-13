package name.alexwayfer.preglide.engine.suggest

internal const val EDIT_BASE = "# word edit base v1"

internal fun PersonalWords.writeTables(out: Appendable, wordType: String, nextType: String, startType: String) {
    for (word in words.values) {
        with(word) { out.append("$wordType\t$spelling\t$count\t$lastUsed\t$kept\n") }
    }
    next.write(out, nextType)
    starts.write(out, startType)
}

internal fun PersonalWords.editedSpelling(word: String): String? =
    if (!hasWordEdits) null else learned(word)?.spelling?.takeIf { spelling ->
        edits.commands.values.any { !it.undone && it.to == spelling }
    }

/** One owner's contribution. A null context is a field start, an empty context an ordinary word. */
internal data class WordUse(val context: String?, val word: Learned) {
    fun putIn(target: PersonalWords) {
        when (context) {
            null -> target.starts.restore(word)
            "" -> target.words.merge(keyOf(word.spelling), word, ::restored)
            else -> target.next.put(context, word, ::restored)
        }
    }
}

internal data class WordEdit(val id: String, val from: String, val to: String, val undone: Boolean) {
    val time = id.take(ID_TIME_LENGTH).toLong(HEX_RADIX)
    private val source = keyOf(from)
    private val target = keyOf(to)
    private val contextSource = foldedKey(from)
    private val contextTarget = foldedKey(to)

    fun affects(word: String) = keyOf(word).let { it == source || it == target }

    fun changesContext(context: String) = contextSource in context.split(' ')

    fun renameContext(context: String?): String? = context?.split(' ')?.joinToString(" ") {
        if (it == contextSource) contextTarget else it
    }
}

private const val ID_TIME_LENGTH = 16
private const val HEX_RADIX = 16
private const val HISTORY_WORD_COLUMN = 3
private const val KEPT_COLUMN = 3
private val editId = Regex("[0-9a-f]{32}")
private fun validId(id: String) = editId.matches(id) && id.take(ID_TIME_LENGTH).toLongOrNull(HEX_RADIX) != null

/** The owner's raw contributions stay separate so an undo can recover both sides of a merge. */
internal class WordEdits {
    val commands = sortedMapOf<String, WordEdit>()
    private val history = mutableListOf<Pair<String, WordUse>>()

    fun replaceWith(other: WordEdits) {
        commands.clear()
        commands.putAll(other.commands)
        history.clear()
        history.addAll(other.history)
    }

    fun observe(owner: PersonalWords, edit: WordEdit) {
        if (edit.id !in commands) {
            for (word in listOf(edit.from, edit.to)) {
                owner.words.remove(keyOf(word))?.let { history += edit.id to WordUse("", it) }
                owner.starts.forget(word)?.let { history += edit.id to WordUse(null, it) }
            }
            owner.next.takeEdited(edit).forEach { history += edit.id to it }
        }
        remember(edit)
    }

    private fun remember(edit: WordEdit) {
        if (commands[edit.id]?.undone != true) commands[edit.id] = edit
    }

    fun projected(cutoffs: Map<String, Long>) = PersonalWords().also { result ->
        for ((first, use) in history) {
            project(use, commands.tailMap(first).values, cutoffs)?.putIn(result)
        }
    }

    fun write(out: Appendable) {
        for ((id, edit) in commands) {
            val canceled = if (edit.undone) 1 else 0
            out.append("e\t$id\t${edit.from}\t${edit.to}\t$canceled\n")
        }
        for ((first, use) in history) {
            val (context, word) = use
            val prefix = if (context == null) "hs\t$first" else "h\t$first\t$context"
            out.append("$prefix\t${word.spelling}\t${word.count}\t${word.lastUsed}\t${word.kept}\n")
        }
    }

    fun read(fields: List<String>) {
        val id = fields.getOrNull(1)?.takeIf(::validId) ?: return
        when (fields.first()) {
            "e" -> readCommand(id, fields)
            "hs" -> readLearned(fields.drop(2))?.let { history += id to WordUse(null, it) }
            "h" -> readLearned(fields.drop(HISTORY_WORD_COLUMN))?.let { history += id to WordUse(fields[2], it) }
        }
    }

    private fun readCommand(id: String, fields: List<String>) {
        val undone = fields.getOrNull(4)
        if (undone !in listOf("0", "1")) return
        val (from, to) = fields.drop(2)
        if (from.isNotEmpty() && validEditTarget(to)) {
            remember(WordEdit(id, from, to, undone == "1"))
        }
    }
}

private fun project(use: WordUse, edits: Collection<WordEdit>, cutoffs: Map<String, Long>): WordUse? {
    var (context, word) = use
    var since = word.lastUsed
    for (edit in edits.filterNot { it.undone }) {
        if (edit.affects(word.spelling)) {
            val forgotten = cutoffs[keyOf(word.spelling)]
            if (forgotten != null && forgotten in since..edit.time) return null
            word = word.copy(spelling = edit.to)
            since = maxOf(since, edit.time)
        }
        context = edit.renameContext(context)
    }
    val cutoff = cutoffs[keyOf(word.spelling)]
    return if (cutoff == null || since > cutoff) WordUse(context, word) else null
}

/** Reads the spelling, count, typing time and optional kept count shared by the TSV record types. */
internal fun readLearned(fields: List<String>): Learned? {
    val count = fields.getOrNull(1)?.toIntOrNull()
    val time = fields.getOrNull(2)?.toLongOrNull()
    val kept = fields.getOrNull(KEPT_COLUMN)?.toIntOrNull() ?: 0
    return if (count != null && time != null) Learned(fields.first(), count, time, kept) else null
}
