package name.alexwayfer.preglide.dictionary

import java.nio.file.Files
import java.nio.file.Path

/** Pairs seen fewer times than this in a text are chance, not a habit of the language. */
private const val MIN_PAIR_OCCURRENCES = 3

/** How many next words of each word a text gives the blend; the blend keeps fewer, from all texts together. */
private const val CANDIDATES_PER_WORD = 30

/**
 * A line of a word list or of subtitles is a single column: a tab is as good a separator as any, since the
 * words hold none and a subtitle line with one is skipped.
 */
private val TAB = literal("\t")

/** Words of a pair go in one key, joined by a space, which no word holds. */
private const val PAIR = "lower(words[i]) || ' ' || lower(words[i + 1])"

/**
 * Counts pairs of neighbor words within a sentence on [db]: a word and the word right after it. Only pairs of
 * words the dictionary has count, given by [useWords]; all others would be far too many to keep.
 */
class PairCounter(
    private val db: DuckDb,
    private val stepDocuments: Int = STEP_DOCUMENTS,
    private val stepLines: Int = STEP_LINES,
) {
    /**
     * Sets the words pairs are counted for, lowercase: a pair needs a word of [previous] and then one of [next].
     * Writes them to a file in [scratch] for DuckDB to read.
     */
    fun useWords(previous: List<String>, next: List<String>, scratch: Path) {
        for ((table, words) in listOf("previous_words" to previous, "next_words" to next)) {
            val file = Files.createDirectories(scratch).resolve("$table.txt")
            Files.write(file, words)
            db.execute(
                "CREATE OR REPLACE TABLE $table AS SELECT word FROM " +
                    "read_csv(${literal(file)}, columns = {'word': 'VARCHAR'}, header = false, delim = $TAB, " +
                    "quote = '', escape = '')",
            )
        }
    }

    /**
     * Counts the pairs in the FineWeb file [text] into the parquet file [pairs], each capped per document as
     * words are, in steps. Calls [progress] after each step and after the merge.
     */
    fun count(text: Path, pairs: Path, progress: (CountProgress) -> Unit) {
        db.countInSteps(
            StepCount(text, pairs, stepDocuments),
            progress,
            step = { first, partial ->
                // A list of one list binds the sentence's words to a name the pairs can use twice.
                """
                COPY (
                    ${keptPairs("sum(least(value, $PER_DOCUMENT_CAP))")}
                    FROM (
                        SELECT unnest(map_entries(list_aggregate(flatten(list_transform(
                            regexp_split_to_array(replace(text, '’', ''''), ${literal(SENTENCE_BREAK)}),
                            lambda sentence: list_transform(
                                [regexp_extract_all(sentence, ${literal(WORD_PATTERN)})],
                                lambda words: list_transform(range(1, len(words)), lambda i: $PAIR)
                            )[1]
                        )), 'histogram')), recursive := true)
                        FROM ${stepRows(text, first, stepDocuments)}
                    )
                    $PAIR_FILTER
                ) TO ${literal(partial)} (FORMAT parquet)
                """
            },
            merge = ::mergePairs,
        )
    }

    /** Sums the pairs of the step files [steps], a glob, into [target]. */
    private fun mergePairs(steps: String, target: Path): String =
        """
        COPY (
            SELECT previous, next, sum(occurrences)::BIGINT AS occurrences
            FROM read_parquet(${literal(steps)})
            GROUP BY previous, next
        ) TO ${literal(target)} (FORMAT parquet)
        """

    /**
     * Counts the pairs in [text], a gzipped text file with a sentence on each line, as movie subtitles come, into
     * the parquet file [pairs]. A line is not a document, so nothing is capped. Goes in steps of [stepLines] lines,
     * calling [progress] as [count] does.
     */
    fun countLines(text: Path, pairs: Path, progress: (CountProgress) -> Unit = {}) {
        val lines = db.linesOf(text)
        db.countInSteps(
            StepCount(lines, pairs, stepLines),
            progress,
            step = { first, partial -> linePairStep(lines, first, partial) },
            merge = ::mergePairs,
        )
    }

    /** Counts the pairs of the lines of [lines], from [db]'s [DuckDb.linesOf], from the [first] one on. */
    private fun linePairStep(lines: Path, first: Long, target: Path): String =
        """
        COPY (
            ${keptPairs("count(*)")}
            FROM (
                SELECT unnest(list_transform(range(1, len(words)), lambda i: $PAIR)) AS key
                FROM (
                    SELECT regexp_extract_all(line, ${literal(WORD_PATTERN)}) AS words
                    FROM ${stepRows(lines, first, stepLines)}
                )
            )
            $PAIR_FILTER
        ) TO ${literal(target)} (FORMAT parquet)
        """

    /**
     * The pairs counted in [pairs], summed over the files: for each word, its most frequent next words, those
     * seen often enough; and the total of all pairs counted, for the share each pair has.
     */
    fun pairs(pairs: List<Path>): PairCounts {
        val kept = db.query(
            """
            SELECT previous, next, occurrences FROM (
                SELECT previous, next, sum(occurrences) AS occurrences
                FROM read_parquet(${files(pairs)})
                GROUP BY previous, next
                HAVING sum(occurrences) >= $MIN_PAIR_OCCURRENCES
            )
            QUALIFY row_number() OVER (PARTITION BY previous ORDER BY occurrences DESC, next) <= $CANDIDATES_PER_WORD
            ORDER BY previous, occurrences DESC, next
            """,
        ) { WordPair(getString("previous"), getString("next"), getLong("occurrences")) }
        val total = db.query("SELECT coalesce(sum(occurrences), 0) FROM read_parquet(${files(pairs)})") {
            getLong(1)
        }.single()
        return PairCounts(kept, total)
    }

    private fun keptPairs(occurrences: String): String =
        "SELECT split_part(key, ' ', 1) AS previous, split_part(key, ' ', 2) AS next, " +
            "$occurrences::BIGINT AS occurrences"
}

/** Keeps the pairs of the words [PairCounter.useWords] set, and sums each. */
private const val PAIR_FILTER = """
    WHERE split_part(key, ' ', 1) IN (SELECT word FROM previous_words)
        AND split_part(key, ' ', 2) IN (SELECT word FROM next_words)
    GROUP BY previous, next
"""
