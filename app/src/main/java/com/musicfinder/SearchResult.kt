package com.musicfinder

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SearchResult(
    val trackName: String,
    val artistName: String,
    val year: String?,
    val outlet: String,        // "YouTube", "Spotify", "YouTube Music", etc.
    val playUrl: String,       // URL or app URI — opened directly on tap
    val videoId: String? = null, // YouTube video ID for direct app launch
    val isVague: Boolean = false
) : Parcelable
