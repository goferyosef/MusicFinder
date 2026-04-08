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

    // Words that indicate a non-music result slipping through (kids content, memes, etc.)
    private val JUNK_KEYWORDS = setOf(
        "nursery", "rhyme", "lullaby", "cartoon", "kids", "children",
        "baby shark", "jelly", "eyeball", "minecraft", "roblox", "fortnite",
        "asmr", "unboxing", "review", "tutorial", "gameplay", "reaction",
        "meme", "compilation", "funny", "prank", "vlogs", "vlog"
    )

    suspend fun search(query: String, pm: PackageManager): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val installedApps = AppDetector.getInstalled(pm)
            val jobs = installedApps.map { app -> async { searchForApp(query, app) } }
            jobs.awaitAll().flatten()
                .distinctBy { it.trackName.lowercase() + it.outlet }
                .take(4)
        }

    private suspend fun searchForApp(query: String, app: MusicApp): List<SearchResult> =
        withContext(Dispatchers.IO) {
            when (app.label) {
                "YouTube", "YouTube Music" -> searchPiped(query, app.label, limit = 2)
                else -> listOf(buildSubscriptionResult(query, app))
            }
        }

    /**
     * Searches YouTube via Piped.
     * Only uses music-specific filters — never falls back to generic video search.
     * Results are relevance-checked against the query before returning.
     */
    private fun searchPiped(query: String, outlet: String, limit: Int): List<SearchResult> {
        // music_songs is most precise; music_videos as fallback — never generic videos
        val filters = listOf("music_songs", "music_videos")
        for (filter in filters) {
            for (instance in PIPED_INSTANCES) {
                try {
                    val results = queryPiped(instance, query, filter, outlet, limit)
                    val relevant = results.filter { isRelevant(it, query) }
                    if (relevant.isNotEmpty()) return relevant
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
                ?: Regex("""\b(19|20)\d{2}\b""").find(title)?.value

            val playUrl = "https://www.youtube.com/watch?v=$videoId&autoplay=1"
                .takeIf { videoId != null }
                ?: "https://www.youtube.com$videoPath&autoplay=1"

            results.add(SearchResult(
                trackName = cleanTitle(title),
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
     * Checks that the result is actually relevant to the query.
     * Rejects junk (kids/gaming/reaction content) and results that share
     * no meaningful words with what was searched.
     */
    private fun isRelevant(result: SearchResult, query: String): Boolean {
        val combined = "${result.trackName} ${result.artistName}".lowercase()

        // Reject known junk
        if (JUNK_KEYWORDS.any { it in combined }) return false

        // At least one significant word from the query must appear in the result
        val queryWords = query.lowercase().split(Regex("\\s+"))
            .filter { it.length > 2 }  // skip short words like "by", "a", "the"
        return queryWords.any { it in combined }
    }

    /** Strips YouTube noise from titles: "(Official Video)", "[HQ]", "4K", etc. */
    private fun cleanTitle(title: String): String =
        title
            .replace(Regex("""\s*[\(\[](Official|Audio|Video|Lyric|HQ|HD|4K|Remaster|Live|Visualizer|Music Video|Topic|Auto-generated)[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\|\s*.+$"""), "")  // remove "| Artist Name" suffix
            .trim()

    private fun buildSubscriptionResult(query: String, app: MusicApp): SearchResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val playUrl = when (app.label) {
            "Spotify"      -> "spotify:search:$query"
            "Tidal"        -> "https://tidal.com/search?q=$encoded"
            "Deezer"       -> "deezer://www.deezer.com/search/$encoded"
            "Amazon Music" -> "https://music.amazon.com/search/$encoded"
            else           -> "https://www.google.com/search?q=$encoded+${URLEncoder.encode(app.label, "UTF-8")}"
        }
        return SearchResult(
            trackName = query,
            artistName = "",
            year = null,
            outlet = app.label,
            playUrl = playUrl
        )
    }

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
