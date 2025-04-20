package com.teemoyang.androidwebview

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.teemoyang.androidwebview.databinding.ActivityBeaconScanBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import org.altbeacon.beacon.Beacon

class BeaconScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeaconScanBinding
    private lateinit var beaconAdapter: BeaconAdapter
    private lateinit var beaconScanner: BeaconScanner
    
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
        
        // 创建并初始化BeaconScanner
        beaconScanner = BeaconScanner(this)
        beaconScanner.initialize()
        
        setupRecyclerView()
        setupBeaconScanner()
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
        // 使用BeaconScanner的权限回调处理
        beaconScanner.handlePermissionResult(requestCode, permissions, grantResults)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        beaconScanner.release()
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
            
            override fun onBeaconFound(beaconCollection: Collection<Beacon>) {
                runOnUiThread {
                    beaconCollection.forEach { beacon ->
                        updateBeaconData(beacon)
                    }
                }
            }
            
            override fun onError(message: String) {
                runOnUiThread {
                    binding.tvStatus.text = message
                    Toast.makeText(this@BeaconScanActivity, message, Toast.LENGTH_SHORT).show()
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
     * 设置监听器
     */
    private fun setupListeners() {
        // 扫描开关
        binding.switchScan.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                beaconScanner.startScan()
            } else {
                beaconScanner.stopScan()
            }
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