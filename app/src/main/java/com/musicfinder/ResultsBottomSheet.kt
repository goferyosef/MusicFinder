package com.musicfinder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.musicfinder.databinding.BottomSheetResultsBinding

class ResultsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetResultsBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "ResultsBottomSheet"
        private const val ARG_MENTIONS = "mentions"
        private const val ARG_RAW = "raw_text"

        fun show(
            manager: FragmentManager,
            mentions: List<MusicMention>,
            rawText: String
        ) {
            val sheet = ResultsBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_MENTIONS, ArrayList(mentions.map { it.toParcelable() }))
                    putString(ARG_RAW, rawText)
                }
            }
            sheet.show(manager, TAG)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val parcelables = arguments?.getParcelableArrayList<MusicMentionParcelable>(ARG_MENTIONS) ?: emptyList<MusicMentionParcelable>()
        val mentions = parcelables.map { it.toMusicMention() }
        val rawText = arguments?.getString(ARG_RAW) ?: ""

        if (mentions.isEmpty()) {
            binding.headerText.text = "No music mentions detected"
            binding.recyclerView.visibility = View.GONE
            binding.emptyHint.visibility = View.VISIBLE
        } else {
            binding.headerText.text = if (mentions.size == 1) "1 music mention found" else "${mentions.size} music mentions found"
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyHint.visibility = View.GONE
            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerView.adapter = MentionsAdapter(mentions) { mention ->
                SearchLauncher.searchOnYouTube(requireContext(), mention.searchQuery)
                dismiss()
            }
        }

        // Fallback: search the raw selected text directly
        binding.searchRawButton.setOnClickListener {
            val query = rawText.take(200).trim()
            if (query.isNotBlank()) {
                SearchLauncher.searchOnYouTube(requireContext(), query)
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Adapter ---

    private class MentionsAdapter(
        private val items: List<MusicMention>,
        private val onTap: (MusicMention) -> Unit
    ) : RecyclerView.Adapter<MentionsAdapter.VH>() {

        inner class VH(item: View) : RecyclerView.ViewHolder(item) {
            val title: TextView = item.findViewById(R.id.textTitle)
            val artist: TextView = item.findViewById(R.id.textArtist)
            val context: TextView = item.findViewById(R.id.textContext)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_music_mention, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.artist.text = item.artist ?: ""
            holder.artist.visibility = if (item.artist != null) View.VISIBLE else View.GONE
            holder.context.text = "\"${item.context}\""
            holder.itemView.setOnClickListener { onTap(item) }
        }
    }
}
