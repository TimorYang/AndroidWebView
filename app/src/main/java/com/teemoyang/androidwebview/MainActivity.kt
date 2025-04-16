package com.teemoyang.androidwebview

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.OnBackPressedCallback
import android.os.Build
import android.util.Log
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.location.LocationManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var webView: WebView
    private val webSocketManager = WebSocketManager()
    private val TAG = "MainActivity"
    
    // 全局数据
    private val globalData = GlobalData()
    
    // 传感器相关
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdate: Long = 0
    private val SHAKE_THRESHOLD = 800 // 摇动阈值
    private val SHAKE_INTERVAL = 1000 // 两次摇动之间的最小间隔（毫秒）
    private var lastShakeTime: Long = 0
    
    // 存储用户ID，等同于小程序中的openId
    private val deviceId: String
        get() = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("deviceId", "") ?: ""
    
    // 建筑列表，对应buildLocateList
    private val buildLocateList = arrayListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置系统栏适配
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
        }

        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://navmobiletest.joysuch.com/navmobile/JoysuchMiniProgram/index.html#")
        
        // 设置返回键处理，兼容新旧版本Android
        setupBackNavigation()
        
        // 检查位置权限并初始化WebSocket
        checkPermissionsAndInitWebSocket()
        
        // 初始化摇一摇功能
        initShakeDetection()
    }
    
    private fun initShakeDetection() {
        // 获取传感器服务
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        // 获取加速度传感器
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        // 如果设备没有加速度传感器，显示提示
        if (accelerometer == null) {
            Toast.makeText(this, "该设备不支持摇一摇功能", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 注册传感器监听
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // 取消传感器监听
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            
            // 设置最小间隔，避免过于频繁计算
            if ((currentTime - lastUpdate) > 100) {
                val diffTime = currentTime - lastUpdate
                lastUpdate = currentTime
                
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                // 计算加速度变化
                val speed = abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000
                
                // 如果变化超过阈值，并且距离上次触发有足够间隔
                if (speed > SHAKE_THRESHOLD && (currentTime - lastShakeTime > SHAKE_INTERVAL)) {
                    lastShakeTime = currentTime
                    
                    // 检测到摇动，打开WebSocket测试页面
                    onShakeDetected()
                }
                
                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // 忽略精度变化
    }
    
    private fun onShakeDetected() {
        // 显示提示
        Toast.makeText(this, "检测到摇动，打开WebSocket测试页面", Toast.LENGTH_SHORT).show()
        
        // 启动WebSocket测试Activity
        val intent = Intent(this, WebSocketTestActivity::class.java)
        startActivity(intent)
    }
    
    private fun checkPermissionsAndInitWebSocket() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            // 请求位置权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            // 已有权限，设置授权状态并初始化WebSocket
            globalData.authorizationStatus = true
            webSocketInit()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限获取成功
                globalData.authorizationStatus = true
            } else {
                // 权限被拒绝
                globalData.authorizationStatus = false
                Toast.makeText(this, "需要位置权限才能提供完整功能", Toast.LENGTH_SHORT).show()
            }
            // 无论权限状态如何，都初始化WebSocket
            webSocketInit()
        }
    }
    
    private fun webSocketInit() {
        Log.d(TAG, "websocket初始化")
        
        // 检查是否需要跳过WebSocket初始化
        if ((buildLocateList.contains(globalData.buildId) && globalData.buildId.isNotEmpty()) || deviceId.isEmpty()) {
            Log.d(TAG, "跳过WebSocket初始化: buildId=${globalData.buildId}, deviceId=$deviceId")
            return
        }
        
        // 准备回调函数
        val webSocketCallback = object : WebSocketManager.WebSocketCallback {
            override fun onMessage(jsonObject: JSONObject) {
                try {
                    val status = jsonObject.optString("status", "")
                    val messageType = jsonObject.optString("messageType", "")
                    val from = jsonObject.optString("from", "")
                    
                    Log.d(TAG, "接收到的消息: status=$status, messageType=$messageType")
                    
                    // 处理心跳消息
                    if (status == "0" && messageType == "0") {
                        webSocketManager.send("0", from, JSONObject().put("status", 0))
                        return
                    }
                    
                    // 处理定位消息
                    if (status == "1") {
                        if (messageType == "1") { // 定位
                            // 这里可以处理定位消息数据
                            // val data = jsonObject.optJSONObject("data")
                            // getArPointCallBack(data)
                        }
                    }
                    
                    // 处理重发请求
                    if (status == "6") {
                        if (messageType == "2") { // 蓝牙
                            changeFlagFun(globalData.isBluetoothOpen, "blueTooth")
                        } else if (messageType == "3") { // 罗盘
                            changeFlagFun(globalData.isCompassFlag, "compass")
                        }
                    }
                    
                    // 处理准备好的消息
                    if (status == "2") {
                        if (messageType == "2") { // 地理和蓝牙监听开启
                            // 把授权状态传递给H5
                            webSocketManager.send("4", "H5", JSONObject().put("authorizationStatus", globalData.authorizationStatus))
                            
                            // 调用当前位置
                            if (globalData.authorizationStatus) {
                                locationAgainFun()
                            }
                        }
                    }
                    
                    // 接收H5的状态用于开启授权定位
                    if (status == "4") {
                        if (messageType == "4") { // 地理和蓝牙监听开启
                            locationAgainFun()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "解析消息失败: ${e.message}")
                }
            }
            
            override fun onClose() {
                Log.d(TAG, "WebSocket关闭等待10s")
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "WebSocket连接失败: $error")
            }
        }
        
        // 准备初始化参数
        val params = mapOf(
            "url" to AppApi.urlApi,
            "deviceId" to deviceId
        )
        
        // 初始化WebSocket
        webSocketManager.init(params, webSocketCallback)
    }
    
    // 处理状态变化
    private fun changeFlagFun(isOpen: Boolean, type: String) {
        try {
            val statusObj = JSONObject()
            statusObj.put("type", type)
            statusObj.put("status", isOpen)
            
            webSocketManager.send("2", "H5", statusObj)
        } catch (e: JSONException) {
            Log.e(TAG, "创建状态消息失败: ${e.message}")
        }
    }
    
    // 定位
    private fun locationAgainFun() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        // 检查GPS是否开启
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isGpsEnabled) {
            Toast.makeText(this, "请开启GPS以获取位置信息", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 这里应该获取位置并发送
        // 由于获取位置的代码较为复杂，这里只实现框架
        try {
            val locationData = JSONObject()
            locationData.put("latitude", 0)
            locationData.put("longitude", 0)
            locationData.put("accuracy", 0)
            
            webSocketManager.send("1", "H5", locationData)
        } catch (e: JSONException) {
            Log.e(TAG, "创建位置消息失败: ${e.message}")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideNavigationBar()
        }
    }

    private fun hideNavigationBar() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // 替换旧的onBackPressed方法，使用新的兼容方式
    private fun setupBackNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 新版本Android使用OnBackPressedCallback
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            })
        }
    }
    
    // 保留旧方法以兼容低版本Android
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        // 关闭WebSocket连接
        webSocketManager.close()
        super.onDestroy()
    }
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
} 