package com.teemoyang.androidwebview

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.HandlerThread
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSON
import com.teemoyang.androidwebview.databinding.ActivitySpeechRecognitionBinding
import com.alibaba.idst.nui.Constants
import com.alibaba.idst.nui.NativeNui
import com.alibaba.idst.nui.INativeNuiCallback
import com.alibaba.idst.nui.AsrResult
import com.alibaba.idst.nui.KwsResult
import com.alibaba.fastjson.JSONObject
import com.teemoyang.androidwebview.data.UserSession
import java.util.Locale
import java.util.Properties

class SpeechRecognitionActivity : AppCompatActivity(), INativeNuiCallback {

    private lateinit var binding: ActivitySpeechRecognitionBinding
    private val TAG = "SpeechRecognitionActivity"
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val handler = Handler(Looper.getMainLooper())
    
    // 脉动动画对象
    private var pulseAnimator: ObjectAnimator? = null
    
    // 进度条旋转动画
    private var progressAnimation: RotateAnimation? = null
    
    // 阿里云语音识别SDK相关变量
    private val nui_instance = NativeNui()
    private var mInit = false
    private var mDebugPath = ""
    private lateinit var mHandler: Handler
    private lateinit var mHandlerThread: HandlerThread
    
    // 认证信息
    private var g_appkey = ""
    private var g_token = ""
    private var g_sts_token = ""
    private var g_ak = ""
    private var g_sk = ""
    private var g_url = ""

