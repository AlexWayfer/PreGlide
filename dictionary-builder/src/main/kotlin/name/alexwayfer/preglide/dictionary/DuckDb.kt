package name.alexwayfer.preglide.dictionary

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.time.measureTime

/** Where a sentence ends: after its end mark and any closing quotes, at a line break, or at a quote after a colon. */
const val SENTENCE_BREAK = "[.!?…][\"»”)]*\\s+|\\n|:\\s*[«\"„“]"

/** A word: letters of any language, joined by a hyphen or an apostrophe. A corpus's own pattern picks its words. */
const val WORD_PATTERN = "\\p{L}+([-']\\p{L}+)*"

/** How much memory DuckDB holds before it spills to its temporary directory. */
private const val MEMORY_LIMIT = "6GB"

/** How many documents a step of a count reads: steps report progress and let a stopped count go on from there. */
const val STEP_DOCUMENTS = 100_000

/**
 * How many subtitle lines a step of a count reads. A line is a few words, so a step takes many: the pairs of all
 * 9.5 GB of English subtitles in one statement ran out of memory at any limit and thread count.
 */
const val STEP_LINES = 5_000_000

/** A count of the parquet file [text] into the parquet file [target], in steps of [stepRows] of its rows. */
data class StepCount(val text: Path, val target: Path, val stepRows: Int)

/** An in-memory DuckDB, which reads parquet and spreads the work over all cores; the counters run on it. */
class DuckDb private constructor(private val connection: Connection) : AutoCloseable {
    /** Runs [sql] built by this tool, not taken from outside. */
    fun execute(sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }

    fun <T> query(sql: String, read: ResultSet.() -> T): List<T> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                buildList { while (result.next()) add(result.read()) }
            }
        }

    /** Runs the statement [copy] makes for a partial file, then gives the file its name [target] once it is done. */
    fun writeDone(target: Path, copy: (partial: Path) -> String) {
        val partial = target.resolveSibling("${target.fileName}.part")
        execute(copy(partial))
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * The lines of [text], a gzipped subtitle text, as a parquet file beside it with a `line` column (see
     * [subtitleLines]), written once: a count reads it in steps, as it reads a FineWeb file, where a gzipped text
     * could only be read from its start.
     */
    fun linesOf(text: Path): Path {
        val lines = text.resolveSibling("${text.fileName}.lines.parquet")
        if (!Files.exists(lines)) {
            writeDone(lines) { partial ->
                "COPY (SELECT line FROM ${subtitleLines(text)}) TO ${literal(partial)} (FORMAT parquet)"
            }
        }
        return lines
    }

    /**
     * Counts what [count] tells in its steps: [step] makes the statement that counts the documents from a first one
     * on into a file, and [merge] the one that sums the step files, found by a glob, into another. Calls [progress]
     * after each step and after the merge. Each step keeps its counts until the last one is done, so a count that
     * stopped goes on from its last step, and the target is written only at the end, so a stopped count leaves no
     * file to mistake for a finished one.
     */
    fun countInSteps(
        count: StepCount,
        progress: (CountProgress) -> Unit,
        step: (first: Long, partial: Path) -> String,
        merge: (steps: String, partial: Path) -> String,
    ) {
        val (text, target, stepDocuments) = count
        val stepsFolder = Files.createDirectories(target.resolveSibling("${target.fileName}.steps"))
        val steps = steps(text, stepDocuments)
        for (index in 0 until steps) {
            val stepCounts = stepsFolder.resolve("$index.parquet")
            val took = if (Files.exists(stepCounts)) {
                null
            } else {
                measureTime { writeDone(stepCounts) { step(index.toLong() * stepDocuments, it) } }
            }
            progress(CountProgress.Step(index + 1, steps, took))
        }
        progress(CountProgress.Merged(measureTime { writeDone(target) { merge("$stepsFolder/*.parquet", it) } }))
        stepsFolder.toFile().deleteRecursively()
    }

    /** How many steps of [stepDocuments] documents the FineWeb file [text] takes, the last one maybe shorter. */
    fun steps(text: Path, stepDocuments: Int): Int {
        val documents = query("SELECT num_rows FROM parquet_file_metadata(${literal(text)})") { getLong(1) }.single()
        return ((documents + stepDocuments - 1) / stepDocuments).toInt()
    }

    override fun close() = connection.close()

    companion object {
        /** A DuckDB that spills what does not fit in memory to [temporary]. */
        fun open(temporary: Path): DuckDb =
            DuckDb(DriverManager.getConnection("jdbc:duckdb:")).apply {
                execute("SET temp_directory = ${literal(temporary.resolve("duckdb"))}")
                // By default, DuckDB takes 80% of the PC's memory before it spills. Counting the pairs of 9.5 GB of
                // English subtitles grew it to 12 GB beside the user's apps, Windows paged, and the
                // count crawled; spilling sooner to the cache's disk keeps the PC usable.
                execute("SET memory_limit = '$MEMORY_LIMIT'")
                // The order of the counted rows does not matter, and keeping it costs memory.
                execute("SET preserve_insertion_order = false")
            }
    }
}

/** [value] as an SQL string literal. */
fun literal(value: Any): String = "'" + value.toString().replace("'", "''") + "'"

/** [paths] as an SQL list of file names. */
fun files(paths: List<Path>): String = paths.joinToString(", ", "[", "]") { literal(it) }

/**
 * The lines of [text], a gzipped text file with a sentence on each line, as movie subtitles come, as a table with
 * a `line` column, its apostrophes made one: ’ is written ', and a run of them, as OPUS's English text has in
 * "you''re", counts once. A line with a tab does not fit the one column and is skipped.
 */
fun subtitleLines(text: Path): String =
    """
    (
        SELECT regexp_replace(replace(line, '’', ''''), ${literal("'{2,}")}, '''', 'g') AS line
        FROM read_csv(
            ${literal(text)},
            columns = {'line': 'VARCHAR'}, header = false, delim = ${literal("\t")}, quote = '', escape = '',
            compression = 'gzip', ignore_errors = true
        )
    )
    """

/** The rows of a FineWeb file [text] that a step from the [first] document on reads, [count] of them. */
fun stepRows(text: Path, first: Long, count: Int): String =
    "read_parquet(${literal(text)}, file_row_number = true) " +
        "WHERE file_row_number >= $first AND file_row_number < ${first + count}"
