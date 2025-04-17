package com.teemoyang.androidwebview

import android.content.Context
import android.location.Location
import android.net.wifi.ScanResult
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.altbeacon.beacon.Beacon
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 传感器数据管理类
 * 集中管理所有传感器数据，实现单例模式以便全局访问
 */
class SensorDataManager private constructor() {
    
    private val TAG = "SensorDataManager"
    
    // 全局配置
    var isDebugging = false
    
    // 加速度计数据
    private var accelerometerData: FloatArray? = null
    private var gravityData: FloatArray? = null
    private var linearAccelerationData: FloatArray? = null
    private var lastAccelerometerUpdateTime = 0L
    
    // 位置数据
    private var currentLocation: Location? = null
    private var lastLocationUpdateTime = 0L
    
    // WiFi扫描数据
    private var wifiScanResults = mutableListOf<ScanResult>()
    private var lastWifiScanTime = 0L
    
    // 信标数据
    private var beaconList = mutableListOf<Beacon>()
    private var lastBeaconScanTime = 0L
    
    // 数据监听器
    private val accelerometerDataListeners = CopyOnWriteArrayList<AccelerometerDataListener>()
    private val locationDataListeners = CopyOnWriteArrayList<LocationDataListener>()
    private val wifiDataListeners = CopyOnWriteArrayList<WifiDataListener>()
    private val beaconDataListeners = CopyOnWriteArrayList<BeaconDataListener>()
    
    // 后台更新
    private val handler = Handler(Looper.getMainLooper())
    private var isAutoUpdating = false
    private var autoUpdateInterval = 1000L // 默认1秒
    
    /**
     * 加速度计数据监听接口
     */
    interface AccelerometerDataListener {
        fun onAccelerometerDataChanged(x: Float, y: Float, z: Float, timestamp: Long)
    }
    
    /**
     * 位置数据监听接口
     */
    interface LocationDataListener {
        fun onLocationChanged(location: Location, timestamp: Long)
    }
    
    /**
     * WiFi数据监听接口
     */
    interface WifiDataListener {
        fun onWifiScanResultsChanged(results: List<ScanResult>, timestamp: Long)
    }
    
    /**
     * 信标数据监听接口
     */
    interface BeaconDataListener {
        fun onBeaconDataChanged(beacons: List<Beacon>, timestamp: Long)
    }
    
    // 加速度相关方法
    
    /**
     * 更新加速度计数据
     */
    fun updateAccelerometerData(x: Float, y: Float, z: Float) {
        if (accelerometerData == null) {
            accelerometerData = FloatArray(3)
        }
        accelerometerData?.let {
            it[0] = x
            it[1] = y
            it[2] = z
        }
        lastAccelerometerUpdateTime = System.currentTimeMillis()
        
        // 通知监听器
        notifyAccelerometerDataChanged()
    }
    
    /**
     * 更新重力数据
     */
    fun updateGravityData(x: Float, y: Float, z: Float) {
        if (gravityData == null) {
            gravityData = FloatArray(3)
        }
        gravityData?.let {
            it[0] = x
            it[1] = y
            it[2] = z
        }
    }
    
    /**
     * 更新线性加速度数据
     */
    fun updateLinearAccelerationData(x: Float, y: Float, z: Float) {
        if (linearAccelerationData == null) {
            linearAccelerationData = FloatArray(3)
        }
        linearAccelerationData?.let {
            it[0] = x
            it[1] = y
            it[2] = z
        }
    }
    
    /**
     * 获取加速度计数据
     */
    fun getAccelerometerData(): FloatArray? = accelerometerData?.clone()
    
    /**
     * 获取重力数据
     */
    fun getGravityData(): FloatArray? = gravityData?.clone()
    
    /**
     * 获取线性加速度数据
     */
    fun getLinearAccelerationData(): FloatArray? = linearAccelerationData?.clone()
    
    /**
     * 获取加速度计更新时间
     */
    fun getLastAccelerometerUpdateTime(): Long = lastAccelerometerUpdateTime
    
    // 位置相关方法
    
    /**
     * 更新位置数据
     */
    fun updateLocation(location: Location) {
        currentLocation = location
        lastLocationUpdateTime = System.currentTimeMillis()
        
        // 通知监听器
        notifyLocationChanged()
    }
    
    /**
     * 获取当前位置
     */
    fun getCurrentLocation(): Location? = currentLocation
    
    /**
     * 获取位置更新时间
     */
    fun getLastLocationUpdateTime(): Long = lastLocationUpdateTime
    
    // WiFi扫描相关方法
    
    /**
     * 更新WiFi扫描结果
     */
    fun updateWifiScanResults(results: List<ScanResult>) {
        wifiScanResults.clear()
        wifiScanResults.addAll(results)
        lastWifiScanTime = System.currentTimeMillis()
        
        // 通知监听器
        notifyWifiDataChanged()
    }
    
    /**
     * 获取WiFi扫描结果
     */
    fun getWifiScanResults(): List<ScanResult> = wifiScanResults.toList()
    
    /**
     * 获取WiFi扫描更新时间
     */
    fun getLastWifiScanTime(): Long = lastWifiScanTime
    
    // 信标相关方法
    
