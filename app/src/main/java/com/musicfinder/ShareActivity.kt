package com.musicfinder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.take(10_000)
        if (text.isNullOrBlank()) { finish(); return }

        val mentions = MusicDetector.detect(text)

        when (mentions.size) {
            0 -> {
                // Nothing detected — search raw text directly
                SearchLauncher.searchOnYouTube(this, text.take(200).trim())
            }
            1 -> {
                // Single match — play immediately
                SearchLauncher.searchOnYouTube(this, mentions.first().searchQuery)
            }
            else -> {
                // 2–4 matches — show picker list
                startActivity(Intent(this, PickerActivity::class.java).apply {
                    putParcelableArrayListExtra(PickerActivity.EXTRA_MENTIONS, ArrayList(mentions.map { it.toParcelable() }))
                    putExtra(PickerActivity.EXTRA_RAW, text)
                })
            }
        }
        finish()
    }
}
