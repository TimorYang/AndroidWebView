package com.joysuch.pcl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.joysuch.pcl.databinding.ActivityWifiScanBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WiFiScanActivity : AppCompatActivity(), WiFiHelper.OnScanResultListener, 
                         WiFiHelper.OnScanErrorListener, WiFiHelper.OnPermissionCallback, 
                         WiFiHelper.OnWifiServiceCallback {

    private lateinit var binding: ActivityWifiScanBinding
    private lateinit var wifiAdapter: WiFiAdapter
    private lateinit var wifiHelper: WiFiHelper
    
    private val TAG = "WiFiScanActivity"
    private val wifiScanList = mutableListOf<ScanResult>()
    private var isScanning = false
    
    // 自动扫描间隔
    private val SCAN_INTERVAL = 10000L // 10秒
    private val handler = Handler(Looper.getMainLooper())
    
    // 日期格式化
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    // WiFi状态监听广播接收器
    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                when (state) {
                    WifiManager.WIFI_STATE_DISABLED -> {
                        // WiFi已关闭
                        onWifiDisabled()
                    }
                    WifiManager.WIFI_STATE_ENABLED -> {
                        // WiFi已开启
                        onWifiEnabled()
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWifiScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置标题和返回按钮
        supportActionBar?.apply {
            title = "WiFi扫描"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // 初始化WiFi帮助类
        wifiHelper = WiFiHelper(this)
        wifiHelper.setOnScanResultListener(this)
        wifiHelper.setOnScanErrorListener(this)
        wifiHelper.setOnPermissionCallback(this)
        wifiHelper.setOnWifiServiceCallback(this)
        
        setupRecyclerView()
        setupListeners()
        
        // 注册WiFi状态变化的广播接收器
        val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(wifiStateReceiver, filter)
        
        // 初始检查WiFi状态
        updateWifiStateUI(wifiHelper.isWifiEnabled())
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 停止定时任务和WiFi扫描
        handler.removeCallbacksAndMessages(null)
        wifiHelper.release()
        
        // 取消注册广播接收器
        try {
            unregisterReceiver(wifiStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun setupRecyclerView() {
        wifiAdapter = WiFiAdapter()
        binding.rvWifiList.apply {
            layoutManager = LinearLayoutManager(this@WiFiScanActivity)
            adapter = wifiAdapter
        }
    }
    
    private fun setupListeners() {
        // 扫描开关
        binding.switchScan.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndStartScan()
            } else {
                stopWifiScan()
            }
        }
    }
    
    private fun checkPermissionsAndStartScan() {
        // 检查WiFi是否已开启
        if (!wifiHelper.isWifiEnabled()) {
            wifiHelper.showWifiSettingsDialog(this)
            binding.switchScan.isChecked = false
            return
        }
        
        // 检查权限
        if (!wifiHelper.checkWifiPermissions()) {
            // 如果需要显示权限说明
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                wifiHelper.showPermissionRationaleDialog(this)
            } else {
                wifiHelper.requestWifiPermissions(this)
            }
        } else {
            startWifiScan()
        }
    }
    
    private fun startWifiScan() {
        if (isScanning) return
        
        binding.tvStatus.text = "正在扫描..."
        binding.switchScan.isChecked = true
        isScanning = true
        
        updateWifiList(emptyList()) // 先清空列表
        
        // 更新空视图状态
        updateEmptyView(true, "扫描中...")
        
        // 开始扫描
        wifiHelper.startWifiScan()
        
        // 启动定时扫描
        handler.postDelayed(scanRunnable, SCAN_INTERVAL)
    }
    
    private fun stopWifiScan() {
        isScanning = false
        binding.tvStatus.text = "扫描已停止"
        // 取消定时扫描
        handler.removeCallbacks(scanRunnable)
        wifiHelper.stopWifiScan()
    }
    
    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!isScanning) return
            
            // 重新扫描
            wifiHelper.startWifiScan()
            
            // 继续定时
            handler.postDelayed(this, SCAN_INTERVAL)
        }
    }
    
    private fun updateWifiList(results: List<ScanResult>) {
        // 更新列表
        wifiScanList.clear()
        wifiScanList.addAll(results)
        
        // 按信号强度排序（RSSI值越大信号越强）
        wifiScanList.sortByDescending { it.level }
        
        // 更新适配器
        wifiAdapter.updateWifiList(wifiScanList)
        
        // 更新计数
        binding.tvWifiCount.text = "发现WiFi: ${wifiScanList.size}"
        
        // 更新空视图状态
        updateEmptyView(wifiScanList.isEmpty(), "未发现WiFi网络")
    }
    
    private fun updateEmptyView(isEmpty: Boolean, message: String = "未发现WiFi网络") {
        binding.tvEmptyView.text = message
        binding.tvEmptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvWifiList.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    // WiFiHelper回调实现
    override fun onScanResult(results: List<ScanResult>) {
        // 扫描成功，更新列表
        updateWifiList(results)
        val formattedTime = dateFormat.format(Date())
        binding.tvStatus.text = "上次扫描: $formattedTime"
    }
    
    override fun onScanError(errorMessage: String) {
        // 扫描出错
        Log.e(TAG, "WiFi scan error: $errorMessage")
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        
        // 尝试获取上次结果
        val lastResults = wifiHelper.getLastScanResults()
        if (lastResults.isNotEmpty()) {
            updateWifiList(lastResults)
            val formattedTime = dateFormat.format(Date())
            binding.tvStatus.text = "扫描失败，显示上次结果 ($formattedTime)"
        } else {
            updateEmptyView(true, "扫描失败")
            binding.tvStatus.text = "扫描失败"
        }
    }
    
    override fun onPermissionGranted() {
        // 权限获取成功，开始扫描
        startWifiScan()
    }
    
    override fun onPermissionDenied() {
        // 权限被拒绝
        Toast.makeText(this, "需要位置权限才能扫描WiFi", Toast.LENGTH_LONG).show()
        binding.switchScan.isChecked = false
    }
    
    override fun onWifiEnabled() {
        // WiFi已启用
        Log.d(TAG, "WiFi enabled")
        updateWifiStateUI(true)
        
        // 如果开关是打开的，则开始扫描
        if (binding.switchScan.isChecked) {
            startWifiScan()
        }
    }
    
    override fun onWifiDisabled() {
        // WiFi未启用
        Log.d(TAG, "WiFi disabled")
        updateWifiStateUI(false)
        
        // 停止扫描
        if (isScanning) {
            stopWifiScan()
        }
    }
    
    // 权限请求回调
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        wifiHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    
    /**
     * 更新WiFi状态UI
     */
    private fun updateWifiStateUI(wifiEnabled: Boolean) {
        if (!wifiEnabled) {
            // WiFi已关闭，更新UI
            binding.switchScan.isChecked = false
            binding.tvStatus.text = "WiFi已关闭，无法扫描"
            updateEmptyView(true, "WiFi已关闭")
            
            // 显示提示
            binding.tvWifiDisabled.visibility = View.VISIBLE
        } else {
            // WiFi已开启，隐藏提示
            binding.tvWifiDisabled.visibility = View.GONE
        }
    }
} 