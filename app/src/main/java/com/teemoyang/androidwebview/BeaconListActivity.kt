package com.teemoyang.androidwebview

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.teemoyang.androidwebview.databinding.ActivityBeaconListBinding
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.Region
import java.util.ArrayList

class BeaconListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBeaconListBinding
    private lateinit var beaconAdapter: BeaconAdapter
    private lateinit var beaconManager: BeaconManager
    private val TAG = "BeaconListActivity"
    private val ALL_BEACONS_REGION = Region("allBeacons", null, null, null)
    private var isScanning = false
    
    // 模拟信标生成器
    private val mockBeaconGenerator = MockBeaconGenerator()
    
    // 模拟数据处理
    private val mockHandler = Handler(Looper.getMainLooper())
    private val MOCK_DATA_INTERVAL = 1000L // 1秒更新一次
    
    // 是否使用模拟数据
    private var useMockData = false

    // 注册权限请求ActivityResultLauncher
    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            if (checkBluetoothStatus()) {
                startScan()
            }
        } else {
            Toast.makeText(
                this,
                "缺少必要权限，无法扫描蓝牙设备",
                Toast.LENGTH_LONG
            ).show()
            binding.switchScan.isChecked = false
        }
    }

    // 注册蓝牙启用请求ActivityResultLauncher
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startScan()
        } else {
            Toast.makeText(
                this,
                "需要启用蓝牙来扫描设备",
                Toast.LENGTH_LONG
            ).show()
            binding.switchScan.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeaconListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupBeaconManager()
        setupMockBeaconGenerator() // 设置模拟信标生成器
    }

    private fun setupUI() {
        beaconAdapter = BeaconAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BeaconListActivity)
            adapter = beaconAdapter
        }

        binding.switchScan.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (useMockData) {
                    startMockBeaconGeneration()
                } else {
                    checkBluetoothAndPermissions()
                }
            } else {
                if (useMockData) {
                    stopMockBeaconGeneration()
                } else {
                    stopBeaconScanning()
                }
            }
        }
        
        // 添加长按功能，切换真实/模拟模式
        binding.switchScan.setOnLongClickListener {
            useMockData = !useMockData
            
            // 如果当前正在扫描，先停止
            if (binding.switchScan.isChecked) {
                if (!useMockData) {
                    stopMockBeaconGeneration()
                    checkBluetoothAndPermissions()
                } else {
                    stopBeaconScanning()
                    startMockBeaconGeneration()
                }
            }
            
            // 提示用户当前模式
            val modeText = if (useMockData) "模拟数据模式" else "真实扫描模式"
            binding.tvScanStatus.text = "已切换到$modeText"
            Toast.makeText(this, "已切换到$modeText", Toast.LENGTH_SHORT).show()
            
            true
        }

        binding.fabSettings.setOnClickListener {
            // 切换模拟/真实模式
            val options = arrayOf("真实扫描模式", "模拟数据模式")
            val currentMode = if (useMockData) 1 else 0
            
            AlertDialog.Builder(this)
                .setTitle("选择扫描模式")
                .setSingleChoiceItems(options, currentMode) { dialog, which ->
                    val newUseMockData = which == 1
                    
                    // 如果模式改变了
                    if (useMockData != newUseMockData) {
                        useMockData = newUseMockData
                        
                        // 如果当前正在扫描，需要重启扫描
                        if (binding.switchScan.isChecked) {
                            if (useMockData) {
                                stopBeaconScanning()
                                startMockBeaconGeneration()
                            } else {
                                stopMockBeaconGeneration()
                                checkBluetoothAndPermissions()
                            }
                        }
                        
                        // 提示用户
                        val modeText = if (useMockData) "模拟数据模式" else "真实扫描模式"
                        binding.tvScanStatus.text = "已切换到$modeText"
                        Toast.makeText(this, "已切换到$modeText", Toast.LENGTH_SHORT).show()
                    }
                    
                    dialog.dismiss()
                }
                .show()
        }

        // 初始状态下空视图显示，RecyclerView隐藏
        updateEmptyView(true)
    }
    
    // 模拟数据生成的Runnable
    private val mockRunnable = object : Runnable {
        override fun run() {
            if (!isScanning || !useMockData) return
            
            mockHandler.postDelayed(this, MOCK_DATA_INTERVAL)
        }
    }
    
    // 设置模拟信标生成器
    private fun setupMockBeaconGenerator() {
        mockBeaconGenerator.setListener(object : MockBeaconGenerator.MockBeaconListener {
            override fun onBeaconDetected(beacon: org.altbeacon.beacon.Beacon) {
                runOnUiThread {
                    // 更新适配器
                    beaconAdapter.updateBeacon(beacon)
                    
                    // 更新计数和空视图
                    val beaconCount = beaconAdapter.getBeacons().size
                    binding.tvBeaconCount.text = "发现设备: $beaconCount"
                    updateEmptyView(beaconCount == 0)
                }
            }
        })
    }

    private fun setupBeaconManager() {
        beaconManager = BeaconManager.getInstanceForApplication(this)
        
        // 设置扫描周期，减少电池消耗
        beaconManager.foregroundScanPeriod = 1100L
        beaconManager.foregroundBetweenScanPeriod = 0L
        
        // 添加iBeacon解析器
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        )

        // 设置扫描回调
        beaconManager.addRangeNotifier(RangeNotifier { beacons, _ ->
            runOnUiThread {
                updateBeaconList(beacons)
            }
        })
    }

    private fun updateBeaconList(beacons: Collection<Beacon>) {
        // 更新UI显示
        val beaconCount = beacons.size
        binding.tvBeaconCount.text = "发现设备: $beaconCount"
        
        // 更新空视图状态
        updateEmptyView(beaconCount == 0)
        
        // 直接更新适配器，无需转换
        beaconAdapter.updateBeacons(beacons.toList())
        
        // Log detected beacons
        for (beacon in beacons) {
            Log.d(TAG, "发现Beacon: ${beacon.id1}, 距离: ${beacon.distance}m, RSSI: ${beacon.rssi}dBm")
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.tvEmptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun checkBluetoothAndPermissions() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_LONG).show()
            binding.switchScan.isChecked = false
            return
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        // Check permissions
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            // Request missing permissions
            requestMultiplePermissions.launch(missingPermissions.toTypedArray())
        } else {
            // All permissions granted, proceed with scanning
            startBeaconScanning()
        }
    }

    private fun startBeaconScanning() {
        if (isScanning) return
        
        if (!hasRequiredPermissions()) {
            requestPermissions()
            return
        }
        
        try {
            beaconManager.startRangingBeacons(ALL_BEACONS_REGION)
            binding.switchScan.isChecked = true
            binding.tvScanStatus.text = "扫描中..."
            isScanning = true
            
            // 更新空视图状态
            updateEmptyView(beaconAdapter.itemCount == 0)
        } catch (e: Exception) {
            Log.e(TAG, "开始扫描失败: ${e.message}", e)
            Toast.makeText(this, "开始扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopBeaconScanning() {
        try {
            beaconManager.stopRangingBeacons(ALL_BEACONS_REGION)
            isScanning = false
            binding.tvScanStatus.text = "未扫描"
            Log.d(TAG, "Stopped ranging beacons")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to stop ranging beacons", e)
        }
    }
    
    private fun startMockBeaconGeneration() {
        if (isScanning) return
        
        // 设置模拟数据生成
        isScanning = true
        binding.switchScan.isChecked = true
        binding.tvScanStatus.text = "使用模拟数据中..."
        
        // 开始生成模拟数据
        mockBeaconGenerator.startGenerating(8) // 生成8个模拟信标
        
        // 更新空视图状态
        updateEmptyView(beaconAdapter.itemCount == 0)
    }
    
    private fun stopMockBeaconGeneration() {
        mockBeaconGenerator.stopGenerating()
        isScanning = false
        binding.tvScanStatus.text = "未扫描"
        Log.d(TAG, "Stopped generating mock beacons")
    }

    // 开始扫描
    private fun startScan() {
        // 根据是否使用模拟数据决定启动哪种扫描方式
        if (!useMockData) {
            startBeaconScanning()
        } else {
            startMockBeaconGeneration()
        }
    }
    
    // 停止扫描
    private fun stopScan() {
        if (!isScanning) return
        
        try {
            if (useMockData) {
                stopMockBeaconGeneration()
            } else {
                stopBeaconScanning()
            }
            binding.switchScan.isChecked = false
            isScanning = false
            updateEmptyView(true)
        } catch (e: Exception) {
            Log.e(TAG, "停止扫描失败: ${e.message}", e)
            Toast.makeText(this, "停止扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) {
            if (useMockData) {
                stopMockBeaconGeneration()
            } else {
                stopBeaconScanning()
            }
        }
        binding.switchScan.isChecked = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScanning) {
            if (useMockData) {
                stopMockBeaconGeneration()
            } else {
                stopBeaconScanning()
            }
        }
        mockHandler.removeCallbacksAndMessages(null)
    }

    // 检查是否有所需权限
    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // 请求所需权限
    private fun requestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestMultiplePermissions.launch(missingPermissions.toTypedArray())
        }
    }
    
    // 检查蓝牙状态
    private fun checkBluetoothStatus(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        // 检查设备是否支持蓝牙
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // 检查蓝牙是否已启用
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return false
        }
        
        return true
    }
} 