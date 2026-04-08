package com.musicfinder

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SearchResult(
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val year: String?,
    val youtubeQuery: String,
    val isVague: Boolean = false   // true = fell back from API, shown as "possible match"
) : Parcelable
