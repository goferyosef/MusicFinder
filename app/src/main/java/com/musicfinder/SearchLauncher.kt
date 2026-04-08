package com.musicfinder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object SearchLauncher {

    private const val BRAVE_PACKAGE = "com.brave.browser"

    /** Opens a direct YouTube video URL (from Piped search results). */
    fun play(context: Context, result: SearchResult) =
        open(context, result.youtubeUrl)

    /** Falls back to a YouTube search URL when no direct video is available. */
    fun searchOnYouTube(context: Context, query: String) =
        open(context, "https://www.youtube.com/results?search_query=${Uri.encode(query)}")

    private fun open(context: Context, url: String) {
        require(url.startsWith("https://")) { "Only HTTPS URLs allowed" }
        val uri = Uri.parse(url)

        // Brave browser (ad-free) — preferred
        val braveIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(BRAVE_PACKAGE) }
        if (braveIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(braveIntent)
            return
        }

        // Fallback: system default browser
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }
}
