package com.teemoyang.androidwebview

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.GeolocationPermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.OnBackPressedCallback
import android.os.Build
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.widget.Toast
import kotlin.math.abs
import android.widget.Button
import android.util.Log
import com.teemoyang.androidwebview.data.UserSession
import com.teemoyang.androidwebview.databinding.ActivityMainBinding
import org.json.JSONObject
import java.util.UUID
import androidx.core.app.ActivityCompat
import android.Manifest
import android.location.Location
import android.net.wifi.ScanResult
import org.altbeacon.beacon.Beacon
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import android.app.AlertDialog
import android.net.Uri
import java.util.Collections
import java.util.Timer
import java.util.TimerTask
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import android.view.View
import android.os.Handler
import android.os.Looper
import android.app.ProgressDialog
import android.webkit.PermissionRequest
import com.teemoyang.androidwebview.js.WebAppInterface
import java.util.Properties

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var binding: ActivityMainBinding
    
    // 用户信息相关变量
    private lateinit var deviceId: String
    private lateinit var userType: String
    private lateinit var permissionId: String
    
    // 声明BeaconScanner变量
    private var beaconScanner: BeaconScanner? = null
    
    // 添加传感器初始化标志
    private var sensorsInitialized = false
    
    // URL相关常量
    private val BASE_URL = "https://navmobiletest.joysuch.com"
    private val MINIPROGRAM_PATH = "/navmobile/JoysuchMiniProgram/index.html#/"
    private val WS_PROTOCOL = "wss://"  // WebSocket安全协议
    private val WS_BASE_DOMAIN = "navmobiletest.joysuch.com"  // 不含协议的域名
    private val WS_PATH = "/locationEngine/websocket/"  // WebSocket路径
    
    // 使用单例模式
    private val webSocketManager by lazy { WebSocketManager.getInstance() }
    private var isWebSocketConnected = false
    
    // 重命名以更清晰地表达变量含义
    private var hasBeaconData = false
    
    // 添加权限结果处理
    private var locationHelper: LocationHelper? = null
    
    // 添加一个成员变量来存储最新的位置信息
    private var lastKnownLocation: Location? = null
    
    // 添加定时器变量，便于取消和重新启动
    private var beaconTimer: Timer? = null
    
    // 添加UUID到编号的映射
    private val uuidToCode = mapOf(
        "FDA50693-A4E2-4FB1-AFCF-C6EB07647825" to "0000",
        "1918FC80-B111-3441-A9AC-B1001C2FE510" to "0001",
        "AB8190D5-D11E-4941-ACC4-42F30510B408" to "0002"
    )
    
    // 上次发送beacon数据的时间
    private var lastBeaconSendTime = 0L
    
    // 发送Beacon数据的时间间隔（毫秒）
    private val BEACON_SEND_INTERVAL = 1000L  // 1秒
    
    // 发送GPS数据的时间间隔（毫秒）
    private val GPS_SEND_INTERVAL = 10000L  // 10秒
    
    // 添加UUID到完整UUID值的映射
    private val codeToUuid = mapOf(
        "0000" to "FDA50693A4E24FB1AFCFC6EB07647825",
        "0001" to "1918FC80B1113441A9ACB1001C2FE510",
        "0002" to "AB8190D5D11E4941ACC442F30510B408"
    )
    
    // 定位计数器
    private var locateCount = 0
    
    // 添加标志，用于追踪是否是首次获取权限
    private var isFirstTimePermissionGrant = true
    
    // 定义WebAppInterface的成员变量
    private lateinit var webAppInterface: WebAppInterface
    
    // 修改原有的SPEECH_RECOGNITION_REQUEST_CODE
    companion object {
        private const val PREFS_NAME = "WebViewAppPrefs"
        private const val KEY_FIRST_TIME_PERMISSION = "first_time_location_permission"
        // 使用WebAppInterface中定义的常量
        private const val SPEECH_RECOGNITION_REQUEST_CODE = WebAppInterface.SPEECH_RECOGNITION_REQUEST_CODE
        
        // 权限请求码
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1003
    }
    
    // 添加字节转十六进制的工具方法
    private fun byteToHex(bytes: ByteArray, length: Int): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(length * 2)
        for (i in 0 until length) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查用户是否已登录，如果未登录则跳转到登录页面
        if (!UserSession.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 获取用户数据并初始化成员变量
        try {
            val userData = UserSession.getUserData()
            deviceId = userData.deviceId
            userType = userData.userType ?: "1" // 默认为员工类型
            permissionId = userData.permissionId
            
            // 记录用户数据到日志
            Log.d("MainActivity", "用户数据初始化 - deviceId: $deviceId, userType: $userType, permissionId: $permissionId")
        } catch (e: Exception) {
            Log.e("MainActivity", "获取用户数据失败", e)
            // 返回登录页面
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 在后台线程获取语音token
        Thread {
            try {
                // 先检查UserSession中是否有有效token
                if (UserSession.isSpeechTokenValid()) {
                    // 使用UserSession中的token
                    val token = UserSession.getSpeechToken()
                    val expireTime = UserSession.getSpeechTokenExpireTime()
                    Log.d("MainActivity.SpeechToken", "使用UserSession中缓存的语音token: $token, 过期时间: $expireTime")
                } else {
                    // UserSession中没有有效token，重新获取
                    // 从配置文件中读取AccessKey和AccessKeySecret
                    val properties = Properties()
                    try {
                        assets.open("api_keys.properties").use { inputStream ->
                            properties.load(inputStream)
                            
                            val accessKeyId = properties.getProperty("alibaba.access_key", "")
                            val accessKeySecret = properties.getProperty("alibaba.access_key_secret", "")
                            
                            if (accessKeyId.isNotEmpty() && accessKeySecret.isNotEmpty()) {
                                Log.d("MainActivity.SpeechToken", "成功从配置文件获取AccessKey信息")
                                
                                val token = TokenUtil.getToken(accessKeyId, accessKeySecret)
                                
                                // 获取并保存token的过期时间
                                if (token != null) {
                                    val expireTime = TokenUtil.getTokenExpireTime()
                                    
                                    // 保存到UserSession中
                                    UserSession.saveSpeechToken(token, expireTime)
                                    
                                    Log.d("MainActivity.SpeechToken", "语音token获取成功并保存到UserSession: $token, 过期时间: $expireTime")
                                } else {
                                    Log.e("MainActivity.SpeechToken", "语音token获取失败")
                                }
                            } else {
                                Log.e("MainActivity.SpeechToken", "配置文件中AccessKey或AccessKeySecret为空")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity.SpeechToken", "读取配置文件失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity.SpeechToken", "获取语音token异常: ${e.message}", e)
            }
        }.start()

        startSensorsAfterWebViewLoad()
        
        // 从SharedPreferences读取首次权限标志
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isFirstTimePermissionGrant = prefs.getBoolean(KEY_FIRST_TIME_PERMISSION, true)
        Log.d("MainActivity.WebView.Permission", "从本地存储读取权限状态: 首次授权=$isFirstTimePermissionGrant")
        
        // 开启 WebView 调试模式
        WebView.setWebContentsDebuggingEnabled(true)
        
        // 获取并显示设备ID
        displayDeviceId()
        
        // 设置系统栏适配
        WindowCompat.setDecorFitsSystemWindows(window, true)
    
        
        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(false)
            // 启用地理位置
            setGeolocationEnabled(true)
        }

        // 创建WebAppInterface实例
        webAppInterface = WebAppInterface(this, webView, deviceId, userType, permissionId)
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(webAppInterface, "Android")

        // 设置WebView加载完成监听器
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("MainActivity", "WebView页面加载完成: $url")
                
                // 将URL添加到WebSocket日志
                WebSocketLogManager.getInstance().addLog(
                    WebSocketLogManager.LogType.INFO,
                    "WebView页面加载完成",
                    url
                )
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("MainActivity", "WebView页面开始加载: $url")
                
                // 将URL添加到WebSocket日志
                WebSocketLogManager.getInstance().addLog(
                    WebSocketLogManager.LogType.INFO,
                    "WebView页面开始加载",
                    url
                )
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // 将URL导航记录添加到WebSocket日志
                WebSocketLogManager.getInstance().addLog(
                    WebSocketLogManager.LogType.INFO,
                    "WebView导航到新URL",
                    url
                )
                return super.shouldOverrideUrlLoading(view, url)
            }
        }
        
        // 设置WebChromeClient来处理地理位置权限
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                // 检查应用是否有定位权限
                val fineLocationPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                val coarseLocationPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                
                if (fineLocationPermission == PackageManager.PERMISSION_GRANTED || 
                    coarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
                    // 有权限时允许网页使用定位
                    callback?.invoke(origin, true, false)
                    Log.d("MainActivity.WebView.Permission", "地理位置权限已授予网页: $origin")
                } else {
                    // 没有权限时拒绝网页使用定位
                    callback?.invoke(origin, false, false)
                    Log.e("MainActivity.WebView.Permission", "应用没有定位权限，已拒绝网页定位请求: $origin")
                }
            }
            
            // 处理网页请求摄像头权限
            override fun onPermissionRequest(request: PermissionRequest) {
                // 在UI线程上处理权限请求
                runOnUiThread {
                    // 检查摄像头权限
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED) {
                        // 如果已有权限，授予网页摄像头访问权限
                        val resources = request.resources.filter { 
                            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE 
                        }.toTypedArray()
                        
                        if (resources.isNotEmpty()) {
                            request.grant(resources)
                            Log.d("MainActivity.WebView.Permission", "摄像头权限已授予网页")
                        } else {
                            request.deny()
                        }
                    } else {
                        // 如果没有权限，向用户请求权限
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.CAMERA),
                            CAMERA_PERMISSION_REQUEST_CODE
                        )
                        
                        // 暂时拒绝网页请求，等待用户授权后WebView会再次请求
                        request.deny()
                        Log.d("MainActivity.WebView.Permission", "请求摄像头权限中，暂时拒绝网页请求")
                    }
                }
            }
        }
        
        // 使用成员变量构建URL
        val urlParams = "?wxOpenId=$deviceId&permissionId=$permissionId&rule=$userType"
        val fullUrl = "$BASE_URL$MINIPROGRAM_PATH$urlParams"
        
        // 记录初始URL到WebSocket日志
        WebSocketLogManager.getInstance().addLog(
            WebSocketLogManager.LogType.INFO,
            "WebView加载初始URL",
            fullUrl
        )
        
        webView.loadUrl(fullUrl)
        
        // 设置返回键处理，兼容新旧版本Android
        setupBackNavigation()
        
        // 初始化按钮
        initButtons()
        
        // 自动连接WebSocket
        connectToWebSocket()
    }
    
    override fun onStart() {
        super.onStart()
        
        // 当应用从后台恢复时，检查登录状态
        if (!UserSession.isLoggedIn()) {
            Log.d("MainActivity", "应用从后台恢复，检测到用户未登录，跳转到登录页面")
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
    }
    
    /**
     * 连接到WebSocket服务器
     */
    private fun connectToWebSocket() {
        // 构建WebSocket URL (格式: wss://domain/locationEngine/websocket/deviceId_WeChat___timestamp)
        val timestamp = System.currentTimeMillis()
        val webSocketUrl = WS_PROTOCOL + WS_BASE_DOMAIN + WS_PATH + deviceId + "_WeChat___" + timestamp
        
        // 准备连接参数
        val params = mapOf(
            "url" to webSocketUrl
        )
        
        // 添加WebSocket回调
        val wsCallback = object : WebSocketManager.WebSocketCallback {
            override fun onMessage(data: JSONObject) {
                Log.d("MainActivity", "WebSocket收到消息: $data")
            }
            
            override fun onClose() {
                Log.d("MainActivity", "WebSocket连接关闭")
                isWebSocketConnected = false
            }
            
            override fun onError(error: String) {
                Log.e("MainActivity", "WebSocket错误: $error")
                isWebSocketConnected = false
            }
        }
        
        // 连接WebSocket
        Log.d("MainActivity", "正在连接WebSocket: $webSocketUrl")
        webSocketManager.init(params, wsCallback)
        
        // 启用重连机制
        webSocketManager.setEnableReconnect(true)
        
        // 延迟检查连接状态
        binding.root.postDelayed({
            isWebSocketConnected = webSocketManager.isConnected()
            if (isWebSocketConnected) {
                Log.d("MainActivity", "WebSocket已连接")
            } else {
                Log.e("MainActivity", "WebSocket连接失败")
            }
        }, 2000) // 2秒后检查
    }
    
    /**
     * 示例：发送蓝牙信标数据到服务端
     * 与JS示例代码功能完全相同： this.webSocketSend("2", "H5", { "blueTooth": obj });
     */
    private fun sendBluetoothData() {
        try {
            // 创建蓝牙信标数据
            val beaconObj = JSONObject().apply {
                put("mac", "AA:BB:CC:DD:EE:FF")
                put("rssi", -75)
                put("distance", 2.5)
                put("uuid", "AAAABBBB-CCCC-DDDD-EEEE-FFFFFFFFFFFF")
                put("major", 1234)
                put("minor", 5678)
                put("timestamp", System.currentTimeMillis())
            }
            
            // 创建与JS示例完全相同的数据结构 { "blueTooth": obj }
            val dataObj = JSONObject().apply {
                put("blueTooth", beaconObj)
            }
            
            // 发送到服务端 (类型"2", 接收者"H5")
            val success = webSocketManager.webSocketSend("2", "H5", dataObj)
            
            if (success) {
                Log.d("MainActivity", "蓝牙信标数据发送成功")
            } else {
                Log.e("MainActivity", "蓝牙信标数据发送失败")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "发送蓝牙信标数据出错: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 检查定位权限变化
        checkLocationPermissionChanges()
    }
    
    /**
     * 检查定位权限是否发生变化（用户可能在设置中手动授予了权限）
     */
    private fun checkLocationPermissionChanges() {
        // 如果locationHelper已初始化
        locationHelper?.let { helper ->
            // 检查当前是否有权限
            if (helper.checkLocationPermission()) {
                // 如果有权限且定位服务开启，但尚未启动，则重新启动
                if (helper.isLocationServiceEnabled()) {
                    // 尝试启动位置更新
                    helper.startLocationUpdates()
                    Log.d("MainActivity.Location", "onResume检测到权限已授予，启动位置更新")
                } else {
                    // 如果定位服务未开启，引导用户去设置页面开启
                    helper.showLocationSettingsDialog(this@MainActivity)
                }
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // 当应用退到后台时，自动退出登录（只清除登录信息，不跳转界面）
        clearLoginState()
        
        Log.d("MainActivity", "应用退到后台，已自动退出登录")
    }
    
    /**
     * 清除登录状态（应用退到后台时调用）
     */
    private fun clearLoginState() {
        try {
            // 关闭WebSocket连接
            if (webSocketManager.isConnected()) {
                webSocketManager.close()
            }
            
            // 取消定时器
            beaconTimer?.cancel()
            beaconTimer = null
            
            // 停止信标扫描
            beaconScanner?.release()
            beaconScanner = null
            
            // 关闭定位服务
            locationHelper?.stopLocationUpdates()
            
            // 清空用户会话
            UserSession.clearLoginInfo()
            
            Log.d("MainActivity", "已清除登录状态")
        } catch (e: Exception) {
            Log.e("MainActivity", "清除登录状态失败: ${e.message}")
        }
    }
    
    /**
     * 执行登出操作（用户手动退出时调用，会跳转到登录页面）
     */
    private fun performLogout() {
        try {
            // 关闭WebSocket连接
            if (webSocketManager.isConnected()) {
                webSocketManager.close()
            }
            
            // 取消定时器
            beaconTimer?.cancel()
            beaconTimer = null
            
            // 停止信标扫描
            beaconScanner?.release()
            beaconScanner = null
            
            // 关闭定位服务
            locationHelper?.stopLocationUpdates()
            
            // 清空用户会话
            UserSession.clearLoginInfo()
            
            // 跳转到登录页面
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            
            Toast.makeText(this, "已成功登出", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "登出失败: ${e.message}")
            Toast.makeText(this, "登出失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 停止信标扫描
        beaconScanner?.release()
        beaconScanner = null
        
        // 关闭WebSocket连接
        if (isWebSocketConnected) {
            webSocketManager.close()
        }
        
        // 取消定时器
        beaconTimer?.cancel()
        beaconTimer = null
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

    private fun initButtons() {
        // 隐藏信标扫描按钮
        binding.btnBeaconScan.visibility = View.GONE
        
        // 隐藏WiFi扫描按钮
        binding.btnWifiScan.visibility = View.GONE
        
        // 隐藏原来的WebSocket日志按钮
        binding.btnWebSocketLog.visibility = View.GONE

        // 隐藏语音识别Demo按钮
        binding.fabSpeechRecognizer.visibility = View.GONE
        
        // 设置悬浮按钮的点击事件
        binding.fabWebSocketLog.setOnClickListener {
            startActivity(Intent(this, WebSocketLogActivity::class.java))
        }

        binding.fabWebSocketLog.visibility = View.GONE
        
        // 设置语音识别按钮点击事件 - 添加悬浮按钮
        // binding.fabSpeechRecognizer.setOnClickListener {
        //     // 直接启动语音识别Activity
        //     // token已经存储在UserSession中，不需要传递参数
        //     startActivity(Intent(this, SpeechRecognizerActivity::class.java))
        // }
        
        // 设置登出按钮
        binding.fabLogout.setOnClickListener {
            // 显示确认对话框
            showLogoutConfirmDialog()
        }

        binding.fabLogout.visibility = View.GONE
        
        // 设置加速度计按钮（已隐藏，不再使用）
        // binding.btnAccelerometer.setOnClickListener {
        //    startActivity(Intent(this, AccelerometerActivity::class.java))
        // }
        
        // 设置WebSocket测试按钮（已隐藏，不再使用）
        // binding.btnWebSocketTest.setOnClickListener {
        //    startActivity(Intent(this, WebSocketTestActivity::class.java))
        // }

        // 新添加的直接进入SpeechRecognitionActivity的按钮
        binding.fabSpeechRecognitionActivity.setOnClickListener {
            // 直接启动SpeechRecognitionActivity
            // 加载语音识别演示页面
            // 如果webView URL 是本地文件，则重新加载 
            if (webView.url?.startsWith("file://") == true) {
                val urlParams = "?wxOpenId=$deviceId&permissionId=$permissionId&rule=$userType"
                val fullUrl = "$BASE_URL$MINIPROGRAM_PATH$urlParams"
                webView.loadUrl(fullUrl)
            } else {
                loadSpeechRecognitionDemo()
            }
            // startActivityForResult(Intent(this, SpeechRecognitionActivity::class.java), SPEECH_RECOGNITION_REQUEST_CODE)
        }

        binding.fabSpeechRecognitionActivity.visibility = View.GONE
    }

    /**
     * 显示设备ID和用户信息
     */
    private fun displayDeviceId() {
        // 显示设备ID
        Toast.makeText(this, "设备ID: $deviceId", Toast.LENGTH_LONG).show()
        
        // 记录用户信息到日志
        Log.d("MainActivity", "设备ID: $deviceId, 用户类型: $userType, 权限ID: $permissionId")
        
        // 记录到WebSocket日志
        WebSocketLogManager.getInstance().addLog(
            WebSocketLogManager.LogType.INFO,
            "用户信息",
            "设备ID: $deviceId, 用户类型: $userType, 权限ID: $permissionId"
        )
    }

    /**
     * WebView加载完成后启动所有传感器
     */
    private fun startSensorsAfterWebViewLoad() {
        // 检查传感器是否已初始化，避免重复初始化
        if (sensorsInitialized) {
            Log.d("MainActivity", "传感器已经初始化，跳过...")
            return
        }
        
        // 设置标志，防止重复初始化
        sensorsInitialized = true
        
        Log.d("MainActivity", "开始启动所有传感器扫描...")
        
        // 1. 启动定位服务
        startLocationService()
        
        // 通知全局数据管理器启动自动更新
        SensorDataManager.getInstance().startAutoUpdate()
        
        // 注意：tenSecondTimer现在在获取到位置权限后调用，而不是在这里
        
        Toast.makeText(this, "所有传感器扫描已启动", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 启动定位服务
     */
    private fun startLocationService() {
        Log.d("MainActivity.Location", "启动定位服务")
        
        // 存储LocationHelper实例以便在onRequestPermissionsResult中使用
        locationHelper = LocationHelper(this)
            .setEnableVerboseLogging(false)  // 控制日志输出
            .setAutoUpdateToSensorManager(true) // 自动更新到SensorDataManager
            
        // 修改位置更新监听器
        locationHelper?.setOnLocationUpdateListener(object : LocationHelper.OnLocationUpdateListener {
            override fun onLocationUpdated(location: Location) {
                Log.d("MainActivity.Location", "位置已更新: ${location.latitude}, ${location.longitude}")
                // LocationHelper已自动更新到SensorDataManager，这里只做日志记录
            }
        })
        
        locationHelper?.setOnLocationErrorListener(object : LocationHelper.OnLocationErrorListener {
            override fun onLocationError(errorMessage: String) {
                Log.e("MainActivity.Location", "位置错误: $errorMessage")
            }
        })
        
        // 修改位置权限回调
        locationHelper?.setOnPermissionCallback(object : LocationHelper.OnPermissionCallback {
            override fun onPermissionGranted() {
                Log.d("MainActivity.Location", "位置权限已授予")
                
                // 获取当前的权限状态记录
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val wasPermissionGrantedBefore = !prefs.getBoolean(KEY_FIRST_TIME_PERMISSION, true)
                
                // 启动蓝牙扫描
                startBeaconScan()
                // 启动WiFi扫描
//                startWifiScan()
                // 在获取位置权限后启动10秒的定时任务
                locationTimerTask()
                
                // 只有在首次获取权限时才刷新WebView（根据本地存储判断）
                if (!wasPermissionGrantedBefore) {
                    runOnUiThread {
                        // 通知WebView地理位置权限已变更
                        if (::webView.isInitialized) {
                            Log.d("MainActivity.WebView.Permission", "首次获取位置权限，刷新WebView")
                            // 刷新当前页面或执行JS来重新请求地理位置权限
                            webView.reload()
                        }
                    }
                    
                    // 保存状态到SharedPreferences
                    prefs.edit().putBoolean(KEY_FIRST_TIME_PERMISSION, false).apply()
                    Log.d("MainActivity.WebView.Permission", "已将权限状态保存到本地存储")
                } else {
                    Log.d("MainActivity.WebView.Permission", "已有权限记录，不刷新WebView")
                }
            }
            
            override fun onPermissionDenied() {
                Log.e("MainActivity.Location", "位置权限被拒绝")
                
                // 读取当前的权限状态记录
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val wasPermissionGrantedBefore = !prefs.getBoolean(KEY_FIRST_TIME_PERMISSION, true)
                
                // 如果之前有权限，现在被拒绝，更新状态
                if (wasPermissionGrantedBefore) {
                    // 更新存储，标记为首次授权
                    prefs.edit().putBoolean(KEY_FIRST_TIME_PERMISSION, true).apply()
                    Log.d("MainActivity.WebView.Permission", "权限被拒绝，重置首次授权标志")
                }
                
                // 弹窗，引导用户去设置页面开启位置权限
//                locationHelper?.showLocationSettingsDialog(this@MainActivity)
            }
        })
        
        // 一键启动位置服务（内部会处理权限请求和位置服务检查）
        locationHelper?.startLocationService(this)
    }
    
    /**
     * 启动蓝牙扫描
     */
    private fun startBeaconScan() {
        Log.d("MainActivity.Beacon", "启动蓝牙信标扫描")
        
        // 确保任何已存在的定时器被取消
        beaconTimer?.cancel()
        beaconTimer = null
        
        try {
            // 在需要使用时创建和初始化BeaconScanner
            beaconScanner = BeaconScanner(this)
            // 初始化BeaconScanner
            beaconScanner?.initialize()
            
            // 设置扫描监听器
            beaconScanner?.setScanListener(object : BeaconScanner.ScanListener {
                override fun onBeaconFound(beaconCollection: Collection<Beacon>) {
                    // 更新到SensorDataManager
                    SensorDataManager.getInstance().updateBeaconData(beaconCollection)
                    
                    // 记录发现的信标信息
                    beaconCollection.forEach { beacon ->
                        Log.d("MainActivity.Beacon", "信标详情: MAC=${beacon.bluetoothAddress}, " +
                                "UUID=${beacon.id1}, " +
                                "Major=${beacon.id2}, " +
                                "Minor=${beacon.id3}, " +
                                "RSSI=${beacon.rssi}, " +
                                "距离=${beacon.distance}米")
                    }
                }
                
                override fun onScanStart() {
                    Log.d("MainActivity.Beacon", "信标扫描已启动")
                    sendBluetoothPermissionStatus(true)
                }
                
                override fun onScanStop() {
                    Log.d("MainActivity.Beacon", "信标扫描已停止")
                }
                
                override fun onError(message: String) {
                    Log.e("MainActivity.Beacon", "信标扫描错误: $message")
                }
            })
            
            // 开始扫描 (startScan方法内会自动处理权限检查和请求)
            beaconScanner?.startScan()
        } catch (e: Exception) {
            Log.e("MainActivity.Beacon", "启动蓝牙扫描失败: ${e.message}")
            sendBluetoothPermissionStatus(false)
            e.printStackTrace()
        }
    }
    
    /**
     * 发送蓝牙权限状态到H5
     * @param granted 权限是否已获取
     */
    private fun sendBluetoothPermissionStatus(granted: Boolean) {
        if (webSocketManager.isConnected()) {
            try {
                // 简单的权限状态消息
                val permissionObj = JSONObject().apply {
                    put("blueTooth", if (granted) "on" else "off")
                }
                
                webSocketManager.webSocketSend("2", "H5", permissionObj)
                Log.d("MainActivity", "已发送蓝牙权限状态: $granted")
            } catch (e: Exception) {
                Log.e("MainActivity", "发送蓝牙权限状态失败: ${e.message}")
            }
        }
    }
    
    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // 将权限结果传递给LocationHelper
        if (locationHelper?.onRequestPermissionsResult(requestCode, permissions, grantResults) == true) {
            Log.d("MainActivity.Location", "位置权限结果已处理")
            
            // 检查是否包含定位权限，并且是否被授予
            val hasLocationPermission = permissions.any { 
                it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION 
            } && grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            
            // 如果是定位权限且被授予，通知WebView重新评估地理位置权限
            if (hasLocationPermission) {
                // 通知WebView地理位置权限已变更
                webView.reload()  // 这是最简单的方式，重新加载当前页面
                Log.d("MainActivity.WebView.Permission", "定位权限已授予，重新加载WebView")
            }
            
            return
        }
        
        // 将权限结果传递给BeaconScanner
        if (beaconScanner != null && beaconScanner?.handlePermissionResult(requestCode, permissions, grantResults) == true) {
            Log.d("MainActivity", "蓝牙权限已获取，继续扫描")
            return
        }
        
        // 处理蓝牙权限请求结果（如果BeaconScanner为null时的备选处理）
        if (requestCode == BeaconScanner.PERMISSION_REQUEST_CODE) { // 蓝牙权限请求码
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("MainActivity", "蓝牙权限已获取，启动扫描")
                startBeaconScan()
            } else {
                Log.e("MainActivity", "蓝牙权限被拒绝")
            }
        }
        
        // 处理摄像头权限请求结果
        else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "摄像头权限已授予")
                // 重新加载WebView以便网页可以使用新授予的权限
                Toast.makeText(this, "摄像头权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MainActivity", "摄像头权限被拒绝")
                Toast.makeText(this, "没有摄像头权限，视频和扫码功能可能无法使用", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 添加onActivityResult来处理蓝牙开启请求结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d("MainActivity", "onActivityResult - requestCode: $requestCode, resultCode: $resultCode")
        
        // 处理蓝牙开启结果
        if (requestCode == BeaconScanner.BLUETOOTH_ENABLE_REQUEST_CODE) {
            beaconScanner?.handleBluetoothEnableResult(requestCode, resultCode)
        }
        // 处理语音识别结果
        else if (requestCode == SPEECH_RECOGNITION_REQUEST_CODE) {
            // 打印更多日志以便调试
            Log.d("MainActivity", "接收到语音识别结果 - resultCode: $resultCode, data: $data")
            
            // 不论resultCode，都尝试处理数据
            val destination = data?.getStringExtra("DESTINATION")
            if (!destination.isNullOrEmpty()) {
                Log.d("MainActivity", "收到语音识别结果: $destination")
                Toast.makeText(this, "语音识别结果: $destination", Toast.LENGTH_SHORT).show()
                
                // 直接将结果发送给JS
                webAppInterface.sendSpeechResultToJs(destination)
            } else {
                Log.e("MainActivity", "语音识别结果为空")
                // 发送一个默认消息到JS，表示识别失败
                webAppInterface.sendSpeechResultToJs("语音识别未返回结果")
            }
        }
    }
    
    
    /**
     * 启动WiFi扫描
     */
    private fun startWifiScan() {
        Log.d("MainActivity.WiFi", "启动WiFi扫描")
        val wifiHelper = WiFiHelper(this)
        
        // 设置WiFi扫描结果监听器
        wifiHelper.setOnScanResultListener(object : WiFiHelper.OnScanResultListener {
            override fun onScanResult(results: List<ScanResult>) {
                Log.d("MainActivity.WiFi", "WiFi扫描结果: ${results.size}个网络")
                // 更新到全局数据管理器
                SensorDataManager.getInstance().updateWifiScanResults(results)
            }
        })
        
        // 设置WiFi扫描错误监听器
        wifiHelper.setOnScanErrorListener(object : WiFiHelper.OnScanErrorListener {
            override fun onScanError(errorMessage: String) {
                Log.e("MainActivity", "WiFi扫描失败: $errorMessage")
            }
        })
        
        // 设置权限回调
        wifiHelper.setOnPermissionCallback(object : WiFiHelper.OnPermissionCallback {
            override fun onPermissionGranted() {
                // 有权限，启动扫描
                if (wifiHelper.isWifiEnabled()) {
                    wifiHelper.startWifiScan()
                    
                    // 设置定期扫描
                    wifiHelper.startPeriodicScan(30000) // 每30秒扫描一次
                } else {
                    // 如果WiFi未开启，显示对话框
                    if (this@MainActivity is Activity) {
                        wifiHelper.showWifiSettingsDialog(this@MainActivity)
                    }
                }
            }
            
            override fun onPermissionDenied() {
                Log.e("MainActivity.WiFi", "WiFi扫描权限被拒绝")
            }
        })
        
        // 检查并请求WiFi权限
        if (!wifiHelper.checkWifiPermissions()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                wifiHelper.showPermissionRationaleDialog(this)
            } else {
                wifiHelper.requestWifiPermissions(this)
            }
        } else {
            // 已有权限，检查WiFi服务
            if (wifiHelper.isWifiEnabled()) {
                wifiHelper.startWifiScan()
                
                // 设置定期扫描
                wifiHelper.startPeriodicScan(30000) // 每30秒扫描一次
            } else {
                wifiHelper.showWifiSettingsDialog(this)
            }
        }
    }

    /**
     * 启动位置数据发送定时任务
     * 每1秒发送一次Beacon数据，如果10秒内没有发送过Beacon数据，则发送GPS数据
     */
    private fun locationTimerTask() {
        Log.d("MainActivity.locationTimerTask", "启动位置数据发送定时任务")
        
        // 取消已有的定时器
        beaconTimer?.cancel()
        
        // 创建新的定时器
        beaconTimer = Timer()
        
        // 创建定时任务，每1秒执行一次
        beaconTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    val currentTime = System.currentTimeMillis()
                    
                    // 检查是否有有效的Beacon数据
                    val hasValidBeacons = checkBeaconData()
                    
                    if (hasValidBeacons) {
                        // 如果有有效beacon数据，发送beacon数据
                        Log.d("MainActivity.locationTimerTask", "发送Beacon数据")
                        sendBeaconData()
                        lastBeaconSendTime = currentTime
                    } else if (currentTime - lastBeaconSendTime >= GPS_SEND_INTERVAL) {
                        // 如果10秒内没有发送过Beacon数据，发送GPS数据
                        Log.d("MainActivity.locationTimerTask", "10秒内未发送Beacon数据，发送GPS数据")
                        sendLocationData()
                        // 更新最后发送时间，避免频繁发送GPS数据
                        lastBeaconSendTime = currentTime
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity.locationTimerTask", "定时任务执行出错: ${e.message}")
                }
            }
        }, 0, BEACON_SEND_INTERVAL) // 立即开始，每1秒执行一次
    }
    
    /**
     * 检查是否有有效的Beacon数据
     */
    private fun checkBeaconData(): Boolean {
        // 如果使用假数据，直接返回true
        // return true
        
        // 如果使用实际数据，可以这样检查
        val beacons = SensorDataManager.getInstance().getBeaconData()
        return beacons.isNotEmpty()
    }

    private fun sendLocationData() {
        try {
            // 从SensorDataManager获取最新位置数据
            val location = SensorDataManager.getInstance().getCurrentLocation() ?: return
            
            // 发送位置数据到H5
            val locationObj = JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracy", location.accuracy)
            }
            
            // 创建与JS示例相同的数据结构 { "location": obj }
            val dataObj = JSONObject().apply {
                put("locateGpsHttpData", locationObj)
            }
            
            // 发送到服务端 (类型"1", 接收者"Engine")
            if (webSocketManager.isConnected()) {
                val success = webSocketManager.webSocketSend("1", "Engine", dataObj)
                if (success) {
                    Log.d("MainActivity.webSocket.sendMessage", "GPS位置数据发送成功 (10秒定时发送)")
                } else {
                    Log.e("MainActivity.webSocket.sendMessage", "GPS位置数据发送失败")
                }
            } else {
                Log.e("MainActivity.webSocket.sendMessage", "WebSocket未连接，无法发送GPS数据")
            }
        } catch (e: Exception) {
            Log.e("MainActivity.webSocket.sendMessage", "发送GPS位置数据出错: ${e.message}")
        }
    }

    private fun sendBeaconData() {
        try {
            /*
            // 假数据
            val fakeBeacons = mutableListOf<Beacon>()
            // 使用Beacon.Builder创建Beacon对象
            val fakeBeacon = Beacon.Builder()
                .setBluetoothAddress("00:00:00:00:00:00")
                .setId1("FDA50693-A4E2-4FB1-AFCF-C6EB07647825")
                .setId2("20012")
                .setId3("53485")
                .setRssi(-65)
                .setTxPower(-59) // 设置一个默认的发射功率
                .build()
                
            fakeBeacons.add(fakeBeacon)
            // 使用假数据
            val beacons = fakeBeacons
            */
            // 从SensorDataManager获取最新的Beacon数据
            val beacons = SensorDataManager.getInstance().getBeaconData()
            
            // 在方法内定义rssiBeaconMap
            val rssiBeaconMap = mutableMapOf<String, Int>()
            
            // 处理每个beacon
            for (beacon in beacons) {
                // 检查RSSI值是否在有效范围内
                val rssi = beacon.rssi
                if (rssi < 0 && rssi > -95) {
                    val uuidStr = beacon.id1.toString().toUpperCase()
                    val code = uuidToCode[uuidStr]
                    
                    if (code != null) {
                        // 获取major和minor
                        val majorInt = beacon.id2.toString().toInt()
                        val minorInt = beacon.id3.toString().toInt()
                        
                        // 转换为字节数组
                        val majorBytes = ByteArray(2)
                        majorBytes[0] = ((majorInt shr 8) and 0xFF).toByte()
                        majorBytes[1] = (majorInt and 0xFF).toByte()
                        
                        val minorBytes = ByteArray(2)
                        minorBytes[0] = ((minorInt shr 8) and 0xFF).toByte()
                        minorBytes[1] = (minorInt and 0xFF).toByte()
                        
                        // 转换为十六进制
                        val majorHex = byteToHex(majorBytes, 2)
                        val minorHex = byteToHex(minorBytes, 2)
                        
                        // 生成MAC地址
                        val mac = code + majorHex + minorHex
                        
                        // 存储或更新RSSI值
                        val currentRssi = rssiBeaconMap[mac.toUpperCase()]
                        if (currentRssi == null || rssi > currentRssi) {
                            rssiBeaconMap[mac.toUpperCase()] = rssi
                        }
                    }
                }
            }
            
            if (rssiBeaconMap.isNotEmpty()) {
                // 创建Beacon数组
                val didArray = JSONArray()
                
                // 遍历rssiBeaconMap，生成JSON对象
                for ((mac, rssiValue) in rssiBeaconMap) {
                    val obj = JSONObject()
                    obj.put("id", mac)
                    obj.put("rssi", rssiValue)
                    didArray.put(obj)
                }
                
                // 创建map数组
                val mapArray = JSONArray()
                for ((code, uuid) in codeToUuid) {
                    val mapObj = JSONObject()
                    mapObj.put("key", code)
                    mapObj.put("value", uuid)
                    mapArray.put(mapObj)
                }
                
                // 获取时间戳和格式化时间
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentDate = dateFormat.format(Date(timestamp))
                
                // 增加定位计数
                locateCount++
                
                // 创建完整的定位请求数据
                val locateJsonObj = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("did", didArray)
                    put("map", mapArray)
                    put("locateCount", locateCount)
                    put("currentDate", currentDate)
                    put("timestamp", timestamp)
                    put("time", currentDate)
                    
                    // 添加mac数组（空）
                    put("mac", JSONArray())
                    
                    // 添加magnetic数组（空）
                    put("magnetic", JSONArray())
                    
                    // 添加orientation数组（空）
                    put("orientation", JSONArray())
                    
                    // 添加sensorInfo对象
                    val sensorInfoObj = JSONObject().apply {
                        put("isSensorValid", "0")
                        put("step", "0")
                        put("isMoving", "0")
                        put("compassValue", "0")
                    }
                    put("sensorInfo", sensorInfoObj)
                }
                
                // 发送到服务端
                if (webSocketManager.isConnected()) {
                    val dataObj = JSONObject().apply {
                        put("httpLocateParam", locateJsonObj)
                    }
                    val success = webSocketManager.webSocketSend("1", "Engine", dataObj)
                    if (success) {
                        Log.d("MainActivity.webSocket.sendMessage", "定位数据发送成功: ${didArray.length()}个信标 (10秒定时发送)")
                    } else {
                        Log.e("MainActivity.webSocket.sendMessage", "定位数据发送失败")
                    }
                } else {
                    Log.e("MainActivity.webSocket.sendMessage", "WebSocket未连接，无法发送定位数据")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity.webSocket.sendMessage", "处理Beacon数据出错: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun sendWifiData() {
        try {
            // 从SensorDataManager获取最新的Wifi数据
            val wifiData = SensorDataManager.getInstance().getWifiScanResults()
            
            // 固定使用code "0001"，对应UUID 1918FC80-B111-3441-A9AC-B1001C2FE510
            val wifiCode = "0001"
            
            // 在方法内定义wifiRssiMap
            val wifiRssiMap = mutableMapOf<String, Int>()
            
            // 处理每个WiFi扫描结果
            for (scanResult in wifiData) {
                try {
                    // 获取MAC地址，跳过没有MAC地址的WiFi热点
                    val bssid = scanResult.BSSID ?: continue
                    
                    // 移除MAC地址中的冒号
                    val macAddress = bssid.replace(":", "")
                    
                    // 获取信号强度
                    val rssi = scanResult.level
                    
                    // 确保MAC地址长度正确
                    if (macAddress.length == 12) {  // 标准MAC地址没有冒号后是12个字符
                        // 分割MAC地址 - 前4个字符作为前缀，中间4个字符作为major，最后4个字符作为minor
                        val major = macAddress.substring(4, 8)
                        val minor = macAddress.substring(8, 12)
                        
                        // 生成ID (格式: code + major + minor)
                        val id = wifiCode + major + minor
                        
                        // 存储或更新RSSI值 - 只保留信号最强的
                        val currentRssi = wifiRssiMap[id.toUpperCase()]
                        if (currentRssi == null || rssi > currentRssi) {
                            wifiRssiMap[id.toUpperCase()] = rssi
                            Log.d("MainActivity.WiFi", "WiFi: $macAddress, RSSI: $rssi, ID: $id")
                        }
                    }
                } catch (e: Exception) {
                    // 跳过无法处理的WiFi热点
                    Log.e("MainActivity.WiFi", "处理WiFi热点时出错: ${e.message}")
                    continue
                }
            }
            
            if (wifiRssiMap.isNotEmpty()) {
                // 创建WiFi数组
                val didArray = JSONArray()
                
                // 遍历wifiRssiMap，生成JSON对象
                for ((id, rssiValue) in wifiRssiMap) {
                    val obj = JSONObject()
                    obj.put("id", id)
                    obj.put("rssi", rssiValue)
                    didArray.put(obj)
                }
                
                // 创建map数组
                val mapArray = JSONArray()
                for ((code, uuid) in codeToUuid) {
                    val mapObj = JSONObject()
                    mapObj.put("key", code)
                    mapObj.put("value", uuid)
                    mapArray.put(mapObj)
                }
                
                // 获取时间戳和格式化时间
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentDate = dateFormat.format(Date(timestamp))
                
                // 增加定位计数
                locateCount++
                
                // 创建完整的定位请求数据
                val locateJsonObj = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("did", didArray)
                    put("map", mapArray)
                    put("locateCount", locateCount)
                    put("currentDate", currentDate)
                    put("timestamp", timestamp)
                    put("time", currentDate)
                    
                    // 添加mac数组（空）
                    put("mac", JSONArray())
                    
                    // 添加magnetic数组（空）
                    put("magnetic", JSONArray())
                    
                    // 添加orientation数组（空）
                    put("orientation", JSONArray())
                    
                    // 添加sensorInfo对象
                    val sensorInfoObj = JSONObject().apply {
                        put("isSensorValid", "0")
                        put("step", "0")
                        put("isMoving", "0")
                        put("compassValue", "0")
                    }
                    put("sensorInfo", sensorInfoObj)
                }
                
                // 发送到服务端
                if (webSocketManager.isConnected()) {
                    val dataObj = JSONObject().apply {
                        put("httpLocateParam", locateJsonObj)
                    }
                    val success = webSocketManager.webSocketSend("1", "Engine", dataObj)
                    if (success) {
                        Log.d("MainActivity.webSocket.sendMessage", "WiFi定位数据发送成功: ${didArray.length()}个WiFi")
                    } else {
                        Log.e("MainActivity.webSocket.sendMessage", "WiFi定位数据发送失败")
                    }
                } else {
                    Log.e("MainActivity.webSocket.sendMessage", "WebSocket未连接，无法发送WiFi数据")
                }
            } else {
                Log.d("MainActivity.webSocket.sendMessage", "没有可用的WiFi数据")
            }
        } catch (e: Exception) {
            Log.e("MainActivity.webSocket.sendMessage", "处理WiFi数据出错: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 显示登出确认对话框
     */
    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("确认登出")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                performLogout()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 加载语音识别演示页面
     */
    private fun loadSpeechRecognitionDemo() {
        try {
            // 从assets目录加载HTML文件
            val demoUrl = "file:///android_asset/speech_recognition_demo.html"
            Log.d("MainActivity", "加载语音识别演示页面: $demoUrl")
            
            // 在WebView中加载HTML
            webView.loadUrl(demoUrl)
            
            // 记录到WebSocket日志
            WebSocketLogManager.getInstance().addLog(
                WebSocketLogManager.LogType.INFO,
                "加载语音识别演示页面",
                demoUrl
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "加载语音识别演示页面失败: ${e.message}")
            Toast.makeText(this, "加载演示页面失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
} 