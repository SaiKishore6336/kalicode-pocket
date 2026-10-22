package com.saikishore.kalicodepocket.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saikishore.kalicodepocket.R
import com.saikishore.kalicodepocket.data.RecentProject
import com.saikishore.kalicodepocket.databinding.ItemRecentBinding

class RecentAdapter(
    private val onClick: (RecentProject) -> Unit,
    private val onLongClick: (RecentProject) -> Unit,
) : ListAdapter<RecentProject, RecentAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemRecentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemRecentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(project: RecentProject) {
            binding.recentName.text = project.name
            val whenText = DateUtils.getRelativeTimeSpanString(
                project.openedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            val type = if (project.type == "sample") "Playground" else "Folder"
            binding.recentMeta.text = "$type  ·  $whenText"
            binding.recentIcon.setImageResource(
                if (project.type == "sample") R.drawable.ic_terminal else R.drawable.ic_folder
            )
            binding.root.setOnClickListener { onClick(project) }
            binding.root.setOnLongClickListener {
                onLongClick(project)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecentProject>() {
            override fun areItemsTheSame(oldItem: RecentProject, newItem: RecentProject): Boolean =
                oldItem.type == newItem.type && oldItem.uri == newItem.uri

            override fun areContentsTheSame(oldItem: RecentProject, newItem: RecentProject): Boolean =
                oldItem == newItem
        }
    }
}
