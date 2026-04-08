package com.musicfinder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.take(10_000)
        if (text.isNullOrBlank()) { finish(); return }

        // Hand off to PickerActivity immediately — it has a stable window
        // and handles the search + loading state itself.
        startActivity(Intent(this, PickerActivity::class.java).apply {
            putExtra(PickerActivity.EXTRA_QUERY, text)
        })
        finish()
    }
}
