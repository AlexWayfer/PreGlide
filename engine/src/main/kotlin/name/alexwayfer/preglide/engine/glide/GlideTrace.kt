package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.layout.KeyPoint
import java.util.Locale

private const val ROW_HEIGHT = "rowHeight="
private const val TRACE = " trace="

/**
 * The points of [glide] in key widths across and rows down, and how tall a row is, as text a test reads the glide
 * back from with [glideOfTrace]: for tuning glides on the user's real paths.
 */
fun glideTrace(glide: Glide): String {
    val line = glide.line
    val points = (0 until line.size).joinToString(";") {
        String.format(Locale.ROOT, "%.2f,%.2f", line.xs[it], line.ys[it] / glide.rowHeight)
    }
    return String.format(Locale.ROOT, "%s%.3f%s%s", ROW_HEIGHT, glide.rowHeight, TRACE, points)
}

/** The glide [text], made by [glideTrace], holds. */
fun glideOfTrace(text: String): Glide {
    val rowHeight = text.substringAfter(ROW_HEIGHT).substringBefore(' ').toFloat()
    val points = text.substringAfter(TRACE).trim().split(';').map { point ->
        val (x, y) = point.split(',').map(String::toFloat)
        KeyPoint(x, y)
    }
    return Glide(points, rowHeight)
}
