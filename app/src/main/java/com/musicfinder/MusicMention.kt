package com.musicfinder

enum class Confidence { HIGH, MEDIUM, LOW }

data class MusicMention(
    val title: String,
    val artist: String? = null,
    val context: String,
    val searchQuery: String,
    val confidence: Confidence = Confidence.MEDIUM
)
