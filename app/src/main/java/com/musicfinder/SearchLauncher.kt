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

    // --- YouTube: try app direct video, then Brave with autoplay ---
    private fun playYouTube(context: Context, result: SearchResult) {
        // 1. YouTube app — opens video and auto-plays immediately
        if (result.videoId != null) {
            val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${result.videoId}")).apply {
                setPackage(YOUTUBE_PACKAGE)
                putExtra("force_fullscreen", false)
            }
            @Suppress("DEPRECATION")
            if (ytIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(ytIntent)
                return
            }
        }
        // 2. Brave browser with autoplay=1
        openUrl(context, result.playUrl)
    }

    // --- YouTube Music app ---
    private fun playYouTubeMusic(context: Context, result: SearchResult) {
        if (result.videoId != null) {
            val ytmIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://music.youtube.com/watch?v=${result.videoId}")).apply {
                setPackage(YOUTUBE_MUSIC_PACKAGE)
            }
            @Suppress("DEPRECATION")
            if (ytmIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(ytmIntent)
                return
            }
        }
        openUrl(context, result.playUrl)
    }

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
