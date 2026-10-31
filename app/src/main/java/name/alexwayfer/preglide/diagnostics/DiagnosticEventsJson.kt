package name.alexwayfer.preglide.diagnostics

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun encodeDiagnosticEvents(events: List<DiagnosticEvent>): String =
    JSONArray().apply {
        events.forEach { event ->
            put(
                JSONObject()
                    .put("kind", event.kind.name)
                    .put("source", event.source)
                    .put("detail", event.detail)
                    .put("message", event.message)
                    .put("firstAt", event.firstAtMillis)
                    .put("lastAt", event.lastAtMillis)
                    .put("count", event.count)
                    .put("recentLog", JSONArray(event.recentLog)),
            )
        }
    }.toString()

/** The events of a journal [text]; a broken journal reads as empty, and an entry of an unknown kind is left out. */
internal fun decodeDiagnosticEvents(text: String): List<DiagnosticEvent> {
    val array = try {
        JSONArray(text)
    } catch (_: JSONException) {
        return emptyList()
    }
    return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::decodeEvent) }
}

private fun decodeEvent(item: JSONObject): DiagnosticEvent? {
    val kind = DiagnosticKind.entries.firstOrNull { it.name == item.optString("kind") } ?: return null
    val firstAt = item.optLong("firstAt")
    return DiagnosticEvent(
        kind = kind,
        source = item.optString("source"),
        detail = item.optString("detail"),
        message = item.optString("message"),
        firstAtMillis = firstAt,
        lastAtMillis = item.optLong("lastAt", firstAt),
        count = item.optInt("count", 1).coerceAtLeast(1),
        recentLog = item.optJSONArray("recentLog")?.let { lines ->
            (0 until lines.length()).map { lines.optString(it) }
        }.orEmpty(),
    )
}
