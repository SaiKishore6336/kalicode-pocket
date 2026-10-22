package com.saikishore.kalicodepocket.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saikishore.kalicodepocket.R
import com.saikishore.kalicodepocket.data.Entry
import com.saikishore.kalicodepocket.databinding.ItemFileBinding

class FileAdapter(
    private val onFile: (Entry) -> Unit,
    private val onDirectory: (Entry) -> Unit,
    private val onLong: (Entry) -> Unit,
) : ListAdapter<Entry, FileAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: Entry) {
            binding.fileName.text = entry.name
            binding.fileIcon.setImageResource(
                if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
            )
            binding.fileIcon.imageTintList = binding.root.context.getColorStateList(
                if (entry.isDirectory) R.color.kali_accent else R.color.kali_muted
            )
            binding.fileMeta.text = if (entry.isDirectory) {
                ""
            } else {
                Formatter.formatShortFileSize(binding.root.context, entry.size)
            }
            binding.fileChevron.visibility = if (entry.isDirectory) View.VISIBLE else View.GONE
            binding.root.setOnClickListener {
                if (entry.isDirectory) onDirectory(entry) else onFile(entry)
            }
            binding.root.setOnLongClickListener {
                onLong(entry)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Entry>() {
            override fun areItemsTheSame(oldItem: Entry, newItem: Entry): Boolean =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: Entry, newItem: Entry): Boolean =
                oldItem == newItem
        }
    }
}
