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
        ResultsBottomSheet.show(supportFragmentManager, mentions, rawText = text)

        // Finish when the bottom sheet is dismissed
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment is com.google.android.material.bottomsheet.BottomSheetDialogFragment) {
                fragment.dialog?.setOnDismissListener { finish() }
            }
        }
    }
}
