package com.musicfinder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object SearchLauncher {

    private const val BRAVE_PACKAGE = "com.brave.browser"
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    fun searchOnYouTube(context: Context, query: String) {
        val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        require(url.startsWith("https://")) { "Only HTTPS URLs allowed" }

        // 1. Try YouTube app directly (fastest, ad-free search within the app)
        val youtubeAppIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(YOUTUBE_PACKAGE)
            putExtra("query", query)
        }
        if (youtubeAppIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(youtubeAppIntent)
            return
        }

        // 2. Try Brave browser (ad-blocking)
        val braveIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(BRAVE_PACKAGE)
        }
        if (braveIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(braveIntent)
            return
        }

        // 3. Fallback: system default browser
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "No browser found to open the search", Toast.LENGTH_SHORT).show()
        }
    }
}
