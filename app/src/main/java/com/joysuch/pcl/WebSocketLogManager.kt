package com.joysuch.pcl

import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import androidx.lifecycle.MutableLiveData

/**
 * WebSocketLogManager - Manages logs for WebSocket connections and operations
 * Uses a singleton pattern for global access
 */
class WebSocketLogManager private constructor() {
    
    private val logs = mutableListOf<LogEntry>()
    private val MAX_LOG_SIZE = 500 // Maximum number of logs to keep
    private val logsLiveData = MutableLiveData<List<LogEntry>>()
    
    companion object {
        private const val TAG = "WebSocketLogManager"
        private var instance: WebSocketLogManager? = null
        
        fun getInstance(): WebSocketLogManager {
            if (instance == null) {
                instance = WebSocketLogManager()
            }
            return instance!!
        }
    }
    
    enum class LogType {
        INFO, ERROR, SEND, RECEIVE, CONNECTION
    }
    
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val type: LogType,
        val message: String,
        val details: String = "",
        val formattedTime: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    )
    
    /**
     * Add a log entry
     */
    fun addLog(type: LogType, message: String, details: String? = null) {
        val log = LogEntry(System.currentTimeMillis(), type, message, details ?: "")
        logs.add(log) // Add to the end for chronological order (oldest first)
        
        // Trim log if it exceeds the maximum size
        if (logs.size > MAX_LOG_SIZE) {
            logs.removeAt(0) // Remove oldest entry
        }
        
        // Update LiveData
        logsLiveData.postValue(logs.toList())
        
        // Also log to Android's logcat
        when (type) {
            LogType.ERROR -> Log.e(TAG, message + (details?.let { ": $it" } ?: ""))
            LogType.INFO -> Log.i(TAG, message + (details?.let { ": $it" } ?: ""))
            LogType.CONNECTION -> Log.d(TAG, "CONNECTION: " + message + (details?.let { ": $it" } ?: ""))
            LogType.SEND -> Log.d(TAG, "SEND: " + message + (details?.let { ": $it" } ?: ""))
            LogType.RECEIVE -> Log.d(TAG, "RECEIVE: " + message + (details?.let { ": $it" } ?: ""))
        }
    }
    
    /**
     * Log connection state change
     */
    fun logConnectionState(connected: Boolean, url: String? = null) {
        if (connected) {
            addLog(LogType.CONNECTION, "Connected", url)
        } else {
            addLog(LogType.CONNECTION, "Disconnected", url)
        }
    }
    
    /**
     * Log message sent
     */
    fun logSend(message: String) {
        addLog(LogType.SEND, "Message sent", message)
    }
    
    /**
     * Log message received
     */
    fun logReceive(message: String) {
        addLog(LogType.RECEIVE, "Message received", message)
    }
    
    /**
     * Log an error
     */
    fun logError(message: String, throwable: Throwable? = null) {
        val details = throwable?.message ?: "No details available"
        addLog(LogType.ERROR, message, details)
    }
    
    /**
     * Get all logs
     */
    fun getAllLogs(): List<LogEntry> {
        return logs.toList()
    }
    
    /**
     * Get logs of a specific type
     */
    fun getLogsByType(type: LogType): List<LogEntry> {
        return logs.filter { it.type == type }
    }
    
    /**
     * Get logs as LiveData for observation
     */
    fun getLogs(): MutableLiveData<List<LogEntry>> {
        return logsLiveData
    }
    
    /**
     * Clear all logs
     */
    fun clearLogs() {
        logs.clear()
        logsLiveData.postValue(emptyList())
    }
    
    /**
     * Format a log entry to a readable string
     */
    fun formatLogEntry(entry: LogEntry): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val date = dateFormat.format(Date(entry.timestamp))
        val type = entry.type.name
        
        return "[$date] $type: ${entry.message}${entry.details.let { if (it.isNotEmpty()) "\nDetails: $it" else "" }}"
    }
    
    /**
     * Export all logs as a formatted string
     */
    fun exportLogsAsString(): String {
        val stringBuilder = StringBuilder()
        logs.forEach { log -> // No need to reverse since logs are already in chronological order
            stringBuilder.append(formatLogEntry(log))
            stringBuilder.append("\n\n")
        }
        return stringBuilder.toString()
    }
} 