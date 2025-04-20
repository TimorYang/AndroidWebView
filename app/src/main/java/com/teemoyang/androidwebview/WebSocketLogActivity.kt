package com.teemoyang.androidwebview

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.teemoyang.androidwebview.databinding.ActivityWebsocketLogBinding
import android.view.LayoutInflater
import android.view.Menu
import com.teemoyang.androidwebview.WebSocketLogManager.LogType

class WebSocketLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebsocketLogBinding
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: WebSocketLogAdapter
    private lateinit var clearLogsButton: FloatingActionButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityWebsocketLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置自定义的Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "WebSocket日志"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Setup RecyclerView
        setupViews()
        observeLogs()
    }
    
    private fun setupViews() {
        logRecyclerView = binding.recyclerView
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        
        logAdapter = WebSocketLogAdapter(WebSocketLogManager.getInstance().getLogs().value ?: emptyList())
        logRecyclerView.adapter = logAdapter
        
        clearLogsButton = binding.clearButton
        clearLogsButton.setOnClickListener {
            WebSocketLogManager.getInstance().clearLogs()
            Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun observeLogs() {
        WebSocketLogManager.getInstance().getLogs().observe(this) { logs ->
            logAdapter.updateLogs(logs)
            if (logs.isNotEmpty()) {
                logRecyclerView.scrollToPosition(logs.size - 1) // 滚动到最新的日志（最后一条）
                binding.emptyView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.VISIBLE
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_clear -> {
                WebSocketLogManager.getInstance().clearLogs()
                Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.websocket_log_menu, menu)
        return true
    }
    
    // 日志适配器
    private inner class WebSocketLogAdapter(private var logs: List<WebSocketLogManager.LogEntry>) :
        RecyclerView.Adapter<WebSocketLogAdapter.LogViewHolder>() {
        
        fun updateLogs(newLogs: List<WebSocketLogManager.LogEntry>) {
            this.logs = newLogs
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log, parent, false)
            return LogViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(logs[position])
        }
        
        override fun getItemCount() = logs.size
        
        inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
            private val tvType: TextView = itemView.findViewById(R.id.tvType)
            private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
            private val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
            
            fun bind(logEntry: WebSocketLogManager.LogEntry) {
                tvTimestamp.text = logEntry.formattedTime
                tvType.text = logEntry.type.name
                tvMessage.text = logEntry.message
                
                // 如果有详细信息，显示详细信息区域
                if (logEntry.details.isNotEmpty()) {
                    tvDetails.visibility = View.VISIBLE
                    tvDetails.text = logEntry.details
                } else {
                    tvDetails.visibility = View.GONE
                }
                
                // 根据日志类型设置不同的背景色
                val backgroundColor = when (logEntry.type) {
                    LogType.ERROR -> R.color.logErrorBackground
                    LogType.SEND -> R.color.logSendBackground
                    LogType.RECEIVE -> R.color.logReceiveBackground
                    LogType.CONNECTION -> R.color.logConnectionBackground
                    else -> R.color.logInfoBackground
                }
                
                itemView.setBackgroundResource(backgroundColor)
            }
        }
    }
} 