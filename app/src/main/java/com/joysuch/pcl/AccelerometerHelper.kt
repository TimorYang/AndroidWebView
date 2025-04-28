package com.joysuch.pcl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 加速度计数据采集助手类
 * 用于封装加速度计相关的数据获取、监听等功能
 */
class AccelerometerHelper(private val context: Context) : SensorEventListener {
    
    private val TAG = "AccelerometerHelper"
    
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gravity: Sensor? = null
    private var linearAcceleration: Sensor? = null
    
    // 收集数据的频率
    private var samplingRate = SensorManager.SENSOR_DELAY_NORMAL
    
    // 数据监听
    private var accelerometerListeners = CopyOnWriteArrayList<OnAccelerometerDataListener>()
    private var gravityListeners = CopyOnWriteArrayList<OnGravityDataListener>()
    private var linearAccelerationListeners = CopyOnWriteArrayList<OnLinearAccelerationDataListener>()
    private var rawDataListeners = CopyOnWriteArrayList<OnRawDataListener>()
    
    // 记录最后的数据
    private var lastAccelerometerData: FloatArray? = null
    private var lastGravityData: FloatArray? = null
    private var lastLinearAccelerationData: FloatArray? = null
    
    // 防抖动计时器
    private val handler = Handler(Looper.getMainLooper())
    private var debounceInterval = 50L // 默认50毫秒防抖
    private var debounceRunnable: Runnable? = null
    
    // 状态标记
    private var isListening = false
    private var isFilteringEnabled = true
    
    // 低通滤波器参数
    private val alpha = 0.8f
    private val filteredValues = FloatArray(3)
    
