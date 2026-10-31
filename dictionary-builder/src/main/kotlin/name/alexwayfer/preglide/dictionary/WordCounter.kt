package name.alexwayfer.preglide.dictionary

import java.nio.file.Path

/**
 * Forms in fewer documents than this are typos, names that one site uses, or spam. Dropping them before the merge
 * keeps it small: they are most of the distinct forms, and no dictionary has room for them anyway.
 */
const val MIN_DOCUMENTS = 5

/**
 * Counts words in FineWeb parquet files on [db], in steps of [stepDocuments] documents, and in subtitle texts in
 * steps of [stepLines] lines.
 */
class WordCounter(
    private val db: DuckDb,
    private val stepDocuments: Int = STEP_DOCUMENTS,
    private val stepLines: Int = STEP_LINES,
) {
    /**
     * Counts every form in the FineWeb file [text] into the parquet file [counts], apart at the start of a
     * sentence and within one, with its occurrences capped per document and the number of documents it is in.
     * Calls [progress] after each step and after the merge.
     */
    fun count(text: Path, counts: Path, progress: (CountProgress) -> Unit) {
        db.countInSteps(
            StepCount(text, counts, stepDocuments),
            progress,
            step = { first, partial -> countStep(text, first, partial) },
            merge = ::mergeCounts,
        )
    }

    /** Sums the counts of the step files [steps], a glob, into [target]. */
    private fun mergeCounts(steps: String, target: Path): String =
        """
        COPY (
            SELECT form, initial, sum(occurrences)::BIGINT AS occurrences, sum(documents)::BIGINT AS documents
            FROM read_parquet(${literal(steps)})
            GROUP BY form, initial
        ) TO ${literal(target)} (FORMAT parquet)
        """

    /**
     * Counts the forms of the documents of [text] from the [first] one on, a step long, into [target]. Each
     * document counts its own forms, so no table of all documents grows; a form's key starts with 1 at a
     * sentence start and with 0 within a sentence.
     */
    private fun countStep(text: Path, first: Long, target: Path): String =
        """
        COPY (
            SELECT
                substr(key, 2) AS form,
                starts_with(key, '1') AS initial,
                sum(least(value, $PER_DOCUMENT_CAP))::BIGINT AS occurrences,
                count(*) AS documents
            FROM (
                SELECT unnest(map_entries(list_aggregate(flatten(list_transform(
                    regexp_split_to_array(replace(text, '’', ''''), ${literal(SENTENCE_BREAK)}),
                    lambda sentence: list_transform(
                        regexp_extract_all(sentence, ${literal(WORD_PATTERN)}),
                        lambda word, position: CASE WHEN position = 1 THEN '1' ELSE '0' END || word
                    )
                )), 'histogram')), recursive := true)
                FROM ${stepRows(text, first, stepDocuments)}
            )
            GROUP BY form, initial
        ) TO ${literal(target)} (FORMAT parquet)
        """

    /**
     * Counts every form in [text], a gzipped text file with a sentence on each line, as movie subtitles come, into
     * the parquet file [counts], as [count] does a FineWeb file: a line's first word starts its sentence. A line
     * is not a document, so nothing is capped, and each occurrence counts as a document of its own. Goes in steps
     * of [stepLines] lines, calling [progress] as [count] does.
     */
    fun countLines(text: Path, counts: Path, progress: (CountProgress) -> Unit = {}) {
        val lines = db.linesOf(text)
        db.countInSteps(
            StepCount(lines, counts, stepLines),
            progress,
            step = { first, partial -> lineCountStep(lines, first, partial) },
            merge = ::mergeCounts,
        )
    }

    /** Counts the forms of the lines of [lines], from [db]'s [DuckDb.linesOf], from the [first] one on. */
    private fun lineCountStep(lines: Path, first: Long, target: Path): String =
        """
        COPY (
            SELECT
                substr(key, 2) AS form,
                starts_with(key, '1') AS initial,
                count(*)::BIGINT AS occurrences,
                count(*)::BIGINT AS documents
            FROM (
                SELECT unnest(list_transform(
                    regexp_extract_all(line, ${literal(WORD_PATTERN)}),
                    lambda word, position: CASE WHEN position = 1 THEN '1' ELSE '0' END || word
                )) AS key
                FROM ${stepRows(lines, first, stepLines)}
            )
            GROUP BY form, initial
        ) TO ${literal(target)} (FORMAT parquet)
        """

    /** How many documents of each Common Crawl dump the FineWeb file [text] holds, oldest dump first. */
    fun dumps(text: Path): Map<String, Long> =
        db.query("SELECT dump, count(*) FROM read_parquet(${literal(text)}) GROUP BY dump ORDER BY dump") {
            getString(1) to getLong(2)
        }.toMap()

    /**
     * The forms in [counts] that are words of [corpus], summed over the files, each in enough documents. A
     * document with a form both at a sentence start and within a sentence counts twice: close enough for a cut.
     */
    fun forms(counts: List<Path>, corpus: Corpus): List<Form> =
        db.query(
            """
            SELECT
                form,
                sum(occurrences) AS occurrences,
                coalesce(sum(occurrences) FILTER (WHERE NOT initial), 0) AS mid_sentence
            FROM read_parquet(${files(counts)})
            WHERE regexp_full_match(form, ${literal(corpus.wordPattern)})
            GROUP BY form
            HAVING sum(documents) >= $MIN_DOCUMENTS
            """,
        ) { Form(getString("form"), getLong("occurrences"), getLong("mid_sentence")) }

    /** All occurrences of [corpus]'s words in [counts], the rare ones included: what a dictionary could cover. */
    fun total(counts: List<Path>, corpus: Corpus): Long =
        db.query(
            """
            SELECT coalesce(sum(occurrences), 0)
            FROM read_parquet(${files(counts)})
            WHERE regexp_full_match(form, ${literal(corpus.wordPattern)})
            """,
        ) { getLong(1) }.single()
}
