package com.musicfinder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchService {

    private const val TIMEOUT_MS = 4000

    /**
     * Searches the iTunes API (free, no key) for the given query.
     * Returns up to [limit] results with track name, artist, album, and year.
     * Falls back to an empty list on any network error.
     */
    suspend fun search(query: String, limit: Int = 5): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=$limit")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("User-Agent", "MusicFinder/1.0")

                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val root = JSONObject(json)
                val items = root.getJSONArray("results")

                (0 until items.length()).mapNotNull { i ->
                    val item = items.getJSONObject(i)
                    val track = item.optString("trackName").ifBlank { null } ?: return@mapNotNull null
                    val artist = item.optString("artistName").ifBlank { "" }
                    val album = item.optString("collectionName").ifBlank { null }
                    val year = item.optString("releaseDate").take(4).ifBlank { null }

                    SearchResult(
                        trackName = track,
                        artistName = artist,
                        albumName = album,
                        year = year,
                        youtubeQuery = "$track $artist"
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** Converts detected MusicMentions into vague SearchResults for fallback display. */
    fun mentionsToVague(mentions: List<MusicMention>): List<SearchResult> =
        mentions.map { m ->
            SearchResult(
                trackName = m.title,
                artistName = m.artist ?: "",
                albumName = null,
                year = null,
                youtubeQuery = m.searchQuery,
                isVague = true
            )
        }
}
