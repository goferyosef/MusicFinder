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
                finish()
            }
            1 -> {
                // Single match — play immediately
                SearchLauncher.searchOnYouTube(this, mentions.first().searchQuery)
                finish()
            }
            else -> {
                // 2–4 matches — show picker
                ResultsBottomSheet.show(supportFragmentManager, mentions, rawText = text)
                supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
                    if (fragment is com.google.android.material.bottomsheet.BottomSheetDialogFragment)
                        fragment.dialog?.setOnDismissListener { finish() }
                }
            }
        }
    }
}
