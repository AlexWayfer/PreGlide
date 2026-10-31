package name.alexwayfer.preglide.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseTest {
    private val assetId = 7L
    private val digest = "a".repeat(64)
    private fun release(name: String = "PreGlide-free-1.1.0.apk", state: String = "uploaded") = """
        {"assets":[{"id":$assetId,"name":"$name","state":"$state","size":3,
        "browser_download_url":"https://github.com/AlexWayfer/PreGlide/releases/download/free-v1.1.0/$name",
        "digest":"sha256:$digest"}]}
    """.trimIndent()

    @Test
    fun onlyTheFinishedFreeApkFromThePublicRepositoryIsOffered() {
        val apk = checkNotNull(githubRelease(release()))
        assertEquals(assetId, apk.release.id)
        assertEquals("1.1.0", apk.release.version.toString())
        assertEquals(digest, apk.release.sha256)
        assertNull(githubRelease(release("PreGlide-premium-1.1.0.apk")))
        assertNull(githubRelease(release(state = "new")))
        assertNull(githubRelease(release().replace("https://github.com/", "https://stranger.invalid/")))
        assertNull(githubRelease("<html>"))
        assertNull(githubRelease("{}"))
    }
}
