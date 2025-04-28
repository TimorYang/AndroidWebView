package com.teemoyang.androidwebview

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.teemoyang.androidwebview.data.UserSession

/**
 * 应用程序类
 * 用于初始化全局组件和管理全局状态
 */
class MyApplication : Application() {
    
    private val TAG = "MyApplication"
    
    // 记录当前可见的Activity数量
    private var visibleActivityCount = 0
    
    // 记录App是否处于前台
    private var isAppInForeground = false
    
    // 监听器接口，用于通知App前后台切换
    interface AppStateListener {
        fun onAppForeground()
        fun onAppBackground()
    }
    
    companion object {
        private var instance: MyApplication? = null
        private val appStateListeners = mutableListOf<AppStateListener>()
        
        fun getInstance(): MyApplication {
            return instance!!
        }
        
        /**
         * 添加应用状态监听器
         */
        fun addAppStateListener(listener: AppStateListener) {
            if (!appStateListeners.contains(listener)) {
                appStateListeners.add(listener)
            }
        }
        
        /**
         * 移除应用状态监听器
         */
        fun removeAppStateListener(listener: AppStateListener) {
            appStateListeners.remove(listener)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.d(TAG, "Application created")
        
        // 初始化用户会话
        initUserSession()
        
        // 初始化设备管理器
        initDeviceManager()
        
        // 初始化全局数据管理器
        initSensorDataManager()
        
        // 使用ActivityLifecycleCallbacks监听应用状态
        setupActivityLifecycleCallbacks()
    }
    
    /**
     * 使用ActivityLifecycleCallbacks监听应用状态
     * 优点：不需要额外依赖，可以精确控制
     */
    private fun setupActivityLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            
            override fun onActivityStarted(activity: Activity) {
                visibleActivityCount++
                if (visibleActivityCount == 1) {
                    // 从后台切换到前台
                    onAppForeground()
                }
            }
            
            override fun onActivityResumed(activity: Activity) {}
            
            override fun onActivityPaused(activity: Activity) {}
            
            override fun onActivityStopped(activity: Activity) {
                visibleActivityCount--
                if (visibleActivityCount == 0) {
                    // 从前台切换到后台
                    onAppBackground()
                }
            }
            
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
    
    /**
     * 应用进入前台时调用
     */
    private fun onAppForeground() {
        if (!isAppInForeground) {
            isAppInForeground = true
            Log.d(TAG, "App进入前台")
            
            // 通知所有监听器
            appStateListeners.forEach { it.onAppForeground() }
        }
    }
    
    /**
     * 应用进入后台时调用
     */
    private fun onAppBackground() {
        if (isAppInForeground) {
            isAppInForeground = false
            Log.d(TAG, "App进入后台")
            
            // 通知所有监听器
            appStateListeners.forEach { it.onAppBackground() }
            
            // 应用进入后台时自动执行登出逻辑
            handleAppBackground()
        }
    }
    
    /**
     * 处理应用退到后台时的逻辑
     */
    private fun handleAppBackground() {
        // 清除登录状态
        UserSession.clearLoginInfo()
        Log.d(TAG, "应用退到后台，已清除登录状态")
    }
    
    /**
     * 初始化用户会话
     */
    private fun initUserSession() {
        UserSession.init(this)
        Log.d(TAG, "UserSession initialized")
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
    
    /**
     * 判断应用是否在前台
     */
    fun isAppInForeground(): Boolean {
        return isAppInForeground
    }
} 