package com.joysuch.pcl

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
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.joysuch.pcl.databinding.ActivityBeaconListBinding
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.Region

class BeaconListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBeaconListBinding
    private lateinit var beaconAdapter: BeaconAdapter
    private lateinit var beaconManager: BeaconManager
    private val TAG = "BeaconListActivity"
    private val ALL_BEACONS_REGION = Region("allBeacons", null, null, null)
    private var isScanning = false

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
    }

    private fun setupUI() {
        beaconAdapter = BeaconAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BeaconListActivity)
            adapter = beaconAdapter
        }

        binding.switchScan.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkBluetoothAndPermissions()
            } else {
                stopBeaconScanning()
            }
        }

        binding.fabSettings.setOnClickListener {
            // 这里可以添加其他设置选项
            AlertDialog.Builder(this)
                .setTitle("扫描设置")
                .setMessage("更多扫描设置功能敬请期待")
                .setPositiveButton("确定", null)
                .show()
        }

        // 初始状态下空视图显示，RecyclerView隐藏
        updateEmptyView(true)
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

        if (bluetoothAdapter == null) {
            // 设备不支持蓝牙
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            binding.switchScan.isChecked = false
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // 请求用户打开蓝牙
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        // 检查所需权限
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            // 请求缺少的权限
            requestMultiplePermissions.launch(missingPermissions)
        } else {
            // 已有所有权限，开始扫描
            startScan()
        }
    }

    private fun checkBluetoothStatus(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        return if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            true
        } else {
            binding.switchScan.isChecked = false
            Toast.makeText(this, "蓝牙未启用，无法扫描", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun startScan() {
        if (isScanning) return
        
        try {
            beaconManager.startRangingBeacons(ALL_BEACONS_REGION)
            isScanning = true
            binding.tvScanStatus.text = "正在扫描..."
            
            Log.d(TAG, "开始扫描蓝牙信标")
        } catch (e: RemoteException) {
            Log.e(TAG, "开始扫描时出错: ${e.message}")
            Toast.makeText(this, "扫描启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.switchScan.isChecked = false
        }
    }

    private fun stopBeaconScanning() {
        if (!isScanning) return
        
        try {
            beaconManager.stopRangingBeacons(ALL_BEACONS_REGION)
            isScanning = false
            binding.tvScanStatus.text = "扫描已停止"
            
            Log.d(TAG, "停止扫描蓝牙信标")
        } catch (e: RemoteException) {
            Log.e(TAG, "停止扫描时出错: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.switchScan.isChecked && !isScanning) {
            checkBluetoothAndPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) {
            stopBeaconScanning()
            // 注意：我们保持开关的状态，以便在返回活动时恢复扫描
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScanning) {
            stopBeaconScanning()
        }
    }
} 