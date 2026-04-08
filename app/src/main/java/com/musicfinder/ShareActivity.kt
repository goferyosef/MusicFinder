package com.musicfinder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.take(10_000)
        if (text.isNullOrBlank()) { finish(); return }

        val mentions = MusicDetector.detect(text)
        val query = if (mentions.isNotEmpty()) mentions.first().searchQuery
                    else text.take(150).trim()

        lifecycleScope.launch {
            val results = MusicSearchService.search(query)

            when {
                // 1 clear result — play immediately
                results.size == 1 -> {
                    SearchLauncher.play(this@ShareActivity, results.first())
                    finish()
                }

                // 2–5 results — show picker
                results.size in 2..5 -> {
                    openPicker(results)
                }

                // API returned nothing — fall back to detected mentions as vague results
                results.isEmpty() && mentions.isNotEmpty() -> {
                    val vague = MusicSearchService.mentionsToVague(mentions)
                    if (vague.size == 1) {
                        SearchLauncher.searchOnYouTube(this@ShareActivity, vague.first().youtubeQuery)
                        finish()
                    } else {
                        openPicker(vague)
                    }
                }

                // API returned nothing and no mentions — search raw text
                else -> {
                    SearchLauncher.searchOnYouTube(this@ShareActivity, query)
                    finish()
                }
            }
        }
    }

    private fun openPicker(results: List<SearchResult>) {
        startActivity(Intent(this, PickerActivity::class.java).apply {
            putParcelableArrayListExtra(PickerActivity.EXTRA_RESULTS, ArrayList(results))
        })
        finish()
    }
}
