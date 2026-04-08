package com.musicfinder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicfinder.databinding.ActivityPickerBinding

class PickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPickerBinding

    companion object {
        const val EXTRA_RESULTS = "results"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val results: List<SearchResult> =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableArrayListExtra(EXTRA_RESULTS, SearchResult::class.java) ?: emptyList()
            else
                intent.getParcelableArrayListExtra(EXTRA_RESULTS) ?: emptyList()

        val hasVague = results.any { it.isVague }
        binding.headerText.text = if (hasVague) "Possible matches — tap to play"
                                   else "Select to play"

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = ResultsAdapter(results) { result ->
            SearchLauncher.searchOnYouTube(this, result.youtubeQuery)
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
            holder.artist.text = item.artistName.ifBlank { "" }
            holder.artist.visibility = if (item.artistName.isNotBlank()) View.VISIBLE else View.GONE

            val meta = listOfNotNull(item.albumName, item.year).joinToString(" · ")
            holder.meta.text = meta
            holder.meta.visibility = if (meta.isNotBlank()) View.VISIBLE else View.GONE

            holder.vagueLabel.visibility = if (item.isVague) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onTap(item) }
        }
    }
}
