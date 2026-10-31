package name.alexwayfer.preglide.update

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateHttpTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer().apply { start() }
    private val reported = mutableListOf<Int>()
    private val http = UpdateHttp(reported::add)
    private val release = UpdateRelease(1, checkNotNull(ReleaseVersion.parse("1.1")), 3, null)

    @After
    fun stop() = server.close()

    @Test
    fun aPublicCheckSendsNoAuthorizationAndReadsTheResponse() {
        server.enqueue(MockResponse(body = "{}"))
        assertEquals("{}", http.json(server.url("/latest").toString()))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/latest", request.target)
        assertEquals(null, request.headers["Authorization"])
    }

    @Test
    fun aPremiumDownloadPutsItsSecretOnlyInTheHeader() {
        server.enqueue(MockResponse(body = "apk"))
        val target = folder.root.resolve("update.apk")
        assertTrue(http.download(server.url("/release").toString(), target, release, secret = "fake-secret") {})
        val request = server.takeRequest()
        assertEquals("/release", request.target)
        assertEquals("Bearer fake-secret", request.headers["Authorization"])
        assertEquals("apk", target.readText())
    }

    @Test
    fun aSecretIsNeverForwardedAcrossARedirect() {
        server.enqueue(MockResponse(code = 302, headers = okhttp3.Headers.headersOf(
            "Location", "https://stranger.invalid/release",
        )))
        try {
            http.download(server.url("/release").toString(), folder.root.resolve("update.apk"), release,
                secret = "fake-secret") {}
            error("Redirect was followed")
        } catch (failure: UpdateHttpFailure) {
            assertEquals(302, failure.status)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun serverFailuresAreReportedButAnAbsentFirstReleaseAndRateLimitsAreRoutine() {
        for (code in listOf(404, 403, 429, 500)) {
            server.enqueue(MockResponse(code = code))
            try {
                http.json(server.url("/latest").toString())
                error("HTTP error was accepted")
            } catch (failure: UpdateHttpFailure) {
                assertEquals(code, failure.status)
                assertFalse(failure.message.orEmpty().contains("localhost"))
            }
        }
        assertEquals(listOf(500), reported)
    }
}
