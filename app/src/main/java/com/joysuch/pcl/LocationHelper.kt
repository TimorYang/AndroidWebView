package com.joysuch.pcl

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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 位置信息帮助类，封装了位置相关的所有功能
 */
class LocationHelper(private val context: Context) {
    companion object {
        const val TAG = "LocationHelper"
        const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var onLocationUpdateListener: OnLocationUpdateListener? = null
    private var onLocationErrorListener: OnLocationErrorListener? = null
    private var permissionCallback: OnPermissionCallback? = null
    private var onLocationServiceCallback: OnLocationServiceCallback? = null
    
    private var updateInterval: Long = 1000 // 默认1秒更新一次
    private var minDistanceChange: Float = 1f // 默认1米变化更新一次
    
    // 是否自动更新到SensorDataManager
    private var autoUpdateToSensorManager: Boolean = true
    
    // 是否打印详细日志
    private var enableVerboseLogging: Boolean = true
    
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
        /**
         * 权限已授予
         */
        fun onPermissionGranted()
        
        /**
         * 权限被拒绝
         */
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
     * 设置是否打印详细日志
     */
    fun setEnableVerboseLogging(enable: Boolean): LocationHelper {
        this.enableVerboseLogging = enable
        return this
    }
    
    /**
     * 设置是否自动更新到SensorDataManager
     */
    fun setAutoUpdateToSensorManager(enable: Boolean): LocationHelper {
        this.autoUpdateToSensorManager = enable
        return this
    }
    
    /**
     * 打印日志
     */
    private fun logDebug(message: String) {
        if (enableVerboseLogging) {
            Log.d(TAG, message)
        }
    }
    
    private fun logInfo(message: String) {
        Log.i(TAG, message)
    }
    
    private fun logError(message: String) {
        Log.e(TAG, message)
    }
    
    /**
     * 设置位置更新监听器
     */
    fun setOnLocationUpdateListener(listener: OnLocationUpdateListener): LocationHelper {
        onLocationUpdateListener = listener
        return this
    }
    
    /**
     * 设置位置错误监听器
     */
    fun setOnLocationErrorListener(listener: OnLocationErrorListener): LocationHelper {
        onLocationErrorListener = listener
        return this
    }
    
    /**
     * 设置权限回调
     */
    fun setOnPermissionCallback(callback: OnPermissionCallback): LocationHelper {
        permissionCallback = callback
        return this
    }
    
    /**
     * 设置位置服务状态回调
     */
    fun setOnLocationServiceCallback(callback: OnLocationServiceCallback): LocationHelper {
        onLocationServiceCallback = callback
        return this
    }
    
    /**
     * 设置位置更新间隔
     * @param interval 更新间隔（毫秒）
     */
    fun setUpdateInterval(interval: Long): LocationHelper {
        updateInterval = interval
        return this
    }
    
    /**
     * 设置位置变化阈值
     * @param distance 变化阈值（米）
     */
    fun setMinDistanceChange(distance: Float): LocationHelper {
        minDistanceChange = distance
        return this
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
                permissionCallback?.onPermissionDenied()
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
                logInfo("位置权限已授予")
                permissionCallback?.onPermissionGranted()
            } else {
                logInfo("位置权限被拒绝")
                permissionCallback?.onPermissionDenied()
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
                    logDebug("位置已更新: ${location.latitude}, ${location.longitude}")
                    
                    // 通知自定义监听器
                    onLocationUpdateListener?.onLocationUpdated(location)
                    
                    // 自动更新到SensorDataManager
                    if (autoUpdateToSensorManager) {
                        try {
                            SensorDataManager.getInstance().updateLocation(location)
                            logDebug("位置已更新到SensorDataManager")
                        } catch (e: Exception) {
                            logError("更新位置到SensorDataManager失败: ${e.message}")
                        }
                    }
                }
                
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    when (status) {
                        android.location.LocationProvider.AVAILABLE -> {
                            logInfo("位置服务可用")
                            onLocationServiceCallback?.onLocationServiceEnabled()
                        }
                        android.location.LocationProvider.TEMPORARILY_UNAVAILABLE -> {
                            logInfo("位置服务暂时不可用")
                            onLocationErrorListener?.onLocationError("位置服务暂时不可用")
                        }
                        android.location.LocationProvider.OUT_OF_SERVICE -> {
                            logInfo("位置服务不可用")
                            onLocationServiceCallback?.onLocationServiceDisabled()
                        }
                    }
                }
                