    /**
     * 更新信标数据
     */
    fun updateBeaconData(beacons: Collection<Beacon>) {
        beaconList.clear()
        beaconList.addAll(beacons)
        lastBeaconScanTime = System.currentTimeMillis()
        
        // 通知监听器
        notifyBeaconDataChanged()
    }
    
    /**
     * 获取信标数据
     */
    fun getBeaconData(): List<Beacon> = beaconList.toList()
    
    /**
     * 获取信标数据更新时间
     */
    fun getLastBeaconScanTime(): Long = lastBeaconScanTime
    
    // 监听器相关方法
    
    /**
     * 添加加速度计数据监听器
     */
    fun addAccelerometerDataListener(listener: AccelerometerDataListener) {
        accelerometerDataListeners.add(listener)
    }
    
    /**
     * 移除加速度计数据监听器
     */
    fun removeAccelerometerDataListener(listener: AccelerometerDataListener) {
        accelerometerDataListeners.remove(listener)
    }
    
    /**
     * 添加位置数据监听器
     */
    fun addLocationDataListener(listener: LocationDataListener) {
        locationDataListeners.add(listener)
    }
    
    /**
     * 移除位置数据监听器
     */
    fun removeLocationDataListener(listener: LocationDataListener) {
        locationDataListeners.remove(listener)
    }
    
    /**
     * 添加WiFi数据监听器
     */
    fun addWifiDataListener(listener: WifiDataListener) {
        wifiDataListeners.add(listener)
    }
    
    /**
     * 移除WiFi数据监听器
     */
    fun removeWifiDataListener(listener: WifiDataListener) {
        wifiDataListeners.remove(listener)
    }
    
    /**
     * 添加信标数据监听器
     */
    fun addBeaconDataListener(listener: BeaconDataListener) {
        beaconDataListeners.add(listener)
    }
    
    /**
     * 移除信标数据监听器
     */
    fun removeBeaconDataListener(listener: BeaconDataListener) {
        beaconDataListeners.remove(listener)
    }
    
    // 通知监听器
    
    private fun notifyAccelerometerDataChanged() {
        val data = accelerometerData ?: return
        val time = lastAccelerometerUpdateTime
        for (listener in accelerometerDataListeners) {
            listener.onAccelerometerDataChanged(data[0], data[1], data[2], time)
        }
    }
    
    private fun notifyLocationChanged() {
        val location = currentLocation ?: return
        val time = lastLocationUpdateTime
        for (listener in locationDataListeners) {
            listener.onLocationChanged(location, time)
        }
    }
    
    private fun notifyWifiDataChanged() {
        val results = wifiScanResults
        val time = lastWifiScanTime
        for (listener in wifiDataListeners) {
            listener.onWifiScanResultsChanged(results, time)
        }
    }
    
    private fun notifyBeaconDataChanged() {
        val beacons = beaconList
        val time = lastBeaconScanTime
        for (listener in beaconDataListeners) {
            listener.onBeaconDataChanged(beacons, time)
        }
    }
    
    // 自动更新相关方法
    
    /**
     * 启动自动更新
     * 定期通知监听器即使数据没有变化
     */
    fun startAutoUpdate(interval: Long = autoUpdateInterval) {
        if (isAutoUpdating) return
        
        autoUpdateInterval = interval
        isAutoUpdating = true
        
        handler.postDelayed(updateRunnable, autoUpdateInterval)
    }
    
    /**
     * 停止自动更新
     */
    fun stopAutoUpdate() {
        isAutoUpdating = false
        handler.removeCallbacks(updateRunnable)
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isAutoUpdating) return
            
            // 强制通知所有监听器
            accelerometerData?.let {
                for (listener in accelerometerDataListeners) {
                    listener.onAccelerometerDataChanged(it[0], it[1], it[2], lastAccelerometerUpdateTime)
                }
            }
            
            currentLocation?.let {
                for (listener in locationDataListeners) {
                    listener.onLocationChanged(it, lastLocationUpdateTime)
                }
            }
            
            if (wifiScanResults.isNotEmpty()) {
                for (listener in wifiDataListeners) {
                    listener.onWifiScanResultsChanged(wifiScanResults, lastWifiScanTime)
                }
            }
            
            if (beaconList.isNotEmpty()) {
                for (listener in beaconDataListeners) {
                    listener.onBeaconDataChanged(beaconList, lastBeaconScanTime)
                }
            }
            
            // 继续下一次更新
            handler.postDelayed(this, autoUpdateInterval)
        }
    }
    
    /**
     * 初始化
     */
    fun init(context: Context) {
        Log.d(TAG, "SensorDataManager initialized")
    }
    
    /**
     * 清除所有数据
     */
    fun clearAllData() {
        accelerometerData = null
        gravityData = null
        linearAccelerationData = null
        currentLocation = null
        wifiScanResults.clear()
        beaconList.clear()
        
        lastAccelerometerUpdateTime = 0
        lastLocationUpdateTime = 0
        lastWifiScanTime = 0
        lastBeaconScanTime = 0
    }
    
    companion object {
        @Volatile
        private var instance: SensorDataManager? = null
        
        /**
         * 获取实例
         */
        fun getInstance(): SensorDataManager {
            return instance ?: synchronized(this) {
                instance ?: SensorDataManager().also { instance = it }
            }
        }
    }
} 