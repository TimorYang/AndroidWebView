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
        this.callback = callback
        this.lastConnectionParams = params
        
        // 添加设备ID参数
        wsUrl = if (wsUrl.contains("?")) {
            "$wsUrl&deviceId=$deviceId"
        } else {
            "$wsUrl?deviceId=$deviceId"
        }
        
        Log.d(TAG, "连接WebSocket: $wsUrl")
        // 记录连接尝试
        WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.INFO, "连接WebSocket", wsUrl)
        
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
                
                // 记录连接成功
                WebSocketLogManager.getInstance().logConnectionState(true, wsUrl)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到消息: $text")
                try {
                    val jsonObject = JSONObject(text)
                    callback.onMessage(jsonObject)
                    
                    // 记录接收到的消息
                    WebSocketLogManager.getInstance().logReceive(text)
                } catch (e: Exception) {
                    Log.e(TAG, "解析消息失败: ${e.message}")
                    
                    // 记录解析错误
                    WebSocketLogManager.getInstance().logError("解析消息失败", e)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接关闭中: $code, $reason")
                isConnected = false
                callback.onClose()
                
                // 记录连接正在关闭
                WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.CONNECTION, "连接关闭中", "代码=$code, 原因=$reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接已关闭: $code, $reason")
                isConnected = false
                
                // 记录连接已关闭
                WebSocketLogManager.getInstance().logConnectionState(false, "代码=$code, 原因=$reason")
                
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
                        
                        // 记录连接失败
                        WebSocketLogManager.getInstance().logError(errorDetail.toString(), t)
                        return@onFailure
                    } catch (e: Exception) {
                        Log.e(TAG, "无法读取响应体: ${e.message}")
                        
                        // 记录错误
                        WebSocketLogManager.getInstance().logError("无法读取响应体", e)
                    }
                }
                
                isConnected = false
                val errorMsg = "连接错误: ${t.message ?: "未知错误"}"
                callback.onError(errorMsg)
                
                // 记录连接错误
                WebSocketLogManager.getInstance().logError(errorMsg, t)
                
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
        
        // 记录发送的消息
        WebSocketLogManager.getInstance().logSend(message.toString())
        
        return sendMessage(message.toString())
    }
    
    // 发送原始消息
    private fun sendMessage(message: String): Boolean {
        if (webSocket != null && isConnected) {
            Log.d(TAG, "发送消息: $message")
            return webSocket!!.send(message)
        }
        Log.e(TAG, "发送消息失败：WebSocket未连接")
        
        // 记录发送失败
        WebSocketLogManager.getInstance().logError("发送消息失败：WebSocket未连接")
        return false
    }
    
    // 关闭连接
    fun close() {
        enableReconnect = false // 禁用重连
        cancelReconnect() // 取消待执行的重连
        
        WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.INFO, "正在关闭WebSocket连接")
        webSocket?.close(1000, "正常关闭")
        webSocket = null
        client = null
        isConnected = false
    }
    
    /**
     * WebSocket向服务端发送数据
     * @param type 消息类型
     * @param to 接收者
     * @param data 数据对象
     * @param status 可选状态参数，默认为"1"
     * @return 是否发送成功
     */
    fun webSocketSend(type: String, to: String, data: Any, status: String = "1"): Boolean {
        // 构建消息对象
        val message = JSONObject().apply {
            put("messageType", type)
            put("status", status)
            put("deviceId", deviceId)
            put("to", to)
            put("from", "WeChat")
            put("data", data)
        }
        
        Log.d(TAG, "发送WebSocket消息: $message")
        
        // 记录发送的消息
        WebSocketLogManager.getInstance().logSend(message.toString())
        
        return sendMessage(message.toString())
    }
    
    // 检查是否已连接
    fun isConnected(): Boolean {
        return isConnected
    }
    
    // 启用或禁用自动重连
    fun setEnableReconnect(enable: Boolean) {
        this.enableReconnect = enable
        
        WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.INFO, "自动重连功能已" + (if (enable) "启用" else "禁用"))
        
        if (!enable) {
            cancelReconnect()
        }
    }
    
    // 安排重连任务
    private fun scheduleReconnect() {
        if (reconnectCount >= maxReconnectAttempts) {
            Log.w(TAG, "已达到最大重连次数: $maxReconnectAttempts")
            WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.ERROR, "已达到最大重连次数: $maxReconnectAttempts，不再尝试重连")
            return
        }
        
        cancelReconnect() // 取消之前的重连任务
        
        // 获取当前重连间隔
        val delay = reconnectIntervals[Math.min(reconnectCount, reconnectIntervals.size - 1)]
        
        Log.d(TAG, "计划在 ${delay}ms 后进行第 ${reconnectCount + 1} 次重连")
        WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.INFO, "计划在 ${delay/1000}秒后进行第 ${reconnectCount + 1} 次重连")
        
        reconnectRunnable = Runnable {
            Log.d(TAG, "执行第 ${reconnectCount + 1} 次重连")
            WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.INFO, "执行第 ${reconnectCount + 1} 次重连")
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
            WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.INFO, "取消待执行的重连任务")
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
            
            WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.INFO, "执行手动重连")
            // 立即重连
            Log.d(TAG, "执行手动重连")
            init(lastConnectionParams!!, callback!!)
        } else {
            Log.e(TAG, "无法重连：没有连接参数或回调")
            WebSocketLogManager.getInstance().logError("无法重连：没有连接参数或回调")
        }
    }
    
    companion object {
        @Volatile
        private var instance: WebSocketManager? = null
        
        fun getInstance(): WebSocketManager {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = WebSocketManager()
                    }
                }
            }
            return instance!!
        }
    }
} 