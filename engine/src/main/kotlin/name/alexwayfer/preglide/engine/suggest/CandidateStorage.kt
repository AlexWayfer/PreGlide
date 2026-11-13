package name.alexwayfer.preglide.engine.suggest

private const val LOCAL = "# local candidates"
private val localSection = Regex("^$LOCAL\r?$", RegexOption.MULTILINE)
private const val CONTEXT_COLUMN = 5

fun PersonalWords.writeLocal(out: Appendable) {
    write(out)
    if (candidates.pending.isEmpty() && candidates.promoted.isEmpty()) return
    out.appendLine(LOCAL)
    for ((kind, table) in listOf("pending" to candidates.pending, "undo" to candidates.promoted)) {
        for (uses in table.values) {
            for (use in uses) {
                val kept = if (use.kept) 1 else 0
                val start = if (use.beganField) 1 else 0
                out.append("$kind\t${use.spelling}\t${use.time}\t$kept\t$start\t${use.context.joinToString(" ")}\n")
            }
        }
    }
}

/** Only shared rows of the profile are eligible to leave the device. */
fun sharedWordText(text: String): String = localSection.find(text)?.let { text.take(it.range.first) } ?: text

fun readLocalWords(lines: Sequence<String>): PersonalWords {
    val iterator = lines.iterator()
    val words = PersonalWords.read(iterator.asSequence().takeWhile { it != LOCAL })
    for (line in iterator) words.readCandidate(line.split('\t'))
    return words
}

private fun PersonalWords.readCandidate(fields: List<String>) {
    val context = fields.getOrNull(CONTEXT_COLUMN) ?: return
    val time = fields[2].toLongOrNull() ?: return
    val word = fields[1]
    val after = context.split(' ').filter(String::isNotEmpty)
    val use = WordOccurrence(word, after, time, kept = fields[3] == "1", beganField = fields[4] == "1")
    val key = keyOf(word)
    when (fields.first()) {
        "pending" -> candidates.pending.getOrPut(key) { mutableListOf() }.add(use)
        "undo" -> candidates.promoted[key] = candidates.promoted[key].orEmpty() + use
    }
}
