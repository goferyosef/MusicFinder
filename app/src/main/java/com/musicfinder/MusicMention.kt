package com.musicfinder

data class MusicMention(
    val title: String,
    val artist: String? = null,
    val context: String,       // surrounding sentence shown in the UI
    val searchQuery: String    // pre-built query: "title artist" or just "title"
)
