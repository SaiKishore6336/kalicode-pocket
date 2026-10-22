package com.saikishore.kalicodepocket.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikishore.kalicodepocket.databinding.ItemTabBinding

data class TabInfo(val name: String, val dirty: Boolean)

class TabAdapter(
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit,
) : RecyclerView.Adapter<TabAdapter.Holder>() {

    private var items: List<TabInfo> = emptyList()
    private var selected = -1

    fun submit(list: List<TabInfo>, selectedIndex: Int) {
        items = list
        selected = selectedIndex
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], position == selected)
    }

    inner class Holder(private val binding: ItemTabBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TabInfo, isSelected: Boolean) {
            binding.root.isSelected = isSelected
            binding.tabName.text = item.name
            binding.tabName.setTextColor(
                binding.root.context.getColor(
                    if (isSelected) com.saikishore.kalicodepocket.R.color.kali_text
                    else com.saikishore.kalicodepocket.R.color.kali_muted
                )
            )
            binding.tabDot.visibility = if (item.dirty) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onSelect(bindingAdapterPosition) }
            binding.tabClose.setOnClickListener { onClose(bindingAdapterPosition) }
        }
    }
}
