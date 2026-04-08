package com.musicfinder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchService {

    // Piped: open-source YouTube proxy, free, no API key.
    // Multiple instances for reliability.
    private val PIPED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://piped-api.garudalinux.org"
    )

    private const val TIMEOUT_MS = 5000

    /**
     * Searches YouTube (via Piped) for music matching [query].
     * Returns up to [limit] results — every entry has a direct, playable YouTube URL.
     */
    suspend fun search(query: String, limit: Int = 5): List<SearchResult> =
        withContext(Dispatchers.IO) {
            for (instance in PIPED_INSTANCES) {
                try {
                    val results = queryPiped(instance, query, limit)
                    if (results.isNotEmpty()) return@withContext results
                } catch (_: Exception) {
                    // try next instance
                }
            }
            emptyList()
        }

    private fun queryPiped(baseUrl: String, query: String, limit: Int): List<SearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // filter=music_songs → only music videos on YouTube
        val url = URL("$baseUrl/search?q=$encoded&filter=music_songs")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "MusicFinder/1.0")

        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val items = JSONObject(json).getJSONArray("items")

        return (0 until minOf(items.length(), limit)).mapNotNull { i ->
            val item = items.getJSONObject(i)
            val title = item.optString("title").ifBlank { null } ?: return@mapNotNull null
            val uploader = item.optString("uploader").ifBlank { "" }
            val videoPath = item.optString("url").ifBlank { null } ?: return@mapNotNull null
            // videoPath is "/watch?v=VIDEO_ID"
            val youtubeUrl = "https://www.youtube.com$videoPath"

            // Some titles include year in parentheses e.g. "Yesterday (1965 Remaster)"
            val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)

            SearchResult(
                trackName = title,
                artistName = uploader,
                year = year,
                youtubeUrl = youtubeUrl
            )
        }
    }

    /** Converts detected MusicMentions into vague results when live search fails. */
    fun mentionsToVague(mentions: List<MusicMention>): List<SearchResult> =
        mentions.map { m ->
            SearchResult(
                trackName = m.title,
                artistName = m.artist ?: "",
                year = null,
                youtubeUrl = "https://www.youtube.com/results?search_query=${
                    URLEncoder.encode(m.searchQuery, "UTF-8")}",
                isVague = true
            )
        }
}