                override fun onProviderEnabled(provider: String) {
                    logInfo("位置提供者已启用: $provider")
                    onLocationServiceCallback?.onLocationServiceEnabled()
                }
                
                override fun onProviderDisabled(provider: String) {
                    logInfo("位置提供者已禁用: $provider")
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
                logInfo("已启动GPS位置更新")
            }
            
            // 添加网络位置提供者
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    updateInterval,
                    minDistanceChange,
                    locationListener!!
                )
                logInfo("已启动网络位置更新")
            }
            
            // 如果两种提供者都不可用，返回失败
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true && 
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) != true) {
                onLocationErrorListener?.onLocationError("没有可用的位置提供者")
                return false
            }
            
            return true
        } catch (e: Exception) {
            logError("启动位置更新失败: ${e.message}")
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
            logInfo("位置更新已停止")
        }
    }
    
    /**
     * 一键启动位置服务，简化MainActivity中的调用
     * 封装了权限检查、服务检查和启动操作
     */
    fun startLocationService(activity: Activity) {
        logInfo("一键启动位置服务")
        
        // 设置默认权限回调（如果用户没有自定义）
        if (permissionCallback == null) {
            setOnPermissionCallback(object : OnPermissionCallback {
                override fun onPermissionGranted() {
                    logInfo("位置权限已获取，检查位置服务...")
                    if (isLocationServiceEnabled()) {
                        startLocationUpdates()
                    } else {
                        showLocationSettingsDialog(activity)
                    }
                }
                
                override fun onPermissionDenied() {
                    logError("位置权限被拒绝，无法启动定位服务")
                }
            })
        }
        
        // 设置默认错误处理（如果用户没有自定义）
        if (onLocationErrorListener == null) {
            setOnLocationErrorListener(object : OnLocationErrorListener {
                override fun onLocationError(errorMessage: String) {
                    logError("位置错误: $errorMessage")
                }
            })
        }
        
        // 检查并请求权限
        if (!checkLocationPermission()) {
            logInfo("没有位置权限，请求授权...")
            // 通知权限回调
            permissionCallback?.onPermissionDenied()
            
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // 用户已经明确拒绝过权限，显示自定义权限说明对话框
                showPermissionRationaleDialog(activity, LOCATION_PERMISSION_REQUEST_CODE)
            } else {
                // 用户第一次请求权限或在系统设置中"不再询问"，直接使用系统权限请求对话框
                requestLocationPermission(activity, LOCATION_PERMISSION_REQUEST_CODE)
            }
        } else {
            logInfo("已有位置权限，检查位置服务...")
            // 通知权限回调
            permissionCallback?.onPermissionGranted()
            
            // 已有权限，检查位置服务
            if (isLocationServiceEnabled()) {
                startLocationUpdates()
            } else {
                showLocationSettingsDialog(activity)
            }
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
            
            // 如果没有GPS位置或者GPS位置较旧，尝试获取网络位置
            if (bestLocation == null || 
                (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true)) {
                val networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (networkLocation != null && (bestLocation == null || 
                    networkLocation.time > bestLocation.time)) {
                    bestLocation = networkLocation
                }
            }
            
            bestLocation
        } catch (e: Exception) {
            logError("获取当前位置失败: ${e.message}")
            null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        logInfo("释放LocationHelper资源")
        stopLocationUpdates()
        locationManager = null
        onLocationUpdateListener = null
        onLocationErrorListener = null
        permissionCallback = null
        onLocationServiceCallback = null
    }
} 