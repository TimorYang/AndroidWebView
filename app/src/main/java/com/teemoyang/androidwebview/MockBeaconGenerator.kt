package com.teemoyang.androidwebview

import android.os.Handler
import android.os.Looper
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.Identifier
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 模拟信标生成器
 * 用于在没有实际蓝牙信标时生成模拟数据进行测试
 */
class MockBeaconGenerator {
    
    interface MockBeaconListener {
        fun onBeaconDetected(beacon: Beacon)
        fun onError(message: String) = Unit
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var isGenerating = false
    private var listener: MockBeaconListener? = null
    private val mockBeacons = mutableListOf<Beacon>()
    private val random = Random
    
    // 定义一些模拟的UUID以保持一致性
    private val mockUuids = listOf(
        "f7826da6-4fa2-4e98-8024-bc5b71e0893e", // iBeacon
        "00000000-0000-1000-8000-00805f9b34fb", // 通用UUID
        "0000fe9a-0000-1000-8000-00805f9b34fb", // Eddystone
        "e2c56db5-dffb-48d2-b060-d0f5a71096e0", // AltBeacon
        "74278bda-b644-4520-8f0c-720eaf059935"  // 自定义UUID
    )
    
    // 定义一些模拟的MAC地址
    private val mockMacs = listOf(
        "00:11:22:33:44:55",
        "AA:BB:CC:DD:EE:FF",
        "11:22:33:44:55:66",
        "22:33:44:55:66:77",
        "33:44:55:66:77:88"
    )
    
    fun setListener(listener: MockBeaconListener) {
        this.listener = listener
    }
    
    fun startGenerating(count: Int = 5) {
        if (isGenerating) return
        isGenerating = true
        
        // 初始化几个模拟信标数据
        if (mockBeacons.isEmpty()) {
            initMockBeacons(count)
        }
        
        handler.post(generateRunnable)
    }
    
    fun stopGenerating() {
        isGenerating = false
        handler.removeCallbacks(generateRunnable)
    }
    
    private val generateRunnable = object : Runnable {
        override fun run() {
            if (!isGenerating) return
            
            // 随机更新0-3个已有信标数据
            val updateCount = random.nextInt(4) // 0-3个
            for (i in 0 until updateCount) {
                if (mockBeacons.isNotEmpty()) {
                    val index = random.nextInt(mockBeacons.size)
                    val beacon = mockBeacons[index]
                    
                    // 创建一个新的Beacon实例，但有一些值会波动
                    val newRssi = fluctuateRssi(beacon.rssi)
                    
                    // 创建一个新的Beacon Builder
                    val builder = Beacon.Builder()
                        .setId1(beacon.id1.toString())
                        .setId2(beacon.id2.toString())
                        .setId3(beacon.id3.toString())
                        .setManufacturer(0x004C) // Apple
                        .setTxPower(beacon.txPower)
                        .setRssi(newRssi)
                        .setBluetoothAddress(beacon.bluetoothAddress)
                    
                    // 创建新的Beacon
                    val updatedBeacon = builder.build()
                    
                    mockBeacons[index] = updatedBeacon
                    listener?.onBeaconDetected(updatedBeacon)
                }
            }
            
            // 随机概率添加一个新信标
            if (random.nextInt(10) == 0 && mockBeacons.size < 10) { // 10%的概率，最多10个
                val newBeacon = generateRandomBeacon()
                mockBeacons.add(newBeacon)
                listener?.onBeaconDetected(newBeacon)
            }
            
            // 随机概率移除一个信标
            if (random.nextInt(20) == 0 && mockBeacons.size > 3) { // 5%的概率，保留至少3个
                val index = random.nextInt(mockBeacons.size)
                mockBeacons.removeAt(index)
            }
            
            handler.postDelayed(this, 1000 + random.nextLong(2000)) // 1-3秒随机间隔
        }
    }
    
    private fun initMockBeacons(count: Int) {
        // 创建初始信标
        for (i in 0 until count) {
            val newBeacon = generateRandomBeacon()
            mockBeacons.add(newBeacon)
        }
    }
    
    private fun generateRandomBeacon(): Beacon {
        val macAddress = mockMacs[random.nextInt(mockMacs.size)]
        val uuid = mockUuids[random.nextInt(mockUuids.size)]
        val major = random.nextInt(65536).toString() // 0-65535 (16位无符号整数范围)
        val minor = random.nextInt(65536).toString() // 0-65535 (16位无符号整数范围)
        val txPower = -59 - random.nextInt(10) // 通常在-59到-69之间
        val rssi = -55 - random.nextInt(50) // 通常在-55到-105之间
        
        // 创建Beacon
        return Beacon.Builder()
            .setId1(uuid)
            .setId2(major)
            .setId3(minor)
            .setManufacturer(0x004C) // Apple
            .setTxPower(txPower)
            .setRssi(rssi)
            .setBluetoothAddress(macAddress)
            .build()
    }
    
    /**
     * 计算估算距离
     */
    private fun calculateDistance(rssi: Int, txPower: Int): Double {
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
    
    private fun fluctuateRssi(currentRssi: Int): Int {
        // RSSI数值可能上下波动，模拟现实世界的信号强度波动
        val fluctuation = random.nextInt(7) - 3 // -3到+3的波动
        val newRssi = currentRssi + fluctuation
        
        // 确保RSSI在合理范围内
        return max(min(newRssi, -30), -105)
    }
} 