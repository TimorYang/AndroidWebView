package com.teemoyang.androidwebview.js

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.teemoyang.androidwebview.SpeechRecognitionActivity

/**
 * WebAppInterface - 提供JavaScript与原生Android代码的交互接口
 * 
 * @param context 上下文，通常是Activity
 * @param webView WebView实例，用于回调JavaScript函数
 * @param deviceId 设备ID
 * @param userType 用户类型
 * @param permissionId 权限ID
 */
class WebAppInterface(
    private val context: Context,
    private val webView: WebView,
    private val deviceId: String,
    private val userType: String,
    private val permissionId: String
) {
    
    companion object {
        private const val TAG = "WebAppInterface"
        const val SPEECH_RECOGNITION_REQUEST_CODE = 1001
    }
    
    /**
     * 显示Toast消息
     * 在JavaScript中调用: Android.showToast("消息");
     */
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "JS调用了showToast: $message")
    }
    
    /**
     * 获取设备ID
     * 在JavaScript中调用: const deviceId = Android.getDeviceId();
     */
    @JavascriptInterface
    fun getDeviceId(): String {
        return deviceId
    }
    
    /**
     * 获取用户类型
     * 在JavaScript中调用: const userType = Android.getUserType();
     */
    @JavascriptInterface
    fun getUserType(): String {
        return userType
    }
    
    /**
     * 获取权限ID
     * 在JavaScript中调用: const permissionId = Android.getPermissionId();
     */
    @JavascriptInterface
    fun getPermissionId(): String {
        return permissionId
    }
    
    /**
     * 打开语音识别功能
     * 在JavaScript中调用: Android.openSpeechRecognition();
     */
    @JavascriptInterface
    fun openSpeechRecognition() {
        if (context is AppCompatActivity) {
            (context as AppCompatActivity).runOnUiThread {
                val intent = Intent(context, SpeechRecognitionActivity::class.java)
                (context as AppCompatActivity).startActivityForResult(
                    intent, 
                    SPEECH_RECOGNITION_REQUEST_CODE
                )
            }
        }
    }
    
    /**
     * 打开语音识别，支持自定义提示语和建议
     * 在JavaScript中调用: 
     *   Android.openSpeechRecognitionWithOptions("提示语", JSON.stringify(["建议1", "建议2"]));
     * 
     * @param prompt 提示语，例如"您可以说:"
     * @param suggestions 建议列表，JSON格式字符串，例如 ["急诊室", "卫生间", "自助服务区"]
     */
    @JavascriptInterface
    fun openSpeechRecognitionWithOptions(prompt: String?, suggestions: String?) {
        if (context is AppCompatActivity) {
            (context as AppCompatActivity).runOnUiThread {
                try {
                    val intent = Intent(context, SpeechRecognitionActivity::class.java)
                    
                    // 如果提供了自定义提示语
                    if (!prompt.isNullOrEmpty()) {
                        intent.putExtra("PROMPT", prompt)
                    }
                    
                    // 如果提供了建议列表
                    if (!suggestions.isNullOrEmpty()) {
                        intent.putExtra("SUGGESTIONS", suggestions)
                    }
                    
                    (context as AppCompatActivity).startActivityForResult(
                        intent, 
                        SPEECH_RECOGNITION_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "打开语音识别失败: ${e.message}")
                    Toast.makeText(context, "打开语音识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 将语音识别结果发送回JavaScript
     * 
     * @param result 语音识别结果
     */
    fun sendSpeechResultToJs(result: String) {
        Log.d(TAG, "开始发送语音识别结果到JS: '$result'")
        
        if (context is AppCompatActivity) {
            try {
                // 对结果进行安全处理，避免JavaScript注入问题
                val safeResult = result.replace("'", "\\'")
                
                // 构建JavaScript回调函数
                val jsCode = "javascript:if(window.onSpeechResult){window.onSpeechResult('$safeResult');}"
                
                Log.d(TAG, "准备执行JS代码: $jsCode")
                
                // 在主线程中执行
                (context as AppCompatActivity).runOnUiThread {
                    try {
                        webView.evaluateJavascript(jsCode) { value ->
                            Log.d(TAG, "语音识别结果回调执行完成: $value")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "执行JavaScript时发生异常: ${e.message}", e)
                    }
                }
                
                // 再额外添加一个直接通过loadUrl的调用方式，以防evaluateJavascript失败
                (context as AppCompatActivity).runOnUiThread {
                    try {
                        webView.loadUrl(jsCode)
                        Log.d(TAG, "通过loadUrl发送语音识别结果")
                    } catch (e: Exception) {
                        Log.e(TAG, "通过loadUrl发送结果失败: ${e.message}", e)
                    }
                }
                
                Log.d(TAG, "已将语音识别结果'$result'发送给JS")
            } catch (e: Exception) {
                Log.e(TAG, "发送语音识别结果到JS时出错: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "无法发送语音识别结果: context不是AppCompatActivity实例")
        }
    }
} 