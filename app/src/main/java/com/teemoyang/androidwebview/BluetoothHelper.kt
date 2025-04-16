package com.teemoyang.androidwebview

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * 蓝牙帮助类，封装了蓝牙相关的所有功能
 * 同时兼容 API 26+（Android 8.0+）和 API 31+（Android 12+）
 * 支持扫描Beacon设备
 */
class BluetoothHelper(private val context: Context) {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var isBeaconScanning = false
    private val handler = Handler(Looper.getMainLooper())
    
    // 回调接口
    private var onDeviceFoundListener: OnDeviceFoundListener? = null
    private var onBluetoothErrorListener: OnBluetoothErrorListener? = null
    private var onPermissionCallback: OnPermissionCallback? = null
    private var onBluetoothStateCallback: OnBluetoothStateCallback? = null
    private var onBeaconFoundListener: OnBeaconFoundListener? = null
    
    // 设备列表
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val discoveredBeacons = mutableListOf<BeaconModel>()
    
    // 扫描设置
    private val scanPeriod = 10000L // 10秒
    
    // BLE扫描回调
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            val device = result.device
            val rssi = result.rssi
            val scanRecord = result.scanRecord?.bytes
            
            // 尝试解析为Beacon
            val beacon = BeaconModel.parse(device, rssi, scanRecord)
            if (beacon != null && beacon.beaconType != BeaconModel.BeaconType.UNKNOWN) {
                // 如果是Beacon，通知Beacon回调
                val isNewBeacon = !discoveredBeacons.contains(beacon)
                if (isNewBeacon) {
                    discoveredBeacons.add(beacon)
                    onBeaconFoundListener?.onBeaconFound(beacon)
                } else {
                    // 更新已有Beacon的RSSI和距离信息
                    val index = discoveredBeacons.indexOf(beacon)
                    if (index >= 0) {
                        discoveredBeacons[index] = beacon
                        onBeaconFoundListener?.onBeaconUpdated(beacon)
                    }
                }
            } else if (device != null) {
                // 普通蓝牙设备
                if (!discoveredDevices.contains(device)) {
                    discoveredDevices.add(device)
                    onDeviceFoundListener?.onDeviceFound(device)
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            for (result in results) {
                onScanResult(0, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            val errorMessage = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "扫描已经开始"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持BLE扫描"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "内部错误"
                else -> "未知错误: $errorCode"
            }
            onBluetoothErrorListener?.onBluetoothError("BLE扫描失败: $errorMessage")
            isBeaconScanning = false
        }
    }
    
    // 广播接收器，用于接收蓝牙事件
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // 发现设备 - 兼容不同API级别
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    device?.let {
                        if (!discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                            onDeviceFoundListener?.onDeviceFound(it)
                        }
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    // 蓝牙状态变化
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> {
                            onBluetoothStateCallback?.onBluetoothEnabled()
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            onBluetoothStateCallback?.onBluetoothDisabled()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    // 开始搜索
                    isScanning = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // 搜索结束
                    isScanning = false
                }
            }
        }
    }
    
    init {
        // 初始化蓝牙管理器和适配器
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }
    
    /**
     * 设备发现监听器接口
     */
    interface OnDeviceFoundListener {
        fun onDeviceFound(device: BluetoothDevice)
    }
    
    /**
     * Beacon发现监听器接口
     */
    interface OnBeaconFoundListener {
        fun onBeaconFound(beacon: BeaconModel)
        fun onBeaconUpdated(beacon: BeaconModel)
    }
    
    /**
     * 蓝牙错误监听器接口
     */
    interface OnBluetoothErrorListener {
        fun onBluetoothError(errorMessage: String)
    }
    
    /**
     * 权限回调接口
     */
    interface OnPermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }
    
    /**
     * 蓝牙状态回调接口
     */
    interface OnBluetoothStateCallback {
        fun onBluetoothEnabled()
        fun onBluetoothDisabled()
    }
    
    /**
     * 设置设备发现监听器
     */
    fun setOnDeviceFoundListener(listener: OnDeviceFoundListener) {
        onDeviceFoundListener = listener
    }
    
    /**
     * 设置Beacon发现监听器
     */
    fun setOnBeaconFoundListener(listener: OnBeaconFoundListener) {
        onBeaconFoundListener = listener
    }
    
    /**
     * 设置蓝牙错误监听器
     */
    fun setOnBluetoothErrorListener(listener: OnBluetoothErrorListener) {
        onBluetoothErrorListener = listener
    }
    
    /**
     * 设置权限回调
     */
    fun setOnPermissionCallback(callback: OnPermissionCallback) {
        onPermissionCallback = callback
    }
    
    /**
     * 设置蓝牙状态回调
     */
    fun setOnBluetoothStateCallback(callback: OnBluetoothStateCallback) {
        onBluetoothStateCallback = callback
    }
    
    /**
     * 检查蓝牙权限
     * @return 是否有蓝牙权限
     */
    fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上需要蓝牙扫描和连接权限
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 以下需要蓝牙和定位权限
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 显示权限请求说明对话框
     */
    fun showPermissionRationaleDialog(activity: Activity, requestCode: Int) {
        AlertDialog.Builder(activity)
            .setTitle("需要蓝牙权限")
            .setMessage("为了扫描和连接蓝牙设备，我们需要蓝牙相关权限和位置权限。")
            .setPositiveButton("确定") { _, _ ->
                requestBluetoothPermission(activity, requestCode)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                onPermissionCallback?.onPermissionDenied()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 请求蓝牙权限
     */
    fun requestBluetoothPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                requestCode
            )
        } else {
            // Android 12 以下
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                requestCode
            )
        }
    }
    
    /**
     * 处理权限请求结果
     * @param requestCode 请求码
     * @param permissions 权限数组
     * @param grantResults 授权结果数组
     * @return 是否处理了权限请求
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                onPermissionCallback?.onPermissionGranted()
            } else {
                onPermissionCallback?.onPermissionDenied()
            }
            return true
        }
        return false
    }
    
    /**
     * 检查蓝牙是否开启
     * @return 蓝牙是否开启
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * 显示蓝牙设置对话框
     */
    fun showBluetoothSettingsDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("需要开启蓝牙")
            .setMessage("为了扫描和连接蓝牙设备，请开启蓝牙。")
            .setPositiveButton("去设置") { _, _ ->
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                try {
                    // 高版本Android需要权限检查
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            activity.startActivity(enableBtIntent)
                        } else {
                            // 如果没有连接权限，则先请求权限
                            requestBluetoothPermission(activity, BLUETOOTH_PERMISSION_REQUEST_CODE)
                        }
                    } else {
                        // 低版本Android直接调用
                        activity.startActivity(enableBtIntent)
                    }
                } catch (e: Exception) {
                    onBluetoothErrorListener?.onBluetoothError("无法打开蓝牙设置: ${e.message}")
                }
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                onBluetoothStateCallback?.onBluetoothDisabled()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 注册蓝牙广播接收器
     */
    fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }
    
    /**
     * 取消注册蓝牙广播接收器
     */
    fun unregisterBluetoothReceiver() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // 忽略未注册的异常
        }
    }
    
    /**
     * 开始扫描蓝牙设备
     * @return 是否成功开始扫描
     */
    fun startScan(): Boolean {
        if (!checkBluetoothPermission()) {
            onBluetoothErrorListener?.onBluetoothError("没有蓝牙权限")
            return false
        }
        
        if (!isBluetoothEnabled()) {
            onBluetoothErrorListener?.onBluetoothError("蓝牙未开启")
            return false
        }
        
        try {
            // 先注册广播接收器
            registerBluetoothReceiver()
            
            // 清空设备列表
            discoveredDevices.clear()
            
            // 如果正在扫描，先停止
            if (isScanning) {
                stopScan()
            }
            
            // 根据不同Android版本处理
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    return bluetoothAdapter?.startDiscovery() ?: false
                } else {
                    onBluetoothErrorListener?.onBluetoothError("没有蓝牙扫描权限")
                    return false
                }
            } else {
                // 低版本Android直接调用
                return bluetoothAdapter?.startDiscovery() ?: false
            }
        } catch (e: Exception) {
            onBluetoothErrorListener?.onBluetoothError("开始扫描失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 开始扫描Beacon设备
     * @param scanPeriod 扫描时间，默认10秒
     * @return 是否成功开始扫描
     */
    fun startBeaconScan(scanPeriod: Long = this.scanPeriod): Boolean {
        if (!checkBluetoothPermission()) {
            onBluetoothErrorListener?.onBluetoothError("没有蓝牙权限")
            return false
        }
        
        if (!isBluetoothEnabled()) {
            onBluetoothErrorListener?.onBluetoothError("蓝牙未开启")
            return false
        }
        
        // 检查设备是否支持BLE
        if (bluetoothLeScanner == null) {
            onBluetoothErrorListener?.onBluetoothError("设备不支持BLE")
            return false
        }
        
        try {
            // 如果正在扫描，先停止
            if (isBeaconScanning) {
                stopBeaconScan()
            }
            
            // 清空Beacon列表
            discoveredBeacons.clear()
            
            // 设置扫描参数
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 高频率扫描，适合于寻找短暂连接的设备
                .build()
            
            // 开始扫描
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothLeScanner?.startScan(null, settings, bleScanCallback)
                    isBeaconScanning = true
                    
                    // 设置超时停止扫描
                    handler.postDelayed({ stopBeaconScan() }, scanPeriod)
                    return true
                } else {
                    onBluetoothErrorListener?.onBluetoothError("没有BLE扫描权限")
                    return false
                }
            } else {
                // 低版本Android直接调用
                bluetoothLeScanner?.startScan(null, settings, bleScanCallback)
                isBeaconScanning = true
                
                // 设置超时停止扫描
                handler.postDelayed({ stopBeaconScan() }, scanPeriod)
                return true
            }
        } catch (e: Exception) {
            onBluetoothErrorListener?.onBluetoothError("开始Beacon扫描失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 停止扫描蓝牙设备
     */
    fun stopScan() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothAdapter?.cancelDiscovery()
                }
            } else {
                bluetoothAdapter?.cancelDiscovery()
            }
            isScanning = false
        } catch (e: Exception) {
            onBluetoothErrorListener?.onBluetoothError("停止扫描失败: ${e.message}")
        }
    }
    
    /**
     * 停止扫描Beacon设备
     */
    fun stopBeaconScan() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothLeScanner?.stopScan(bleScanCallback)
                }
            } else {
                bluetoothLeScanner?.stopScan(bleScanCallback)
            }
            isBeaconScanning = false
        } catch (e: Exception) {
            onBluetoothErrorListener?.onBluetoothError("停止Beacon扫描失败: ${e.message}")
        }
    }
    
    /**
     * 获取已发现的蓝牙设备列表
     * @return 设备列表
     */
    fun getDiscoveredDevices(): List<BluetoothDevice> {
        return discoveredDevices.toList()
    }
    
    /**
     * 获取已发现的Beacon设备列表
     * @return Beacon列表
     */
    fun getDiscoveredBeacons(): List<BeaconModel> {
        return discoveredBeacons.toList()
    }
    
    /**
     * 获取设备名称，兼容不同Android版本
     * @param device 蓝牙设备
     * @return 设备名称，如果为空则返回MAC地址
     */
    fun getDeviceName(device: BluetoothDevice): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    device.name ?: device.address
                } else {
                    device.address
                }
            } else {
                // 低版本Android直接获取名称
                device.name ?: device.address
            }
        } catch (e: Exception) {
            device.address
        }
    }
    
    /**
     * 获取蓝牙设备信息的JSON字符串
     * @return 蓝牙设备信息的JSON字符串
     */
    fun getBluetoothInfoJson(): String? {
        if (!isBluetoothEnabled()) {
            return null
        }
        
        return try {
            val jsonObject = JSONObject()
            jsonObject.put("enabled", true)
            jsonObject.put("deviceCount", discoveredDevices.size)
            
            val devicesArray = JSONObject()
            discoveredDevices.forEachIndexed { index, device ->
                val deviceJson = JSONObject()
                deviceJson.put("name", getDeviceName(device))
                deviceJson.put("address", device.address)
                devicesArray.put(index.toString(), deviceJson)
            }
            
            jsonObject.put("devices", devicesArray)
            jsonObject.toString()
        } catch (e: Exception) {
            onBluetoothErrorListener?.onBluetoothError("生成蓝牙JSON失败: ${e.message}")
            null
        }
    }
    
    /**
     * 获取Beacon设备信息的JSON字符串
     * @return Beacon设备信息的JSON字符串
     */
    fun getBeaconInfoJson(): String? {
        if (!isBluetoothEnabled()) {
            return null
        }
        
        return try {
            val jsonObject = JSONObject()
            jsonObject.put("enabled", true)
            jsonObject.put("beaconCount", discoveredBeacons.size)
            
            val beaconsArray = JSONObject()
            discoveredBeacons.forEachIndexed { index, beacon ->
                beaconsArray.put(index.toString(), beacon.toJson())
            }
            
            jsonObject.put("beacons", beaconsArray)
            jsonObject.toString()
        } catch (e: Exception) {
            onBluetoothErrorListener?.onBluetoothError("生成Beacon JSON失败: ${e.message}")
            null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopScan()
        stopBeaconScan()
        unregisterBluetoothReceiver()
        handler.removeCallbacksAndMessages(null)
        discoveredDevices.clear()
        discoveredBeacons.clear()
        onDeviceFoundListener = null
        onBluetoothErrorListener = null
        onPermissionCallback = null
        onBluetoothStateCallback = null
        onBeaconFoundListener = null
    }
    
    companion object {
        const val BLUETOOTH_PERMISSION_REQUEST_CODE = 2001
    }
} 