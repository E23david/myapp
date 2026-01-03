package com.example.practive

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*


@SuppressLint("MissingPermission")
class BleService : Service() {

    // Service 專用的 Binder，讓 Activity 可以綁定取得這個 Service 的實體
    private val binder = LocalBinder()

    // Service 的生命週期範圍
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    // --- 狀態流 (跟 ViewModel 一樣，讓 UI 觀察) ---
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _scannedDevices = MutableStateFlow<List<ScanResult>>(emptyList())
    val scannedDevices: StateFlow<List<ScanResult>> = _scannedDevices

    // --- 藍牙變數 ---
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false

    // 記錄上次連線的裝置位址 (用於自動重連)
    private var lastDeviceAddress: String? = null

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    // ⭐️ 關鍵：Service 啟動時執行
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 建立通知管道 (Notification Channel) - Android 8.0+ 必要
        createNotificationChannel()

        // 建立常駐通知
        val notification = NotificationCompat.Builder(this, "BLE_SERVICE_CHANNEL")
            .setContentTitle("STM32 解鎖服務執行中")
            .setContentText("保持連線以進行離身鎖定偵測")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // 你可以換成自己的 icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 啟動前景服務 (這行讓 Service 不會被系統輕易殺掉)
        startForeground(1, notification)

        // 嘗試自動重連 (如果有的話)
        val savedAddress = DeviceStorage.loadDeviceAddress(this)
        if (!savedAddress.isNullOrEmpty()) {
            Log.d("BleService", "啟動時嘗試自動連線: $savedAddress")
            connect(savedAddress)
        }

        return START_STICKY // 如果系統記憶體不足殺掉，有空時會自動重啟
    }

    // --- 以下是原本 BleViewModel 的邏輯搬過來 ---

    fun scanDevices() {
        if (!isScanning) {
            _scannedDevices.value = emptyList()
            isScanning = true
            bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)
            serviceScope.launch {
                delay(10000)
                stopScan()
            }
        }
    }

    fun stopScan() {
        if (isScanning) {
            isScanning = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                if (_scannedDevices.value.find { dev -> dev.device.address == result.device.address } == null) {
                    _scannedDevices.value += it
                }
            }
        }
    }

    fun connect(deviceAddress: String) {
        lastDeviceAddress = deviceAddress
        stopScan() // 連線前停止掃描
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        // 這裡 autoConnect = true 可能更適合背景長期重連，但 false 反應較快
        // 為了穩定性，我們用 false 搭配自己的重連機制
        bluetoothGatt = device?.connectGatt(this, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        // 手動斷線時，清空紀錄以免一直自動重連 (看你需求)
        // lastDeviceAddress = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BleService", "連線成功")
                _isConnected.value = true
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BleService", "已斷線")
                _isConnected.value = false
                bluetoothGatt?.close()
                bluetoothGatt = null
                writeCharacteristic = null

                // ✨ 自動重連機制 ✨
                Log.d("BleService", "背景服務：嘗試自動重連...")
                serviceScope.launch {
                    delay(3000) // 3秒後重連
                    lastDeviceAddress?.let { connect(it) }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(Hm10Gatt.SERVICE_UUID)
                writeCharacteristic = service?.getCharacteristic(Hm10Gatt.CHARACTERISTIC_UUID)
            }
        }
    }

    fun sendStringData(text: String) {
        if (bluetoothGatt == null || writeCharacteristic == null) return
        val bytes = text.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(writeCharacteristic!!, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            writeCharacteristic?.value = bytes
            writeCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            bluetoothGatt?.writeCharacteristic(writeCharacteristic)
        }
    }

    fun sendIntData(value: Int) {
        if (bluetoothGatt == null || writeCharacteristic == null) return
        val dataBytes = ByteArray(4)
        dataBytes[0] = (value and 0xFF).toByte()
        dataBytes[1] = ((value shr 8) and 0xFF).toByte()
        dataBytes[2] = ((value shr 16) and 0xFF).toByte()
        dataBytes[3] = ((value shr 24) and 0xFF).toByte()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(writeCharacteristic!!, dataBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            writeCharacteristic?.value = dataBytes
            writeCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            bluetoothGatt?.writeCharacteristic(writeCharacteristic)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "BLE_SERVICE_CHANNEL",
                "BLE Service Channel",
                NotificationManager.IMPORTANCE_LOW // 低干擾，只顯示小圖示
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}