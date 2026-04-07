package com.musicfinder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.take(10_000)
        if (text.isNullOrBlank()) {
            finish()
            return
        }

        val mentions = MusicDetector.detect(text)

        when {
            // Nothing detected → search the raw text immediately, no UI
            mentions.isEmpty() -> {
                SearchLauncher.searchOnYouTube(this, text.take(200).trim())
                finish()
            }

            // Single HIGH-confidence → launch instantly, no UI
            mentions.size == 1 && mentions.first().confidence == Confidence.HIGH -> {
                SearchLauncher.searchOnYouTube(this, mentions.first().searchQuery)
                finish()
            }

            // Multiple or lower confidence → show sheet; also auto-launch top if HIGH
            else -> {
                if (mentions.first().confidence == Confidence.HIGH) {
                    SearchLauncher.searchOnYouTube(this, mentions.first().searchQuery)
                }
                ResultsBottomSheet.show(supportFragmentManager, mentions, rawText = text)
                supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
                    if (fragment is com.google.android.material.bottomsheet.BottomSheetDialogFragment) {
                        fragment.dialog?.setOnDismissListener { finish() }
                    }
                }
            }
        }
    }
}
