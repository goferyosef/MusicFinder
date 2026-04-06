package com.musicfinder

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MusicMentionParcelable(
    val title: String,
    val artist: String?,
    val context: String,
    val searchQuery: String
) : Parcelable

fun MusicMention.toParcelable() = MusicMentionParcelable(title, artist, context, searchQuery)
fun MusicMentionParcelable.toMusicMention() = MusicMention(title, artist, context, searchQuery)
