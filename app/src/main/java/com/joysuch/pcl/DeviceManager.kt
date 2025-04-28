package com.joysuch.pcl

import android.content.Context
import android.util.Log

/**
 * 设备管理类
 * 负责设备唯一标识和设备信息管理
 */
class DeviceManager private constructor() {
    
    private val TAG = "DeviceManager"
    
    // 设备唯一标识
    private var deviceId: String = ""
    
    // 设备相关信息
    private var deviceModel: String = android.os.Build.MODEL
    private var deviceManufacturer: String = android.os.Build.MANUFACTURER
    private var deviceVersion: String = android.os.Build.VERSION.RELEASE
    
    /**
     * 初始化
     */
    fun init(context: Context) {
        Log.d(TAG, "DeviceManager initialized")
        
        // 初始化设备唯一标识
        initDeviceId(context)
    }
    
    /**
     * 初始化设备唯一标识
     */
    private fun initDeviceId(context: Context) {
        // 尝试从SharedPreferences获取
        val prefs = context.getSharedPreferences("device_preferences", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        
        // 如果不存在则生成新的
        if (id.isNullOrEmpty()) {
            id = generateDeviceId()
            // 保存到SharedPreferences
            prefs.edit().putString("device_id", id).apply()
        }
        
        deviceId = id
        Log.d(TAG, "Device ID: $deviceId")
    }
    
    /**
     * 生成设备唯一标识
     */
    private fun generateDeviceId(): String {
        // 使用UUID生成唯一标识
        return java.util.UUID.randomUUID().toString()
    }
    
    /**
     * 获取设备唯一标识
     */
    fun getDeviceId(): String {
        return deviceId
    }
    
    /**
     * 重置设备唯一标识
     * 生成一个新的ID并持久化
     */
    fun resetDeviceId(context: Context) {
        deviceId = generateDeviceId()
        
        // 保存到SharedPreferences
        val prefs = context.getSharedPreferences("device_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString("device_id", deviceId).apply()
        
        Log.d(TAG, "Device ID reset: $deviceId")
    }
    
    /**
     * 获取设备型号
     */
    fun getDeviceModel(): String {
        return deviceModel
    }
    
    /**
     * 获取设备制造商
     */
    fun getDeviceManufacturer(): String {
        return deviceManufacturer
    }
    
    /**
     * 获取Android版本
     */
    fun getDeviceVersion(): String {
        return deviceVersion
    }
    
    /**
     * 获取完整设备信息
     */
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "id" to deviceId,
            "model" to deviceModel,
            "manufacturer" to deviceManufacturer,
            "androidVersion" to deviceVersion
        )
    }
    
    companion object {
        @Volatile
        private var instance: DeviceManager? = null
        
        /**
         * 获取实例
         */
        fun getInstance(): DeviceManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceManager().also { instance = it }
            }
        }
    }
} 