package com.musicfinder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicfinder.databinding.ActivityPickerBinding
import kotlinx.coroutines.launch

class PickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPickerBinding

    companion object {
        const val EXTRA_QUERY = "query"           // raw shared text
        const val EXTRA_RESULTS = "results"       // pre-built results (from camera)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Camera path: results already built, show immediately
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

        // Share path: do the search here with a loading indicator
        val text = intent.getStringExtra(EXTRA_QUERY) ?: run { finish(); return }
        showLoading()

        lifecycleScope.launch {
            val mentions = MusicDetector.detect(text)
            val query = if (mentions.isNotEmpty()) mentions.first().searchQuery
                        else text.take(150).trim()

            val results = MusicSearchService.search(query)

            when {
                results.size == 1 -> {
                    // Single match — play directly, no list needed
                    SearchLauncher.play(this@PickerActivity, results.first())
                    finish()
                }
                results.size in 2..5 -> showResults(results)
                results.isEmpty() && mentions.isNotEmpty() -> {
                    val vague = MusicSearchService.mentionsToVague(mentions)
                    if (vague.size == 1) {
                        SearchLauncher.play(this@PickerActivity, vague.first())
                        finish()
                    } else {
                        showResults(vague)
                    }
                }
                else -> {
                    // Nothing found at all — open YouTube search and close
                    SearchLauncher.searchOnYouTube(this@PickerActivity, query)
                    finish()
                }
            }
        }
    }

    private fun showLoading() {
        binding.headerText.text = "Searching…"
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showResults(results: List<SearchResult>) {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE

        val hasVague = results.any { it.isVague }
        binding.headerText.text = if (hasVague) "Possible matches — tap to play"
                                   else "Tap to play"

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = ResultsAdapter(results) { result ->
            SearchLauncher.play(this, result)
            finish()
        }
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
            holder.artist.text = item.artistName
            holder.artist.visibility = if (item.artistName.isNotBlank()) View.VISIBLE else View.GONE
            val meta = listOfNotNull(item.year).joinToString(" · ")
            holder.meta.text = meta
            holder.meta.visibility = if (meta.isNotBlank()) View.VISIBLE else View.GONE
            holder.vagueLabel.visibility = if (item.isVague) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onTap(item) }
        }
    }
}
