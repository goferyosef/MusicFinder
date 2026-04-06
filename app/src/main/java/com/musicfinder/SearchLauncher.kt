package com.musicfinder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object SearchLauncher {

    private const val BRAVE_PACKAGE = "com.brave.browser"

    fun searchOnYouTube(context: Context, query: String) {
        val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        val uri = Uri.parse(url)

        // Try Brave first (ad-free)
        val braveIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(BRAVE_PACKAGE)
        }

        @Suppress("DEPRECATION")
        if (braveIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(braveIntent)
        } else {
            // Fallback: system default browser
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: Exception) {
                Toast.makeText(context, "No browser found to open the search", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
