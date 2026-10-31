package name.alexwayfer.preglide.update

import java.io.File
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GitHubUpdatesTest {
    private val server = MockWebServer().apply { start() }
    private val backend = GitHubUpdates(UpdateHttp {}, server.url("/latest").toString())

    @After
    fun stop() = server.close()

    @Test
    fun anEmptyRepositoryAndAnEmptyAssetListHaveNoRelease() = runBlocking {
        server.enqueue(MockResponse(code = 404))
        assertEquals(UpdateResult.Read(null), backend.latest())
        server.enqueue(MockResponse(body = """{"assets":[]}"""))
        assertEquals(UpdateResult.Read(null), backend.latest())
    }

    @Test
    fun malformedAnswersAndRateLimitsAreFailures() = runBlocking {
        server.enqueue(MockResponse(body = "<html>"))
        assertEquals(UpdateResult.Failed(UpdateFailure.InvalidResponse), backend.latest())
        server.enqueue(MockResponse(body = "{}"))
        assertEquals(UpdateResult.Failed(UpdateFailure.InvalidResponse), backend.latest())
        server.enqueue(MockResponse(code = 403))
        assertEquals(UpdateResult.Failed(UpdateFailure.Server), backend.latest())
    }

    @Test
    fun theParsedOfferIsKeptAndADifferentAssetCannotBeDownloaded() = runBlocking {
        server.enqueue(MockResponse(body = """{"assets":[{"id":1,"state":"uploaded","size":3,
            "name":"PreGlide-free-1.1.apk",
            "browser_download_url":"https://github.com/AlexWayfer/PreGlide/releases/download/free-v1.1/test.apk"}]}"""))
        val result = backend.latest() as UpdateResult.Read
        val release = checkNotNull(result.release)
        assertEquals("1.1", release.version.toString())
        assertFalse(backend.download(release.copy(id = 2), File("unused.apk")) {})
    }
}
