package com.teemoyang.androidwebview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.altbeacon.beacon.Beacon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 蓝牙信标适配器
 * 使用AltBeacon库的Beacon类
 */
class BeaconAdapter : RecyclerView.Adapter<BeaconAdapter.BeaconViewHolder>() {

    private val beacons = mutableListOf<Beacon>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val lastSeenMap = mutableMapOf<String, Date>() // 记录每个Beacon最后一次扫描的时间

    /**
     * 更新信标列表
     */
    fun updateBeacons(newBeacons: List<Beacon>) {
        val currentTime = Date()
        // 更新最后发现时间
        newBeacons.forEach { beacon ->
            val key = getBeaconKey(beacon)
            lastSeenMap[key] = currentTime
        }
        
        beacons.clear()
        beacons.addAll(newBeacons)
        notifyDataSetChanged()
    }

    /**
     * 更新单个信标
     */
    fun updateBeacon(beacon: Beacon) {
        val key = getBeaconKey(beacon)
        lastSeenMap[key] = Date() // 更新最后发现时间
        
        val index = beacons.indexOfFirst { getBeaconKey(it) == key }
        
        if (index >= 0) {
            beacons[index] = beacon
            notifyItemChanged(index)
        } else {
            beacons.add(beacon)
            // 按信号强度排序
            beacons.sortByDescending { it.rssi }
            notifyDataSetChanged()
        }
    }
    
    /**
     * 获取当前信标列表
     */
    fun getBeacons(): List<Beacon> {
        return beacons.toList()
    }
    
    /**
     * 生成信标的唯一键
     */
    private fun getBeaconKey(beacon: Beacon): String {
        return "${beacon.bluetoothAddress ?: "unknown"}_${beacon.id1}_${beacon.id2}_${beacon.id3}"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeaconViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_beacon, parent, false
        )
        return BeaconViewHolder(view)
    }

    override fun onBindViewHolder(holder: BeaconViewHolder, position: Int) {
        val beacon = beacons[position]
        holder.bind(beacon)
    }

    override fun getItemCount(): Int = beacons.size

    /**
     * 信标视图持有者
     */
    inner class BeaconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMacAddress: TextView = itemView.findViewById(R.id.tvMacAddress)
        private val tvUuid: TextView = itemView.findViewById(R.id.tvUuid)
        private val tvMajor: TextView = itemView.findViewById(R.id.tvMajor)
        private val tvMinor: TextView = itemView.findViewById(R.id.tvMinor)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvRssi)
        private val tvTxPower: TextView = itemView.findViewById(R.id.tvTxPower)
        private val tvLastSeen: TextView = itemView.findViewById(R.id.tvLastSeen)

        /**
         * 绑定信标数据
         */
        fun bind(beacon: Beacon) {
            tvMacAddress.text = beacon.bluetoothAddress ?: "未知"
            tvUuid.text = formatUuid(beacon.id1.toString())
            tvMajor.text = beacon.id2.toInt().toString()
            tvMinor.text = beacon.id3.toInt().toString()
            
            // 距离显示：如果距离无效（小于0）则显示"-"
            val distanceText = if (beacon.distance < 0) {
                "-"
            } else {
                String.format("%.2f m", beacon.distance)
            }
            tvDistance.text = distanceText
            
            // 信号强度
            tvRssi.text = "${beacon.rssi} dBm"
            tvTxPower.text = "${beacon.txPower} dBm"
            
            // 最后发现时间
            val key = getBeaconKey(beacon)
            val lastSeen = lastSeenMap[key] ?: Date()
            tvLastSeen.text = formatLastSeen(lastSeen)
        }
        
        /**
         * 格式化UUID (缩短显示)
         */
        private fun formatUuid(uuid: String): String {
            return if (uuid.length > 8) {
                "${uuid.substring(0, 8)}..."
            } else {
                uuid
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