package com.musicfinder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musicfinder.databinding.ActivityPickerBinding

class PickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPickerBinding

    companion object {
        const val EXTRA_MENTIONS = "mentions"
        const val EXTRA_RAW = "raw_text"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val parcelables: List<MusicMentionParcelable> =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableArrayListExtra(EXTRA_MENTIONS, MusicMentionParcelable::class.java) ?: emptyList()
            else
                intent.getParcelableArrayListExtra(EXTRA_MENTIONS) ?: emptyList()

        val mentions = parcelables.map { it.toMusicMention() }

        binding.headerText.text = "Select music to play (${mentions.size} found)"
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.recyclerView.adapter = MentionsAdapter(mentions) { mention ->
            SearchLauncher.searchOnYouTube(this, mention.searchQuery)
            finish()
        }
    }

    private class MentionsAdapter(
        private val items: List<MusicMention>,
        private val onTap: (MusicMention) -> Unit
    ) : RecyclerView.Adapter<MentionsAdapter.VH>() {

        inner class VH(item: View) : RecyclerView.ViewHolder(item) {
            val title: TextView = item.findViewById(R.id.textTitle)
            val artist: TextView = item.findViewById(R.id.textArtist)
            val context: TextView = item.findViewById(R.id.textContext)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_music_mention, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.artist.text = item.artist ?: ""
            holder.artist.visibility = if (item.artist != null) View.VISIBLE else View.GONE
            holder.context.text = item.context
            holder.itemView.setOnClickListener { onTap(item) }
        }
    }
}
