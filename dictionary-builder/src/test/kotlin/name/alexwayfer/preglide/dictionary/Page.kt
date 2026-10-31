package name.alexwayfer.preglide.dictionary

import java.nio.file.Path
import java.sql.DriverManager

/** A page of a FineWeb file: its id, text, and the Common Crawl dump it comes from. */
data class Page(val id: String, val text: String, val dump: String = "CC-MAIN-2025-26")

/** Writes [pages] to [target] as a FineWeb parquet file, with the columns the builder reads. */
fun writeFineWeb(target: Path, pages: List<Page>): Path {
    val rows = pages.joinToString(", ") { "(${literal(it.id)}, ${literal(it.text)}, ${literal(it.dump)})" }
    duckDb { "COPY (SELECT * FROM (VALUES $rows) AS pages(id, text, dump)) TO ${literal(target)} (FORMAT parquet)" }
    return target
}

/** The rows of the counts file [counts], as "form initial occurrences documents", sorted. */
fun readCounts(counts: Path): List<String> =
    duckDb(
        "SELECT form, initial, occurrences, documents FROM read_parquet(${literal(counts)}) ORDER BY form, initial",
    )

private fun duckDb(statement: () -> String) {
    DriverManager.getConnection("jdbc:duckdb:").use { connection ->
        connection.createStatement().use { it.execute(statement()) }
    }
}

private fun duckDb(query: String): List<String> =
    DriverManager.getConnection("jdbc:duckdb:").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(query).use { result ->
                val columns = 1..result.metaData.columnCount
                buildList {
                    while (result.next()) add(columns.joinToString(" ") { result.getString(it) })
                }
            }
        }
    }
