package com.musicfinder

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MusicMentionParcelable(
    val title: String,
    val artist: String?,
    val context: String,
    val searchQuery: String,
    val confidence: String  // Confidence enum as string for parceling
) : Parcelable

fun MusicMention.toParcelable() = MusicMentionParcelable(title, artist, context, searchQuery, confidence.name)
fun MusicMentionParcelable.toMusicMention() = MusicMention(title, artist, context, searchQuery, Confidence.valueOf(confidence))
