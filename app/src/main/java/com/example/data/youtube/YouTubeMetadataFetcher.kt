package com.example.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class YouTubeMetadata(
    val videoId: String,
    val title: String,
    val channelName: String,
    val fetchedFromNetwork: Boolean
)

/**
 * Parses YouTube URLs and optionally fetches public oEmbed metadata (title & channel)
 * when online. Never claims to have watched a video without transcript/notes.
 */
class YouTubeMetadataFetcher(private val httpClient: OkHttpClient) {

    fun extractVideoId(url: String): String {
        val trimmed = url.trim()
        val patterns = listOf(
            Regex("""(?:v=|/)([0-9A-Za-z_-]{11})(?:[?&/]|$)"""),
            Regex("""youtu\.be/([0-9A-Za-z_-]{11})"""),
            Regex("""youtube\.com/shorts/([0-9A-Za-z_-]{11})"""),
            Regex("""youtube\.com/live/([0-9A-Za-z_-]{11})""")
        )
        for (p in patterns) {
            val match = p.find(trimmed)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }

    suspend fun fetchMetadataIfPossible(rawUrl: String): YouTubeMetadata = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(rawUrl)
        val encoded = URLEncoder.encode(rawUrl.trim(), "UTF-8")
        val oembedUrl = "https://www.youtube.com/oembed?url=$encoded&format=json"

        val result = runCatching {
            val req = Request.Builder().url(oembedUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                YouTubeMetadata(
                    videoId = videoId,
                    title = json.optString("title").trim(),
                    channelName = json.optString("author_name").trim(),
                    fetchedFromNetwork = true
                )
            }
        }
        result.getOrElse {
            YouTubeMetadata(
                videoId = videoId,
                title = "",
                channelName = "",
                fetchedFromNetwork = false
            )
        }
    }
}
