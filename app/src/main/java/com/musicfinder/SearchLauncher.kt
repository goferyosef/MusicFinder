package com.musicfinder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object SearchLauncher {

    private const val BRAVE_PACKAGE = "com.brave.browser"
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
    private const val SPOTIFY_PACKAGE = "com.spotify.music"

    fun play(context: Context, result: SearchResult) {
        when (result.outlet) {
            "YouTube" -> playYouTube(context, result)
            "YouTube Music" -> playYouTubeMusic(context, result)
            "Spotify" -> playSpotify(context, result)
            else -> openUrl(context, result.playUrl)
        }
    }

    fun searchOnYouTube(context: Context, query: String) =
        openUrl(context, "https://www.youtube.com/results?search_query=${Uri.encode(query)}")

    // --- YouTube / YouTube Music: in-app WebView player (autoplay with sound) ---
    // Falls back to Brave only for vague/search URLs that have no video ID.
    private fun playYouTube(context: Context, result: SearchResult) {
        if (result.videoId != null) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_VIDEO_ID, result.videoId)
            )
        } else {
            openUrl(context, result.playUrl)
        }
    }

    private fun playYouTubeMusic(context: Context, result: SearchResult) =
        playYouTube(context, result)

    // --- Spotify: deep-link opens app and searches ---
    private fun playSpotify(context: Context, result: SearchResult) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.playUrl)).apply {
                setPackage(SPOTIFY_PACKAGE)
            }
            @Suppress("DEPRECATION")
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}
        openUrl(context, result.playUrl)
    }

    private fun openUrl(context: Context, url: String) {
        require(url.startsWith("https://") || url.startsWith("spotify:")) { "Invalid URL" }
        val uri = Uri.parse(url)

        // Prefer Brave (ad-free)
        if (url.startsWith("https://")) {
            val braveIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(BRAVE_PACKAGE) }
            @Suppress("DEPRECATION")
            if (braveIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(braveIntent)
                return
            }
        }

        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open player", Toast.LENGTH_SHORT).show()
        }
    }
}
