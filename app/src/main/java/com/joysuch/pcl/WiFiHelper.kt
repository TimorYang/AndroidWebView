package com.joysuch.pcl

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log

/**
 * WiFi扫描帮助类，封装了WiFi扫描相关的所有功能
 */
class WiFiHelper(private val context: Context) {

    private val TAG = "WiFiHelper"
    private var wifiManager: WifiManager? = null
    private var scanResultReceiver: BroadcastReceiver? = null
    private var isScanning = false
    private var onScanResultListener: OnScanResultListener? = null
    private var onScanErrorListener: OnScanErrorListener? = null
    private var onPermissionCallback: OnPermissionCallback? = null
    private var onWifiServiceCallback: OnWifiServiceCallback? = null
    
    init {
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    companion object {
        const val WIFI_PERMISSION_REQUEST_CODE = 1002
        
        /**
         * 获取所需的权限列表
         */
        fun getRequiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CHANGE_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
                )
            }
        }
    }
    
    /**
     * WiFi扫描结果监听器接口
     */
    interface OnScanResultListener {
        fun onScanResult(results: List<ScanResult>)
    }
    
    /**
     * WiFi扫描错误监听器接口
     */
    interface OnScanErrorListener {
        fun onScanError(errorMessage: String)
    }
    
    /**
     * 权限回调接口
     */
    interface OnPermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }
    
    /**
     * WiFi服务状态回调接口
     */
    interface OnWifiServiceCallback {
        fun onWifiEnabled()
        fun onWifiDisabled()
    }
    
    /**
     * 设置扫描结果监听器
     */
    fun setOnScanResultListener(listener: OnScanResultListener) {
        onScanResultListener = listener
    }
    
    /**
     * 设置扫描错误监听器
     */
    fun setOnScanErrorListener(listener: OnScanErrorListener) {
        onScanErrorListener = listener
    }
    
    /**
     * 设置权限回调
     */
    fun setOnPermissionCallback(callback: OnPermissionCallback) {
        onPermissionCallback = callback
    }
    
    /**
     * 设置WiFi服务状态回调
     */
    fun setOnWifiServiceCallback(callback: OnWifiServiceCallback) {
        onWifiServiceCallback = callback
    }
    
    /**
     * 检查WiFi扫描所需权限
     * @return 是否有所有所需权限
     */
    fun checkWifiPermissions(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }
    
    /**
     * 显示权限请求说明对话框
     */
    fun showPermissionRationaleDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("需要位置和WiFi权限")
            .setMessage("为了扫描周围的WiFi网络，我们需要位置和WiFi相关权限。")
            .setPositiveButton("确定") { _, _ ->
                requestWifiPermissions(activity)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                onPermissionCallback?.onPermissionDenied()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 请求WiFi扫描所需权限
     */
    fun requestWifiPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            getRequiredPermissions(),
            WIFI_PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * 处理权限请求结果
     * @return 是否处理了权限请求
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == WIFI_PERMISSION_REQUEST_CODE) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            
            if (allGranted) {
                onPermissionCallback?.onPermissionGranted()
            } else {
                onPermissionCallback?.onPermissionDenied()
            }
            return true
        }
        return false
    }
    
    /**
     * 检查WiFi是否已开启
     */
    fun isWifiEnabled(): Boolean {
        return wifiManager?.isWifiEnabled == true
    }
    
    /**
     * 显示WiFi设置对话框
     */
    fun showWifiSettingsDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("需要开启WiFi")
            .setMessage("为了扫描周围的WiFi网络，请开启WiFi。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                onWifiServiceCallback?.onWifiDisabled()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 开始WiFi扫描
     * @return 是否成功开始扫描
     */
    fun startWifiScan(): Boolean {
        if (isScanning) {
            return true // 已经在扫描中
        }
        
        if (!checkWifiPermissions()) {
            onScanErrorListener?.onScanError("没有所需权限")
            return false
        }
        
        if (!isWifiEnabled()) {
            onScanErrorListener?.onScanError("WiFi未开启")
            return false
        }
        
        try {
            // 注册广播接收器
            if (scanResultReceiver == null) {
                scanResultReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                        if (success) {
                            val results = wifiManager?.scanResults ?: emptyList()
                            onScanResultListener?.onScanResult(results)
                        } else {
                            // 扫描失败，尝试使用上一次的结果
                            val lastResults = wifiManager?.scanResults ?: emptyList()
                            if (lastResults.isNotEmpty()) {
                                onScanResultListener?.onScanResult(lastResults)
                                Log.w(TAG, "Scan failed, using last results")
                            } else {
                                onScanErrorListener?.onScanError("扫描失败")
                            }
                        }
                    }
                }
                
                context.registerReceiver(
                    scanResultReceiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                )
            }
            
            // 开始扫描
            val success = wifiManager?.startScan() ?: false
            if (!success) {
                onScanErrorListener?.onScanError("无法启动扫描")
                return false
            }
            
            isScanning = true
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting WiFi scan: ${e.message}")
            onScanErrorListener?.onScanError("扫描失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 停止WiFi扫描
     */
    fun stopWifiScan() {
        try {
            if (scanResultReceiver != null) {
                context.unregisterReceiver(scanResultReceiver)
                scanResultReceiver = null
            }
            isScanning = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WiFi scan: ${e.message}")
        }
    }
    
    /**
     * 获取上次扫描结果
     * @return WiFi扫描结果列表
     */
    fun getLastScanResults(): List<ScanResult> {
        return wifiManager?.scanResults ?: emptyList()
    }
    
    /**
     * 开始周期性WiFi扫描
     * @param interval 扫描间隔（毫秒）
     */
    private var periodicScanHandler: android.os.Handler? = null
    private var periodicScanRunnable: Runnable? = null
    
    fun startPeriodicScan(interval: Long) {
        stopPeriodicScan() // 先停止之前的周期性扫描
        
        if (periodicScanHandler == null) {
            periodicScanHandler = android.os.Handler(android.os.Looper.getMainLooper())
        }
        
        periodicScanRunnable = object : Runnable {
            override fun run() {
                if (isWifiEnabled()) {
                    Log.d(TAG, "执行周期性WiFi扫描")
                    startWifiScan()
                } else {
                    Log.d(TAG, "WiFi未开启，跳过周期性扫描")
                }
                
                // 继续下一次扫描
                periodicScanHandler?.postDelayed(this, interval)
            }
        }
        
        // 立即执行第一次扫描
        periodicScanRunnable?.let {
            periodicScanHandler?.post(it)
        }
        
        Log.d(TAG, "已启动周期性WiFi扫描, 间隔: $interval ms")
    }
    
    /**
     * 停止周期性WiFi扫描
     */
    fun stopPeriodicScan() {
        periodicScanRunnable?.let {
            periodicScanHandler?.removeCallbacks(it)
            periodicScanRunnable = null
        }
        
        Log.d(TAG, "已停止周期性WiFi扫描")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopWifiScan()
        stopPeriodicScan()
        periodicScanHandler = null
        wifiManager = null
        onScanResultListener = null
        onScanErrorListener = null
        onPermissionCallback = null
        onWifiServiceCallback = null
    }
} 