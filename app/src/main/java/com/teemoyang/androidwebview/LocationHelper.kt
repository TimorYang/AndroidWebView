package com.teemoyang.androidwebview

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 位置信息帮助类，封装了位置相关的所有功能
 */
class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var currentLocation: Location? = null
    private var updateInterval: Long = 1000 // 默认1秒更新一次
    private var minDistanceChange: Float = 1f // 默认1米变化更新一次
    private var locationUpdateListener: OnLocationUpdateListener? = null
    private var locationErrorListener: OnLocationErrorListener? = null
    
    /**
     * 位置更新监听器接口
     */
    interface OnLocationUpdateListener {
        fun onLocationUpdated(location: Location)
    }
    
    /**
     * 位置错误监听器接口
     */
    interface OnLocationErrorListener {
        fun onLocationError(errorMessage: String)
    }
    
    /**
     * 设置位置更新监听器
     */
    fun setOnLocationUpdateListener(listener: OnLocationUpdateListener) {
        locationUpdateListener = listener
    }
    
    /**
     * 设置位置错误监听器
     */
    fun setOnLocationErrorListener(listener: OnLocationErrorListener) {
        locationErrorListener = listener
    }
    
    /**
     * 设置位置更新间隔
     * @param interval 更新间隔（毫秒）
     */
    fun setUpdateInterval(interval: Long) {
        updateInterval = interval
    }
    
    /**
     * 设置位置变化阈值
     * @param distance 变化阈值（米）
     */
    fun setMinDistanceChange(distance: Float) {
        minDistanceChange = distance
    }
    
    /**
     * 检查位置权限
     * @return 是否有位置权限
     */
    fun checkLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        return fineLocation || coarseLocation
    }
    
    /**
     * 请求位置权限
     */
    fun requestLocationPermission(activity: android.app.Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            requestCode
        )
    }
    
    /**
     * 检查位置服务是否开启
     * @return 位置服务是否开启
     */
    fun isLocationServiceEnabled(): Boolean {
        val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        return isGPSEnabled || isNetworkEnabled
    }
    
    /**
     * 显示位置设置对话框
     */
    fun showLocationSettingsDialog(activity: android.app.Activity) {
        AlertDialog.Builder(activity)
            .setTitle("位置服务未开启")
            .setMessage("需要开启位置服务才能获取位置信息，是否前往设置？")
            .setPositiveButton("设置") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 开始位置更新
     * @return 是否成功启动位置更新
     */
    fun startLocationUpdates(): Boolean {
        try {
            // 检查是否有可用的定位提供者
            val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            // 先尝试使用网络定位，因为它通常更快
            if (isNetworkEnabled) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        updateInterval,
                        minDistanceChange,
                        locationListener
                    )
                    
                    // 获取最近一次的位置
                    val lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (lastLocation != null) {
                        currentLocation = lastLocation
                        locationUpdateListener?.onLocationUpdated(lastLocation)
                    }
                } else {
                    locationErrorListener?.onLocationError("缺少网络定位权限")
                    return false
                }
            }
            
            // 同时使用GPS定位，因为它更精确
            if (isGPSEnabled) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        updateInterval,
                        minDistanceChange,
                        locationListener
                    )
                    
                    // 获取最近一次的位置
                    val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (lastLocation != null) {
                        currentLocation = lastLocation
                        locationUpdateListener?.onLocationUpdated(lastLocation)
                    }
                } else {
                    locationErrorListener?.onLocationError("缺少GPS定位权限")
                    return false
                }
            }
            
            if (!isGPSEnabled && !isNetworkEnabled) {
                locationErrorListener?.onLocationError("没有可用的定位提供者")
                return false
            }
            
            return true
        } catch (e: Exception) {
            locationErrorListener?.onLocationError("启动定位失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 获取当前位置
     * @return 当前位置，如果没有则返回null
     */
    fun getCurrentLocation(): Location? {
        return currentLocation
    }
    
    /**
     * 获取位置信息的JSON字符串
     * @return 位置信息的JSON字符串
     */
    fun getLocationJson(): String? {
        val location = currentLocation ?: return null
        
        return try {
            val json = org.json.JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracy", location.accuracy)
            }
            json.toString()
        } catch (e: Exception) {
            locationErrorListener?.onLocationError("生成位置JSON失败: ${e.message}")
            null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            // 忽略可能的安全异常
        }
        locationUpdateListener = null
        locationErrorListener = null
    }
    
    // 位置监听器
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            locationUpdateListener?.onLocationUpdated(location)
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // 不需要实现
        }
        
        override fun onProviderEnabled(provider: String) {
            // 不需要实现
        }
        
        override fun onProviderDisabled(provider: String) {
            // 不需要实现
        }
    }
} 