package com.teemoyang.androidwebview

import android.net.wifi.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WiFi网络列表适配器
 */
class WiFiAdapter : RecyclerView.Adapter<WiFiAdapter.WiFiViewHolder>() {

    private val wifiList = mutableListOf<ScanResult>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val lastSeenMap = mutableMapOf<String, Date>() // 记录每个WiFi最后一次扫描的时间

    /**
     * 更新WiFi列表
     */
    fun updateWifiList(newWifiList: List<ScanResult>) {
        val currentTime = Date()
        // 更新最后发现时间
        newWifiList.forEach { wifi ->
            lastSeenMap[wifi.BSSID] = currentTime
        }
        
        wifiList.clear()
        wifiList.addAll(newWifiList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WiFiViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_wifi, parent, false
        )
        return WiFiViewHolder(view)
    }

    override fun onBindViewHolder(holder: WiFiViewHolder, position: Int) {
        val wifi = wifiList[position]
        holder.bind(wifi)
    }

    override fun getItemCount(): Int = wifiList.size

    /**
     * WiFi视图持有者
     */
    inner class WiFiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSSID: TextView = itemView.findViewById(R.id.tvSSID)
        private val tvBSSID: TextView = itemView.findViewById(R.id.tvBSSID)
        private val tvSignalStrength: TextView = itemView.findViewById(R.id.tvSignalStrength)
        private val tvChannel: TextView = itemView.findViewById(R.id.tvChannel)
        private val tvSecurity: TextView = itemView.findViewById(R.id.tvSecurity)
        private val tvFrequency: TextView = itemView.findViewById(R.id.tvFrequency)
        private val tvLastSeen: TextView = itemView.findViewById(R.id.tvLastSeen)
        private val ivSignalStrength: ImageView = itemView.findViewById(R.id.ivSignalStrength)

        /**
         * 绑定WiFi数据
         */
        fun bind(wifi: ScanResult) {
            // SSID（网络名称）
            val ssid = if (wifi.SSID.isNullOrEmpty()) "<隐藏网络>" else wifi.SSID
            tvSSID.text = ssid
            
            // BSSID（MAC地址）
            tvBSSID.text = wifi.BSSID
            
            // 信号强度（RSSI）
            val level = WifiUtils.calculateSignalLevel(wifi.level)
            tvSignalStrength.text = "${wifi.level} dBm (${level}%)"
            
            // 频率和信道
            val channel = WifiUtils.getChannelFromFrequency(wifi.frequency)
            tvChannel.text = "信道: $channel"
            tvFrequency.text = "频率: ${wifi.frequency} MHz"
            
            // 安全类型
            val securityType = WifiUtils.getSecurityType(wifi)
            tvSecurity.text = securityType
            
            // 最后发现时间
            val lastSeen = lastSeenMap[wifi.BSSID] ?: Date()
            tvLastSeen.text = formatLastSeen(lastSeen)
            
            // 信号强度图标
            ivSignalStrength.setImageResource(getSignalStrengthIcon(level))
        }
        
        /**
         * 根据信号强度获取对应的图标
         */
        private fun getSignalStrengthIcon(level: Int): Int {
            return when {
                level >= 80 -> R.drawable.ic_signal_4
                level >= 60 -> R.drawable.ic_signal_3
                level >= 40 -> R.drawable.ic_signal_2
                level >= 20 -> R.drawable.ic_signal_1
                else -> R.drawable.ic_signal_0
            }
        }
        
        /**
         * 格式化最后发现时间
         */
        private fun formatLastSeen(lastSeen: Date): String {
            val now = System.currentTimeMillis()
            val diff = now - lastSeen.time
            
            return when {
                diff < 10000 -> "刚刚" // 10秒内
                diff < 60000 -> "${diff / 1000}秒前" // 1分钟内
                else -> dateFormat.format(lastSeen) // 显示时间
            }
        }
    }
} 