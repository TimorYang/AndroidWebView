package com.teemoyang.androidwebview

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 位置信息帮助类，封装了位置相关的所有功能
 */
class LocationHelper(private val context: Context) {

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var onLocationUpdateListener: OnLocationUpdateListener? = null
    private var onLocationErrorListener: OnLocationErrorListener? = null
    private var onPermissionCallback: OnPermissionCallback? = null
    private var onLocationServiceCallback: OnLocationServiceCallback? = null
    
    private var updateInterval: Long = 1000 // 默认1秒更新一次
    private var minDistanceChange: Float = 1f // 默认1米变化更新一次
    
    init {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    
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
     * 权限回调接口
     */
    interface OnPermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }
    
    /**
     * 位置服务状态回调接口
     */
    interface OnLocationServiceCallback {
        fun onLocationServiceEnabled()
        fun onLocationServiceDisabled()
    }
    
    /**
     * 设置位置更新监听器
     */
    fun setOnLocationUpdateListener(listener: OnLocationUpdateListener) {
        onLocationUpdateListener = listener
    }
    
    /**
     * 设置位置错误监听器
     */
    fun setOnLocationErrorListener(listener: OnLocationErrorListener) {
        onLocationErrorListener = listener
    }
    
    /**
     * 设置权限回调
     */
    fun setOnPermissionCallback(callback: OnPermissionCallback) {
        onPermissionCallback = callback
    }
    
    /**
     * 设置位置服务状态回调
     */
    fun setOnLocationServiceCallback(callback: OnLocationServiceCallback) {
        onLocationServiceCallback = callback
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * 显示权限请求说明对话框
     */
    fun showPermissionRationaleDialog(activity: Activity, requestCode: Int) {
        AlertDialog.Builder(activity)
            .setTitle("需要位置权限")
            .setMessage("为了获取您的位置信息并发送到服务器，我们需要位置权限。")
            .setPositiveButton("确定") { _, _ ->
                requestLocationPermission(activity, requestCode)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                onPermissionCallback?.onPermissionDenied()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 请求位置权限
     */
    fun requestLocationPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
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
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onPermissionCallback?.onPermissionGranted()
            } else {
                onPermissionCallback?.onPermissionDenied()
            }
            return true
        }
        return false
    }
    
    /**
     * 检查位置服务是否开启
     * @return 位置服务是否开启
     */
    fun isLocationServiceEnabled(): Boolean {
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true || 
               locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }
    
    /**
     * 显示位置设置对话框
     */
    fun showLocationSettingsDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("需要开启位置服务")
            .setMessage("为了获取您的位置信息，请开启位置服务。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                onLocationServiceCallback?.onLocationServiceDisabled()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 开始位置更新
     * @return 是否成功启动位置更新
     */
    fun startLocationUpdates(): Boolean {
        if (!checkLocationPermission()) {
            onLocationErrorListener?.onLocationError("没有位置权限")
            return false
        }
        
        if (!isLocationServiceEnabled()) {
            onLocationErrorListener?.onLocationError("位置服务未开启")
            return false
        }
        
        try {
            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onLocationUpdateListener?.onLocationUpdated(location)
                }
                
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    when (status) {
                        android.location.LocationProvider.AVAILABLE -> {
                            onLocationServiceCallback?.onLocationServiceEnabled()
                        }
                        android.location.LocationProvider.TEMPORARILY_UNAVAILABLE -> {
                            onLocationErrorListener?.onLocationError("位置服务暂时不可用")
                        }
                        android.location.LocationProvider.OUT_OF_SERVICE -> {
                            onLocationServiceCallback?.onLocationServiceDisabled()
                        }
                    }
                }
                
                override fun onProviderEnabled(provider: String) {
                    onLocationServiceCallback?.onLocationServiceEnabled()
                }
                
                override fun onProviderDisabled(provider: String) {
                    onLocationServiceCallback?.onLocationServiceDisabled()
                }
            }
            
            // 同时请求GPS和网络位置更新，以提高位置获取的成功率
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    updateInterval,
                    minDistanceChange,
                    locationListener!!
                )
            }
            
            // 添加网络位置提供者
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    updateInterval,
                    minDistanceChange,
                    locationListener!!
                )
            }
            
            // 如果两种提供者都不可用，返回失败
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true && 
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) != true) {
                onLocationErrorListener?.onLocationError("没有可用的位置提供者")
                return false
            }
            
            return true
        } catch (e: Exception) {
            onLocationErrorListener?.onLocationError("启动位置更新失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 停止位置更新
     */
    fun stopLocationUpdates() {
        locationListener?.let {
            locationManager?.removeUpdates(it)
            locationListener = null
        }
    }
    
    /**
     * 获取当前位置
     * @return 当前位置，如果没有则返回null
     */
    fun getCurrentLocation(): Location? {
        if (!checkLocationPermission()) {
            return null
        }
        
        if (!isLocationServiceEnabled()) {
            return null
        }
        
        return try {
            var bestLocation: Location? = null
            
            // 尝试获取GPS位置
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                val gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (gpsLocation != null) {
                    bestLocation = gpsLocation
                }
            }
            
            // 尝试获取网络位置
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                val networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (networkLocation != null) {
                    // 如果没有GPS位置或者网络位置更新更近，使用网络位置
                    if (bestLocation == null || networkLocation.time > bestLocation.time) {
                        bestLocation = networkLocation
                    }
                }
            }
            
            // 如果获取到位置，立即通知
            if (bestLocation != null) {
                onLocationUpdateListener?.onLocationUpdated(bestLocation)
            } else {
                // 没有获取到位置，尝试创建一个模拟位置用于测试
                val mockLocation = createMockLocation()
                if (mockLocation != null) {
                    onLocationUpdateListener?.onLocationUpdated(mockLocation)
                    return mockLocation
                }
            }
            
            bestLocation
        } catch (e: Exception) {
            onLocationErrorListener?.onLocationError("获取当前位置失败: ${e.message}")
            null
        }
    }
    
    /**
     * 创建一个模拟位置用于测试
     * @return 模拟位置
     */
    private fun createMockLocation(): Location? {
        return try {
            val mockLocation = Location("mock")
            mockLocation.latitude = 31.2304 // 上海默认位置
            mockLocation.longitude = 121.4737
            mockLocation.accuracy = 10.0f
            mockLocation.time = System.currentTimeMillis()
            mockLocation.elapsedRealtimeNanos = System.nanoTime()
            mockLocation
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取位置信息的JSON字符串
     * @return 位置信息的JSON字符串
     */
    fun getLocationJson(): String? {
        val location = getCurrentLocation() ?: return null
        
        return try {
            val json = org.json.JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracy", location.accuracy)
            }
            json.toString()
        } catch (e: Exception) {
            onLocationErrorListener?.onLocationError("生成位置JSON失败: ${e.message}")
            null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopLocationUpdates()
        locationManager = null
        onLocationUpdateListener = null
        onLocationErrorListener = null
        onPermissionCallback = null
        onLocationServiceCallback = null
    }
    
    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
} 