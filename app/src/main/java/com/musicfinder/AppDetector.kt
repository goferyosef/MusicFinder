package com.musicfinder

import android.content.pm.PackageManager

data class MusicApp(
    val packageName: String,
    val label: String,
    val priority: Int          // lower = searched first
)

object AppDetector {

    private val KNOWN_APPS = listOf(
        MusicApp("com.spotify.music",                      "Spotify",       1),
        MusicApp("com.google.android.apps.youtube.music",  "YouTube Music", 2),
        MusicApp("com.aspiro.tidal",                       "Tidal",         3),
        MusicApp("deezer.android.app",                     "Deezer",        4),
        MusicApp("com.amazon.mp3",                         "Amazon Music",  5),
        MusicApp("com.google.android.youtube",             "YouTube",       6)
    )

    /** Returns installed music apps sorted by priority (subscriptions first). */
    fun getInstalled(pm: PackageManager): List<MusicApp> =
        KNOWN_APPS.filter { app ->
            try { pm.getPackageInfo(app.packageName, 0); true }
            catch (_: PackageManager.NameNotFoundException) { false }
        }.sortedBy { it.priority }

    /** True if any premium subscription app (Spotify, Tidal, Deezer, Amazon, YT Music) is installed. */
    fun hasSubscriptionApp(pm: PackageManager): Boolean =
        getInstalled(pm).any { it.priority <= 5 }
}
