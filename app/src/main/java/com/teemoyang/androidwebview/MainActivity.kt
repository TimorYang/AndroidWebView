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
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import kotlin.math.abs
import android.widget.Button
import android.util.Log
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

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var webView: WebView
    private lateinit var binding: ActivityMainBinding
    
    // URL相关常量
    private val BASE_URL = "https://navmobiletest.joysuch.com"
    private val MINIPROGRAM_PATH = "/navmobile/JoysuchMiniProgram/index.html#"
    private val WS_PROTOCOL = "wss://"  // WebSocket安全协议
    private val WS_BASE_DOMAIN = "navmobiletest.joysuch.com"  // 不含协议的域名
    private val WS_PATH = "/locationEngine/websocket/"  // WebSocket路径
    
    // WebSocket相关
    private val webSocketManager = WebSocketManager()
    private var isWebSocketConnected = false
    
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取并显示设备ID
        displayDeviceId()
        
        // 设置系统栏适配
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
        }

        // 设置WebView加载完成监听器
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("MainActivity", "WebView页面加载完成: $url")
                
                // WebView加载完成后，启动传感器扫描
                startSensorsAfterWebViewLoad()
            }
        }
        // get 形式 拼接 deviceId
        val deviceId = DeviceManager.getInstance().getDeviceId()
        webView.loadUrl(BASE_URL + MINIPROGRAM_PATH + "?deviceId=$deviceId")
        
        // 设置返回键处理，兼容新旧版本Android
        setupBackNavigation()
        
        // 初始化摇一摇功能
        initShakeDetection()

        // 初始化按钮
        initButtons()
        
        // 自动连接WebSocket
        connectToWebSocket()
    }
    
    /**
     * 连接到WebSocket服务器
     */
    private fun connectToWebSocket() {
        // 使用设备管理器获取设备ID
        val deviceId = DeviceManager.getInstance().getDeviceId()
        
        // 构建WebSocket URL (格式: wss://domain/locationEngine/websocket/deviceId_WeChat___timestamp)
        val timestamp = System.currentTimeMillis()
        val webSocketUrl = WS_PROTOCOL + WS_BASE_DOMAIN + WS_PATH + deviceId + "_WeChat___" + timestamp
        
        // 准备连接参数
        val params = mapOf(
            "url" to webSocketUrl,
            "deviceId" to deviceId
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
    
    private fun initShakeDetection() {
        // 获取传感器服务
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        
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
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 关闭WebSocket连接
        if (isWebSocketConnected) {
            webSocketManager.close()
        }
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
        val btnWebsocketTest = findViewById<Button>(R.id.btnWebsocketTest)
        btnWebsocketTest.setOnClickListener {
            val intent = Intent(this, WebSocketTestActivity::class.java)
            startActivity(intent)
        }

        val btnBeaconScan = findViewById<Button>(R.id.btnBeaconScan)
        btnBeaconScan.setOnClickListener {
            val intent = Intent(this, BeaconScanActivity::class.java)
            startActivity(intent)
        }
        
        val btnWifiScan = findViewById<Button>(R.id.btnWifiScan)
        btnWifiScan.setOnClickListener {
            val intent = Intent(this, WiFiScanActivity::class.java)
            startActivity(intent)
        }
        
        val btnAccelerometer = findViewById<Button>(R.id.btnAccelerometer)
        btnAccelerometer.setOnClickListener {
            val intent = Intent(this, AccelerometerActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * 显示设备唯一标识
     */
    private fun displayDeviceId() {
        // 获取设备ID
        val deviceId = DeviceManager.getInstance().getDeviceId()
        
        // 显示设备ID（可以根据实际布局调整）
        Toast.makeText(this, "设备ID: $deviceId", Toast.LENGTH_LONG).show()
        
        // 记录到日志
        Log.d("MainActivity", "当前设备ID: $deviceId")
        
        // 同时显示设备信息
        val deviceInfo = DeviceManager.getInstance().getDeviceInfo()
        Log.d("MainActivity", "设备信息: $deviceInfo")
    }

    /**
     * WebView加载完成后启动所有传感器
     */
    private fun startSensorsAfterWebViewLoad() {
        Log.d("MainActivity", "开始启动所有传感器扫描...")
        
        // 1. 启动定位服务
        startLocationService()
        
        // 2. 启动蓝牙扫描
        startBeaconScan()
        
        // 3. 启动WiFi扫描
        startWifiScan()
        
        // 通知全局数据管理器启动自动更新
        SensorDataManager.getInstance().startAutoUpdate()
        
        Toast.makeText(this, "所有传感器扫描已启动", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 启动定位服务
     */
    private fun startLocationService() {
        Log.d("MainActivity", "启动定位服务")
        val locationHelper = LocationHelper(this)
        
        // 设置位置更新监听器
        locationHelper.setOnLocationUpdateListener(object : LocationHelper.OnLocationUpdateListener {
            override fun onLocationUpdated(location: Location) {
                Log.d("MainActivity", "位置已更新: ${location.latitude}, ${location.longitude}")
                // 更新到全局数据管理器
                SensorDataManager.getInstance().updateLocation(location)
            }
        })
        
        // 设置位置错误监听器
        locationHelper.setOnLocationErrorListener(object : LocationHelper.OnLocationErrorListener {
            override fun onLocationError(errorMessage: String) {
                Log.e("MainActivity", "定位错误: $errorMessage")
            }
        })
        
        // 设置权限回调
        locationHelper.setOnPermissionCallback(object : LocationHelper.OnPermissionCallback {
            override fun onPermissionGranted() {
                // 启动位置更新
                if (locationHelper.isLocationServiceEnabled()) {
                    locationHelper.startLocationUpdates()
                } else {
                    // 如果是Activity，可以显示设置对话框
                    locationHelper.showLocationSettingsDialog(this@MainActivity)
                }
            }
            
            override fun onPermissionDenied() {
                Log.e("MainActivity", "位置权限被拒绝")
            }
        })
        
        // 检查并请求位置权限
        if (!locationHelper.checkLocationPermission()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                locationHelper.showPermissionRationaleDialog(this, LocationHelper.LOCATION_PERMISSION_REQUEST_CODE)
            } else {
                locationHelper.requestLocationPermission(this, LocationHelper.LOCATION_PERMISSION_REQUEST_CODE)
            }
        } else {
            // 已有权限，检查位置服务
            if (locationHelper.isLocationServiceEnabled()) {
                locationHelper.startLocationUpdates()
            } else {
                locationHelper.showLocationSettingsDialog(this)
            }
        }
    }
    
    /**
     * 启动蓝牙扫描
     */
    private fun startBeaconScan() {
        Log.d("MainActivity", "启动蓝牙信标扫描")
        
        try {
            // 检查蓝牙权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                // 有权限，执行蓝牙扫描
                Log.d("MainActivity", "蓝牙权限已获取，开始扫描信标")
                
                // 向H5发送权限状态
                sendBluetoothPermissionStatus(true)
                
                // 使用BeaconManager进行扫描
                val beaconManager = BeaconManager.getInstanceForApplication(this)
                
                // 添加解析器，支持多种格式
                // iBeacon格式
                beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"))
                // Eddystone UID格式
                beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19"))
                // Eddystone URL格式
                beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout("s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v"))
                
                // 设置前台扫描周期 (1秒扫描，0秒休息)
                beaconManager.foregroundScanPeriod = 1000L
                beaconManager.foregroundBetweenScanPeriod = 0L
                
                // 应用扫描设置
                beaconManager.updateScanPeriods()
                
                // 添加监听器
                beaconManager.addRangeNotifier { beacons, _ ->
                    // 记录发现的信标数量
                    Log.d("MainActivity", "发现 ${beacons.size} 个信标")
                    
                    if (beacons.isNotEmpty()) {
                        // 将信标数据更新到SensorDataManager
                        SensorDataManager.getInstance().updateBeaconData(beacons)
                        
                        // 查看第一个信标的信息
                        val firstBeacon = beacons.first()
                        Log.d("MainActivity", "信标详情: MAC=${firstBeacon.bluetoothAddress}, " +
                                "UUID=${firstBeacon.id1}, " +
                                "Major=${firstBeacon.id2}, " +
                                "Minor=${firstBeacon.id3}, " +
                                "RSSI=${firstBeacon.rssi}, " +
                                "距离=${firstBeacon.distance}米")
                    }
                }
                
                // 开始扫描 (扫描所有信标，无过滤)
                val region = Region("allBeaconsRegion", null, null, null)
                beaconManager.startRangingBeacons(region)
                
                Log.d("MainActivity", "信标扫描已启动")
            } else {
                // 请求蓝牙权限
                Log.d("MainActivity", "请求蓝牙权限")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    100
                )
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "启动蓝牙扫描失败: ${e.message}")
            e.printStackTrace()
            // 发送权限状态为false
            sendBluetoothPermissionStatus(false)
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
        
        if (requestCode == 100) { // 蓝牙权限请求码
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            // 向H5发送权限状态
            sendBluetoothPermissionStatus(granted)
            
            if (granted) {
                // 权限已获取，重新启动蓝牙扫描
                startBeaconScan()
            } else {
                // 显示提示
                Toast.makeText(this, "没有蓝牙权限，无法扫描附近的蓝牙设备", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 启动WiFi扫描
     */
    private fun startWifiScan() {
        Log.d("MainActivity", "启动WiFi扫描")
        val wifiHelper = WiFiHelper(this)
        
        // 设置WiFi扫描结果监听器
        wifiHelper.setOnScanResultListener(object : WiFiHelper.OnScanResultListener {
            override fun onScanResult(results: List<ScanResult>) {
                Log.d("MainActivity", "WiFi扫描结果: ${results.size}个网络")
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
                Log.e("MainActivity", "WiFi扫描权限被拒绝")
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
} 