package com.joysuch.pcl

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import com.alibaba.idst.nui.AsrResult
import com.alibaba.idst.nui.Constants
import com.alibaba.idst.nui.INativeNuiCallback
import com.alibaba.idst.nui.KwsResult
import com.alibaba.idst.nui.NativeNui
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayList
import java.util.HashMap
import java.util.Properties
import java.util.concurrent.LinkedBlockingQueue
import com.joysuch.pcl.data.UserSession

/**
 * 阿里云一句话语音识别示例Activity
 */
class SpeechRecognizerActivity : Activity(), INativeNuiCallback, AdapterView.OnItemSelectedListener {
    companion object {
        private const val TAG = "SpeechRecognizer"
        private const val SAMPLE_RATE = 16000
        private const val WAVE_FRAM_SIZE = 20 * 2 * 1 * SAMPLE_RATE / 1000 // 20ms audio for 16k/16bit/mono
    }

    // 阿里云认证信息
    private var g_appkey = ""
    private var g_token = ""
    private var g_sts_token = ""
    private var g_ak = ""
    private var g_sk = ""
    private var g_url = ""

    // NUI实例
    private val nui_instance = NativeNui()
    private val paramMap = HashMap<String, List<String>>()
    
    // 录音
    private var mAudioRecorder: AudioRecord? = null

    // UI组件
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button
    private lateinit var mVadSwitch: Switch
    private lateinit var mSaveAudioSwitch: Switch
    private lateinit var asrView: TextView
    private lateinit var resultView: TextView
    private lateinit var mSampleRateSpin: Spinner
    private lateinit var mFormatSpin: Spinner

