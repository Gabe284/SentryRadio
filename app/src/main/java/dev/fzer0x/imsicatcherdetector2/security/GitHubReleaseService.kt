package dev.fzer0x.imsicatcherdetector2.security

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reads releases from the maintained SentryRadio fork, not the upstream/Xposed repo.
 *
 * Release bodies should include the app version in "versionCode-versionName" form
 * (for example, "12-0.5.0"). If that line is absent, the semantic version is
 * extracted from the release tag and compared by VersionComparator's fallback.
 */
class GitHubReleaseService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ReleaseInfo(val version: String, val downloadUrl: String)

    companion object {
        private const val TAG = "GitHubReleaseService"
        private const val RELEASE_API =
            "https://api.github.com/repos/Gabe284/SentryRadio/releases/latest"
        private const val RELEASE_ASSET_PREFIX =
            "https://github.com/Gabe284/SentryRadio/releases/download/"
        private val versionCodeLine = Regex("(?m)^\\s*(\\d+)-(\\d+(?:\\.\\d+)+)\\s*$")
        private val semanticVersion = Regex("v?(\\d+\\.\\d+(?:\\.\\d+)*)", RegexOption.IGNORE_CASE)
    }

    suspend fun fetchLatestRelease(): ReleaseInfo? {
        val request = Request.Builder()
            .url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SentryRadio-UpdateManager")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Release check failed with HTTP ${response.code}")
                    return null
                }

                val json = JSONObject(response.body?.string() ?: return null)
                val tagName = json.optString("tag_name")
                val releaseBody = json.optString("body")
                val assets = json.optJSONArray("assets") ?: return null

                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) &&
                        url.startsWith(RELEASE_ASSET_PREFIX)
                    ) {
                        downloadUrl = url
                        break
                    }
                }

                val versionMatch = versionCodeLine.find(releaseBody)
                val version = if (versionMatch != null) {
                    "${versionMatch.groupValues[1]}-${versionMatch.groupValues[2]}"
                } else {
                    val semantic = semanticVersion.find(tagName)?.groupValues?.get(1)
                        ?: semanticVersion.find(json.optString("name"))?.groupValues?.get(1)
                    semantic?.let { "0-$it" }
                }

                if (version != null && downloadUrl != null) {
                    ReleaseInfo(version, downloadUrl)
                } else {
                    Log.w(TAG, "Latest fork release is missing a parseable version or APK asset")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to check GitHub releases", e)
            null
        }
    }
}
