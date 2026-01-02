package com.example.practive

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler // 📦 新增
import android.os.Looper  // 📦 新增
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

// HM-10 專用的 UUID (或是你自定義的 STM32 UUID)
object Hm10Gatt {
    val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
}

@SuppressLint("MissingPermission") // 我們已在 UI 層處理權限
class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    // --- 連線狀態 ---
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // --- 掃描狀態 ---
    private val _scannedDevices = MutableStateFlow<List<ScanResult>>(emptyList())
    val scannedDevices: StateFlow<List<ScanResult>> = _scannedDevices
    private var isScanning = false

    // --- GATT 相關 ---
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    // 掃描 BLE 裝置
    fun scanDevices() {
        if (!isScanning) {
            _scannedDevices.value = emptyList() // 清除舊列表
            isScanning = true
            bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)
            // 10 秒後停止掃描
            viewModelScope.launch {
                kotlinx.coroutines.delay(10000)
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

    // 掃描回調
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                if (_scannedDevices.value.find { it.device.address == result.device.address } == null) {
                    _scannedDevices.value += it
                }
            }
        }
    }

    // 連線到裝置
    fun connect(deviceAddress: String) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device != null) {
            // autoConnect = false 表示立即發起連線
            bluetoothGatt = device.connectGatt(getApplication(), false, gattCallback)
        }
    }

    // 斷線 (手動斷線通常不希望自動重連，但在這個範例中我們保持簡單)
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    // GATT 回調 (非同步事件處理)
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "連線成功")
                _isConnected.value = true
                gatt.discoverServices() // 連線成功，開始探索服務
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "已斷線")
                _isConnected.value = false
                bluetoothGatt?.close()
                bluetoothGatt = null
                writeCharacteristic = null

                // ✨✨✨ 新增：自動重連機制 (實現離身鎖定/靠近解鎖) ✨✨✨
                Log.d("BLE", "嘗試於 2 秒後自動重連...")
                Handler(Looper.getMainLooper()).postDelayed({
                    // 嘗試重新連線同一個裝置
                    val address = gatt.device.address
                    if (address != null) {
                        connect(address)
                    }
                }, 2000)
            }
        }

        // 探索到服務
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(Hm10Gatt.SERVICE_UUID)
                writeCharacteristic = service?.getCharacteristic(Hm10Gatt.CHARACTERISTIC_UUID)
                Log.d("BLE", "服務探索完成，特徵值: ${writeCharacteristic != null}")
            }
        }
    }

    // 傳送 Int 資料 (QR Code 掃描用)
    fun sendIntData(value: Int) {
        if (bluetoothGatt == null || writeCharacteristic == null || !_isConnected.value) {
            return
        }

        // 轉成 4 Bytes (Little Endian)
        val dataBytes = ByteArray(4)
        dataBytes[0] = (value and 0xFF).toByte()         // 數值低位
        dataBytes[1] = ((value shr 8) and 0xFF).toByte() // 數值中位
        dataBytes[2] = ((value shr 16) and 0xFF).toByte()// 數值高位
        dataBytes[3] = ((value shr 24) and 0xFF).toByte()// ID (v3/v7)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                writeCharacteristic!!,
                dataBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            // 🔧 修正：舊版 API 必須先設定 value 才能寫入
            writeCharacteristic?.value = dataBytes
            writeCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            bluetoothGatt?.writeCharacteristic(writeCharacteristic)
        }
    }

    // 傳送 String 資料 (手機條碼用)
    fun sendStringData(text: String) {
        if (bluetoothGatt == null || writeCharacteristic == null) {
            Log.e("BLE", "尚未連線或找不到寫入特徵")
            return
        }

        // 將字串轉為 Byte Array (UTF-8)
        val bytes = text.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                writeCharacteristic!!,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            // 舊版 API 寫法
            writeCharacteristic?.value = bytes
            writeCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            bluetoothGatt?.writeCharacteristic(writeCharacteristic)
        }
    }
}