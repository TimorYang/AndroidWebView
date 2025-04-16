package com.teemoyang.androidwebview

import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import java.util.*

/**
 * Beacon设备模型类，支持解析iBeacon和Eddystone格式
 */
@Parcelize
data class BeaconModel(
    val device: BluetoothDevice,
    val rssi: Int,
    val address: String,
    val name: String?,
    val scanRecord: ByteArray?,
    val beaconType: BeaconType = BeaconType.UNKNOWN,
    val uuid: String = "",
    val major: Int = 0,
    val minor: Int = 0,
    val txPower: Int = 0,
    val distance: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    enum class BeaconType {
        IBEACON,
        EDDYSTONE,
        UNKNOWN
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BeaconModel

        if (address != other.address) return false

        return true
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    /**
     * 转换为JSON格式
     */
    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("address", address)
            json.put("name", name ?: "Unknown")
            json.put("rssi", rssi)
            json.put("beaconType", beaconType.name)
            json.put("uuid", uuid)
            json.put("major", major)
            json.put("minor", minor)
            json.put("txPower", txPower)
            json.put("distance", String.format("%.2f", distance))
            json.put("timestamp", timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating JSON: ${e.message}")
        }
        return json
    }

    companion object {
        private const val TAG = "BeaconModel"
        private const val IBEACON_PREFIX = 0x0215
        private const val IBEACON_MANUFACTURER_ID = 0x004C // Apple's company identifier

        /**
         * 解析蓝牙广播数据，生成Beacon对象
         */
        fun parse(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?): BeaconModel? {
            if (scanRecord == null || scanRecord.size < 24) {
                return null
            }

            val name = device.name
            val address = device.address

            try {
                // 检查是否是iBeacon
                val iBeacon = parseIBeacon(device, rssi, scanRecord)
                if (iBeacon != null) {
                    return iBeacon
                }

                // 检查是否是Eddystone
                val eddystone = parseEddystone(device, rssi, scanRecord)
                if (eddystone != null) {
                    return eddystone
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing beacon: ${e.message}")
            }

            // 如果都不匹配，返回普通蓝牙设备
            return BeaconModel(
                device = device,
                rssi = rssi,
                address = address,
                name = name,
                scanRecord = scanRecord,
                beaconType = BeaconType.UNKNOWN
            )
        }

        /**
         * 解析iBeacon格式数据
         */
        private fun parseIBeacon(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray): BeaconModel? {
            var offset = 0
            while (offset < scanRecord.size - 2) {
                val length = scanRecord[offset++].toInt() and 0xFF
                if (length == 0) break

                val type = scanRecord[offset++].toInt() and 0xFF
                
                // 检查是否是制造商特定数据
                if (type == 0xFF && length >= 4) {
                    val manufacturerId = ((scanRecord[offset].toInt() and 0xFF) shl 8) or (scanRecord[offset + 1].toInt() and 0xFF)
                    
                    // 检查是否是苹果公司ID
                    if (manufacturerId == IBEACON_MANUFACTURER_ID) {
                        // 检查是否有iBeacon前缀
                        val iBeaconPrefix = ((scanRecord[offset + 2].toInt() and 0xFF) shl 8) or (scanRecord[offset + 3].toInt() and 0xFF)
                        if (iBeaconPrefix == IBEACON_PREFIX && length >= 26) {
                            // 解析UUID (16字节)
                            val uuidBytes = ByteArray(16)
                            System.arraycopy(scanRecord, offset + 4, uuidBytes, 0, 16)
                            val uuid = formatUUID(uuidBytes)
                            
                            // 解析Major (2字节)
                            val major = ((scanRecord[offset + 20].toInt() and 0xFF) shl 8) or (scanRecord[offset + 21].toInt() and 0xFF)
                            
                            // 解析Minor (2字节)
                            val minor = ((scanRecord[offset + 22].toInt() and 0xFF) shl 8) or (scanRecord[offset + 23].toInt() and 0xFF)
                            
                            // 解析发射功率 (1字节)
                            val txPower = scanRecord[offset + 24].toInt() and 0xFF - 256
                            
                            // 计算大致距离
                            val distance = calculateDistance(txPower, rssi)
                            
                            return BeaconModel(
                                device = device,
                                rssi = rssi,
                                address = device.address,
                                name = device.name,
                                scanRecord = scanRecord,
                                beaconType = BeaconType.IBEACON,
                                uuid = uuid,
                                major = major,
                                minor = minor,
                                txPower = txPower,
                                distance = distance
                            )
                        }
                    }
                }
                
                offset += length - 1
            }
            
            return null
        }

        /**
         * 解析Eddystone格式数据
         */
        private fun parseEddystone(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray): BeaconModel? {
            var offset = 0
            while (offset < scanRecord.size - 2) {
                val length = scanRecord[offset++].toInt() and 0xFF
                if (length == 0) break

                val type = scanRecord[offset++].toInt() and 0xFF
                
                // 检查是否是服务数据
                if (type == 0x16 && length >= 4) {
                    // Eddystone UUID: 0xFEAA
                    val serviceUUID = ((scanRecord[offset].toInt() and 0xFF) shl 8) or (scanRecord[offset + 1].toInt() and 0xFF)
                    if (serviceUUID == 0xFEAA) {
                        // 这是Eddystone格式，根据帧类型进行进一步解析
                        val frameType = scanRecord[offset + 2].toInt() and 0xFF
                        
                        // 简单处理，这里不详细解析各类型的Eddystone
                        val uuid = "Eddystone-" + when (frameType) {
                            0x00 -> "UID"
                            0x10 -> "URL"
                            0x20 -> "TLM"
                            0x30 -> "EID"
                            else -> "Unknown"
                        }
                        
                        // 获取发射功率
                        val txPower = scanRecord[offset + 3].toInt() and 0xFF - 256
                        val distance = calculateDistance(txPower, rssi)
                        
                        return BeaconModel(
                            device = device,
                            rssi = rssi,
                            address = device.address,
                            name = device.name,
                            scanRecord = scanRecord,
                            beaconType = BeaconType.EDDYSTONE,
                            uuid = uuid,
                            txPower = txPower,
                            distance = distance
                        )
                    }
                }
                
                offset += length - 1
            }
            
            return null
        }

        /**
         * 格式化UUID字节数组为标准UUID字符串
         */
        private fun formatUUID(uuidBytes: ByteArray): String {
            val hexString = StringBuilder()
            for (i in uuidBytes.indices) {
                val hex = Integer.toHexString(uuidBytes[i].toInt() and 0xFF)
                if (hex.length == 1) {
                    hexString.append('0')
                }
                hexString.append(hex)
                
                // 添加UUID分隔符
                when (i) {
                    3, 5, 7, 9 -> hexString.append('-')
                }
            }
            return hexString.toString()
        }

        /**
         * 使用RSSI和发射功率计算大致距离
         */
        private fun calculateDistance(txPower: Int, rssi: Int): Double {
            if (rssi == 0) {
                return -1.0
            }
            
            val ratio = rssi * 1.0 / txPower
            return if (ratio < 1.0) {
                Math.pow(ratio, 10.0)
            } else {
                0.89976 * Math.pow(ratio, 7.7095) + 0.111
            }
        }
    }
} 