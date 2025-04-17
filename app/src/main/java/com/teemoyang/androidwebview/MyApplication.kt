package com.teemoyang.androidwebview

import android.app.Application
import android.util.Log

/**
 * 应用程序类
 * 用于初始化全局组件和管理全局状态
 */
class MyApplication : Application() {
    
    private val TAG = "MyApplication"
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application created")
        
        // 初始化设备管理器
        initDeviceManager()
        
        // 初始化全局数据管理器
        initSensorDataManager()
    }
    
    /**
     * 初始化设备管理器
     */
    private fun initDeviceManager() {
        // 获取单例实例并初始化
        val manager = DeviceManager.getInstance()
        manager.init(this)
        
        Log.d(TAG, "DeviceManager initialized")
    }
    
    /**
     * 初始化传感器数据管理器
     */
    private fun initSensorDataManager() {
        // 获取单例实例并初始化
        val manager = SensorDataManager.getInstance()
        manager.init(this)
        
        Log.d(TAG, "SensorDataManager initialized")
    }
} 