    // 状态标志
    private var mInit = false
    private var mStopping = false
    private var mDebugPath = ""
    private var curTaskId = ""
    private val tmpAudioQueue = LinkedBlockingQueue<ByteArray>()
    private var mRecordingAudioFilePath = ""
    private var mRecordingAudioFile: OutputStream? = null
    private lateinit var mHandler: Handler
    private lateinit var mHanderThread: HandlerThread
    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_recognizer)

        // 从配置文件读取密钥信息
        loadApiKeysFromConfig()
        
        // 从UserSession获取token
        loadTokenFromUserSession()

        val version = nui_instance.GetVersion()
        Log.i(TAG, "current sdk version: $version")
        val version_text = "内部SDK版本号:$version"
        runOnUiThread { Toast.makeText(this@SpeechRecognizerActivity, version_text, Toast.LENGTH_SHORT).show() }

        // 从Intent获取参数（已废弃，保留代码兼容性）
        intent?.let {
            val appkey = it.getStringExtra("appkey")
            val token = it.getStringExtra("token")
            val stsToken = it.getStringExtra("stsToken")
            val ak = it.getStringExtra("accessKey")
            val sk = it.getStringExtra("accessKeySecret")
            val url = it.getStringExtra("url")
            
            // 只有当Intent中传入了参数才覆盖配置文件中的值
            if (!appkey.isNullOrEmpty()) g_appkey = appkey
            if (!token.isNullOrEmpty()) g_token = token
            if (!stsToken.isNullOrEmpty()) g_sts_token = stsToken
            if (!ak.isNullOrEmpty()) g_ak = ak
            if (!sk.isNullOrEmpty()) g_sk = sk
            if (!url.isNullOrEmpty()) g_url = url

            Log.i(TAG, "Get access from intent ->\n Appkey:$g_appkey\n Token:$g_token" +
                    "\n AccessKey:$g_ak\n AccessKeySecret:$g_sk" +
                    "\n STS_Token:$g_sts_token" +
                    "\n URL:$g_url")
        }

        initUIWidgets()

        mHanderThread = HandlerThread("process_thread")
        mHanderThread.start()
        mHandler = Handler(mHanderThread.looper)
    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
        doInit()
    }

    override fun onStop() {
        Log.i(TAG, "onStop")
        super.onStop()
        nui_instance.release()
    }

    private fun getSampleRateList() {
        val sr = ArrayList<String>()
        sr.add("16000")
        sr.add("8000")
        val spinnerSR = ArrayAdapter(
            this@SpeechRecognizerActivity,
            android.R.layout.simple_spinner_dropdown_item, sr
        )
        mSampleRateSpin.adapter = spinnerSR
        mSampleRateSpin.setSelection(0)
        paramMap["sample_rate"] = sr
    }

    private fun getFormatList() {
        val format = ArrayList<String>()
        format.add("opus")
        format.add("pcm")
        val spinnerFormat = ArrayAdapter(
            this@SpeechRecognizerActivity,
            android.R.layout.simple_spinner_dropdown_item, format
        )
        mFormatSpin.adapter = spinnerFormat
        mFormatSpin.setSelection(0)
        paramMap["format"] = format
    }

    private fun initUIWidgets() {
        asrView = findViewById(R.id.textView)
        asrView.movementMethod = ScrollingMovementMethod()
        resultView = findViewById(R.id.kws_text)

        mVadSwitch = findViewById(R.id.vad_switch)
        mVadSwitch.visibility = View.VISIBLE
        mVadSwitch.isChecked = true
        mSaveAudioSwitch = findViewById(R.id.save_audio_switch)
        mSaveAudioSwitch.visibility = View.VISIBLE

        mSampleRateSpin = findViewById(R.id.set_sample_rate)
        mSampleRateSpin.onItemSelectedListener = this
        mFormatSpin = findViewById(R.id.set_format)
        mFormatSpin.onItemSelectedListener = this
        getSampleRateList()
        getFormatList()

        startButton = findViewById(R.id.button_start)
        cancelButton = findViewById(R.id.button_cancel)
        setButtonState(startButton, true)
        setButtonState(cancelButton, false)
        startButton.setOnClickListener {
            Log.i(TAG, "start!!!")

            setButtonState(startButton, false)
            setButtonState(cancelButton, true)

            showText(asrView, "")
            showText(resultView, "")
            val ret = startDialog()
            if (!ret) {
                setButtonState(startButton, true)
                setButtonState(cancelButton, false)
            }
        }

        cancelButton.setOnClickListener {
            Log.i(TAG, "cancel")
            if (!checkNotInitToast()) {
                return@setOnClickListener
            }

            setButtonState(startButton, true)
            setButtonState(cancelButton, false)
            mHandler.post {
                mStopping = true
                val ret = nui_instance.stopDialog()
                Log.i(TAG, "cancel dialog $ret end")
            }
        }
    }

    private fun doInit() {
        showText(asrView, "")
        showText(resultView, "")

        setButtonState(startButton, true)
        setButtonState(cancelButton, false)

        // 注意: V2.6.2版本开始纯云端功能可不需要资源文件
        // if (CommonUtils.copyAssetsData(this)) {
        //     Log.i(TAG, "copy assets data done")
        // } else {
        //     Log.i(TAG, "copy assets failed")
        //     return
        // }

        // 获取工作路径
        // 注意: V2.6.2版本开始纯云端功能可不需要资源文件
        // 注意: 029版本如果不设置workspace也可继续用一句话识别, 但是日志系统会刷WARN日志
        val asset_path = ""
        // asset_path = CommonUtils.getModelPath(this)
        // Log.i(TAG, "use workspace $asset_path")

        mDebugPath = externalCacheDir?.absolutePath + "/debug"
        Utils.createDir(mDebugPath)

        // 初始化SDK
        val ret = nui_instance.initialize(this, genInitParams(asset_path, mDebugPath),
            Constants.LogLevel.LOG_LEVEL_DEBUG, true)
        Log.i(TAG, "result = $ret")
        if (ret == Constants.NuiResultCode.SUCCESS) {
            mInit = true
        } else {
            val msg_text = Utils.getMsgWithErrorCode(ret, "init")
            runOnUiThread {
                Toast.makeText(this@SpeechRecognizerActivity, msg_text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun genParams(): String {
        var params = ""
        try {
            val nls_config = JSONObject()

            // 是否返回中间识别结果，默认值：False
            nls_config.put("enable_intermediate_result", true)
            // 是否在后处理中添加标点，默认值：False
            nls_config.put("enable_punctuation_prediction", true)

            nls_config.put("sample_rate", mSampleRateSpin.selectedItem.toString().toInt())
            nls_config.put("sr_format", mFormatSpin.selectedItem.toString())

            // 若要使用VAD模式，需要设置参数启动在线VAD模式
            if (mVadSwitch.isChecked) {
                nls_config.put("enable_voice_detection", true)
                nls_config.put("max_start_silence", 10000)
                nls_config.put("max_end_silence", 800)
            } else {
                nls_config.put("enable_voice_detection", false)
            }

            val parameters = JSONObject()
            parameters.put("nls_config", nls_config)
            parameters.put("service_type", Constants.kServiceTypeASR) // 必填

            params = parameters.toString()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return params
    }

    private fun startDialog(): Boolean {
        // 首先，录音权限动态申请
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 检查该权限是否已经获取
            val i = ContextCompat.checkSelfPermission(this, permissions[0])
            // 权限是否已经授权
            if (i != PackageManager.PERMISSION_GRANTED) {
                // 如果没有授予该权限，就去提示用户请求
                requestPermissions(permissions, 321)
            }
        }
        
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (mAudioRecorder == null) {
                // 录音初始化，录音参数中格式只支持16bit/单通道，采样率支持8K/16K
                mAudioRecorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                    mSampleRateSpin.selectedItem.toString().toInt(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    WAVE_FRAM_SIZE * 4)
                Log.d(TAG, "AudioRecorder new ...")
            } else {
                Log.w(TAG, "AudioRecord has been new ...")
            }
        } else {
            Log.e(TAG, "donnot get RECORD_AUDIO permission!")
            runOnUiThread {
                Toast.makeText(this@SpeechRecognizerActivity,
                    "未获得录音权限，无法正常运行。请通过设置界面重新开启权限。", Toast.LENGTH_LONG).show()
            }
            showText(asrView, "未获得录音权限，无法正常运行。通过设置界面重新开启权限。")
            return false
        }

        mHandler.post {
            if (mVadSwitch.isChecked) {
                //TYPE_VAD: 云端服务自动判断句尾结束识别
                runOnUiThread {
                    Toast.makeText(this@SpeechRecognizerActivity,
                        "使用Voice Active Detection模式", Toast.LENGTH_SHORT).show()
                }
            } else {
                //TYPE_P2T: 由用户主动stop()以告知识别完成
                runOnUiThread {
                    Toast.makeText(this@SpeechRecognizerActivity,
                        "使用Push To Talk模式\n点击<停止>结束实时识别",
                        Toast.LENGTH_SHORT).show()
                }
            }

            //设置相关识别参数
            val setParamsString = genParams()
            Log.i(TAG, "nui set params $setParamsString")
            nui_instance.setParams(setParamsString)
            
            //开始一句话识别
            val ret = nui_instance.startDialog(Constants.VadMode.TYPE_P2T, genDialogParams())
            Log.i(TAG, "start done with $ret")
            if (ret != 0) {
                val msg_text = Utils.getMsgWithErrorCode(ret, "start")
                runOnUiThread {
                    Toast.makeText(this@SpeechRecognizerActivity, msg_text, Toast.LENGTH_LONG).show()
                }
            }
        }

        return true
    }

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
                    Toast.makeText(this@SpeechRecognizerActivity, "未获得有效临时凭证！", Toast.LENGTH_LONG).show()
                }
            }

            object_data.put("device_id", "empty_device_id")
            if (g_url.isEmpty()) {
                g_url = "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1" // 默认
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

    private fun genDialogParams(): String {
        var params = ""
        try {
            var dialog_param = JSONObject()
            
            // 检查UserSession中的token
            if (UserSession.isSpeechTokenValid()) {
                val token = UserSession.getSpeechToken()
                if (token != null && token != g_token) {
                    g_token = token
                    Log.i(TAG, "从UserSession更新token: $token")
                    Auth.setToken(g_token)
                }
            }
            
            // 运行过程中可以在startDialog时更新临时参数，尤其是更新过期token
            val distance_expire_time_5m: Long = 300
            dialog_param = Auth.refreshTokenIfNeed(dialog_param, distance_expire_time_5m)
            params = dialog_param.toString()
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        Log.i(TAG, "dialog params: $params")
        return params
    }

    private fun checkNotInitToast(): Boolean {
        if (!mInit) {
            runOnUiThread {
                Toast.makeText(this@SpeechRecognizerActivity, "SDK未成功初始化.", Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    private fun setButtonState(btn: Button, state: Boolean) {
        runOnUiThread {
            Log.i(TAG, "setBtn state ${btn.text} state=$state")
            btn.isEnabled = state
        }
    }

    private fun showText(who: TextView, text: String) {
        runOnUiThread {
            Log.d(TAG, "showText text=$text")
            if (TextUtils.isEmpty(text)) {
                Log.w(TAG, "asr text is empty")
                if (who == resultView) {
                    who.text = "识别内容"
                } else {
                    who.text = "响应文本"
                }
            } else {
                who.text = text
            }
        }
    }

    private fun appendText(who: TextView, text: String) {
        runOnUiThread {
            Log.d(TAG, "append text=$text")
            if (!TextUtils.isEmpty(text)) {
                val orign = who.text.toString()
                who.text = "$orign\n---\n$text"
            }
        }
    }

    override fun onItemSelected(view: AdapterView<*>, arg1: View, arg2: Int, arg3: Long) {
        if (view == mFormatSpin) {
            // 当用户选择不同的音频格式时
        } else if (view == mSampleRateSpin) {
            // 当用户选择不同的采样率时，重新初始化录音器
            if (mAudioRecorder != null) {
                Log.d(TAG, "AudioRecorder release.")
                mAudioRecorder?.release()
                mAudioRecorder = null
            }
        }
    }

    override fun onNothingSelected(adapterView: AdapterView<*>) {
        // 用户没有选择任何项时的回调
    }

    // INativeNuiCallback接口实现
    override fun onNuiEventCallback(
        event: Constants.NuiEvent, resultCode: Int, arg2: Int,
        kwsResult: KwsResult?, asrResult: AsrResult?
    ) {
        Log.i(TAG, "event=$event resultCode=$resultCode")

        when (event) {
            Constants.NuiEvent.EVENT_ASR_STARTED -> {
                showText(asrView, "EVENT_ASR_STARTED")
                val jsonObject = JSON.parseObject(asrResult?.allResponse)
                val header = jsonObject.getJSONObject("header")
                curTaskId = header.getString("task_id")
            }
            Constants.NuiEvent.EVENT_ASR_RESULT -> {
                asrResult?.let {
                    appendText(asrView, it.asrResult)
                    setButtonState(startButton, true)
                    setButtonState(cancelButton, false)
                    mStopping = false
                    val jsonObject = JSON.parseObject(it.asrResult)
                    val payload = jsonObject.getJSONObject("payload")
                    val result = payload.getString("result")
                    showText(resultView, result)
                }
            }
            Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT -> {
                asrResult?.let {
                    if (mStopping) {
                        appendText(asrView, it.asrResult)
                    } else {
                        showText(asrView, it.asrResult)
                    }
                    val jsonObject = JSON.parseObject(it.asrResult)
                    val payload = jsonObject.getJSONObject("payload")
                    val result = payload.getString("result")
                    showText(resultView, result)
                }
            }
            Constants.NuiEvent.EVENT_ASR_ERROR -> {
                asrResult?.let {
                    appendText(asrView, it.asrResult)
                }
                runOnUiThread {
                    Toast.makeText(this@SpeechRecognizerActivity, "ERROR with $resultCode",
                        Toast.LENGTH_SHORT).show()
                }
                val msg_text = Utils.getMsgWithErrorCode(resultCode, "start")
                runOnUiThread {
                    Toast.makeText(this@SpeechRecognizerActivity, msg_text, Toast.LENGTH_SHORT).show()
                }
                setButtonState(startButton, true)
                setButtonState(cancelButton, false)
                mStopping = false
            }
            Constants.NuiEvent.EVENT_VAD_START -> {
                appendText(asrView, "EVENT_VAD_START")
            }
            Constants.NuiEvent.EVENT_VAD_END -> {
                appendText(asrView, "EVENT_VAD_END")
            }
            Constants.NuiEvent.EVENT_MIC_ERROR -> {
                val msg_text = Utils.getMsgWithErrorCode(resultCode, "start")
                runOnUiThread {
                    Toast.makeText(this@SpeechRecognizerActivity, msg_text, Toast.LENGTH_SHORT).show()
                }
                setButtonState(startButton, true)
                setButtonState(cancelButton, false)
                mStopping = false
            }
            Constants.NuiEvent.EVENT_DIALOG_EX -> {
                asrResult?.let {
                    Log.i(TAG, "dialog extra message = ${it.asrResult}")
                }
            }
            else -> {}
        }
    }

    // 当调用NativeNui的start后，会一定时间反复回调该接口，底层会提供buffer并告知这次需要数据的长度
    override fun onNuiNeedAudioData(buffer: ByteArray, len: Int): Int {
        val audioRecorder = mAudioRecorder ?: return -1
        
        if (audioRecorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "audio recorder not init")
            return -1
        }

        // 送入SDK
        val audio_size = audioRecorder.read(buffer, 0, len)

        // 音频存储到本地
        if (mSaveAudioSwitch.isChecked && audio_size > 0) {
            if (mRecordingAudioFile == null) {
                // 音频存储文件未打开，则等获得task_id后打开音频存储文件，否则数据存储到tmpAudioQueue
                if (curTaskId.isNotEmpty() && mRecordingAudioFile == null) {
                    try {
                        mRecordingAudioFilePath = "$mDebugPath/sr_task_id_$curTaskId.pcm"
                        Log.i(TAG, "save recorder data into $mRecordingAudioFilePath")
                        mRecordingAudioFile = FileOutputStream(mRecordingAudioFilePath, true)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                } else {
                    tmpAudioQueue.offer(buffer.clone())
                }
            }
            
            mRecordingAudioFile?.let { file ->
                // 若tmpAudioQueue有存储的音频，先存到音频存储文件中
                if (tmpAudioQueue.size > 0) {
                    try {
                        // 将未打开recorder前的音频存入文件中
                        val audioData = tmpAudioQueue.take()
                        try {
                            file.write(audioData)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

                // 当前音频数据存到音频存储文件
                try {
                    file.write(buffer, 0, audio_size)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        return audio_size
    }

    // 当录音状态发送变化的时候调用
    override fun onNuiAudioStateChanged(state: Constants.AudioState) {
        Log.i(TAG, "onNuiAudioStateChanged")
        when (state) {
            Constants.AudioState.STATE_OPEN -> {
                Log.i(TAG, "audio recorder start")
                mAudioRecorder?.startRecording()
                Log.i(TAG, "audio recorder start done")
            }
            Constants.AudioState.STATE_CLOSE -> {
                Log.i(TAG, "audio recorder close")
                mAudioRecorder?.release()
                mAudioRecorder = null

                try {
                    mRecordingAudioFile?.let {
                        it.close()
                        mRecordingAudioFile = null
                        val show = "存储录音音频到 $mRecordingAudioFilePath"
                        appendText(asrView, show)
                        runOnUiThread {
                            Toast.makeText(this@SpeechRecognizerActivity, show, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            Constants.AudioState.STATE_PAUSE -> {
                Log.i(TAG, "audio recorder pause")
                mAudioRecorder?.stop()

                try {
                    mRecordingAudioFile?.let {
                        it.close()
                        mRecordingAudioFile = null
                        val show = "存储录音音频到 $mRecordingAudioFilePath"
                        appendText(asrView, show)
                        runOnUiThread {
                            Toast.makeText(this@SpeechRecognizerActivity, show, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            else -> {}
        }
    }

    override fun onNuiAudioRMSChanged(val_input: Float) {
        // Log.i(TAG, "onNuiAudioRMSChanged vol $val_input")
    }

    override fun onNuiVprEventCallback(event: Constants.NuiVprEvent) {
        Log.i(TAG, "onNuiVprEventCallback event $event")
    }

    override fun onNuiLogTrackCallback(level: Constants.LogLevel, log: String) {
        Log.i(TAG, "onNuiLogTrackCallback log level:$level, message -> $log")
    }

    // 从配置文件读取密钥信息
    private fun loadApiKeysFromConfig() {
        try {
            val properties = Properties()
            
            // 尝试从assets目录读取配置
            assets.open("api_keys.properties").use { inputStream ->
                properties.load(inputStream)
                
                g_appkey = properties.getProperty("alibaba.appkey", "")
                g_token = properties.getProperty("alibaba.token", "")
                g_sts_token = properties.getProperty("alibaba.sts_token", "")
                g_ak = properties.getProperty("alibaba.access_key", "")
                g_sk = properties.getProperty("alibaba.access_key_secret", "")
                g_url = properties.getProperty("alibaba.url", "")
                
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
     * 从UserSession获取语音token
     */
    private fun loadTokenFromUserSession() {
        try {
            // 检查UserSession中是否有有效token
            if (UserSession.isSpeechTokenValid()) {
                val token = UserSession.getSpeechToken()
                if (token != null) {
                    g_token = token
                    Log.i(TAG, "成功从UserSession获取token: $token")
                } else {
                    Log.e(TAG, "UserSession中的token为null")
                }
            } else {
                Log.e(TAG, "UserSession中无有效token或token已过期")
            }
        } catch (e: Exception) {
            Log.e(TAG, "从UserSession获取token异常: ${e.message}")
        }
    }
} 