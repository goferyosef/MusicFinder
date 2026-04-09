package com.musicfinder

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

object MusicSearchService {

    private val PIPED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://piped-api.garudalinux.org",
        "https://api.piped.projectsegfau.lt",
        "https://pipedapi.ducks.party"
    )
    private val INVIDIOUS_INSTANCES = listOf(
        "https://inv.nadeko.net",
        "https://invidious.privacyredirect.com",
        "https://yewtu.be",
        "https://invidious.nerdvpn.de"
    )
    private const val TIMEOUT_MS = 5000

    private val JUNK_KEYWORDS = setOf(
        "nursery", "rhyme", "lullaby", "cartoon", "kids", "children",
        "baby shark", "jelly", "eyeball", "minecraft", "roblox", "fortnite",
        "asmr", "unboxing", "review", "tutorial", "gameplay", "reaction",
        "meme", "compilation", "funny", "prank", "vlogs", "vlog"
    )

    private val DEFAULT_APPS = listOf(
        MusicApp("com.google.android.youtube", "YouTube", 6)
    )

    suspend fun search(query: String, pm: PackageManager): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val installedApps = AppDetector.getInstalled(pm).ifEmpty { DEFAULT_APPS }
            val jobs = installedApps.map { app -> async { searchForApp(query, app) } }
            jobs.awaitAll().flatten()
                .distinctBy { it.trackName.lowercase() + it.outlet }
                .sortedByDescending { relevanceScore(it, query) }
                .take(5)
        }

    private fun relevanceScore(result: SearchResult, query: String): Int {
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        val combined = "${result.trackName} ${result.artistName}".lowercase()
        return queryWords.count { it in combined }
    }

    private suspend fun searchForApp(query: String, app: MusicApp): List<SearchResult> =
        withContext(Dispatchers.IO) {
            when (app.label) {
                "YouTube", "YouTube Music" -> searchYouTube(query, app.label, limit = 3)
                else -> listOf(buildSubscriptionResult(query, app))
            }
        }

    /**
     * Search order:
     *  1. YouTube Data API v3 (official, reliable — requires key in local.properties)
     *  2. Piped (community proxy, music filters)
     *  3. Invidious (community proxy, general search)
     */
    private suspend fun searchYouTube(query: String, outlet: String, limit: Int): List<SearchResult> =
        withContext(Dispatchers.IO) {
            // 1. Official YouTube Data API — always try first
            val ytResults = searchYouTubeDataAPI(query, outlet, limit)
            if (ytResults.isNotEmpty()) return@withContext ytResults

            // 2. Piped (music_songs → music_videos → videos), all instances in parallel
            val pipedFilters = listOf("music_songs", "music_videos", "videos")
            var bestJunkFree = emptyList<SearchResult>()

            for (filter in pipedFilters) {
                val jobs = PIPED_INSTANCES.map { instance ->
                    async {
                        try { queryPiped(instance, query, filter, outlet, limit) }
                        catch (_: Exception) { emptyList() }
                    }
                }
                val raw = jobs.awaitAll().firstOrNull { it.isNotEmpty() } ?: continue
                val nonJunk = raw.filter { notJunk(it) }
                val relevant = nonJunk.filter { isRelevant(it, query) }
                if (relevant.isNotEmpty()) return@withContext relevant
                if (nonJunk.isNotEmpty() && bestJunkFree.isEmpty()) bestJunkFree = nonJunk
            }
            if (bestJunkFree.isNotEmpty()) return@withContext bestJunkFree

            // 3. Invidious fallback
            queryInvidious(query, outlet, limit)
        }

    // ── YouTube Data API v3 ────────────────────────────────────────────────

    private fun searchYouTubeDataAPI(query: String, outlet: String, limit: Int): List<SearchResult> {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey.isBlank() || apiKey == "PASTE_YOUR_YOUTUBE_KEY_HERE") return emptyList()
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // videoCategoryId=10 = Music
            val url = URL(
                "https://www.googleapis.com/youtube/v3/search" +
                "?key=$apiKey&q=$encoded&part=snippet&type=video" +
                "&videoCategoryId=10&maxResults=$limit&safeSearch=none"
            )
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
                val videoId = item.getJSONObject("id").optString("videoId").ifBlank { null } ?: continue
                val snippet = item.getJSONObject("snippet")
                val title = snippet.optString("title").ifBlank { null } ?: continue
                val artist = snippet.optString("channelTitle").ifBlank { "" }
                if (!notJunk(title, artist)) continue
                val year = Regex("""^(\d{4})""").find(snippet.optString("publishedAt"))?.value

                results.add(SearchResult(
                    trackName = cleanTitle(title),
                    artistName = artist,
                    year = year,
                    outlet = outlet,
                    playUrl = "https://www.youtube.com/watch?v=$videoId&autoplay=1",
                    videoId = videoId
                ))
            }
            results
        } catch (_: Exception) { emptyList() }
    }

    // ── Piped ──────────────────────────────────────────────────────────────

    private fun queryPiped(
        baseUrl: String, query: String, filter: String, outlet: String, limit: Int
    ): List<SearchResult> {
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
            val artist = item.optString("uploaderName").ifBlank { item.optString("uploader") }
            val videoPath = item.optString("url").ifBlank { null } ?: continue
            val videoId = extractVideoId(videoPath)

            results.add(SearchResult(
                trackName = cleanTitle(title),
                artistName = artist,
                year = extractYear(title),
                outlet = outlet,
                playUrl = buildYouTubeUrl(videoId, videoPath),
                videoId = videoId
            ))
        }
        return results
    }

    // ── Invidious ──────────────────────────────────────────────────────────

    private suspend fun queryInvidious(query: String, outlet: String, limit: Int): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val jobs = INVIDIOUS_INSTANCES.map { instance ->
                async {
                    try {
                        val encoded = URLEncoder.encode(query, "UTF-8")
                        val url = URL("$instance/api/v1/search?q=$encoded&type=video&page=1")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = TIMEOUT_MS
                        conn.readTimeout = TIMEOUT_MS
                        conn.setRequestProperty("User-Agent", "MusicFinder/1.0")
                        val json = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()

                        val items = JSONArray(json)
                        val results = mutableListOf<SearchResult>()
                        for (i in 0 until items.length()) {
                            if (results.size >= limit) break
                            val item = items.getJSONObject(i)
                            if (item.optString("type") != "video") continue
                            val title = item.optString("title").ifBlank { null } ?: continue
                            val artist = item.optString("author").ifBlank { "" }
                            val videoId = item.optString("videoId").ifBlank { null } ?: continue
                            if (!notJunk(title, artist)) continue

                            val published = item.optLong("published")
                            val year = if (published > 0)
                                Calendar.getInstance().apply { timeInMillis = published * 1000 }
                                    .get(Calendar.YEAR).toString()
                            else extractYear(title)

                            results.add(SearchResult(
                                trackName = cleanTitle(title),
                                artistName = artist,
                                year = year,
                                outlet = outlet,
                                playUrl = "https://www.youtube.com/watch?v=$videoId&autoplay=1",
                                videoId = videoId
                            ))
                        }
                        results
                    } catch (_: Exception) { emptyList() }
                }
            }
            jobs.awaitAll().firstOrNull { it.isNotEmpty() } ?: emptyList()
        }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun notJunk(result: SearchResult) = notJunk(result.trackName, result.artistName)
    private fun notJunk(title: String, artist: String): Boolean {
        val combined = "$title $artist".lowercase()
        return JUNK_KEYWORDS.none { it in combined }
    }

    private fun isRelevant(result: SearchResult, query: String): Boolean {
        if (!notJunk(result)) return false
        val combined = "${result.trackName} ${result.artistName}".lowercase()
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        return queryWords.any { it in combined }
    }

    private fun extractVideoId(path: String): String? = when {
        path.contains("v=") -> path.substringAfter("v=").substringBefore("&").ifBlank { null }
        path.contains("/watch/") -> path.substringAfterLast("/").ifBlank { null }
        else -> null
    }

    private fun buildYouTubeUrl(videoId: String?, fallbackPath: String): String =
        if (videoId != null) "https://www.youtube.com/watch?v=$videoId&autoplay=1"
        else "https://www.youtube.com$fallbackPath&autoplay=1"

    private fun extractYear(title: String): String? =
        Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)
            ?: Regex("""\b(19|20)\d{2}\b""").find(title)?.value

    private fun cleanTitle(title: String): String =
        title
            .replace(Regex("""\s*[\(\[](Official|Audio|Video|Lyric|HQ|HD|4K|Remaster|Live|Visualizer|Music Video|Topic|Auto-generated)[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\|\s*.+$"""), "")
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
        mentions.take(5).map { m ->
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
