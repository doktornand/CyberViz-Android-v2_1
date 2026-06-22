package com.cyberviz

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ModeAdapter(
    private val modes: List<ProcessingMode>,
    private var selected: ProcessingMode,
    private val onSelect: (ProcessingMode) -> Unit
) : RecyclerView.Adapter<ModeAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        v.setPadding(24, 8, 24, 8)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val mode = modes[position]
        holder.label.text = mode.displayName
        holder.label.setTextColor(if (mode == selected) Color.CYAN else Color.WHITE)
        holder.label.textSize = 12f
        holder.itemView.setOnClickListener {
            selected = mode
            onSelect(mode)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = modes.size
}
