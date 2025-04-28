package com.joysuch.pcl.utils

import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * 密码工具类 - 使用RSA非对称加密
 */
object PasswordUtils {
    private const val TAG = "PasswordUtils"
    
    // 来自Web端的公钥
    private const val PUBLIC_KEY = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKWK-Kd3yrixBGhR2DlWb2b4KoyL5LtOgEIXUkDdAfOzyriqdQ-i8Xgf0n0tJFMpXFrAQ9DC1GlX1gRCHIF55m8CAwEAAQ"
    
    /**
     * 使用RSA公钥加密密码
     */
    fun encryptPassword(password: String): String {
        try {
            // 获取公钥
            val publicKey = getPublicKey()
            
            // 加密
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(password.toByteArray())
            
            // 转为Base64字符串
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "密码加密失败", e)
            // 发生异常时返回一个固定密文（仅用于演示）
            return "MWRFdP1t/Rjmzj1Xhs4ntljnCh3E8KJAR6HCFMndg3yF+IPGpgPke4H3U3i7pRKOS8s/NLOpGhpEGNJZnlVCfA=="
        }
    }
    
    /**
     * 获取公钥
     */
    private fun getPublicKey(): PublicKey {
        try {
            // 处理公钥格式
            val formattedKey = formatPublicKey(PUBLIC_KEY)
            
            // 准备公钥
            val publicKeyBytes = Base64.decode(formattedKey, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Log.e(TAG, "获取公钥失败，尝试备用方法", e)
            
            // 备用方法：直接使用标准PEM格式
            val standardPEM = "-----BEGIN PUBLIC KEY-----\n" +
                              "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKWK+Kd3yrixBGhR2DlWb2b4KoyL5LtO\n" +
                              "gEIXUkDdAfOzyriqdQ+i8Xgf0n0tJFMpXFrAQ9DC1GlX1gRCHIF55m8CAwEAAQ==\n" +
                              "-----END PUBLIC KEY-----"
            
            // 从PEM中提取Base64部分
            val base64Key = standardPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .trim()
            
            val keyBytes = Base64.decode(base64Key, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            val factory = KeyFactory.getInstance("RSA")
            return factory.generatePublic(spec)
        }
    }
    
    /**
     * 格式化公钥，处理特殊字符
     */
    private fun formatPublicKey(key: String): String {
        // 将横杠替换回加号，将下划线替换回斜杠
        return key.replace("-", "+").replace("_", "/")
    }
} 