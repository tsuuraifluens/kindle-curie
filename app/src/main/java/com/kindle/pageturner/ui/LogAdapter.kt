package com.kindle.pageturner.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kindle.pageturner.R
import com.kindle.pageturner.diagnostics.LogEntry
import com.kindle.pageturner.diagnostics.LogLevel

class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(DiffCallback) {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tvLogTime)
        val tvLevel: TextView = itemView.findViewById(R.id.tvLogLevel)
        val tvCategory: TextView = itemView.findViewById(R.id.tvLogCategory)
        val tvMessage: TextView = itemView.findViewById(R.id.tvLogMessage)
        val tvDetails: TextView = itemView.findViewById(R.id.tvLogDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvTime.text = item.formattedTime
        holder.tvLevel.text = "[${item.level.name.first()}]"
        holder.tvCategory.text = "[${item.category.name}]"
        holder.tvMessage.text = item.message

        val levelColor = when (item.level) {
            LogLevel.DEBUG -> Color.GRAY
            LogLevel.INFO -> Color.rgb(25, 118, 210)  // Blue
            LogLevel.WARN -> Color.rgb(239, 108, 0)   // Orange
            LogLevel.ERROR -> Color.rgb(198, 40, 40)  // Red
        }
        holder.tvLevel.setTextColor(levelColor)

        if (!item.details.isNullOrEmpty()) {
            holder.tvDetails.text = item.details
            holder.tvDetails.visibility = View.VISIBLE
        } else {
            holder.tvDetails.visibility = View.GONE
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
