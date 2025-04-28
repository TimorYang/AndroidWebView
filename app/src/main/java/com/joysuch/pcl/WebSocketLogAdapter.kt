package com.joysuch.pcl

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.joysuch.pcl.WebSocketLogManager.LogEntry
import com.joysuch.pcl.WebSocketLogManager.LogType

/**
 * Adapter for displaying WebSocket logs in a RecyclerView
 */
class WebSocketLogAdapter(private var logs: List<LogEntry>) : 
    RecyclerView.Adapter<WebSocketLogAdapter.LogViewHolder>() {
    
    fun updateLogs(newLogs: List<LogEntry>) {
        this.logs = newLogs
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }
    
    override fun getItemCount() = logs.size
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }
    
    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        
        fun bind(log: LogEntry) {
            tvTimestamp.text = log.formattedTime
            tvType.text = log.type.name
            tvMessage.text = log.message
            
            // Set color based on log type
            val colorRes = when(log.type) {
                LogType.ERROR -> R.color.log_error
                LogType.SEND -> R.color.log_send
                LogType.RECEIVE -> R.color.log_receive
                LogType.CONNECTION -> R.color.log_connection
                LogType.INFO -> R.color.log_info
            }
            
            tvType.setTextColor(ContextCompat.getColor(itemView.context, colorRes))
            
            // Show details if available
            if (log.details.isEmpty()) {
                tvDetails.visibility = View.GONE
            } else {
                tvDetails.visibility = View.VISIBLE
                tvDetails.text = log.details
            }
            
            // Toggle details visibility on click
            itemView.setOnClickListener {
                if (tvDetails.visibility == View.VISIBLE) {
                    tvDetails.visibility = View.GONE
                } else if (log.details.isNotEmpty()) {
                    tvDetails.visibility = View.VISIBLE
                }
            }
        }
    }
} 