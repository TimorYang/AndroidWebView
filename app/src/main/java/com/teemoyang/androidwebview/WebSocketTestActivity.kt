package com.teemoyang.androidwebview

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.teemoyang.androidwebview.databinding.ActivityWebsocketTestBinding
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.Timer
import java.util.TimerTask

class WebSocketTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebsocketTestBinding
    private val webSocketManager = WebSocketManager.getInstance()
    private var isConnected = false
    
    // 消息类型和接收者
    private var selectedStatus = "0"
    private var selectedRecipient = "H5"
    
    // 选择的连接方式 - 固定为微信小程序格式
    private var selectedConnectionType = 4 // 微信小程序格式索引
    
    // 定位相关
    private var locationHelper: LocationHelper? = null
    private val locationPermissionCode = 1001
    
    // 自动发送位置信息
    private var isAutoSendEnabled = false
    private val autoSendHandler = Handler(Looper.getMainLooper())
    private val autoSendInterval = 2000L // 2秒发送一次
    private val autoSendRunnable = object : Runnable {
        override fun run() {
            if (isAutoSendEnabled && isConnected) {
                val location = locationHelper?.getCurrentLocation()
                if (location != null) {
                    logMessage("准备自动发送位置信息...", LogType.SYSTEM)
                    sendLocationMessage(location)
                    // 再次调度
                    autoSendHandler.postDelayed(this, autoSendInterval)
                } else {
                    logMessage("等待获取位置信息...", LogType.SYSTEM)
                    // 继续等待位置信息
                    autoSendHandler.postDelayed(this, autoSendInterval)
                }
            } else if (isAutoSendEnabled && !isConnected) {
                logMessage("WebSocket未连接，无法自动发送位置", LogType.ERROR)
                isAutoSendEnabled = false
                binding.autoSendSwitch.isChecked = false
            }
        }
    }
    
    // 定位监听器
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateLocationInfo(location)
            logMessage("位置已更新: 纬度=${location.latitude}, 经度=${location.longitude}, 精度=${location.accuracy}米", LogType.SYSTEM)
        }
        
        override fun onProviderDisabled(provider: String) {
            logMessage("定位提供者已禁用: $provider", LogType.SYSTEM)
        }
        
        override fun onProviderEnabled(provider: String) {
            logMessage("定位提供者已启用: $provider", LogType.SYSTEM)
        }
    }

    // 添加一个标志位，记录用户是否正在尝试开启自动发送位置
    private var isAttemptingToEnableAutoSend = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebsocketTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置标题
        title = "WebSocket测试工具"

        // 设置默认的服务器地址和设备ID
        binding.urlInput.setText("https://navmobiletest.joysuch.com")
        loadOrGenerateDeviceId()
        
        // 设置消息类型下拉菜单
        setupMessageTypeSpinner()
        
        // 隐藏连接方式选择UI元素
        binding.connectionTypeSpinner.visibility = View.GONE
        binding.connectionTypeLabel.visibility = View.GONE
        
        // 隐藏发送消息相关UI元素
        binding.sendMessageLabel.visibility = View.GONE
        binding.messageTypeSpinner.visibility = View.GONE
        binding.messageInputLayout.visibility = View.GONE
        binding.sendButton.visibility = View.GONE
        
        // 设置日志文本可滚动
        binding.logsTextView.movementMethod = ScrollingMovementMethod()
        
        // 设置按钮点击事件
        setupButtonClickListeners()
        
        // 添加自动发送位置按钮
        setupAutoSendLocationButton()
        
        // 记录默认连接信息
        logMessage("默认服务器地址: ${binding.urlInput.text}", LogType.SYSTEM)
        logMessage("默认设备ID: ${binding.deviceIdInput.text}", LogType.SYSTEM)
        
        // 增加对WebSocketLogManager的支持
        WebSocketLogManager.getInstance().addLog(
            WebSocketLogManager.LogType.INFO,
            "WebSocket测试活动已启动"
        )
    }
    
    private fun setupAutoSendLocationButton() {
        // 添加自动发送位置按钮
        binding.autoSendSwitch.setOnCheckedChangeListener { _, isChecked ->
            isAutoSendEnabled = isChecked
            
            if (isChecked) {
                if (isConnected) {
                    // 设置标志位，表示用户正在尝试开启自动发送
                    isAttemptingToEnableAutoSend = true
                    
                    // 初始化位置服务
                    if (locationHelper == null) {
                        initLocationHelper()
                        startLocationService()
                    } else {
                        // 如果已经初始化过，但可能被释放了，重新启动
                        startLocationService()
                    }
                    
                    // 立即尝试获取一次位置
                    locationHelper?.getCurrentLocation()
                    
                    // 开始自动发送
                    autoSendHandler.removeCallbacks(autoSendRunnable)
                    autoSendHandler.postDelayed(autoSendRunnable, autoSendInterval)
                } else {
                    binding.autoSendSwitch.isChecked = false
                    Toast.makeText(this, "请先连接WebSocket", Toast.LENGTH_SHORT).show()
                    logMessage("无法开启自动发送: WebSocket未连接", LogType.ERROR)
                }
            } else {
                // 停止自动发送
                autoSendHandler.removeCallbacks(autoSendRunnable)
                logMessage("已停止自动发送位置", LogType.SYSTEM)
                
                // 释放位置服务资源
                locationHelper?.release()
                locationHelper = null
                logMessage("位置服务已停止", LogType.SYSTEM)
                
                // 重置标志位
                isAttemptingToEnableAutoSend = false
            }
        }
    }
    
    private fun sendLocationMessage(location: Location) {
        if (!isConnected) {
            logMessage("无法发送位置: WebSocket未连接", LogType.ERROR)
            return
        }
        
        try {
            val latitude = location.latitude
            val longitude = location.longitude
            val accuracy = location.accuracy
            
            // 打印详细的位置信息
            logMessage("准备发送位置: 纬度=$latitude, 经度=$longitude, 精度=${accuracy}米", LogType.SYSTEM)
            
            val locationJson = JSONObject().apply {
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracy", accuracy)
            }
            
            logMessage("正在发送位置信息: $locationJson", LogType.SYSTEM)
            
            // 使用位置消息类型 (status=1)
            val success = webSocketManager.send("1", selectedRecipient, locationJson)
            
            if (success) {
                logMessage("自动发送位置成功: 纬度=$latitude, 经度=$longitude, 精度=${accuracy}米", LogType.SENT)
            } else {
                logMessage("自动发送位置失败", LogType.ERROR)
                // 如果发送失败，停止自动发送
                isAutoSendEnabled = false
                binding.autoSendSwitch.isChecked = false
            }
        } catch (e: Exception) {
            logMessage("自动发送位置错误: ${e.message}", LogType.ERROR)
            // 发生错误时停止自动发送
            isAutoSendEnabled = false
            binding.autoSendSwitch.isChecked = false
        }
    }
    
    private fun initLocationHelper() {
        locationHelper = LocationHelper(this)
        locationHelper?.setOnLocationUpdateListener(object : LocationHelper.OnLocationUpdateListener {
            override fun onLocationUpdated(location: Location) {
                runOnUiThread {
                    logMessage("位置更新: 纬度=${location.latitude}, 经度=${location.longitude}, 精度=${location.accuracy}米", LogType.SYSTEM)
                    
                    // 如果正在自动发送，立即发送一次位置
                    if (isAutoSendEnabled && isConnected) {
                        sendLocationMessage(location)
                    }
                }
            }
        })
        
        locationHelper?.setOnLocationErrorListener(object : LocationHelper.OnLocationErrorListener {
            override fun onLocationError(errorMessage: String) {
                runOnUiThread {
                    logMessage(errorMessage, LogType.ERROR)
                    binding.autoSendSwitch.isChecked = false
                }
            }
        })
        
        locationHelper?.setOnPermissionCallback(object : LocationHelper.OnPermissionCallback {
            override fun onPermissionGranted() {
                startLocationService()
            }
            
            override fun onPermissionDenied() {
                runOnUiThread {
                    logMessage("位置权限被拒绝", LogType.ERROR)
                    binding.autoSendSwitch.isChecked = false
                }
            }
        })
        
        locationHelper?.setOnLocationServiceCallback(object : LocationHelper.OnLocationServiceCallback {
            override fun onLocationServiceEnabled() {
                runOnUiThread {
                    logMessage("位置服务已开启", LogType.SYSTEM)
                }
            }
            
            override fun onLocationServiceDisabled() {
                runOnUiThread {
                    logMessage("位置服务已关闭", LogType.ERROR)
                    binding.autoSendSwitch.isChecked = false
                }
            }
        })
    }
    
    private fun startLocationService() {
        if (!locationHelper?.checkLocationPermission()!!) {
            locationHelper?.showPermissionRationaleDialog(this, LocationHelper.LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        
        if (!locationHelper?.isLocationServiceEnabled()!!) {
            locationHelper?.showLocationSettingsDialog(this)
            return
        }
        
        if (locationHelper?.startLocationUpdates()!!) {
            logMessage("位置服务已启动", LogType.SYSTEM)
        }
    }
    
    private fun updateLocationInfo(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        val accuracy = location.accuracy
        
        // 打印更详细的位置信息
        logMessage("位置更新: 纬度=$latitude, 经度=$longitude, 精度=${accuracy}米", LogType.SYSTEM)
        
        // 如果当前选择的是位置消息类型(索引1)，自动更新消息内容
        if (binding.messageTypeSpinner.selectedItemPosition == 1) {
            val locationJson = JSONObject().apply {
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracy", accuracy)
            }
            binding.messageInput.setText(locationJson.toString())
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationHelper?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    
    private fun loadOrGenerateDeviceId() {
        // 从SharedPreferences加载deviceId，如果不存在则生成一个
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("deviceId", "")
        
        if (deviceId.isNullOrEmpty()) {
            // 生成一个随机UUID作为deviceId
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", deviceId).apply()
        }
        
        binding.deviceIdInput.setText(deviceId)
    }
    
    private fun setupMessageTypeSpinner() {
        // 设置消息类型下拉菜单
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.message_types,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.messageTypeSpinner.adapter = adapter
        
        // 设置消息类型选择监听
        binding.messageTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // 获取选择的消息状态码
                val statusArray = resources.getStringArray(R.array.message_status)
                selectedStatus = statusArray[position]
                
                // 根据选择的消息类型更新示例JSON
                updateMessageExample(position)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                // 什么都不做
            }
        }
    }
    
    private fun updateMessageExample(position: Int) {
        val jsonExample = when (position) {
            0 -> """{"status": 0}""" // 心跳消息
            1 -> {
                // 使用静态示例位置，不主动获取实际位置
                """{"latitude": 30.123, "longitude": 120.456, "accuracy": 10.0}"""
            }
            2 -> """{"type": "blueTooth", "status": true}""" // 状态消息
            3 -> """{"authorizationStatus": true}""" // 授权状态
            else -> """{"key": "value"}""" // 自定义消息
        }
        binding.messageInput.setText(jsonExample)
    }
    
    private fun setupButtonClickListeners() {
        // 连接按钮
        binding.connectButton.setOnClickListener {
            val url = binding.urlInput.text.toString()
            val deviceId = binding.deviceIdInput.text.toString()
            
            if (url.isEmpty() || deviceId.isEmpty()) {
                Toast.makeText(this, "请输入服务器地址和设备ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 保存deviceId到SharedPreferences
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putString("deviceId", deviceId).apply()
            
            // 显示连接中状态
            updateConnectionStatus("连接中...")
            
            // 连接WebSocket - 固定使用微信小程序格式
            connectWebSocket(url, deviceId, selectedConnectionType)
        }
        
        // 断开按钮
        binding.disconnectButton.setOnClickListener {
            webSocketManager.close()
            updateConnectionStatus("已断开")
            isConnected = false
            updateButtonState()
            logMessage("已断开WebSocket连接", LogType.SYSTEM)
            
            // 停止自动发送
            isAutoSendEnabled = false
            binding.autoSendSwitch.isChecked = false
        }
        
        // 发送按钮
        binding.sendButton.setOnClickListener {
            sendMessage()
        }
        
        // 清除日志按钮
        binding.clearLogsButton.setOnClickListener {
            binding.logsTextView.text = ""
        }
    }
    
    private fun connectWebSocket(url: String, deviceId: String, connectionType: Int = 4) {
        // 创建连接参数
        val params = mutableMapOf(
            "url" to url,
            "deviceId" to deviceId,
            "connectionType" to "0" // 直接使用处理后的URL
        )
        
        // 使用微信小程序URL格式
        val wechatUrl = url.replace("https", "wss").replace("http", "ws") +
                "/locationEngine/websocket/" + deviceId + "_WeChat___" + System.currentTimeMillis()
        
        // 更新参数使用转换后的URL
        params["url"] = wechatUrl
        logMessage("使用微信小程序格式URL: $wechatUrl", LogType.SYSTEM)

        /*
        // 注释掉其他连接方式的处理代码
        if (connectionType == 4) { // 对应资源文件中的索引4
            // 使用小程序的URL转换逻辑
            val wechatUrl = url.replace("https", "wss").replace("http", "ws") +
                    "/locationEngine/websocket/" + deviceId + "_WeChat___" + System.currentTimeMillis()
            
            // 更新参数使用转换后的URL
            params["url"] = wechatUrl
            // 使用连接方式0（直接使用URL），因为URL已经处理好了
            params["connectionType"] = "0"
            
            // 记录实际连接的URL
            logMessage("使用微信小程序格式URL: $wechatUrl", LogType.SYSTEM)
        }
        */
        
        val callback = object : WebSocketManager.WebSocketCallback {
            override fun onMessage(data: JSONObject) {
                runOnUiThread {
                    logMessage("收到: $data", LogType.RECEIVED)
                }
            }
            
            override fun onClose() {
                runOnUiThread {
                    updateConnectionStatus("已断开")
                    isConnected = false
                    updateButtonState()
                    logMessage("WebSocket连接已关闭", LogType.SYSTEM)
                    
                    // 停止自动发送
                    isAutoSendEnabled = false
                    binding.autoSendSwitch.isChecked = false
                    
                    // 释放位置服务资源
                    locationHelper?.release()
                    locationHelper = null
                    logMessage("位置服务已停止", LogType.SYSTEM)
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    updateConnectionStatus("连接失败")
                    isConnected = false
                    updateButtonState()
                    logMessage("连接错误: $error", LogType.ERROR)
                    
                    // 停止自动发送
                    isAutoSendEnabled = false
                    binding.autoSendSwitch.isChecked = false
                }
            }
        }
        
        try {
            webSocketManager.init(params, callback)
            
            // 添加一个延迟检查连接状态
            binding.root.postDelayed({
                if (webSocketManager.isConnected()) {
                    updateConnectionStatus("已连接")
                    isConnected = true
                    updateButtonState()
                    logMessage("WebSocket连接成功", LogType.SYSTEM)
                } else {
                    updateConnectionStatus("连接失败")
                    logMessage("WebSocket连接超时", LogType.ERROR)
                }
            }, 3000) // 3秒后检查
        } catch (e: Exception) {
            updateConnectionStatus("连接错误")
            logMessage("连接错误: ${e.message}", LogType.ERROR)
        }
    }
    
    private fun sendMessage() {
        if (!isConnected) {
            Toast.makeText(this, "请先连接WebSocket", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val messageStr = binding.messageInput.text.toString()
            val messageData = JSONObject(messageStr)
            
            // 如果是custom状态，需要获取用户输入的status值
            val status = if (selectedStatus == "custom") {
                val customStatus = messageData.optString("status", "0")
                messageData.remove("status") // 从data中移除status
                customStatus
            } else {
                selectedStatus
            }
            
            // 发送消息
            val success = webSocketManager.send(status, selectedRecipient, messageData)
            
            if (success) {
                logMessage("发送: status=$status, to=$selectedRecipient, data=$messageData", LogType.SENT)
            } else {
                logMessage("发送失败", LogType.ERROR)
            }
        } catch (e: JSONException) {
            logMessage("消息格式错误: ${e.message}", LogType.ERROR)
            Toast.makeText(this, "消息必须是有效的JSON格式", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateConnectionStatus(status: String) {
        binding.statusLabel.text = "连接状态: $status"
    }
    
    private fun updateButtonState() {
        binding.connectButton.isEnabled = !isConnected
        binding.disconnectButton.isEnabled = isConnected
        
        // 自动发送开关只有在连接状态下才能启用
        binding.autoSendSwitch.isEnabled = isConnected
        if (!isConnected) {
            binding.autoSendSwitch.isChecked = false
            isAutoSendEnabled = false
        }
    }
    
    // 日志类型枚举
    enum class LogType {
        SENT, RECEIVED, SYSTEM, ERROR
    }
    
    // 添加日志消息 - 同时更新内部日志和WebSocketLogManager
    private fun logMessage(message: String, type: LogType) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logPrefix = when (type) {
            LogType.SENT -> "↑ "
            LogType.RECEIVED -> "↓ "
            LogType.SYSTEM -> "i "
            LogType.ERROR -> "! "
        }
        
        val logColor = when (type) {
            LogType.SENT -> "#2196F3" // 蓝色
            LogType.RECEIVED -> "#4CAF50" // 绿色
            LogType.SYSTEM -> "#9E9E9E" // 灰色
            LogType.ERROR -> "#F44336" // 红色
        }
        
        val logEntry = "[$timestamp] <font color='$logColor'>$logPrefix$message</font><br>"
        
        binding.logsTextView.append(android.text.Html.fromHtml(logEntry, android.text.Html.FROM_HTML_MODE_LEGACY))
        
        // 滚动到底部
        binding.logsScrollView.post {
            binding.logsScrollView.fullScroll(View.FOCUS_DOWN)
        }
        
        // 同时更新到WebSocketLogManager
        when (type) {
            LogType.SENT -> WebSocketLogManager.getInstance().logSend(message)
            LogType.RECEIVED -> WebSocketLogManager.getInstance().logReceive(message)
            LogType.ERROR -> WebSocketLogManager.getInstance().logError(message)
            LogType.SYSTEM -> WebSocketLogManager.getInstance().addLog(WebSocketLogManager.LogType.INFO, message)
        }
    }
    
    override fun onDestroy() {
        // 停止位置更新
        locationHelper?.release()
        locationHelper = null
        
        // 停止自动发送
        autoSendHandler.removeCallbacks(autoSendRunnable)
        
        // 关闭WebSocket连接
        if (isConnected) {
            webSocketManager.close()
        }
        
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // 在Activity恢复时，只有当WebSocket已连接且自动发送开启时才检查位置服务状态
        if (isConnected && isAutoSendEnabled && locationHelper != null) {
            val hasPermission = locationHelper?.checkLocationPermission() ?: false
            val isServiceEnabled = locationHelper?.isLocationServiceEnabled() ?: false
            if (hasPermission && isServiceEnabled) {
                startLocationService()
            }
        }
    }
} 