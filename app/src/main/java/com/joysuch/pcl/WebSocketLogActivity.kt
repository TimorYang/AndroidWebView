package com.joysuch.pcl

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
import com.joysuch.pcl.databinding.ActivityWebsocketLogBinding
import android.view.LayoutInflater
import android.view.Menu
import com.joysuch.pcl.WebSocketLogManager.LogType
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context

class WebSocketLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebsocketLogBinding
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: WebSocketLogAdapter
    private lateinit var clearLogsButton: FloatingActionButton
    private lateinit var scrollToBottomButton: FloatingActionButton
    
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
        
        // 显示提示，告诉用户可以点击条目复制
        Toast.makeText(this, "点击日志条目可复制内容", Toast.LENGTH_LONG).show()
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
        
        // 添加滚动到底部按钮的处理
        scrollToBottomButton = binding.scrollToBottomButton
        scrollToBottomButton.setOnClickListener {
            val logs = WebSocketLogManager.getInstance().getLogs().value ?: emptyList()
            if (logs.isNotEmpty()) {
                logRecyclerView.smoothScrollToPosition(logs.size - 1)
            }
        }
    }
    
    private fun observeLogs() {
        WebSocketLogManager.getInstance().getLogs().observe(this) { logs ->
            logAdapter.updateLogs(logs)
            if (logs.isNotEmpty()) {
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
            
            init {
                // 设置点击整个条目的监听器
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val logEntry = logs[position]
                        copyToClipboard(logEntry)
                    }
                }
            }
            
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
    
    /**
     * 将日志条目复制到剪贴板
     */
    private fun copyToClipboard(logEntry: WebSocketLogManager.LogEntry) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val formattedLog = formatLogEntryForClipboard(logEntry)
        
        val clipData = ClipData.newPlainText("WebSocket日志", formattedLog)
        clipboardManager.setPrimaryClip(clipData)
        
        // 显示提示
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 格式化日志条目用于剪贴板
     */
    private fun formatLogEntryForClipboard(logEntry: WebSocketLogManager.LogEntry): String {
        val sb = StringBuilder()
        sb.append("[${logEntry.formattedTime}] ${logEntry.type.name}: ${logEntry.message}")
        
        if (logEntry.details.isNotEmpty()) {
            sb.append("\n详情: ${logEntry.details}")
        }
        
        return sb.toString()
    }
} 