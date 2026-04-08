package com.musicfinder

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicfinder.databinding.ActivityPickerBinding
import kotlinx.coroutines.launch

class PickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPickerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var spinAnimator: ObjectAnimator? = null
    private var messageIndex = 0

    companion object {
        const val EXTRA_QUERY = "query"
        const val EXTRA_RESULTS = "results"

        private val FUNNY_MESSAGES = listOf(
            "Bribing the DJ…",
            "Waking up the band…",
            "Asking Beethoven nicely…",
            "Checking under the couch…",
            "Tuning the algorithm…",
            "Consulting the oracle…",
            "Pressing play on the universe…",
            "Googling your vibe…",
            "Shazaming the void…",
            "The bassist is lost, one sec…",
            "Flipping through vinyl…",
            "Warming up the synths…"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Camera path: results already built
        @Suppress("DEPRECATION")
        val prebuilt: List<SearchResult>? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableArrayListExtra(EXTRA_RESULTS, SearchResult::class.java)
            else
                intent.getParcelableArrayListExtra(EXTRA_RESULTS)

        if (!prebuilt.isNullOrEmpty()) {
            showResults(prebuilt)
            return
        }

        // Share path: search with animated loading screen
        val text = intent.getStringExtra(EXTRA_QUERY) ?: run { finish(); return }

        // Detect query early so we can show it in the loading header
        val mentions = MusicDetector.detect(text)
        val query = if (mentions.isNotEmpty()) mentions.first().searchQuery
                    else text.take(150).trim()

        startLoadingAnimation(query)

        lifecycleScope.launch {
            val results = MusicSearchService.search(query, packageManager)
            stopLoadingAnimation()

            when {
                // 1–4 results → always show list, let user pick
                results.isNotEmpty() -> showResults(results)

                // Nothing from live search → show detected mentions as vague
                mentions.isNotEmpty() -> showResults(MusicSearchService.mentionsToVague(mentions))

                // Absolute fallback
                else -> {
                    SearchLauncher.searchOnYouTube(this@PickerActivity, query)
                    finish()
                }
            }
        }
    }

    private fun startLoadingAnimation(query: String) {
        binding.loadingBlock.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.headerText.text = "On it: $query"

        spinAnimator = ObjectAnimator.ofFloat(binding.spinningNote, "rotation", 0f, 360f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        scheduleFunnyMessage()
    }

    private fun scheduleFunnyMessage() {
        handler.postDelayed({
            if (!isFinishing) {
                fadeTextTo(FUNNY_MESSAGES[messageIndex % FUNNY_MESSAGES.size])
                messageIndex++
                scheduleFunnyMessage()
            }
        }, 1800)
    }

    private fun fadeTextTo(newText: String) {
        binding.funnyText.animate().alpha(0f).setDuration(250).withEndAction {
            binding.funnyText.text = newText
            binding.funnyText.animate().alpha(1f).setDuration(250).start()
        }.start()
    }

    private fun stopLoadingAnimation() {
        handler.removeCallbacksAndMessages(null)
        spinAnimator?.cancel()
        binding.loadingBlock.visibility = View.GONE
    }

    private fun showResults(results: List<SearchResult>) {
        stopLoadingAnimation()
        binding.recyclerView.visibility = View.VISIBLE
        binding.headerText.text = if (results.any { it.isVague }) "Possible matches" else "Tap to play"
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = ResultsAdapter(results) { result ->
            SearchLauncher.play(this, result)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        spinAnimator?.cancel()
    }

    private class ResultsAdapter(
        private val items: List<SearchResult>,
        private val onTap: (SearchResult) -> Unit
    ) : RecyclerView.Adapter<ResultsAdapter.VH>() {

        inner class VH(item: View) : RecyclerView.ViewHolder(item) {
            val track: TextView = item.findViewById(R.id.textTrack)
            val artist: TextView = item.findViewById(R.id.textArtist)
            val meta: TextView = item.findViewById(R.id.textMeta)
            val vagueLabel: TextView = item.findViewById(R.id.textVague)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.track.text = item.trackName
            holder.artist.text = item.artistName.ifBlank { "" }
            holder.artist.visibility = if (item.artistName.isNotBlank()) View.VISIBLE else View.GONE

            // Show outlet + year
            val meta = listOfNotNull(
                item.outlet,
                item.year
            ).joinToString(" · ")
            holder.meta.text = meta
            holder.meta.visibility = if (meta.isNotBlank()) View.VISIBLE else View.GONE

            holder.vagueLabel.visibility = if (item.isVague) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onTap(item) }
        }
    }
}
