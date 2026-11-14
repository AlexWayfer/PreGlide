package name.alexwayfer.preglide.engine.suggest

import java.util.Locale

private val RUSSIAN_SIGNS = setOf('ь', 'Ь', 'ъ', 'Ъ')

/** Confirmed corpus corruption, shared by dictionary builds and old models whose token ids must stay intact. */
fun isDictionaryGarbage(word: String): Boolean =
    word.firstOrNull() in RUSSIAN_SIGNS || word.lowercase(Locale.ROOT) in RejectedWords.spellings

private object RejectedWords {
    val spellings: Set<String> = checkNotNull(javaClass.getResourceAsStream("/dictionary-garbage.tsv"))
        .bufferedReader().useLines { lines ->
            lines.map { it.substringBefore('\t').lowercase(Locale.ROOT) }.toSet()
        }
}
