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

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var webView: WebView
    
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
} 