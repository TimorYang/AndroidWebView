package com.teemoyang.androidwebview

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.teemoyang.androidwebview.databinding.ActivityAccelerometerBinding
import java.text.DecimalFormat

class AccelerometerActivity : AppCompatActivity(), 
    AccelerometerHelper.OnAccelerometerDataListener,
    AccelerometerHelper.OnGravityDataListener,
    AccelerometerHelper.OnLinearAccelerationDataListener {

    private lateinit var binding: ActivityAccelerometerBinding
    private lateinit var accelerometerHelper: AccelerometerHelper
    private val decimalFormat = DecimalFormat("#.##")
    
    // 全局数据管理器
    private val sensorDataManager = SensorDataManager.getInstance()
    
    // 更新UI的最小时间间隔（毫秒）
    private val UI_UPDATE_INTERVAL = 100L
    private var lastUpdateTime = 0L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccelerometerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置标题和返回按钮
        supportActionBar?.apply {
            title = "加速度计数据"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // 初始化加速度计助手
        accelerometerHelper = AccelerometerHelper(this)
        
        // 设置滑块监听器
        setupSliders()
        
        // 设置开关监听器
        setupSwitches()
        
        // 检查传感器可用性
        checkSensors()
    }
    
    private fun setupSliders() {
        // 采样率滑块
        binding.sliderSamplingRate.addOnChangeListener { _, value, _ ->
            val rate = when (value.toInt()) {
                0 -> AccelerometerHelper.SAMPLING_NORMAL
                1 -> AccelerometerHelper.SAMPLING_UI
                2 -> AccelerometerHelper.SAMPLING_GAME
                3 -> AccelerometerHelper.SAMPLING_FASTEST
                else -> AccelerometerHelper.SAMPLING_NORMAL
            }
            accelerometerHelper.setSamplingRate(rate)
            updateSamplingRateText(value.toInt())
        }
        
        // 防抖动滑块
        binding.sliderDebounce.addOnChangeListener { _, value, _ ->
            val debounce = (value * 100).toLong() // 0-500ms
            accelerometerHelper.setDebounceInterval(debounce)
            binding.tvDebounceValue.text = "${debounce}ms"
        }
        
        // 设置初始值
        binding.sliderSamplingRate.value = 0f
        binding.sliderDebounce.value = 0.5f
        updateSamplingRateText(0)
        binding.tvDebounceValue.text = "50ms"
    }
    
    private fun updateSamplingRateText(value: Int) {
        val rateText = when (value) {
            0 -> "正常"
            1 -> "UI"
            2 -> "游戏"
            3 -> "最快"
            else -> "正常"
        }
        binding.tvSamplingRateValue.text = rateText
    }
    
    private fun setupSwitches() {
        // 加速度计开关
        binding.switchAccelerometer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                accelerometerHelper.addAccelerometerListener(this)
            } else {
                accelerometerHelper.removeAccelerometerListener(this)
                resetAccelerometerUI()
            }
            updateMonitoringState()
        }
        
        // 重力开关
        binding.switchGravity.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                accelerometerHelper.addGravityListener(this)
            } else {
                accelerometerHelper.removeGravityListener(this)
                resetGravityUI()
            }
            updateMonitoringState()
        }
        
        // 线性加速度开关
        binding.switchLinearAcceleration.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                accelerometerHelper.addLinearAccelerationListener(this)
            } else {
                accelerometerHelper.removeLinearAccelerationListener(this)
                resetLinearAccelerationUI()
            }
            updateMonitoringState()
        }
        
        // 过滤开关
        binding.switchFilter.setOnCheckedChangeListener { _, isChecked ->
            accelerometerHelper.setFilteringEnabled(isChecked)
        }
        
        // 设置初始状态
        binding.switchFilter.isChecked = true
    }
    
    /**
     * 更新监控状态
     */
    private fun updateMonitoringState() {
        val isAnyActive = binding.switchAccelerometer.isChecked || 
                        binding.switchGravity.isChecked || 
                        binding.switchLinearAcceleration.isChecked
        
        if (isAnyActive) {
            if (!accelerometerHelper.startListening()) {
                Toast.makeText(this, "无法启动传感器监听", Toast.LENGTH_SHORT).show()
            }
        } else {
            accelerometerHelper.stopListening()
        }
    }
    
    /**
     * 检查传感器可用性
     */
    private fun checkSensors() {
        if (!accelerometerHelper.hasAccelerometer()) {
            binding.switchAccelerometer.isEnabled = false
            binding.switchGravity.isEnabled = false
            binding.switchLinearAcceleration.isEnabled = false
            Toast.makeText(this, "设备不支持加速度计", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 重置加速度计UI
     */
    private fun resetAccelerometerUI() {
        binding.tvAccelXValue.text = "0.00"
        binding.tvAccelYValue.text = "0.00"
        binding.tvAccelZValue.text = "0.00"
        binding.progressAccelX.progress = 50
        binding.progressAccelY.progress = 50
        binding.progressAccelZ.progress = 50
    }
    
    /**
     * 重置重力UI
     */
    private fun resetGravityUI() {
        binding.tvGravityXValue.text = "0.00"
        binding.tvGravityYValue.text = "0.00"
        binding.tvGravityZValue.text = "0.00"
        binding.progressGravityX.progress = 50
        binding.progressGravityY.progress = 50
        binding.progressGravityZ.progress = 50
    }
    
    /**
     * 重置线性加速度UI
     */
    private fun resetLinearAccelerationUI() {
        binding.tvLinearXValue.text = "0.00"
        binding.tvLinearYValue.text = "0.00"
        binding.tvLinearZValue.text = "0.00"
        binding.progressLinearX.progress = 50
        binding.progressLinearY.progress = 50
        binding.progressLinearZ.progress = 50
    }
    
    /**
     * 计算进度条值
     * 将范围 -20 到 20 映射到 0 到 100
     */
    private fun calculateProgressValue(value: Float): Int {
        val clamped = value.coerceIn(-20f, 20f)
        return ((clamped + 20f) * 2.5f).toInt()
    }
    
    override fun onAccelerometerData(x: Float, y: Float, z: Float, timestamp: Long) {
        // 限制UI更新频率
        if (timestamp - lastUpdateTime < UI_UPDATE_INTERVAL) return
        lastUpdateTime = timestamp
        
        // 更新全局数据
        sensorDataManager.updateAccelerometerData(x, y, z)
        
        runOnUiThread {
            // 更新X值
            binding.tvAccelXValue.text = decimalFormat.format(x)
            binding.progressAccelX.progress = calculateProgressValue(x)
            
            // 更新Y值
            binding.tvAccelYValue.text = decimalFormat.format(y)
            binding.progressAccelY.progress = calculateProgressValue(y)
            
            // 更新Z值
            binding.tvAccelZValue.text = decimalFormat.format(z)
            binding.progressAccelZ.progress = calculateProgressValue(z)
            
            // 更新矢量大小
            val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            binding.tvAccelMagnitude.text = decimalFormat.format(magnitude)
            
            // 动态设置颜色，根据加速度大小
            val color = getColorForMagnitude(magnitude)
            binding.cardAccelerometer.setCardBackgroundColor(color)
        }
    }
    
    override fun onGravityData(x: Float, y: Float, z: Float, timestamp: Long) {
        // 更新全局数据
        sensorDataManager.updateGravityData(x, y, z)
        
        runOnUiThread {
            // 更新X值
            binding.tvGravityXValue.text = decimalFormat.format(x)
            binding.progressGravityX.progress = calculateProgressValue(x)
            
            // 更新Y值
            binding.tvGravityYValue.text = decimalFormat.format(y)
            binding.progressGravityY.progress = calculateProgressValue(y)
            
            // 更新Z值
            binding.tvGravityZValue.text = decimalFormat.format(z)
            binding.progressGravityZ.progress = calculateProgressValue(z)
            
            // 更新矢量大小
            val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            binding.tvGravityMagnitude.text = decimalFormat.format(magnitude)
        }
    }
    
    override fun onLinearAccelerationData(x: Float, y: Float, z: Float, timestamp: Long) {
        // 更新全局数据
        sensorDataManager.updateLinearAccelerationData(x, y, z)
        
        runOnUiThread {
            // 更新X值
            binding.tvLinearXValue.text = decimalFormat.format(x)
            binding.progressLinearX.progress = calculateProgressValue(x)
            
            // 更新Y值
            binding.tvLinearYValue.text = decimalFormat.format(y)
            binding.progressLinearY.progress = calculateProgressValue(y)
            
            // 更新Z值
            binding.tvLinearZValue.text = decimalFormat.format(z)
            binding.progressLinearZ.progress = calculateProgressValue(z)
            
            // 更新矢量大小
            val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            binding.tvLinearMagnitude.text = decimalFormat.format(magnitude)
            
            // 动态设置颜色，根据加速度大小
            val color = getColorForMagnitude(magnitude)
            binding.cardLinearAcceleration.setCardBackgroundColor(color)
        }
    }
    
    /**
     * 根据加速度大小获取颜色
     */
    private fun getColorForMagnitude(magnitude: Float): Int {
        return when {
            magnitude < 2f -> Color.parseColor("#FFFFFF") // 白色
            magnitude < 5f -> Color.parseColor("#E0F7FA") // 浅蓝色
            magnitude < 10f -> Color.parseColor("#B2EBF2") // 淡蓝色
            magnitude < 15f -> Color.parseColor("#80DEEA") // 中蓝色
            else -> Color.parseColor("#4DD0E1") // 深蓝色
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        accelerometerHelper.release()
    }
} 