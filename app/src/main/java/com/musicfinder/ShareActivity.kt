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
        autoPlayOrShowSheet(mentions, text)
    }

    private fun autoPlayOrShowSheet(mentions: List<MusicMention>, rawText: String) {
        val top = mentions.firstOrNull()

        // Single HIGH-confidence match → launch instantly, no UI shown
        if (mentions.size == 1 && top?.confidence == Confidence.HIGH) {
            SearchLauncher.searchOnYouTube(this, top.searchQuery)
            finish()
            return
        }

        // Any HIGH-confidence match when multiple results → auto-launch the best one,
        // but still show the sheet so the user can pick a different one if needed
        if (top?.confidence == Confidence.HIGH) {
            SearchLauncher.searchOnYouTube(this, top.searchQuery)
            // Don't finish — show sheet for any remaining mentions
        }

        // LOW/MEDIUM or multiple → show bottom sheet
        ResultsBottomSheet.show(supportFragmentManager, mentions, rawText = rawText)
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment is com.google.android.material.bottomsheet.BottomSheetDialogFragment) {
                fragment.dialog?.setOnDismissListener { finish() }
            }
        }
    }
}
