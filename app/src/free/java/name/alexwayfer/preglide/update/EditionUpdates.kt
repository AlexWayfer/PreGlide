package name.alexwayfer.preglide.update

import java.io.File
import java.net.HttpURLConnection
import org.json.JSONException
import org.json.JSONObject
import kotlin.time.Duration.Companion.days
import name.alexwayfer.preglide.PreGlideApplication

internal val UPDATE_INTERVAL = 1.days
internal val UPDATE_RELEASES_URL: String? = GITHUB_RELEASES
internal val followUpdateAccess: (PreGlideApplication) -> Unit = { }
private const val GITHUB_LATEST = "https://api.github.com/repos/AlexWayfer/PreGlide/releases/latest"

internal val editionUpdates: (PreGlideApplication) -> UpdateBackend = { GitHubUpdates(updateHttp()) }

internal class GitHubUpdates(
    private val http: UpdateHttp,
    private val latestUrl: String = GITHUB_LATEST,
) : UpdateBackend {
    private var offered: GitHubRelease? = null

    override suspend fun latest(): UpdateResult = try {
        val text = http.json(latestUrl)
        if (!JSONObject(text).has("assets")) throw JSONException("No release assets")
        offered = githubRelease(text)
        UpdateResult.Read(offered?.release)
    } catch (error: UpdateHttpFailure) {
        if (error.status == HttpURLConnection.HTTP_NOT_FOUND) UpdateResult.Read(null)
        else UpdateResult.Failed(UpdateFailure.Server)
    } catch (_: JSONException) {
        UpdateResult.Failed(UpdateFailure.InvalidResponse)
    }

    override suspend fun download(release: UpdateRelease, target: File, progress: (Float) -> Unit): Boolean {
        val asset = offered?.takeIf { it.release == release } ?: return false
        return http.download(asset.url, target, release, progress = progress)
    }
}
