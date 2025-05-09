package com.joysuch.pcl.data

import android.content.Context
import android.content.SharedPreferences
import com.joysuch.pcl.model.UserData

/**
 * 用户会话管理类，负责存储和获取用户登录信息
 */
object UserSession {
    private const val PREF_NAME = "user_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_NICK_NAME = "nick_name"
    private const val KEY_ROLE = "role"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_PERMISSION_ID = "permission_id"
    private const val KEY_BUILDING_ID = "building_id"
    // 添加语音token相关常量
    private const val KEY_SPEECH_TOKEN = "speech_token"
    private const val KEY_SPEECH_TOKEN_EXPIRE_TIME = "speech_token_expire_time"
    
    private lateinit var prefs: SharedPreferences
    
    /**
     * 初始化用户会话
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 保存用户登录信息
     */
    fun saveUserData(userData: UserData) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_NAME, userData.userName)
            putString(KEY_NICK_NAME, userData.nickName)
            putString(KEY_ROLE, userData.role)
            putString(KEY_DEVICE_ID, userData.deviceId)
            putString(KEY_PERMISSION_ID, userData.permissionId)
            putString(KEY_BUILDING_ID, userData.buildingId)
        }.apply()
    }
    
    /**
     * 保存语音token信息
     */
    fun saveSpeechToken(token: String, expireTime: Long) {
        prefs.edit().apply {
            putString(KEY_SPEECH_TOKEN, token)
            putLong(KEY_SPEECH_TOKEN_EXPIRE_TIME, expireTime)
        }.apply()
    }
    
    /**
     * 获取语音token
     */
    fun getSpeechToken(): String? = prefs.getString(KEY_SPEECH_TOKEN, null)
    
    /**
     * 获取语音token过期时间
     */
    fun getSpeechTokenExpireTime(): Long = prefs.getLong(KEY_SPEECH_TOKEN_EXPIRE_TIME, 0)
    
    /**
     * 检查语音token是否有效
     * @param bufferTime 提前多少秒判定为过期，默认300秒(5分钟)
     */
    fun isSpeechTokenValid(bufferTime: Long = 300): Boolean {
        val token = getSpeechToken()
        val expireTime = getSpeechTokenExpireTime()
        val currentTime = System.currentTimeMillis() / 1000
        
        return token != null && expireTime > 0 && currentTime < (expireTime - bufferTime)
    }
    
    /**
     * 清除用户登录信息
     */
    fun clearUserData() {
        prefs.edit().clear().apply()
    }
    
    /**
     * 清除登录信息 (别名方法，与clearUserData功能相同)
     */
    fun clearLoginInfo() {
        clearUserData()
    }
    
    /**
     * 用户是否已登录
     */
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    
    /**
     * 获取用户角色
     */
    fun getRole(): String = prefs.getString(KEY_ROLE, "") ?: ""
    
    /**
     * 获取用户名
     */
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    
    /**
     * 获取昵称
     */
    fun getNickName(): String = prefs.getString(KEY_NICK_NAME, "") ?: ""
    
    /**
     * 获取设备ID
     */
    fun getDeviceId(): String = prefs.getString(KEY_DEVICE_ID, "") ?: ""
    
    /**
     * 获取权限ID
     */
    fun getPermissionId(): String = prefs.getString(KEY_PERMISSION_ID, "") ?: ""
    
    /**
     * 获取建筑ID
     */
    fun getBuildingId(): String = prefs.getString(KEY_BUILDING_ID, "") ?: ""
    
    /**
     * 获取用户显示名称（优先使用昵称，如果没有昵称则使用用户名）
     */
    fun getDisplayName(): String {
        val nickName = getNickName()
        return if (nickName.isNotEmpty()) nickName else getUserName()
    }
    
    /**
     * 获取完整的用户数据对象
     */
    fun getUserData(): UserData {
        return UserData(
            userName = getUserName(),
            nickName = getNickName(),
            phone = null,
            buildingId = getBuildingId(),
            role = getRole(),
            loginTime = "",
            permissionId = getPermissionId(),
            deviceId = getDeviceId(),
            userType = getRole() // 使用role作为userType
        )
    }
} 