package com.joysuch.pcl

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WSLogAdapter : RecyclerView.Adapter<WSLogAdapter.LogViewHolder>() {
    
    enum class LogType {
        INFO, ERROR, SEND, RECEIVE, CONNECTION
    }
    
    data class LogItem(
        val timestamp: Long = System.currentTimeMillis(),
        val type: LogType,
        val message: String,
        val details: String? = null
    )
    
    private val logs = mutableListOf<LogItem>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun addLog(type: LogType, message: String, details: String? = null) {
        val logItem = LogItem(System.currentTimeMillis(), type, message, details)
        logs.add(0, logItem) // Add to the beginning of the list
        notifyItemInserted(0)
    }
    
    fun clear() {
        val size = logs.size
        logs.clear()
        notifyItemRangeRemoved(0, size)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val logItem = logs[position]
        holder.bind(logItem)
    }
    
    override fun getItemCount(): Int = logs.size
    
    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView as CardView
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val logItem = logs[position]
                    if (!logItem.details.isNullOrEmpty()) {
                        val isVisible = tvDetails.visibility == View.VISIBLE
                        tvDetails.visibility = if (isVisible) View.GONE else View.VISIBLE
                    }
                }
            }
        }
        
        fun bind(logItem: LogItem) {
            tvTimestamp.text = dateFormat.format(Date(logItem.timestamp))
            
            val context = itemView.context
            
            // Set type text and color
            when (logItem.type) {
                LogType.INFO -> {
                    tvType.text = "INFO"
                    tvType.setTextColor(ContextCompat.getColor(context, R.color.log_info))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.logInfoBackground))
                }
                LogType.ERROR -> {
                    tvType.text = "ERROR"
                    tvType.setTextColor(ContextCompat.getColor(context, R.color.log_error))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.logErrorBackground))
                }
                LogType.SEND -> {
                    tvType.text = "SEND"
                    tvType.setTextColor(ContextCompat.getColor(context, R.color.log_send))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.logSendBackground))
                }
                LogType.RECEIVE -> {
                    tvType.text = "RECEIVE"
                    tvType.setTextColor(ContextCompat.getColor(context, R.color.log_receive))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.logReceiveBackground))
                }
                LogType.CONNECTION -> {
                    tvType.text = "CONNECTION"
                    tvType.setTextColor(ContextCompat.getColor(context, R.color.log_connection))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.logConnectionBackground))
                }
            }
            
            tvMessage.text = logItem.message
            
            if (logItem.details.isNullOrEmpty()) {
                tvDetails.visibility = View.GONE
            } else {
                tvDetails.text = logItem.details
                tvDetails.visibility = View.GONE // Initially hidden
            }
        }
    }
} 