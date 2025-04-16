package com.teemoyang.androidwebview

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager {
    private val TAG = "WebSocketManager"
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var listener: WebSocketListener? = null
    private var wsUrl: String = ""
    private var isConnected = false
    private var deviceId: String = ""
    
    // 断线重连相关变量
    private var enableReconnect = true // 是否启用断线重连
    private var reconnectCount = 0 // 重连次数
    private val maxReconnectAttempts = 5 // 最大重连次数
    private val reconnectIntervals = longArrayOf(3000, 6000, 10000, 15000, 30000) // 重连间隔（毫秒）
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var callback: WebSocketCallback? = null
    private var lastConnectionParams: Map<String, String>? = null
    
    // 接口用于向外部传递WebSocket事件
    interface WebSocketCallback {
        fun onMessage(data: JSONObject)
        fun onClose()
        fun onError(error: String)
    }
    
    // 初始化并连接WebSocket
    fun init(params: Map<String, String>, callback: WebSocketCallback) {
        wsUrl = params["url"] ?: ""
        deviceId = params["deviceId"] ?: ""
        val connectionType = params["connectionType"]?.toIntOrNull() ?: 0 // 默认使用第一种连接方式
        this.callback = callback
        this.lastConnectionParams = params
        
        // 基于不同的连接类型构建URL
        when (connectionType) {
            0 -> {
                // 方式1: 直接使用原始URL
                // 例如: https://navmobiletest.joysuch.com
                // 不做任何协议转换
            }
            1 -> {
                // 方式2: 使用WSS协议，添加/ws路径
                if (!wsUrl.startsWith("ws")) {
                    wsUrl = "wss://${wsUrl.removePrefix("https://").removePrefix("http://")}/ws"
                }
            }
            2 -> {
                // 方式3: 使用WSS协议，添加/websocket路径
                if (!wsUrl.startsWith("ws")) {
                    wsUrl = "wss://${wsUrl.removePrefix("https://").removePrefix("http://")}/websocket"
                }
            }
            3 -> {
                // 方式4: 使用WSS协议，不添加额外路径
                if (!wsUrl.startsWith("ws")) {
                    wsUrl = "wss://${wsUrl.removePrefix("https://").removePrefix("http://")}"
                }
            }
        }
        
        // 添加设备ID参数
        wsUrl = if (wsUrl.contains("?")) {
            "$wsUrl&deviceId=$deviceId"
        } else {
            "$wsUrl?deviceId=$deviceId"
        }
        
        Log.d(TAG, "连接WebSocket: $wsUrl")
        
        // 创建OkHttpClient
        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // 保持连接活跃
            .build()
        
        // 创建WebSocket监听器
        listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket连接成功")
                isConnected = true
                reconnectCount = 0 // 重置重连计数
                cancelReconnect() // 取消任何待执行的重连任务
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到消息: $text")
                try {
                    val jsonObject = JSONObject(text)
                    callback.onMessage(jsonObject)
                } catch (e: Exception) {
                    Log.e(TAG, "解析消息失败: ${e.message}")
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接关闭中: $code, $reason")
                isConnected = false
                callback.onClose()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接已关闭: $code, $reason")
                isConnected = false
                
                // 如果是正常关闭则不重连
                if (code == 1000 && reason == "正常关闭") {
                    Log.d(TAG, "正常关闭，不进行重连")
                    return
                }
                
                // 尝试重连
                if (enableReconnect) {
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "连接失败: ${t.message}")
                // 添加更详细的错误信息
                if (response != null) {
                    Log.e(TAG, "HTTP状态码: ${response.code}")
                    Log.e(TAG, "HTTP消息: ${response.message}")
                    try {
                        val responseBody = response.body?.string()
                        Log.e(TAG, "响应体: $responseBody")
                        
                        // 构建详细错误信息
                        val errorDetail = StringBuilder()
                        errorDetail.append("连接失败 - HTTP ${response.code}")
                        if (responseBody != null && responseBody.isNotEmpty()) {
                            errorDetail.append(", 服务器响应: $responseBody")
                        } else {
                            errorDetail.append(", ${t.message ?: "未知错误"}")
                        }
                        
                        callback.onError(errorDetail.toString())
                        return@onFailure
                    } catch (e: Exception) {
                        Log.e(TAG, "无法读取响应体: ${e.message}")
                    }
                }
                
                isConnected = false
                callback.onError("连接错误: ${t.message ?: "未知错误"}")
                
                // 连接失败时尝试重连
                if (enableReconnect) {
                    scheduleReconnect()
                }
            }
        }
        
        // 创建并连接WebSocket
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        webSocket = client?.newWebSocket(request, listener!!)
    }
    
    // 发送消息
    fun send(status: String, to: String, data: Any): Boolean {
        val message = JSONObject()
        message.put("status", status)
        message.put("to", to)
        message.put("from", deviceId)
        message.put("data", data)
        
        return sendMessage(message.toString())
    }
    
    // 发送原始消息
    private fun sendMessage(message: String): Boolean {
        if (webSocket != null && isConnected) {
            Log.d(TAG, "发送消息: $message")
            return webSocket!!.send(message)
        }
        Log.e(TAG, "发送消息失败：WebSocket未连接")
        return false
    }
    
    // 关闭连接
    fun close() {
        enableReconnect = false // 禁用重连
        cancelReconnect() // 取消待执行的重连
        
        webSocket?.close(1000, "正常关闭")
        webSocket = null
        client = null
        isConnected = false
    }
    
    // 检查是否已连接
    fun isConnected(): Boolean {
        return isConnected
    }
    
    // 启用或禁用自动重连
    fun setEnableReconnect(enable: Boolean) {
        this.enableReconnect = enable
        
        if (!enable) {
            cancelReconnect()
        }
    }
    
    // 安排重连任务
    private fun scheduleReconnect() {
        if (reconnectCount >= maxReconnectAttempts) {
            Log.w(TAG, "已达到最大重连次数: $maxReconnectAttempts")
            return
        }
        
        cancelReconnect() // 取消之前的重连任务
        
        // 获取当前重连间隔
        val delay = reconnectIntervals[Math.min(reconnectCount, reconnectIntervals.size - 1)]
        
        Log.d(TAG, "计划在 ${delay}ms 后进行第 ${reconnectCount + 1} 次重连")
        
        reconnectRunnable = Runnable {
            Log.d(TAG, "执行第 ${reconnectCount + 1} 次重连")
            reconnectCount++
            
            // 使用最后的参数重新初始化连接
            lastConnectionParams?.let { params ->
                init(params, callback!!)
            }
        }
        
        mainHandler.postDelayed(reconnectRunnable!!, delay)
    }
    
    // 取消重连任务
    private fun cancelReconnect() {
        reconnectRunnable?.let {
            mainHandler.removeCallbacks(it)
            reconnectRunnable = null
        }
    }
    
    // 手动触发重连
    fun reconnect() {
        if (lastConnectionParams != null && callback != null) {
            cancelReconnect() // 取消任何待执行的重连
            
            // 关闭现有连接但不禁用重连
            webSocket?.close(1000, "手动重连")
            webSocket = null
            client = null
            isConnected = false
            
            // 立即重连
            Log.d(TAG, "执行手动重连")
            init(lastConnectionParams!!, callback!!)
        } else {
            Log.e(TAG, "无法重连：没有连接参数或回调")
        }
    }
} 