    // 录音相关常量
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val WAVE_FRAM_SIZE = 20 * 2 * 1 * SAMPLE_RATE / 1000 // 20ms audio for 16k/16bit/mono
    }

    // 其他成员变量
    private var mAudioRecorder: AudioRecord? = null
    private var isRecording = false
    private var mStopping = false
    private var currentRecognizedText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechRecognitionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 检查权限
        checkPermission()
        
        // 初始化后台处理线程
        mHandlerThread = HandlerThread("process_thread")
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
        
        // 加载认证信息
        loadAuthInfo()

        initializeSDK()
        
        // 如果初始化失败，禁用录音按钮
        if (!mInit) {
            binding.micButton.isEnabled = false
            binding.micButton.alpha = 0.5f
            binding.tvListening.text = "语音识别不可用"
            Toast.makeText(this, "语音识别功能不可用，请先登录获取授权", Toast.LENGTH_LONG).show()
        }
        
        // 设置按钮监听器
        setupListeners()
        
        // 设置返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 处理自定义提示和建议
        processIntentExtras()
    }
    
    /**
     * 加载认证信息
     */
    private fun loadAuthInfo() {
        try {
            // 尝试从配置文件读取密钥信息
            loadApiKeysFromConfig()
            
            // 从UserSession获取token
            if (UserSession.isSpeechTokenValid()) {
                g_token = UserSession.getSpeechToken() ?: ""
                Log.i(TAG, "成功从UserSession获取token: $g_token")
            } else {
                Log.e(TAG, "UserSession中没有有效的语音识别token")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载认证信息异常: ${e.message}")
            g_token = ""
        }
    }
    
    /**
     * 从配置文件读取API密钥
     */
    private fun loadApiKeysFromConfig() {
        try {
            val properties = Properties()
            // 尝试从assets目录读取配置
            assets.open("api_keys.properties").use { inputStream ->
                properties.load(inputStream)
                g_appkey = properties.getProperty("alibaba.appkey", "")
                g_ak = properties.getProperty("alibaba.access_key", "")
                g_sk = properties.getProperty("alibaba.access_key_secret", "")
                Log.i(TAG, "成功从配置文件读取API密钥")
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取API密钥失败: ${e.message}")
            // 读取失败时使用默认空值
            g_appkey = ""
            g_token = ""
            g_sts_token = ""
            g_ak = ""
            g_sk = ""
            g_url = ""
        }
    }
    
    /**
     * 初始化语音识别SDK
     */
    private fun initializeSDK() {
        try {
            // 显示SDK版本
            val version = nui_instance.GetVersion()
            Log.i(TAG, "当前SDK版本: $version")

            // 获取工作路径
            // 注意: V2.6.2版本开始纯云端功能可不需要资源文件
            // 注意: 029版本如果不设置workspace也可继续用一句话识别, 但是日志系统会刷WARN日志
            val asset_path = ""
            // asset_path = CommonUtils.getModelPath(this)
            // Log.i(TAG, "use workspace $asset_path")

            // 获取缓存路径
            mDebugPath = externalCacheDir?.absolutePath + "/speech_debug"
            Utils.createDir(mDebugPath)
            
            // 生成初始化参数
            val initParams = genInitParams(asset_path, mDebugPath)
            
            // 进行SDK初始化
            val ret = nui_instance.initialize(this, initParams, Constants.LogLevel.LOG_LEVEL_DEBUG, true)
            
            if (ret == Constants.NuiResultCode.SUCCESS) {
                mInit = true
                Log.i(TAG, "语音识别SDK初始化成功")
            } else {
                mInit = false
                Log.e(TAG, "语音识别SDK初始化失败，错误码: $ret")
                runOnUiThread {
                    Toast.makeText(this, "语音识别功能初始化失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            mInit = false
            Log.e(TAG, "语音识别SDK初始化异常: ${e.message}")
            runOnUiThread {
                Toast.makeText(this, "语音识别初始化发生异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 生成初始化参数
     */
    private fun genInitParams(workpath: String, debugpath: String): String {
        var str = ""
        try {
            // 获取账号访问凭证
            var method = Auth.GetTicketMethod.GET_TOKEN_FROM_SERVER_FOR_ONLINE_FEATURES
            if (g_appkey.isNotEmpty()) {
                Auth.setAppKey(g_appkey)
            }
            if (g_token.isNotEmpty()) {
                Auth.setToken(g_token)
            }
            if (g_ak.isNotEmpty()) {
                Auth.setAccessKey(g_ak)
            }
            if (g_sk.isNotEmpty()) {
                Auth.setAccessKeySecret(g_sk)
            }
            Auth.setStsToken(g_sts_token)
            
            if (g_appkey.isNotEmpty()) {
                if (g_ak.isNotEmpty() && g_sk.isNotEmpty()) {
                    method = if (g_sts_token.isEmpty()) {
                        Auth.GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES
                    } else {
                        Auth.GetTicketMethod.GET_STS_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES
                    }
                }
                if (g_token.isNotEmpty()) {
                    method = Auth.GetTicketMethod.GET_TOKEN_IN_CLIENT_FOR_ONLINE_FEATURES
                }
            }
            
            Log.i(TAG, "Use method:$method")
            val object_data = Auth.getTicket(method)
            if (!object_data.containsKey("token")) {
                Log.e(TAG, "Cannot get token !!!")
                runOnUiThread {
                    Toast.makeText(this@SpeechRecognitionActivity, "未获得有效临时凭证！", Toast.LENGTH_LONG).show()
                }
            }

            // 设备标识，从userSession获取
            val deviceId = UserSession.getDeviceId()
            object_data.put("device_id", deviceId)
            // 服务地址
            if (g_url.isEmpty()) {
                g_url = "wss://nls-gateway.aliyuncs.com/ws/v1" // 默认
            }
            object_data.put("url", g_url)

            // 当初始化SDK时的save_log参数取值为true时，该参数生效
            object_data.put("save_wav", "true")
            object_data.put("debug_path", debugpath)

            // 过滤SDK内部日志通过回调送回到用户层
            object_data.put("log_track_level", 
                Constants.LogLevel.toInt(Constants.LogLevel.LOG_LEVEL_INFO).toString())

            // FullCloud = 1
            // AsrCloud = 4
            object_data.put("service_mode", Constants.ModeAsrCloud) // 必填
            str = object_data.toString()
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        Log.i(TAG, "InsideUserContext: $str")
        return str
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
                    
                    // 开始语音识别
                    startRecognition()
                    
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
                        
                        // 取消语音识别
                        cancelRecognition()
                    } else {
                        // 正常结束语音识别
                        stopRecognition()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleRecognizedSpeech(text: String) {
        // 处理识别到的文本
        val normalizedText = text.trim().toLowerCase(Locale.getDefault())
        showSearchResults(normalizedText)
        // 立即调用navigateToDestination，不需要延迟
        navigateToDestination(text)
    }

    private fun navigateToDestination(destination: String) {
        Log.d(TAG, "navigateToDestination: 返回结果 '$destination' 给MainActivity")
        Toast.makeText(this, "语音识别结果: $destination", Toast.LENGTH_SHORT).show()
        
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
        
        // 修改提示文字
        binding.tvListening.text = "按住说话"
        
        // 移除一秒后关闭页面的代码，由navigateToDestination处理
        // handler.postDelayed({
        //    finish()
        // }, 1000)
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
        binding.tvSuggestion1.text = "卫生间"
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
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 释放语音识别SDK资源
        try {
            nui_instance.release()
            Log.i(TAG, "语音识别SDK已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放语音识别SDK异常: ${e.message}")
        }
        
        // 移除所有待执行的回调
        handler.removeCallbacksAndMessages(null)
        mHandler.removeCallbacksAndMessages(null)
        
        // 停止动画
        stopPulseAnimation()
        
        // 确保进度条隐藏
        binding.progressBar.visibility = View.GONE
        
        // 停止后台线程
        mHandlerThread.quitSafely()
    }
    
    // INativeNuiCallback接口实现
    override fun onNuiEventCallback(
        event: Constants.NuiEvent, 
        resultCode: Int, 
        arg2: Int,
        kwsResult: KwsResult?, 
        asrResult: AsrResult?
    ) {
        Log.i(TAG, "语音识别事件: $event, 结果码: $resultCode")
        
        when (event) {
            Constants.NuiEvent.EVENT_ASR_STARTED -> {
                // 语音识别开始
                runOnUiThread {
                    binding.tvListening.text = "正在聆听..."
                }
                Log.i(TAG, "语音识别已开始")
            }
            
            Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT -> {
                // 语音识别中间结果
                asrResult?.let {
                    try {
                        val jsonObject = JSON.parseObject(it.asrResult)
                        val payload = jsonObject.getJSONObject("payload")
                        val result = payload.getString("result")
                        
                        Log.i(TAG, "语音识别中间结果: $result")
                        
                        // 更新UI
                        runOnUiThread {
                            if (result.isNotEmpty()) {
                                binding.tvPrompt.text = "搜索"
                                binding.tvSuggestion1.text = result
                            }
                        }
                        
                        // 保存当前识别结果
                        currentRecognizedText = result
                    } catch (e: Exception) {
                        Log.e(TAG, "解析中间结果异常: ${e.message}")
                    }
                }
            }
            
            Constants.NuiEvent.EVENT_ASR_RESULT -> {
                // 语音识别最终结果
                asrResult?.let {
                    try {
                        val jsonObject = JSON.parseObject(it.asrResult)
                        val payload = jsonObject.getJSONObject("payload")
                        val result = payload.getString("result")
                        
                        Log.i(TAG, "语音识别最终结果: $result")
                        
                        // 更新UI
                        runOnUiThread {
                            // 隐藏进度条
                            binding.progressBar.visibility = View.GONE
                            binding.tvListening.text = "按住说话"
                            
                            // 处理识别结果
                            if (result.isNotEmpty()) {
                                handleRecognizedSpeech(result)
                            } else {
                                // 结果为空，重置界面
                                resetSuggestions()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析最终结果异常: ${e.message}")
                        runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.tvListening.text = "按住说话"
                            resetSuggestions()
                        }
                    }
                }
            }
            
            Constants.NuiEvent.EVENT_ASR_ERROR -> {
                // 语音识别错误
                Log.e(TAG, "语音识别错误，错误码: $resultCode")
                
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.tvListening.text = "按住说话"
                    
                    // 显示错误信息
                    val errorMsg = when (resultCode) {
                        20006 -> "未检测到语音"
                        20000 -> "网络连接失败"
                        20001 -> "网络超时"
                        20002 -> "认证失败"
                        20004 -> "音频解码失败"
                        else -> "语音识别错误($resultCode)"
                    }
                    
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    resetSuggestions()
                }
            }
            
            Constants.NuiEvent.EVENT_VAD_START -> {
                // 检测到语音开始
                Log.i(TAG, "检测到语音开始")
            }
            
            Constants.NuiEvent.EVENT_VAD_END -> {
                // 检测到语音结束
                Log.i(TAG, "检测到语音结束")
                
                runOnUiThread {
                    binding.tvListening.text = "处理中..."
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
            
            else -> {
                // 其他事件
                Log.d(TAG, "其他语音识别事件: $event")
            }
        }
    }
    
    // 麦克风录音回调
    override fun onNuiNeedAudioData(buffer: ByteArray, len: Int): Int {
        val audioRecorder = mAudioRecorder ?: return -1
        
        if (audioRecorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "音频录音器未初始化")
            return -1
        }
        
        val audioSize = audioRecorder.read(buffer, 0, len)
        return audioSize
    }
    
    // 录音状态变化回调
    override fun onNuiAudioStateChanged(state: Constants.AudioState) {
        Log.i(TAG, "音频状态变化: $state")
        
        when (state) {
            Constants.AudioState.STATE_OPEN -> {
                // 开始录音
                Log.i(TAG, "audio recorder start")
                mAudioRecorder?.startRecording()
                Log.i(TAG, "audio recorder start done")
            }
            
            Constants.AudioState.STATE_CLOSE -> {
                // 停止录音
                Log.i(TAG, "audio recorder close")
                mAudioRecorder?.release()
                mAudioRecorder = null
            }
            
            Constants.AudioState.STATE_PAUSE -> {
                // 暂停录音
                mAudioRecorder?.stop()
                Log.i(TAG, "音频录音器已暂停")
            }
            
            else -> {
                // 其他状态
                Log.d(TAG, "其他音频状态: $state")
            }
        }
    }

    override fun onNuiAudioRMSChanged(val_input: Float) {
        // 处理音量变化，可用于显示音量动画
        // Log.d(TAG, "音频音量变化: $rms")
    }

    override fun onNuiVprEventCallback(event: Constants.NuiVprEvent) {
        // 处理VPR事件
        Log.i(TAG, "onNuiVprEventCallback event $event")
    }

    override fun onNuiLogTrackCallback(level: Constants.LogLevel, log: String) {
        // 处理SDK日志
        Log.i(TAG, "onNuiLogTrackCallback log level:$level, message -> $log")
    }
    
    /**
     * 开始语音识别
     */
    private fun startRecognition() {
        mHandler.post {
            try {
                // 检查录音权限
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "没有录音权限")
                    runOnUiThread {
                        Toast.makeText(this, "未获得录音权限，无法正常运行", Toast.LENGTH_LONG).show()
                    }
                    return@post
                }
                
                // 初始化录音器
                if (mAudioRecorder == null) {
                    // 录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
                    mAudioRecorder = AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        WAVE_FRAM_SIZE * 4
                    )
                    Log.d(TAG, "AudioRecorder initialized")
                }
                
                // 记录当前状态
                isRecording = true
                currentRecognizedText = ""
                
                // 更新UI
                runOnUiThread {
                    binding.tvListening.text = "正在聆听..."
                    binding.progressBar.visibility = View.GONE
                }
                
                // 设置语音识别参数
                val params = genRecognitionParams()
                Log.i(TAG, "设置识别参数: $params")
                nui_instance.setParams(params)
                
                // 启动语音识别
                val ret = nui_instance.startDialog(
                    Constants.VadMode.TYPE_P2T, // 使用VAD模式(自动检测语音开始和结束)
                    genDialogParams() // 生成对话参数
                )
                
                if (ret != Constants.NuiResultCode.SUCCESS) {
                    Log.e(TAG, "启动语音识别失败，错误码: $ret")
                    runOnUiThread {
                        Toast.makeText(this, "启动语音识别失败", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.i(TAG, "语音识别启动成功")
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动语音识别异常: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "启动语音识别异常", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 停止语音识别
     */
    private fun stopRecognition() {
        mHandler.post {
            try {
                // 记录状态
                mStopping = true
                
                // 更新UI
                runOnUiThread {
                    binding.tvListening.text = "处理中..."
                    binding.progressBar.visibility = View.VISIBLE
                }
                
                // 停止语音识别
                val ret = nui_instance.stopDialog()
                
                if (ret != Constants.NuiResultCode.SUCCESS) {
                    Log.e(TAG, "停止语音识别失败，错误码: $ret")
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.tvListening.text = "按住说话"
                        Toast.makeText(this, "停止语音识别失败", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.i(TAG, "语音识别停止成功")
                    // 处理结果UI的更新在onNuiEventCallback回调中完成
                }
            } catch (e: Exception) {
                Log.e(TAG, "停止语音识别异常: ${e.message}")
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.tvListening.text = "按住说话"
                    Toast.makeText(this, "停止语音识别异常", Toast.LENGTH_SHORT).show()
                }
            } finally {
                mStopping = false
            }
        }
    }
    
    /**
     * 取消语音识别
     */
    private fun cancelRecognition() {
        mHandler.post {
            try {
                // 停止语音识别
                nui_instance.stopDialog()
                Log.i(TAG, "已取消语音识别")
                isRecording = false
                
                // 更新UI
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.tvListening.text = "按住说话"
                }
            } catch (e: Exception) {
                Log.e(TAG, "取消语音识别异常: ${e.message}")
            }
        }
    }
    
    /**
     * 生成语音识别参数
     */
    private fun genRecognitionParams(): String {
        var params = ""
        try {
            val nlsConfig = JSONObject()
            
            // 基本设置
            nlsConfig.put("sample_rate", SAMPLE_RATE)
            nlsConfig.put("format", "pcm")
            
            // VAD设置
            nlsConfig.put("enable_voice_detection", true)
            nlsConfig.put("max_start_silence", 10000) // 最长开始静音时间(ms)
            nlsConfig.put("max_end_silence", 800)     // 最长结束静音时间(ms)
            
            // 其他设置
            nlsConfig.put("enable_intermediate_result", true) // 返回中间识别结果
            nlsConfig.put("enable_punctuation_prediction", true) // 智能添加标点
            nlsConfig.put("enable_inverse_text_normalization", true) // 开启ITN
            
            // 整体参数
            val parametersObj = JSONObject()
            parametersObj.put("nls_config", nlsConfig)
            parametersObj.put("service_type", Constants.kServiceTypeASR) // ASR服务
            
            params = parametersObj.toString()
            Log.i(TAG, "语音识别参数: $params")
        } catch (e: Exception) {
            Log.e(TAG, "生成语音识别参数异常: ${e.message}")
        }
        return params
    }
    
    /**
     * 生成对话参数
     */
    private fun genDialogParams(): String {
        var params = ""
        try {
            val dialogParam = JSONObject()
            
            // 使用已有的token
            if (g_token.isNotEmpty()) {
                dialogParam.put("token", g_token)
            }
            
            params = dialogParam.toString()
            Log.i(TAG, "对话参数: $params")
        } catch (e: Exception) {
            Log.e(TAG, "生成对话参数异常: ${e.message}")
        }
        return params
    }

    /**
     * 处理Intent中传入的额外参数
     */
    private fun processIntentExtras() {
        try {
            // 处理自定义提示文字
            val prompt = intent.getStringExtra("PROMPT")
            if (!prompt.isNullOrEmpty()) {
                binding.tvPrompt.text = prompt
            }
            
            // 处理自定义建议选项
            val suggestionsJson = intent.getStringExtra("SUGGESTIONS")
            if (!suggestionsJson.isNullOrEmpty()) {
                try {
                    val suggestionArray = com.alibaba.fastjson.JSON.parseArray(suggestionsJson, String::class.java)
                    
                    // 更新UI上的建议选项
                    if (suggestionArray.size > 0) {
                        binding.tvSuggestion1.text = suggestionArray[0]
                        binding.tvSuggestion1.visibility = View.VISIBLE
                    } else {
                        binding.tvSuggestion1.visibility = View.GONE
                    }


                Log.d(TAG, "已应用自定义建议选项: $suggestionsJson")
                } catch (e: Exception) {
                    Log.e(TAG, "解析建议选项JSON失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理Intent额外参数失败: ${e.message}")
        }
    }
} 