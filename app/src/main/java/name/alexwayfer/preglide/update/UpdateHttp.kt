package name.alexwayfer.preglide.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.seconds

private val HTTP_TIMEOUT = 30.seconds
private const val RATE_LIMITED = 429

/** Carries only a status: a URL, a bearer secret, and a response body never enter logs or reports. */
internal class UpdateHttpFailure(val status: Int) : IOException("Update server answered HTTP $status")

internal class UpdateHttp(private val report: (Int) -> Unit) {
    fun json(url: String): String = connection(url, secret = null) { connection ->
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    fun download(
        url: String,
        target: File,
        release: UpdateRelease,
        secret: String? = null,
        progress: (Float) -> Unit,
    ): Boolean = connection(url, secret) { connection ->
        connection.inputStream.use { saveUpdate(it, target, release, progress) }
    }

    private fun <T> connection(url: String, secret: String?, read: (HttpURLConnection) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = HTTP_TIMEOUT.inWholeMilliseconds.toInt()
            connection.readTimeout = HTTP_TIMEOUT.inWholeMilliseconds.toInt()
            // Authenticated downloads never follow a redirect carrying the device secret to another host.
            connection.instanceFollowRedirects = secret == null
            connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            secret?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                val routine = setOf(HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_NOT_FOUND, RATE_LIMITED)
                if (status !in routine) {
                    report(status)
                }
                throw UpdateHttpFailure(status)
            }
            return read(connection)
        } finally {
            connection.disconnect()
        }
    }
}
