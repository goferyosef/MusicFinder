package com.musicfinder

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SearchResult(
    val trackName: String,
    val artistName: String,
    val year: String?,
    val youtubeUrl: String,        // direct YouTube video URL — guaranteed playable
    val isVague: Boolean = false   // true = fell back from detected text, not from live search
) : Parcelable
