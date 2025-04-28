package com.joysuch.pcl

import android.util.Log
import android.util.Base64
import com.alibaba.fastjson.JSON
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.HashMap
import java.util.SimpleTimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 阿里云Token工具类，用于获取NLS服务的Token
 */
object TokenUtil {
    private const val TAG = "TokenUtil"
    private const val TIME_ZONE = "GMT"
    private const val FORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private const val URL_ENCODING = "UTF-8"
    private const val ALGORITHM_NAME = "HmacSHA1"
    private const val ENCODING = "UTF-8"

    // 缓存的token信息
    private var token: String? = null
    private var expireTime: Long = 0

    /**
     * 获取Token - 同步方法，需要在非UI线程中调用
     */
    fun getToken(accessKeyId: String, accessKeySecret: String): String? {
        try {
            // 检查缓存的token是否仍然有效（提前5分钟刷新）
            val currentTime = System.currentTimeMillis() / 1000
            if (token != null && expireTime > 0 && currentTime < (expireTime - 300)) {
                Log.d(TAG, "使用缓存的token，过期时间: ${formatExpireTime(expireTime)}")
                return token
            }

            // 所有请求参数
            val queryParamsMap = HashMap<String, String>()
            queryParamsMap["AccessKeyId"] = accessKeyId
            queryParamsMap["Action"] = "CreateToken"
            queryParamsMap["Version"] = "2019-02-28"
            queryParamsMap["Timestamp"] = getISO8601Time(null)
            queryParamsMap["Format"] = "JSON"
            queryParamsMap["RegionId"] = "cn-shanghai"
            queryParamsMap["SignatureMethod"] = "HMAC-SHA1"
            queryParamsMap["SignatureVersion"] = "1.0"
            queryParamsMap["SignatureNonce"] = getUniqueNonce()

            // 1.构造规范化的请求字符串
            val queryString = canonicalizedQuery(queryParamsMap) ?: return null

            // 2.构造签名字符串
            val method = "GET"  // 发送请求的 HTTP 方法，GET
            val urlPath = "/"   // 请求路径
            val stringToSign = createStringToSign(method, urlPath, queryString) ?: return null

            // 3.计算签名
            val signature = sign(stringToSign, "$accessKeySecret&") ?: return null

            // 4.将签名加入请求字符串
            val queryStringWithSign = "Signature=$signature&$queryString"

            // 5.发送HTTP GET请求，获取token
            processGETRequest(queryStringWithSign)

            return token
        } catch (e: Exception) {
            Log.e(TAG, "获取token异常: ${e.message}", e)
            return null
        }
    }

    /**
     * 获取当前Token的过期时间（Unix时间戳，单位：秒）
     */
    fun getTokenExpireTime(): Long {
        return expireTime
    }

    /**
     * 获取ISO8601格式的时间
     */
    private fun getISO8601Time(date: Date?): String {
        val nowDate = date ?: Date()
        val df = SimpleDateFormat(FORMAT_ISO8601)
        df.timeZone = SimpleTimeZone(0, TIME_ZONE)
        return df.format(nowDate)
    }

    /**
     * 获取UUID
     */
    private fun getUniqueNonce(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * URL编码
     */
    private fun percentEncode(value: String?): String? {
        return value?.let {
            try {
                URLEncoder.encode(it, URL_ENCODING)
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~")
            } catch (e: UnsupportedEncodingException) {
                Log.e(TAG, "URL编码异常: ${e.message}", e)
                null
            }
        }
    }

    /**
     * 将参数排序后，进行规范化设置
     */
    private fun canonicalizedQuery(queryParamsMap: Map<String, String>): String? {
        val sortedKeys = queryParamsMap.keys.toTypedArray()
        Arrays.sort(sortedKeys)

        try {
            val canonicalizedQueryString = StringBuilder()
            for (key in sortedKeys) {
                canonicalizedQueryString.append("&")
                    .append(percentEncode(key)).append("=")
                    .append(percentEncode(queryParamsMap[key]))
            }
            val queryString = canonicalizedQueryString.toString().substring(1)
            Log.d(TAG, "规范化后的请求参数串：$queryString")
            return queryString
        } catch (e: Exception) {
            Log.e(TAG, "UTF-8编码不支持: ${e.message}", e)
            return null
        }
    }

    /**
     * 构造签名字符串
     */
    private fun createStringToSign(method: String, urlPath: String, queryString: String): String? {
        try {
            val strBuilderSign = StringBuilder()
            strBuilderSign.append(method)
            strBuilderSign.append("&")
            strBuilderSign.append(percentEncode(urlPath))
            strBuilderSign.append("&")
            strBuilderSign.append(percentEncode(queryString))
            val stringToSign = strBuilderSign.toString()
            Log.d(TAG, "构造的签名字符串：$stringToSign")
            return stringToSign
        } catch (e: Exception) {
            Log.e(TAG, "UTF-8编码不支持: ${e.message}", e)
            return null
        }
    }

    /**
     * 计算签名
     */
    private fun sign(stringToSign: String, accessKeySecret: String): String? {
        try {
            val mac = Mac.getInstance(ALGORITHM_NAME)
            mac.init(SecretKeySpec(accessKeySecret.toByteArray(charset(ENCODING)), ALGORITHM_NAME))
            val signData = mac.doFinal(stringToSign.toByteArray(charset(ENCODING)))
            
            // 使用Android的Base64替代javax.xml.bind.DatatypeConverter
            val signBase64 = Base64.encodeToString(signData, Base64.NO_WRAP)
            
            Log.d(TAG, "计算得到的签名：$signBase64")
            val signUrlEncode = percentEncode(signBase64)
            Log.d(TAG, "UrlEncode编码后的签名：$signUrlEncode")
            return signUrlEncode
        } catch (e: Exception) {
            when (e) {
                is NoSuchAlgorithmException, is UnsupportedEncodingException, is InvalidKeyException -> {
                    Log.e(TAG, "计算签名异常: ${e.message}", e)
                }
            }
            return null
        }
    }

    /**
     * 发送HTTP GET请求，获取token
     */
    private fun processGETRequest(queryString: String) {
        val url = "https://nls-meta.cn-shanghai.aliyuncs.com/?$queryString"
        Log.d(TAG, "HTTPS请求链接：$url")
        
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
            
        try {
            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            val result = response.body?.string()
            
            if (response.isSuccessful && result != null) {
                val rootObj = JSON.parseObject(result)
                val tokenObj = rootObj.getJSONObject("Token")
                
                if (tokenObj != null) {
                    token = tokenObj.getString("Id")
                    expireTime = tokenObj.getLongValue("ExpireTime")
                    Log.d(TAG, "获取的Token：$token, 有效期时间戳（秒）：$expireTime")
                    
                    // 将10位数的时间戳转换为北京时间
                    val expireDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(expireTime * 1000))
                    Log.d(TAG, "Token有效期的北京时间：$expireDate")
                } else {
                    Log.e(TAG, "获取Token失败: $result")
                }
            } else {
                Log.e(TAG, "提交获取Token请求失败: $result")
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "HTTP请求异常: ${e.message}", e)
        }
    }
    
    /**
     * 格式化过期时间
     */
    private fun formatExpireTime(expireTimeInSeconds: Long): String {
        val currentTime = System.currentTimeMillis() / 1000
        val remainingTime = expireTimeInSeconds - currentTime
        
        return if (remainingTime > 0) {
            "${remainingTime}秒后过期 ($expireTimeInSeconds)"
        } else {
            "已过期"
        }
    }
} 