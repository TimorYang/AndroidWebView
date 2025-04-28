package com.joysuch.pcl

import android.util.Log
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject

/**
 * 阿里云语音识别SDK认证工具类
 */
object Auth {
    private const val TAG = "Auth"
    
    // 认证信息
    private var appKey = ""
    private var token = ""
    private var accessKey = ""
    private var accessKeySecret = ""
    private var stsToken = ""
    
    // 票据获取方式
    enum class GetTicketMethod {
        GET_TOKEN_FROM_SERVER_FOR_ONLINE_FEATURES,
        GET_TOKEN_IN_CLIENT_FOR_ONLINE_FEATURES,
        GET_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES,
        GET_STS_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES
    }

    /**
     * 设置AppKey
     */
    fun setAppKey(appKey: String) {
        Auth.appKey = appKey
    }

    /**
     * 设置Token
     */
    fun setToken(token: String) {
        Auth.token = token
    }

    /**
     * 设置AccessKey
     */
    fun setAccessKey(accessKey: String) {
        Auth.accessKey = accessKey
    }

    /**
     * 设置AccessKeySecret
     */
    fun setAccessKeySecret(accessKeySecret: String) {
        Auth.accessKeySecret = accessKeySecret
    }

    /**
     * 设置STS Token
     */
    fun setStsToken(stsToken: String) {
        Auth.stsToken = stsToken
    }

    /**
     * 获取认证票据
     */
    fun getTicket(method: GetTicketMethod): JSONObject {
        val object_data = JSONObject()
        try {
            when (method) {
                GetTicketMethod.GET_TOKEN_FROM_SERVER_FOR_ONLINE_FEATURES -> {
                    // 此处应向应用服务器请求Token
                    // 示例代码仅作参考，实际实现需要根据应用服务器的接口进行调整
                    Log.w(TAG, "在实际产品中，应当向应用服务器请求Token")
                    object_data.put("app_key", appKey)
                    object_data.put("token", token)
                }
                GetTicketMethod.GET_TOKEN_IN_CLIENT_FOR_ONLINE_FEATURES -> {
                    // 直接在客户端使用Token
                    Log.w(TAG, "在实际产品中，不建议在客户端直接使用Token")
                    object_data.put("app_key", appKey)
                    object_data.put("token", token)
                }
                GetTicketMethod.GET_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES -> {
                    // 直接在客户端使用AccessKey
                    Log.w(TAG, "在实际产品中，不建议在客户端直接使用AccessKey")
                    object_data.put("app_key", appKey)
                    object_data.put("ak_id", accessKey)
                    object_data.put("ak_secret", accessKeySecret)
                }
                GetTicketMethod.GET_STS_ACCESS_IN_CLIENT_FOR_ONLINE_FEATURES -> {
                    // 直接在客户端使用STS Token
                    Log.w(TAG, "在实际产品中，不建议在客户端直接使用STS Token")
                    object_data.put("app_key", appKey)
                    object_data.put("ak_id", accessKey)
                    object_data.put("ak_secret", accessKeySecret)
                    object_data.put("sts_token", stsToken)
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return object_data
    }

    /**
     * 如果需要，刷新Token
     */
    fun refreshTokenIfNeed(dialog_param: JSONObject, distanceExpireTime_s: Long): JSONObject {
        // 此处应实现token过期检查和刷新逻辑
        // 示例代码仅作参考，实际实现需要根据应用服务器的接口进行调整
        
        // 如果token即将过期，刷新token
        val currentTime = System.currentTimeMillis() / 1000
        val expireTime = 0L // 实际应用中应该从token中获取过期时间
        
        if (expireTime > 0 && (expireTime - currentTime) < distanceExpireTime_s) {
            Log.i(TAG, "Token will expire soon, refreshing...")
            
            try {
                val object_data = getTicket(GetTicketMethod.GET_TOKEN_FROM_SERVER_FOR_ONLINE_FEATURES)
                
                if (object_data.containsKey("token")) {
                    val newToken = object_data.getString("token")
                    dialog_param.put("token", newToken)
                    Log.i(TAG, "Token refreshed")
                } else {
                    Log.e(TAG, "Failed to refresh token")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing token: ${e.message}")
            }
        }
        
        return dialog_param
    }
} 