    // 是否自动同步到全局数据管理器
    private var shouldSyncToManager = false
    
    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gravity = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAcceleration = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }
    
    /**
     * 加速度计原始数据监听接口
     */
    interface OnRawDataListener {
        fun onRawData(sensorType: Int, values: FloatArray, accuracy: Int, timestamp: Long)
    }
    
    /**
     * 加速度计数据监听接口
     */
    interface OnAccelerometerDataListener {
        fun onAccelerometerData(x: Float, y: Float, z: Float, timestamp: Long)
    }
    
    /**
     * 重力数据监听接口
     */
    interface OnGravityDataListener {
        fun onGravityData(x: Float, y: Float, z: Float, timestamp: Long)
    }
    
    /**
     * 线性加速度数据监听接口
     */
    interface OnLinearAccelerationDataListener {
        fun onLinearAccelerationData(x: Float, y: Float, z: Float, timestamp: Long)
    }
    
    /**
     * 设置采样率
     * @param rate 采样率常量，如 SensorManager.SENSOR_DELAY_NORMAL
     */
    fun setSamplingRate(rate: Int) {
        samplingRate = rate
        if (isListening) {
            // 如果已经在监听，需要重新注册以应用新的采样率
            stopListening()
            startListening()
        }
    }
    
    /**
     * 设置防抖动间隔
     * @param interval 间隔时间（毫秒）
     */
    fun setDebounceInterval(interval: Long) {
        debounceInterval = interval
    }
    
    /**
     * 启用或禁用滤波
     * @param enabled 是否启用
     */
    fun setFilteringEnabled(enabled: Boolean) {
        isFilteringEnabled = enabled
    }
    
    /**
     * 添加加速度计数据监听器
     */
    fun addAccelerometerListener(listener: OnAccelerometerDataListener) {
        accelerometerListeners.add(listener)
    }
    
    /**
     * 添加重力数据监听器
     */
    fun addGravityListener(listener: OnGravityDataListener) {
        gravityListeners.add(listener)
    }
    
    /**
     * 添加线性加速度数据监听器
     */
    fun addLinearAccelerationListener(listener: OnLinearAccelerationDataListener) {
        linearAccelerationListeners.add(listener)
    }
    
    /**
     * 添加原始数据监听器
     */
    fun addRawDataListener(listener: OnRawDataListener) {
        rawDataListeners.add(listener)
    }
    
    /**
     * 移除加速度计数据监听器
     */
    fun removeAccelerometerListener(listener: OnAccelerometerDataListener) {
        accelerometerListeners.remove(listener)
    }
    
    /**
     * 移除重力数据监听器
     */
    fun removeGravityListener(listener: OnGravityDataListener) {
        gravityListeners.remove(listener)
    }
    
    /**
     * 移除线性加速度数据监听器
     */
    fun removeLinearAccelerationListener(listener: OnLinearAccelerationDataListener) {
        linearAccelerationListeners.remove(listener)
    }
    
    /**
     * 移除原始数据监听器
     */
    fun removeRawDataListener(listener: OnRawDataListener) {
        rawDataListeners.remove(listener)
    }
    
    /**
     * 开始监听传感器数据
     * @return 是否成功启动监听
     */
    fun startListening(): Boolean {
        if (isListening) return true
        
        if (sensorManager == null) {
            Log.e(TAG, "SensorManager is null")
            return false
        }
        
        var success = true
        
        // 注册加速度计
        if (accelerometer != null) {
            success = success && sensorManager?.registerListener(
                this,
                accelerometer,
                samplingRate
            ) ?: false
        } else {
            Log.w(TAG, "Accelerometer sensor not available")
            success = false
        }
        
        // 注册重力传感器
        if (gravity != null) {
            success = success && sensorManager?.registerListener(
                this,
                gravity,
                samplingRate
            ) ?: false
        } else {
            Log.w(TAG, "Gravity sensor not available")
        }
        
        // 注册线性加速度传感器
        if (linearAcceleration != null) {
            success = success && sensorManager?.registerListener(
                this,
                linearAcceleration,
                samplingRate
            ) ?: false
        } else {
            Log.w(TAG, "Linear acceleration sensor not available")
        }
        
        isListening = success
        return success
    }
    
    /**
     * 停止监听传感器数据
     */
    fun stopListening() {
        if (!isListening) return
        
        sensorManager?.unregisterListener(this)
        isListening = false
        
        // 取消防抖动计时器
        debounceRunnable?.let { handler.removeCallbacks(it) }
    }
    
    /**
     * 获取最后一次的加速度计数据
     * @return 加速度计数据，如果没有则返回null
     */
    fun getLastAccelerometerData(): FloatArray? = lastAccelerometerData?.clone()
    
    /**
     * 获取最后一次的重力数据
     * @return 重力数据，如果没有则返回null
     */
    fun getLastGravityData(): FloatArray? = lastGravityData?.clone()
    
    /**
     * 获取最后一次的线性加速度数据
     * @return 线性加速度数据，如果没有则返回null
     */
    fun getLastLinearAccelerationData(): FloatArray? = lastLinearAccelerationData?.clone()
    
    /**
     * 检查设备是否有加速度计
     * @return 是否有加速度计
     */
    fun hasAccelerometer(): Boolean = accelerometer != null
    
    /**
     * 应用低通滤波器
     * @param input 输入数据
     * @param output 输出数据
     */
    private fun applyLowPassFilter(input: FloatArray, output: FloatArray) {
        if (output[0] == 0f && output[1] == 0f && output[2] == 0f) {
            // 初始化
            output[0] = input[0]
            output[1] = input[1]
            output[2] = input[2]
        } else {
            // 应用滤波
            output[0] = alpha * output[0] + (1 - alpha) * input[0]
            output[1] = alpha * output[1] + (1 - alpha) * input[1]
            output[2] = alpha * output[2] + (1 - alpha) * input[2]
        }
    }
    
    /**
     * 采样后处理
     */
    private fun processSensorEvent(event: SensorEvent) {
        // 首先通知原始数据监听器
        val eventTime = System.currentTimeMillis()
        
        // 创建数据副本
        val values = event.values.clone()
        
        // 应用滤波
        if (isFilteringEnabled) {
            applyLowPassFilter(values, filteredValues)
            values[0] = filteredValues[0]
            values[1] = filteredValues[1]
            values[2] = filteredValues[2]
        }
        
        // 通知原始数据监听器
        for (listener in rawDataListeners) {
            listener.onRawData(event.sensor.type, values, event.accuracy, eventTime)
        }
        
        // 根据传感器类型处理
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // 保存最新数据
                lastAccelerometerData = values.clone()
                
                // 通知监听器
                for (listener in accelerometerListeners) {
                    listener.onAccelerometerData(values[0], values[1], values[2], eventTime)
                }
            }
            Sensor.TYPE_GRAVITY -> {
                // 保存最新数据
                lastGravityData = values.clone()
                
                // 通知监听器
                for (listener in gravityListeners) {
                    listener.onGravityData(values[0], values[1], values[2], eventTime)
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // 保存最新数据
                lastLinearAccelerationData = values.clone()
                
                // 通知监听器
                for (listener in linearAccelerationListeners) {
                    listener.onLinearAccelerationData(values[0], values[1], values[2], eventTime)
                }
            }
        }
    }
    
    /**
     * 传感器数据变化回调
     */
    override fun onSensorChanged(event: SensorEvent) {
        // 使用防抖动处理
        if (debounceInterval > 0) {
            debounceRunnable?.let { handler.removeCallbacks(it) }
            
            debounceRunnable = Runnable {
                processSensorEvent(event)
                
                // 将数据同步到全局数据管理器（如果需要）
                syncDataToManager(event)
            }.also {
                handler.postDelayed(it, debounceInterval)
            }
        } else {
            // 直接处理
            processSensorEvent(event)
            
            // 将数据同步到全局数据管理器（如果需要）
            syncDataToManager(event)
        }
    }
    
    /**
     * 设置是否自动同步到全局数据管理器
     */
    fun setSyncToManager(sync: Boolean) {
        shouldSyncToManager = sync
    }
    
    /**
     * 将数据同步到全局数据管理器
     */
    private fun syncDataToManager(event: SensorEvent) {
        if (!shouldSyncToManager) return
        
        val manager = SensorDataManager.getInstance()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelerometerData?.let {
                    manager.updateAccelerometerData(it[0], it[1], it[2])
                }
            }
            Sensor.TYPE_GRAVITY -> {
                lastGravityData?.let {
                    manager.updateGravityData(it[0], it[1], it[2])
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                lastLinearAccelerationData?.let {
                    manager.updateLinearAccelerationData(it[0], it[1], it[2])
                }
            }
        }
    }
    
    /**
     * 传感器精度变化回调
     */
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor.name}, accuracy: $accuracy")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopListening()
        handler.removeCallbacksAndMessages(null)
        accelerometerListeners.clear()
        gravityListeners.clear()
        linearAccelerationListeners.clear()
        rawDataListeners.clear()
        sensorManager = null
        accelerometer = null
        gravity = null
        linearAcceleration = null
    }
    
    companion object {
        // 采样率常量，可直接使用SensorManager中的常量
        const val SAMPLING_FASTEST = SensorManager.SENSOR_DELAY_FASTEST
        const val SAMPLING_GAME = SensorManager.SENSOR_DELAY_GAME
        const val SAMPLING_UI = SensorManager.SENSOR_DELAY_UI
        const val SAMPLING_NORMAL = SensorManager.SENSOR_DELAY_NORMAL
    }
} 