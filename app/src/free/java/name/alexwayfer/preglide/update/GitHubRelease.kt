package name.alexwayfer.preglide.update

import org.json.JSONException
import org.json.JSONObject

internal const val GITHUB_RELEASES = "https://github.com/AlexWayfer/PreGlide/releases"
private const val GITHUB_DOWNLOAD = "$GITHUB_RELEASES/download/"
private val apkName = Regex("PreGlide-free-([0-9]+(?:\\.[0-9]+)+)\\.apk")
private val sha256Digest = Regex("[a-f0-9]{64}")

internal data class GitHubRelease(val release: UpdateRelease, val url: String)

/** Only an uploaded free APK from this repository is an update, regardless of other attached files. */
internal fun githubRelease(text: String): GitHubRelease? {
    val assets = try {
        JSONObject(text).optJSONArray("assets")
    } catch (_: JSONException) {
        null
    } ?: return null
    return (0 until assets.length()).firstNotNullOfOrNull { index ->
        assets.optJSONObject(index)?.let(::githubAsset)
    }
}

private fun githubAsset(asset: JSONObject): GitHubRelease? {
    val name = apkName.matchEntire(asset.optString("name"))
    val version = name?.groupValues?.get(1)?.let(ReleaseVersion::parse)
    val url = asset.optString("browser_download_url")
    val id = asset.optLong("id")
    val size = asset.optLong("size")
    val rawDigest = asset.optString("digest")
    val digest = rawDigest.removePrefix("sha256:").lowercase()
    val digestValid = rawDigest.isEmpty() || rawDigest.startsWith("sha256:") && sha256Digest.matches(digest)
    val uploaded = asset.optString("state") == "uploaded" && url.startsWith(GITHUB_DOWNLOAD)
    val metadataValid = id > 0 && size > 0 && digestValid
    return if (uploaded && version != null && metadataValid) {
        GitHubRelease(UpdateRelease(id, version, size, digest.takeIf(sha256Digest::matches)), url)
    } else null
}
