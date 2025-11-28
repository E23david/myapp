package com.example.practive

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import android.os.Build
// HM-10 專用的 UUID
object Hm10Gatt {
    val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
}

@SuppressLint("MissingPermission") // 我們已在 Composable 中處理權限
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
            bluetoothGatt = device.connectGatt(getApplication(), false, gattCallback)
        }
    }

    // 斷線
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    // GATT 回調 (非同步事件處理)
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _isConnected.value = true
                gatt.discoverServices() // 連線成功，開始探索服務
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _isConnected.value = false
                bluetoothGatt?.close()
                bluetoothGatt = null
                writeCharacteristic = null
            }
        }

        // 探索到服務
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(Hm10Gatt.SERVICE_UUID)
                writeCharacteristic = service?.getCharacteristic(Hm10Gatt.CHARACTERISTIC_UUID)
                // 這裡可以再做 setCharacteristicNotification 啟用
            }
        }
    }

    // 傳送資料
    fun sendData(data: String) {
        if (bluetoothGatt == null || writeCharacteristic == null || !_isConnected.value) {
            return
        }

        val dataBytes = data.toByteArray(Charsets.UTF_8)
        writeCharacteristic?.value = dataBytes

        // Android 13+ 需要這樣寫
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                writeCharacteristic!!,
                dataBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            // 舊版寫法
            bluetoothGatt?.writeCharacteristic(writeCharacteristic)
        }
    }
}