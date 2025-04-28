package com.joysuch.pcl

import android.net.wifi.ScanResult

/**
 * WiFi工具类，处理WiFi相关计算和信息获取
 */
object WifiUtils {
    
    /**
     * 获取信号强度百分比
     * @param rssi WiFi信号强度，通常为负值，范围约为-100 dBm（信号最弱）到 -30 dBm（信号最强）
     * @return 信号强度百分比（0-100）
     */
    fun calculateSignalLevel(rssi: Int): Int {
        return when {
            rssi >= -50 -> 100
            rssi >= -60 -> 80
            rssi >= -70 -> 60
            rssi >= -80 -> 40
            rssi >= -90 -> 20
            else -> 0
        }
    }
    
    /**
     * 根据频率获取WiFi信道
     * @param frequency WiFi频率（MHz）
     * @return WiFi信道
     */
    fun getChannelFromFrequency(frequency: Int): Int {
        return when {
            // 2.4 GHz频段
            frequency >= 2412 && frequency <= 2484 -> {
                // 2.4 GHz信道的特殊计算
                if (frequency == 2484) {
                    14  // 日本的2.4 GHz信道14
                } else {
                    ((frequency - 2412) / 5) + 1  // 2.4 GHz信道1-13
                }
            }
            // 5 GHz频段
            frequency >= 5170 && frequency <= 5825 -> {
                when {
                    frequency == 5170 -> 34
                    frequency == 5180 -> 36
                    frequency == 5190 -> 38
                    frequency == 5200 -> 40
                    frequency == 5210 -> 42
                    frequency == 5220 -> 44
                    frequency == 5230 -> 46
                    frequency == 5240 -> 48
                    frequency == 5250 -> 50
                    frequency == 5260 -> 52
                    frequency == 5270 -> 54
                    frequency == 5280 -> 56
                    frequency == 5290 -> 58
                    frequency == 5300 -> 60
                    frequency == 5310 -> 62
                    frequency == 5320 -> 64
                    frequency == 5500 -> 100
                    frequency == 5520 -> 104
                    frequency == 5540 -> 108
                    frequency == 5560 -> 112
                    frequency == 5580 -> 116
                    frequency == 5600 -> 120
                    frequency == 5620 -> 124
                    frequency == 5640 -> 128
                    frequency == 5660 -> 132
                    frequency == 5680 -> 136
                    frequency == 5700 -> 140
                    frequency == 5720 -> 144
                    frequency == 5745 -> 149
                    frequency == 5765 -> 153
                    frequency == 5785 -> 157
                    frequency == 5805 -> 161
                    frequency == 5825 -> 165
                    // 大约计算一下
                    else -> (frequency - 5000) / 5
                }
            }
            // 6 GHz频段
            frequency >= 5925 && frequency <= 7125 -> {
                val base = 1;
                (frequency - 5925) / 5 + base
            }
            // 其他频段或未知
            else -> -1
        }
    }
    
    /**
     * 获取WiFi安全类型
     * @param scanResult WiFi扫描结果
     * @return 安全类型描述
     */
    fun getSecurityType(scanResult: ScanResult): String {
        val capabilities = scanResult.capabilities ?: ""
        return when {
            capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
            capabilities.contains("WPA2", ignoreCase = true) && capabilities.contains("PSK", ignoreCase = true) -> "WPA2-PSK"
            capabilities.contains("WPA2", ignoreCase = true) && capabilities.contains("EAP", ignoreCase = true) -> "WPA2-企业级"
            capabilities.contains("WPA", ignoreCase = true) && capabilities.contains("PSK", ignoreCase = true) -> "WPA-PSK"
            capabilities.contains("WPA", ignoreCase = true) && capabilities.contains("EAP", ignoreCase = true) -> "WPA-企业级"
            capabilities.contains("WEP", ignoreCase = true) -> "WEP"
            capabilities.contains("ESS", ignoreCase = true) && !capabilities.contains("WPA", ignoreCase = true) && 
                !capabilities.contains("WEP", ignoreCase = true) && !capabilities.contains("WPA2", ignoreCase = true) -> "开放"
            else -> "未知"
        }
    }
    
    /**
     * 判断WiFi是否是5G频段
     * @param frequency WiFi频率（MHz）
     * @return 是否是5G频段
     */
    fun is5GHz(frequency: Int): Boolean {
        return frequency > 4900 && frequency < 5900
    }
    
    /**
     * 判断WiFi是否是2.4G频段
     * @param frequency WiFi频率（MHz）
     * @return 是否是2.4G频段
     */
    fun is24GHz(frequency: Int): Boolean {
        return frequency > 2400 && frequency < 2500
    }
    
    /**
     * 判断WiFi是否是6G频段
     * @param frequency WiFi频率（MHz）
     * @return 是否是6G频段
     */
    fun is6GHz(frequency: Int): Boolean {
        return frequency >= 5925 && frequency <= 7125
    }
    
    /**
     * 获取WiFi频段描述
     * @param frequency WiFi频率（MHz）
     * @return 频段描述
     */
    fun getFrequencyBand(frequency: Int): String {
        return when {
            is24GHz(frequency) -> "2.4 GHz"
            is5GHz(frequency) -> "5 GHz"
            is6GHz(frequency) -> "6 GHz"
            else -> "未知"
        }
    }
} 