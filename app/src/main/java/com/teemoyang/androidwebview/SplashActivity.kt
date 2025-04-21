package com.teemoyang.androidwebview

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.teemoyang.androidwebview.data.UserSession

/**
 * 启动页面
 * 用于展示应用启动画面，并检查用户是否已登录
 */
class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DELAY = 1000L // 启动页面显示时间（毫秒）
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // 使用Handler延迟执行跳转，以便显示启动页面
        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatusAndNavigate()
        }, SPLASH_DELAY)
    }
    
    /**
     * 检查用户登录状态并跳转到相应页面
     */
    private fun checkLoginStatusAndNavigate() {
        if (UserSession.isLoggedIn()) {
            // 用户已登录，直接进入主界面
            Log.d(TAG, "用户已登录，跳转到MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // 用户未登录，跳转到登录页面
            Log.d(TAG, "用户未登录，跳转到LoginActivity")
            startActivity(Intent(this, LoginActivity::class.java))
        }
        
        // 结束当前Activity
        finish()
    }
} 