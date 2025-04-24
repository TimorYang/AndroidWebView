package com.teemoyang.androidwebview

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
// import android.speech.RecognitionListener
// import android.speech.RecognizerIntent
// import android.speech.SpeechRecognizer
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.teemoyang.androidwebview.databinding.ActivitySpeechRecognitionBinding
import java.util.Locale

class SpeechRecognitionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpeechRecognitionBinding
    // private lateinit var speechRecognizer: SpeechRecognizer
    private val TAG = "SpeechRecognitionActivity"
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val handler = Handler(Looper.getMainLooper())
    
    // 脉动动画对象
    private var pulseAnimator: ObjectAnimator? = null
    
    // 进度条旋转动画
    private var progressAnimation: RotateAnimation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechRecognitionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 检查权限
        checkPermission()
        
        // 初始化语音识别 - 暂时注释
        // initSpeechRecognizer()
        
        // 设置按钮监听器
        setupListeners()
        
        // 设置返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )
        }
    }

    /*
    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "语音识别不可用", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.tvListening.text = "正在聆听..."
                Log.d(TAG, "onReadyForSpeech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 可以根据音量大小更新UI
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d(TAG, "onBufferReceived")
            }

            override fun onEndOfSpeech() {
                binding.tvListening.text = "处理中..."
                Log.d(TAG, "onEndOfSpeech")
            }

            override fun onError(error: Int) {
                binding.tvListening.text = "按住说话"
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配的结果"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有语音输入"
                    else -> "未知错误"
                }
                Log.e(TAG, "onError: $errorMessage")
                Toast.makeText(this@SpeechRecognitionActivity, errorMessage, Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                binding.tvListening.text = "按住说话"
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    Log.d(TAG, "识别结果: $recognizedText")
                    handleRecognizedSpeech(recognizedText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                Log.d(TAG, "onPartialResults")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "onEvent")
            }
        })
    }
    */

    private fun setupListeners() {
        // 记录按下时的初始位置
        var initialTouchY = 0f
        // 上划取消的阈值（dp）
        val cancelThresholdDp = 100
        // 将dp转换为像素
        val cancelThreshold = cancelThresholdDp * resources.displayMetrics.density
        // 是否处于取消模式
        var isCancelMode = false
        
        binding.micButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始触摸位置
                    initialTouchY = event.rawY
                    isCancelMode = false
                    
                    // 重置建议区域
                    resetSuggestions()
                    
                    // 按住时按钮缩小到80%
                    view.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).start()
                    // 启动脉动动画
                    startPulseAnimation()
                    // startListening()
                    simulateListening()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 计算垂直移动距离
                    val deltaY = initialTouchY - event.rawY
                    
                    // 如果上划超过阈值，进入取消模式
                    if (deltaY > cancelThreshold && !isCancelMode) {
                        isCancelMode = true
                        // 改变提示文字
                        binding.tvListening.text = "松开手指取消"
                        // 改变按钮颜色为红色，表示取消
                        binding.micButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#E57373") // 浅红色
                        )
                        // 停止脉动动画
                        stopPulseAnimation()
                    } 
                    // 如果从取消模式回到正常模式
                    else if (deltaY <= cancelThreshold && isCancelMode) {
                        isCancelMode = false
                        // 恢复提示文字
                        binding.tvListening.text = "正在聆听..."
                        // 恢复按钮颜色
                        binding.micButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#4AAF8C") // 深绿色
                        )
                        // 重新启动脉动动画
                        startPulseAnimation()
                    }
                    
                    // 阻止事件继续传递，确保按钮位置不变
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 松开时恢复原始大小
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    // 停止脉动动画
                    stopPulseAnimation()
                    
                    if (isCancelMode) {
                        // 如果在取消模式下松开，则取消语音识别并恢复原始提示
                        binding.tvListening.text = "按住说话"
                        // 恢复按钮颜色
                        binding.micButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#6AC3A9") // 原始绿色
                        )
                        
                        // 恢复原始提示词
                        resetSuggestions()
                        
                        // 确保进度条隐藏
                        binding.progressBar.visibility = View.GONE
                    } else {
                        // 正常结束语音识别
                        // stopListening()
                        simulateStopListening()
                    }
                    true
                }
                else -> false
            }
        }
        
        // 添加建议项的点击事件
        binding.tvSuggestion1.setOnClickListener {
            navigateToDestination("急诊室")
        }
        
        binding.tvSuggestion2.setOnClickListener {
            navigateToDestination("卫生间")
        }
        
        binding.tvSuggestion3.setOnClickListener {
            navigateToDestination("自助服务区")
        }
    }
    
    // 模拟开始听取语音
    private fun simulateListening() {
        binding.tvListening.text = "正在聆听..."
        // 更改麦克风按钮的背景颜色为更深的绿色
        binding.micButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor("#4AAF8C") // 更深的绿色
        )
        
        // 显示"搜索..."
        binding.tvPrompt.text = "搜索"
        binding.tvSuggestion1.text = "..."
        binding.tvSuggestion2.visibility = View.GONE
        binding.tvSuggestion3.visibility = View.GONE
    }
    
    // 模拟结束听取语音
    private fun simulateStopListening() {
        binding.tvListening.text = "处理中..."
        // 恢复麦克风按钮的原始背景颜色
        binding.micButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor("#6AC3A9") // 原始绿色
        )
        
        // 显示进度条并确保它在最上层
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.alpha = 1f  // 直接设置为完全不透明
        binding.progressBar.bringToFront()
        
        Log.d(TAG, "显示进度条，尺寸: ${binding.progressBar.width}x${binding.progressBar.height}, 可见性: ${binding.progressBar.visibility}")
        
        // 使用Handler延迟一段时间，模拟语音处理过程
        handler.postDelayed({
            // 随机选择一个目的地进行模拟
            val destinations = arrayOf("急诊室", "卫生间", "自助服务区", "药房", "挂号处")
            val randomDestination = destinations.random()
            
            // 隐藏进度条
            binding.progressBar.visibility = View.GONE
            Log.d(TAG, "隐藏进度条")
            
            if (randomDestination == "药房" || randomDestination == "挂号处") {
                // 显示搜索结果
                showSearchResults(randomDestination)
            } else {
                // 直接导航
                handleRecognizedSpeech(randomDestination)
            }
        }, 1500) // 延迟1.5秒
    }

    /*
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer.stopListening()
    }
    */

    private fun handleRecognizedSpeech(text: String) {
        // 处理识别到的文本
        val normalizedText = text.trim().toLowerCase(Locale.getDefault())
        
        when {
            normalizedText.contains("急诊室") || normalizedText.contains("急诊") -> {
                // 更新UI显示结果
                binding.tvPrompt.text = "搜索"
                binding.tvSuggestion1.text = "急诊室"
                binding.tvSuggestion2.visibility = View.GONE
                binding.tvSuggestion3.visibility = View.GONE
                
                // 一秒后导航并关闭
                handler.postDelayed({
                    navigateToDestination("急诊室")
                }, 1000)
            }
            normalizedText.contains("卫生间") || normalizedText.contains("厕所") || normalizedText.contains("洗手间") -> {
                // 更新UI显示结果
                binding.tvPrompt.text = "搜索"
                binding.tvSuggestion1.text = "卫生间"
                binding.tvSuggestion2.visibility = View.GONE
                binding.tvSuggestion3.visibility = View.GONE
                
                // 一秒后导航并关闭
                handler.postDelayed({
                    navigateToDestination("卫生间")
                }, 1000)
            }
            normalizedText.contains("自助") || normalizedText.contains("服务区") || normalizedText.contains("自助服务") -> {
                // 更新UI显示结果
                binding.tvPrompt.text = "搜索"
                binding.tvSuggestion1.text = "自助服务区"
                binding.tvSuggestion2.visibility = View.GONE
                binding.tvSuggestion3.visibility = View.GONE
                
                // 一秒后导航并关闭
                handler.postDelayed({
                    navigateToDestination("自助服务区")
                }, 1000)
            }
            else -> {
                // 显示搜索结果界面
                showSearchResults(normalizedText)
            }
        }
    }

    private fun navigateToDestination(destination: String) {
        Toast.makeText(this, "正在导航到: $destination", Toast.LENGTH_SHORT).show()
        
        // 这里可以返回结果给MainActivity或直接处理导航
        val resultIntent = Intent().apply {
            putExtra("DESTINATION", destination)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun showSearchResults(query: String) {
        // 显示搜索结果
        binding.tvPrompt.text = "搜索"
        binding.tvSuggestion1.text = query
        binding.tvSuggestion2.visibility = View.GONE
        binding.tvSuggestion3.visibility = View.GONE
        
        // 修改提示文字
        binding.tvListening.text = "按住说话"
        
        // 一秒后关闭页面
        handler.postDelayed({
            finish()
        }, 1000)
    }
    
    // 启动脉动动画
    private fun startPulseAnimation() {
        // 如果存在旧的动画，先停止
        stopPulseAnimation()
        
        // 创建缩放动画
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.micButton,
            PropertyValuesHolder.ofFloat("scaleX", 0.8f, 0.9f),
            PropertyValuesHolder.ofFloat("scaleY", 0.8f, 0.9f)
        ).apply {
            duration = 500 // 半秒一次脉动
            repeatCount = ObjectAnimator.INFINITE // 无限循环
            repeatMode = ObjectAnimator.REVERSE // 反向播放
            start() // 开始动画
        }
    }
    
    // 停止脉动动画
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    private fun resetSuggestions() {
        // 重置建议区域到初始状态
        binding.tvPrompt.text = "您可以说:"
        binding.tvSuggestion1.text = "急诊室"
        binding.tvSuggestion2.text = "卫生间"
        binding.tvSuggestion3.text = "自助服务区"
        binding.tvSuggestion2.visibility = View.VISIBLE
        binding.tvSuggestion3.visibility = View.VISIBLE
        
        // 隐藏搜索结果
        binding.tvNoResults.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "麦克风权限已获取", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请授予麦克风权限以使用语音功能", Toast.LENGTH_SHORT).show()
                // 在实际开发中，可以给用户一个更友好的提示，但暂时先不要关闭页面
                // finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // speechRecognizer.destroy()
        // 移除所有待执行的回调
        handler.removeCallbacksAndMessages(null)
        // 停止动画
        stopPulseAnimation()
        // 确保进度条隐藏
        binding.progressBar.visibility = View.GONE
    }
} 