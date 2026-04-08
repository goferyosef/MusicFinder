package com.musicfinder

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchService {

    private val PIPED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://piped-api.garudalinux.org"
    )
    private const val TIMEOUT_MS = 5000

    /**
     * Searches all available music outlets in parallel.
     * Subscription apps (Spotify, Tidal, etc.) are listed first.
     * Returns up to 4 results total.
     */
    suspend fun search(query: String, pm: PackageManager): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val installedApps = AppDetector.getInstalled(pm)

            // Build parallel search jobs for each installed app + YouTube via Piped
            val jobs = installedApps.map { app ->
                async { searchForApp(query, app) }
            }

            val allResults = jobs.awaitAll().flatten()
                .distinctBy { it.trackName.lowercase() + it.outlet }
                .take(4)

            allResults
        }

    /** Searches a specific music app for the query and returns up to 2 results for it. */
    private suspend fun searchForApp(query: String, app: MusicApp): List<SearchResult> =
        withContext(Dispatchers.IO) {
            when (app.label) {
                "YouTube", "YouTube Music" -> searchPiped(query, app.label, limit = 2)
                else -> listOf(buildSubscriptionResult(query, app))
            }
        }

    /** Searches YouTube via Piped (free, no API key). Tries multiple filters for best coverage. */
    private fun searchPiped(query: String, outlet: String, limit: Int): List<SearchResult> {
        val filters = listOf("music_songs", "music_videos", "videos")
        for (filter in filters) {
            for (instance in PIPED_INSTANCES) {
                try {
                    val results = queryPiped(instance, query, filter, outlet, limit)
                    if (results.isNotEmpty()) return results
                } catch (_: Exception) {}
            }
        }
        return emptyList()
    }

    private fun queryPiped(baseUrl: String, query: String, filter: String, outlet: String, limit: Int): List<SearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("$baseUrl/search?q=$encoded&filter=$filter")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "MusicFinder/1.0")

        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val items = JSONObject(json).getJSONArray("items")
        val results = mutableListOf<SearchResult>()

        for (i in 0 until items.length()) {
            if (results.size >= limit) break
            val item = items.getJSONObject(i)
            if (item.optString("type") !in listOf("stream", "")) continue

            val title = item.optString("title").ifBlank { null } ?: continue
            val uploader = item.optString("uploader").ifBlank { "" }
            val videoPath = item.optString("url").ifBlank { null } ?: continue
            val videoId = videoPath.substringAfter("v=").substringBefore("&").ifBlank { null }
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)

            val playUrl = if (videoId != null)
                "https://www.youtube.com/watch?v=$videoId&autoplay=1"
            else
                "https://www.youtube.com$videoPath&autoplay=1"

            results.add(SearchResult(
                trackName = title,
                artistName = uploader,
                year = year,
                outlet = outlet,
                playUrl = playUrl,
                videoId = videoId
            ))
        }
        return results
    }

    /**
     * For subscription apps (Spotify, Tidal, Deezer, etc.) we can't search their API
     * without a key, so we create a deep-link result that opens a search in the app.
     * The app auto-plays the top result.
     */
    private fun buildSubscriptionResult(query: String, app: MusicApp): SearchResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val playUrl = when (app.label) {
            "Spotify"      -> "spotify:search:$query"
            "Tidal"        -> "https://tidal.com/search?q=$encoded"
            "Deezer"       -> "deezer://www.deezer.com/search/$encoded"
            "Amazon Music" -> "https://music.amazon.com/search/$encoded"
            else           -> "https://www.google.com/search?q=${encoded}+site:${app.label.lowercase().replace(" ", "")}.com"
        }
        return SearchResult(
            trackName = query,
            artistName = "",
            year = null,
            outlet = app.label,
            playUrl = playUrl
        )
    }

    /** Converts detected MusicMentions into vague fallback results. */
    fun mentionsToVague(mentions: List<MusicMention>): List<SearchResult> =
        mentions.take(4).map { m ->
            val encoded = URLEncoder.encode(m.searchQuery, "UTF-8")
            SearchResult(
                trackName = m.title,
                artistName = m.artist ?: "",
                year = null,
                outlet = "YouTube",
                playUrl = "https://www.youtube.com/results?search_query=$encoded",
                isVague = true
            )
        }
}
