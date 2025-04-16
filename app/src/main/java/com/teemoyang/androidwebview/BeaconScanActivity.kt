package com.teemoyang.androidwebview

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.teemoyang.androidwebview.databinding.ActivityBeaconScanBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.altbeacon.beacon.Beacon

class BeaconScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeaconScanBinding
    private lateinit var beaconAdapter: BeaconAdapter
    private lateinit var beaconScanner: BeaconScanner
    
    // 模拟数据生成器
    private val mockBeaconGenerator = MockBeaconGenerator()
    
    // 是否使用模拟数据
    private var useMockData = false
    
    // 发现的信标列表
    private val beacons = mutableListOf<Beacon>()
    
    // 日期格式
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeaconScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置标题和返回按钮
        supportActionBar?.apply {
            title = "信标扫描"
            setDisplayHomeAsUpEnabled(true)
        }
        
        setupRecyclerView()
        setupBeaconScanner()
        setupMockBeaconGenerator()
        setupListeners()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (beaconScanner.handlePermissionResult(requestCode, permissions, grantResults)) {
            beaconScanner.startScan()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        beaconScanner.stopScan()
        mockBeaconGenerator.stopGenerating()
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        beaconAdapter = BeaconAdapter()
        binding.rvBeacons.apply {
            layoutManager = LinearLayoutManager(this@BeaconScanActivity)
            adapter = beaconAdapter
        }
    }
    
    /**
     * 设置信标扫描器
     */
    private fun setupBeaconScanner() {
        beaconScanner = BeaconScanner(this)
        beaconScanner.setScanListener(object : BeaconScanner.ScanListener {
            override fun onScanStart() {
                runOnUiThread {
                    binding.switchScan.isChecked = true
                    binding.tvStatus.text = "正在扫描..."
                    updateScanCount()
                }
            }
            
            override fun onScanStop() {
                runOnUiThread {
                    binding.switchScan.isChecked = false
                    binding.tvStatus.text = "扫描已停止"
                }
            }
            
            override fun onBeaconFound(beacon: org.altbeacon.beacon.Beacon) {
                runOnUiThread {
                    updateBeaconData(beacon)
                }
            }
            
            override fun onError(message: String) {
                runOnUiThread {
                    // 如果扫描出现错误，自动切换到模拟模式
                    if (!useMockData) {
                        useMockData = true
                        binding.tvStatus.text = "使用模拟数据: $message"
                        Toast.makeText(this@BeaconScanActivity, "切换到模拟数据模式", Toast.LENGTH_SHORT).show()
                        startMockBeaconGeneration()
                    } else {
                        binding.tvStatus.text = message
                        Toast.makeText(this@BeaconScanActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
    
    /**
     * 设置模拟信标生成器
     */
    private fun setupMockBeaconGenerator() {
        mockBeaconGenerator.setListener(object : MockBeaconGenerator.MockBeaconListener {
            override fun onBeaconDetected(beacon: org.altbeacon.beacon.Beacon) {
                runOnUiThread {
                    // 直接使用AltBeacon的Beacon类型
                    updateBeaconData(beacon)
                }
            }
        })
    }
    
    /**
     * 更新信标数据
     */
    private fun updateBeaconData(beacon: Beacon) {
        // 更新信标
        val existingIndex = beacons.indexOfFirst { it.bluetoothAddress == beacon.bluetoothAddress && it.id1.toString() == beacon.id1.toString() }
        if (existingIndex >= 0) {
            beacons[existingIndex] = beacon
        } else {
            beacons.add(beacon)
        }
        
        // 按信号强度排序
        beacons.sortByDescending { it.rssi }
        
        // 更新适配器
        beaconAdapter.updateBeacons(beacons)
        
        // 更新扫描计数
        updateScanCount()
    }
    
    /**
     * 开始生成模拟信标数据
     */
    private fun startMockBeaconGeneration() {
        if (binding.switchScan.isChecked) {
            // 确保真实扫描停止
            beaconScanner.stopScan()
            
            // 开始生成模拟数据
            mockBeaconGenerator.startGenerating(8) // 生成8个模拟信标
            binding.switchScan.isChecked = true
            binding.tvStatus.text = "正在使用模拟数据"
        }
    }
    
    /**
     * 停止生成模拟信标数据
     */
    private fun stopMockBeaconGeneration() {
        mockBeaconGenerator.stopGenerating()
        binding.switchScan.isChecked = false
        binding.tvStatus.text = "模拟数据停止"
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 扫描开关
        binding.switchScan.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (useMockData) {
                    startMockBeaconGeneration()
                } else {
                    beaconScanner.startScan()
                }
            } else {
                if (useMockData) {
                    stopMockBeaconGeneration()
                } else {
                    beaconScanner.stopScan()
                }
            }
        }
        
        // 添加长按切换模式功能
        binding.switchScan.setOnLongClickListener {
            useMockData = !useMockData
            if (useMockData) {
                Toast.makeText(this, "切换到模拟数据模式", Toast.LENGTH_SHORT).show()
                binding.tvStatus.text = "使用模拟数据模式"
                // 如果扫描开关已开启，则立即启动模拟
                if (binding.switchScan.isChecked) {
                    beaconScanner.stopScan()
                    startMockBeaconGeneration()
                }
            } else {
                Toast.makeText(this, "切换到真实扫描模式", Toast.LENGTH_SHORT).show()
                binding.tvStatus.text = "使用真实扫描模式"
                // 如果扫描开关已开启，则立即启动扫描
                if (binding.switchScan.isChecked) {
                    mockBeaconGenerator.stopGenerating()
                    beaconScanner.startScan()
                }
            }
            true
        }
        
        // WebSocket测试按钮
        binding.fabWebsocket.setOnClickListener {
            // 启动WebSocket测试页面
            val intent = Intent(this, WebSocketTestActivity::class.java)
            startActivity(intent)
        }
    }
    
    /**
     * 更新扫描计数
     */
    private fun updateScanCount() {
        binding.tvScanCount.text = "发现信标: ${beacons.size}"
    }
    
    /**
     * 向WebSocket服务器发送信标数据
     */
    private fun sendBeaconData(beacon: Beacon) {
        // 转换为JSON格式数据
        val beaconJson = JSONObject().apply {
            put("type", "beacon")
            put("macAddress", beacon.bluetoothAddress)
            put("uuid", beacon.id1.toString())
            put("major", beacon.id2.toString())
            put("minor", beacon.id3.toString())
            put("rssi", beacon.rssi)
            put("txPower", beacon.txPower)
            put("distance", String.format("%.2f", beacon.distance))
            put("timestamp", System.currentTimeMillis())
        }
        
        // TODO: 调用WebSocketManager发送数据
        // WebSocketManager.sendMessage(beaconJson.toString())
    }
} 