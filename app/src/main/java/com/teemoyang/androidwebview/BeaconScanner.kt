package com.teemoyang.androidwebview

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.*
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.distance.ModelSpecificDistanceCalculator
import org.altbeacon.beacon.distance.AndroidModel
import org.altbeacon.beacon.BeaconManager

/**
 * 信标扫描器
 * 用于扫描和解析周围的蓝牙信标设备
 */
class BeaconScanner(private val activity: AppCompatActivity) {
    
    companion object {
        private const val TAG = "BeaconScanner"
        private const val SCAN_PERIOD = 60000L  // 扫描时间增加到60秒
        private const val PERMISSION_REQUEST_CODE = 100 // 权限请求码
        
        // 蓝牙权限
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    // 修改接口名以匹配Activity中的使用
    interface ScanListener {
        fun onBeaconFound(beacon: Beacon)
        fun onScanStart()
        fun onScanStop()
        fun onError(message: String)
    }
    
    private var listener: ScanListener? = null
    private var isScanning = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private val beaconManager: BeaconManager by lazy { BeaconManager.getInstanceForApplication(activity) }
    
    // 蓝牙开启请求
    private val enableBluetoothLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startScanning()
        } else {
            listener?.onError("蓝牙未开启")
        }
    }
    
    // 权限请求
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                allGranted = false
                return@forEach
            }
        }
        
        if (allGranted) {
            initializeBluetooth()
        } else {
            listener?.onError("缺少必要的蓝牙权限")
        }
    }
    
    // 扫描回调
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processResult(result)
        }
        
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { processResult(it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            listener?.onError("扫描失败，错误码: $errorCode")
            listener?.onScanStop()
        }
    }
    
    fun setScanListener(listener: ScanListener) {
        this.listener = listener
    }
    
    fun startScan() {
        if (isScanning) return
        
        // 检查权限
        if (!hasRequiredPermissions()) {
            requestPermissions()
            return
        }
        
        initializeBluetooth()
    }
    
    fun stopScan() {
        if (!isScanning) return
        
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            listener?.onScanStop()
        } catch (e: Exception) {
            Log.e(TAG, "停止扫描出错: ${e.message}")
        }
        
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * 处理权限请求结果
     * @param requestCode 请求码
     * @param permissions 权限
     * @param grantResults 授权结果
     * @return 是否已获得所有必要权限
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                return true
            } else {
                listener?.onError("缺少必要的蓝牙权限")
                return false
            }
        }
        return false
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        // 尝试使用launcher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            } catch (e: Exception) {
                // 如果launcher失败，回退到传统方法
                ActivityCompat.requestPermissions(
                    activity,
                    REQUIRED_PERMISSIONS,
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            // 低于M版本默认授予权限
            initializeBluetooth()
        }
    }
    
    private fun initializeBluetooth() {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null) {
            listener?.onError("此设备不支持蓝牙")
            return
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            // 请求开启蓝牙
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            startScanning()
        }
    }
    
    /**
     * 实际执行蓝牙扫描的内部方法
     */
    private fun startScanning() {
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bluetoothLeScanner == null) {
            listener?.onError("蓝牙LE扫描器不可用")
            return
        }
        
        try {
            // 设置扫描模式
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            // 可选：设置过滤器，例如只扫描特定UUID的设备
            // val filters = listOf<ScanFilter>()
            
            // 开始扫描
            bluetoothLeScanner?.startScan(null, settings, scanCallback)
            isScanning = true
            listener?.onScanStart()
            
            // 设置超时后重新开始扫描
            handler.postDelayed({
                if (isScanning) {
                    // 暂停扫描
                    bluetoothLeScanner?.stopScan(scanCallback)
                    
                    // 短暂延迟后重新开始扫描
                    handler.postDelayed({
                        if (isScanning) { // 确认用户没有手动停止
                            bluetoothLeScanner?.startScan(null, settings, scanCallback)
                        }
                    }, 1000) // 延迟1秒后重新开始
                }
            }, SCAN_PERIOD)
            
        } catch (e: Exception) {
            Log.e(TAG, "启动扫描出错: ${e.message}")
            listener?.onError("启动扫描出错: ${e.message}")
        }
    }
    
    private fun processResult(result: ScanResult) {
        try {
            val device = result.device
            val rssi = result.rssi
            val scanRecord = result.scanRecord ?: return
            
            // 解析设备数据，识别为iBeacon
            val manufacturerData = scanRecord.manufacturerSpecificData
            if (manufacturerData != null) {
                for (i in 0 until manufacturerData.size()) {
                    val manufacturerId = manufacturerData.keyAt(i)
                    val data = manufacturerData.get(manufacturerId)
                    
                    // 苹果公司ID为0x004C
                    if (manufacturerId == 0x004C && data != null && data.size >= 23) {
                        // iBeacon格式检查
                        if (data[0] == 0x02.toByte() && data[1] == 0x15.toByte()) {
                            // 解析UUID
                            val uuidBytes = ByteArray(16)
                            System.arraycopy(data, 2, uuidBytes, 0, 16)
                            val uuid = formatUuid(uuidBytes)
                            
                            // 解析Major和Minor
                            val major = ((data[18].toInt() and 0xff) shl 8) or (data[19].toInt() and 0xff)
                            val minor = ((data[20].toInt() and 0xff) shl 8) or (data[21].toInt() and 0xff)
                            
                            // 测量功率
                            val txPower = data[22].toInt()
                            
                            // 创建Beacon对象
                            val beacon = Beacon.Builder()
                                .setId1(uuid)
                                .setId2(major.toString())
                                .setId3(minor.toString())
                                .setManufacturer(manufacturerId)
                                .setTxPower(txPower)
                                .setRssi(rssi)
                                .setBluetoothAddress(device.address)
                                .build()
                                
                            // 记录发现的iBeacon日志
                            Log.d(TAG, "发现iBeacon设备: UUID=${uuid}, Major=${major}, Minor=${minor}, MAC=${device.address}")
                            
                            listener?.onBeaconFound(beacon)
                            return
                        }
                    }
                }
            }
            
            // 如果不是iBeacon格式，则不处理
            // 记录未识别设备的日志，但不回调
            Log.d(TAG, "扫描到非iBeacon设备: ${device.name ?: "未命名"}, MAC: ${device.address}, 已忽略")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理扫描结果出错: ${e.message}")
        }
    }
    
    private fun formatUuid(uuidBytes: ByteArray): String {
        val bb = ByteBuffer.wrap(uuidBytes)
        val mostSigBits = bb.long
        val leastSigBits = bb.long
        return UUID(mostSigBits, leastSigBits).toString()
    }
} 