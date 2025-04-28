package com.joysuch.pcl

import android.util.Log
import java.io.File

/**
 * 工具类，提供各种辅助功能
 */
object Utils {
    private const val TAG = "Utils"

    /**
     * 创建目录
     *
     * @param dirPath 目录路径
     * @return 成功返回true，否则返回false
     */
    fun createDir(dirPath: String): Boolean {
        val dir = File(dirPath)
        if (!dir.exists()) {
            val success = dir.mkdirs()
            if (success) {
                Log.i(TAG, "成功创建目录：$dirPath")
                return true
            } else {
                Log.e(TAG, "创建目录失败：$dirPath")
                return false
            }
        } else {
            Log.i(TAG, "目录已存在：$dirPath")
            return true
        }
    }

    /**
     * 根据错误码获取错误消息
     *
     * @param errorCode 错误码
     * @param actionName 操作名称
     * @return 格式化后的错误消息
     */
    fun getMsgWithErrorCode(errorCode: Int, actionName: String): String {
        return when (errorCode) {
            0 -> "$actionName 成功"
            -1 -> "$actionName 参数错误"
            -2 -> "$actionName 重连错误"
            -3 -> "$actionName 连接失败"
            -4 -> "$actionName 录音失败"
            -5 -> "$actionName 网络不可用"
            -6 -> "$actionName 网络超时"
            -7 -> "$actionName 网络异常"
            -8 -> "$actionName 服务器响应无效"
            -9 -> "$actionName 授权失败，请检查token和appkey"
            -10 -> "$actionName 账户余额不足"
            -11 -> "$actionName SDK未初始化"
            -12 -> "$actionName SDK已释放"
            -13 -> "$actionName 对话进行中"
            -14 -> "$actionName SDK版本不匹配"
            -15 -> "$actionName 开始对话调用未完成"
            else -> "$actionName 错误: $errorCode"
        }
    }
} 