package name.alexwayfer.preglide.engine.suggest

/** Indefinite forms and fixed expressions keep their own typing counts. */
private val toStems = setOf(
    "кто", "кого", "кому", "кем", "ком", "что", "чего", "чему", "чем",
    "какой", "какая", "какое", "какие", "какого", "какому", "каким", "каком", "какую", "какою", "каких", "какими",
    "такой", "такая", "такое", "такие", "такого", "такому", "таким", "таком", "такую", "такою", "таких", "такими",
    "чей", "чья", "чье", "чьи", "чьего", "чьему", "чьим", "чьем", "чьей", "чьею", "чью", "чьих", "чьими",
    "который", "которая", "которое", "которые", "которого", "которому", "которым", "котором", "которую",
    "которой", "которою", "которых", "которыми",
    "сколько", "скольких", "скольким", "сколькими", "насколько", "столько", "стольких", "стольким", "столькими",
    "как", "где", "куда", "откуда", "когда", "почему", "зачем", "отчего", "почем", "вообще", "наконец", "всего", "так",
)

/** Freely attached particles need no personal entry for every combination, even if the dictionary has one. */
internal fun hasProductiveParticle(word: String): Boolean {
    val hyphen = word.lastIndexOf('-')
    if (hyphen < 0) return false
    return when (word.substring(hyphen + 1).lowercase()) {
        "с" -> true
        "то" -> word.substring(0, hyphen).map(::fold).joinToString("") !in toStems
        else -> false
    }
}

internal fun particleCompletions(typed: String, head: String): List<String> {
    val tail = typed.substring(head.length)
    val last = head.dropLast(1).lastOrNull()?.lowercaseChar()
    if (last !in 'а'..'я' && last != 'ё') return emptyList()
    return listOf("то", "с").filter { tail.length < it.length && startsWithFolded(it, tail) }
        .map { head + if (isInCaps(typed)) it.uppercase() else it }
}
