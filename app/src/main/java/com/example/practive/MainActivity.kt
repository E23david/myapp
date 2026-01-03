// ⬇️ 1. 檔案層級註解
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalGetImage::class // 增加這個以解決 CameraPreview 的警告
)

package com.example.practive

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private var bleService: BleService? = null
    private var isBound = false
    private val isServiceReady = mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isBound = true
            isServiceReady.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            bleService = null
            isServiceReady.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, BleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            MaterialTheme {
                if (isServiceReady.value && bleService != null) {
                    QRScannerWithDrawer(bleService!!)
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF2196F3))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("啟動背景服務中...", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerWithDrawer(bleService: BleService) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("scan") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                DrawerContent(
                    currentScreen = currentScreen,
                    onScreenSelected = { screen ->
                        currentScreen = screen
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = when (currentScreen) {
                            "scan" -> "QR Code 掃描器"
                            "barcode" -> "手機條碼"
                            "stats" -> "花費與地點紀錄"
                            "settings" -> "設定"
                            "about" -> "關於"
                            else -> "QR Scanner"
                        })
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "選單")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF2196F3),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (currentScreen) {
                    "scan" -> ScanScreen(bleService)
                    "barcode" -> BarcodeScreen(bleService)
                    "stats" -> StatsScreen()
                    "settings" -> SettingsScreen(bleService)
                    "about" -> AboutScreen()
                }
            }
        }
    }
}

@Composable
fun DrawerContent(currentScreen: String, onScreenSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Box(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF2196F3)).padding(24.dp)
        ) {
            Column {
                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(48.dp), tint = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("QR Scanner", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("v1.3.0 (Service+DB)", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DrawerMenuItem(Icons.Default.QrCodeScanner, "掃描", currentScreen == "scan") { onScreenSelected("scan") }
        DrawerMenuItem(Icons.Default.PhoneAndroid, "手機條碼", currentScreen == "barcode") { onScreenSelected("barcode") }
        DrawerMenuItem(Icons.Default.PieChart, "統計紀錄", currentScreen == "stats") { onScreenSelected("stats") }
        DrawerMenuItem(Icons.Default.Settings, "設定", currentScreen == "settings") { onScreenSelected("settings") }
        DrawerMenuItem(Icons.Default.Info, "關於", currentScreen == "about") { onScreenSelected("about") }

        Spacer(modifier = Modifier.weight(1f))
        Text("© 2024 QR Scanner", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun DrawerMenuItem(icon: ImageVector, title: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) Color(0xFF2196F3).copy(alpha = 0.2f) else Color.Transparent)
            .padding(16.dp, 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, title, tint = if (isSelected) Color(0xFF2196F3) else Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontSize = 16.sp, color = if (isSelected) Color(0xFF2196F3) else Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(bleService: BleService) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    var scannedText by remember { mutableStateOf("") }
    var scanHistory by remember { mutableStateOf(listOf<String>()) }
    val isConnected by bleService.isConnected.collectAsState()
    var isCameraEnabled by remember { mutableStateOf(true) }

    var showPriceDialog by remember { mutableStateOf(false) }
    var pendingScanContent by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }

    val savedDeviceAddress = DeviceStorage.loadDeviceAddress(context)

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.CAMERA, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    if (showPriceDialog) {
        AlertDialog(
            onDismissRequest = { showPriceDialog = false },
            title = { Text("新增記帳") },
            text = {
                Column {
                    Text("內容: $pendingScanContent")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) priceInput = it },
                        label = { Text("輸入金額") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val lat = try {
                        @SuppressLint("MissingPermission")
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.latitude ?: 0.0
                    } catch (e: Exception) { 0.0 }
                    val lng = try {
                        @SuppressLint("MissingPermission")
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.longitude ?: 0.0
                    } catch (e: Exception) { 0.0 }

                    scope.launch {
                        val price = priceInput.toIntOrNull() ?: 0
                        db.scanDao().insert(ScanRecord(
                            content = pendingScanContent,
                            price = price,
                            latitude = lat,
                            longitude = lng
                        ))
                    }
                    showPriceDialog = false
                    priceInput = ""
                }) { Text("儲存") }
            },
            dismissButton = {
                Button(onClick = { showPriceDialog = false }) { Text("取消") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        if (!permissionsState.allPermissionsGranted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) { Text("授予權限") }
            }
        } else {
            Box(modifier = Modifier.weight(0.6f)) {
                if (isCameraEnabled) {
                    CameraPreview(onQRCodeScanned = { qrText ->
                        scannedText = qrText
                        val regex = Regex("(?i)(v3|v7)[:\\s-]?(\\d+)")
                        val match = regex.find(qrText)

                        if (match != null) {
                            val typeStr = match.groupValues[1].lowercase()
                            val valueStr = match.groupValues[2]
                            val id = if (typeStr == "v3") 0x03 else 0x07
                            val packet32Bit = (id shl 24) or (valueStr.toInt() and 0xFFFFFF)
                            bleService.sendIntData(packet32Bit)
                        } else {
                            if (!showPriceDialog && qrText != pendingScanContent) {
                                pendingScanContent = qrText
                                showPriceDialog = true
                            }
                        }
                    })
                    Box(Modifier.size(250.dp).align(Alignment.Center).border(3.dp, Color(0xFF4CAF50), RoundedCornerShape(12.dp)))
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF2C2C2C)), contentAlignment = Alignment.Center) {
                        Text("相機已暫停", color = Color.Gray)
                    }
                }
                FloatingActionButton(
                    onClick = { isCameraEnabled = !isCameraEnabled },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = Color(0xFF2196F3)
                ) {
                    Icon(if (isCameraEnabled) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                }
            }

            Column(modifier = Modifier.weight(0.4f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (isConnected) "已連線" else "未連線", color = if (isConnected) Color.Green else Color.Red)
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { if(isConnected) bleService.disconnect() else savedDeviceAddress?.let { bleService.connect(it) } }) {
                        Text(if (isConnected) "斷線" else "連線")
                    }
                }
                Text("最新掃描: $scannedText", color = Color.White)
            }
        }
    }
}

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val records by db.scanDao().getAllRecords().collectAsState(initial = emptyList())
    val totalExpense by db.scanDao().getTotalExpense().collectAsState(initial = 0)

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp)) {
        Text("花費統計", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Box(modifier = Modifier.height(250.dp).fillMaxWidth()) {
            if (records.isNotEmpty()) {
                AndroidView(
                    factory = { ctx ->
                        PieChart(ctx).apply {
                            description.isEnabled = false
                            legend.isEnabled = false
                            setHoleColor(AndroidColor.TRANSPARENT)
                            setEntryLabelColor(AndroidColor.WHITE)
                            holeRadius = 40f
                        }
                    },
                    update = { chart ->
                        val entries = records.take(5).map { PieEntry(it.price.toFloat(), it.content.take(6)) }
                        val dataSet = PieDataSet(entries, "花費").apply {
                            colors = listOf(AndroidColor.rgb(33, 150, 243), AndroidColor.rgb(76, 175, 80), AndroidColor.rgb(255, 193, 7), AndroidColor.rgb(244, 67, 54))
                            valueTextColor = AndroidColor.WHITE
                            valueTextSize = 14f
                        }
                        chart.data = PieData(dataSet)
                        chart.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("尚無資料", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            }
        }

        Text("總計: $${totalExpense ?: 0}", fontSize = 20.sp, color = Color(0xFF4CAF50), modifier = Modifier.padding(vertical = 8.dp))
        Divider(color = Color.Gray)

        LazyColumn {
            items(records) { record ->
                RecordItem(record, onDelete = { scope.launch { db.scanDao().delete(record) } })
            }
        }
    }
}

@Composable
fun RecordItem(record: ScanRecord, onDelete: () -> Unit) {
    val context = LocalContext.current
    val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.content, color = Color.White, fontWeight = FontWeight.Bold)
                Text(dateStr, color = Color.Gray, fontSize = 12.sp)
            }
            Text("$${record.price}", color = Color(0xFF4CAF50), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = {
                val uri = Uri.parse("geo:${record.latitude},${record.longitude}?q=${record.latitude},${record.longitude}(地點)")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                try { context.startActivity(mapIntent) } catch (e: Exception) { }
            }) { Icon(Icons.Default.Map, "地點", tint = Color(0xFF2196F3)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "刪除", tint = Color.Gray) }
        }
    }
}

// ✨ 補上完整的 BarcodeScreen 邏輯
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(bleService: BleService) {
    var barcodeInput by remember { mutableStateOf("/GDDC7WL") }
    var barcodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isConnected by bleService.isConnected.collectAsState()

    LaunchedEffect(barcodeInput) {
        if (barcodeInput.isNotEmpty()) {
            barcodeBitmap = generateBarcodeBitmap(barcodeInput.uppercase(), 600, 150)
        } else {
            barcodeBitmap = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("手機條碼生成", fontSize = 22.sp, color = Color.White, modifier = Modifier.padding(vertical = 16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("輸入載具代碼", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = barcodeInput,
                    onValueChange = { barcodeInput = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF2196F3),
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )
                Text("例如：/GDDC7WL (包含斜線)", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (barcodeBitmap != null) {
                    Image(bitmap = barcodeBitmap!!.asImageBitmap(), contentDescription = "Barcode", modifier = Modifier.fillMaxWidth().height(100.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(barcodeInput, color = Color.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                } else {
                    Text("請輸入代碼以產生條碼", color = Color.Gray, modifier = Modifier.padding(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { if (barcodeInput.isNotEmpty()) bleService.sendStringData(barcodeInput) },
            enabled = isConnected && barcodeInput.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3), disabledContainerColor = Color.Gray)
        ) {
            Text(if (isConnected) "上傳至 STM32" else "未連線 STM32")
        }
    }
}

fun generateBarcodeBitmap(content: String, width: Int, height: Int): Bitmap? {
    return try {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.CODE_39, width, height)
        val pixels = IntArray(bitMatrix.width * bitMatrix.height)
        for (y in 0 until bitMatrix.height) {
            val offset = y * bitMatrix.width
            for (x in 0 until bitMatrix.width) {
                pixels[offset + x] = if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(pixels, bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) { null }
}

// ✨ 補上完整的 SettingsScreen 邏輯
@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen(bleService: BleService) {
    var autoConnect by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var saveHistory by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scannedDevices by bleService.scannedDevices.collectAsState()
    val savedDeviceAddress = DeviceStorage.loadDeviceAddress(context)

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("一般設定", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingItem("自動連線", "啟動時自動連線到 STM32", autoConnect) { autoConnect = it }
                Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
                SettingItem("掃描音效", "掃描成功時播放提示音", soundEnabled) { soundEnabled = it }
                Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
                SettingItem("震動回饋", "掃描成功時震動提示", vibrationEnabled) { vibrationEnabled = it }
                Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
                SettingItem("儲存歷史記錄", "保留最近 100 筆掃描記錄", saveHistory) { saveHistory = it }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("藍牙設定", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("已儲存的 STM32 裝置位址", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(savedDeviceAddress ?: "尚未設定", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (permissionsState.allPermissionsGranted) bleService.scanDevices()
                        else permissionsState.launchMultiplePermissionRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.Bluetooth, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("搜尋藍牙裝置")
                }

                if (scannedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("掃描到的裝置 (點擊以儲存並連線):", color = Color.White, fontWeight = FontWeight.Bold)
                    Column(modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                        scannedDevices.forEach { result ->
                            val deviceName = result.device.name ?: "未知裝置"
                            val deviceAddress = result.device.address
                            Text(
                                text = "$deviceName ($deviceAddress)",
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    DeviceStorage.saveDeviceAddress(context, deviceAddress)
                                    bleService.stopScan()
                                    bleService.connect(deviceAddress)
                                }.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { DeviceStorage.saveDeviceAddress(context, "") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
        ) { Text("清除所有資料") }
    }
}

@Composable
fun SettingItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF2196F3), uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color(0xFF3C3C3C)))
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(onQRCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var lastScannedText by remember { mutableStateOf("") }
    var lastScanTime by remember { mutableStateOf(0L) }

    AndroidView(factory = { ctx ->
        val previewView = PreviewView(ctx)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageAnalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                it.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        BarcodeScanning.getClient().process(image).addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val rawValue = barcode.rawValue ?: continue
                                if (rawValue != lastScannedText || System.currentTimeMillis() - lastScanTime > 2000) {
                                    lastScannedText = rawValue
                                    lastScanTime = System.currentTimeMillis()
                                    onQRCodeScanned(rawValue)
                                }
                            }
                        }.addOnCompleteListener { imageProxy.close() }
                    } else { imageProxy.close() }
                }
            }
            try { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer) } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(ctx))
        previewView
    }, modifier = Modifier.fillMaxSize())
}

@Composable
fun AboutScreen() {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(Icons.Default.QrCodeScanner, null, Modifier.size(100.dp), tint = Color(0xFF2196F3))
        Spacer(modifier = Modifier.height(24.dp))
        Text("QR Code 掃描器", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text("版本 1.3.0", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))) {
            Column(modifier = Modifier.padding(20.dp)) {
                InfoRow("開發者", "Your Name")
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow("更新日期", "2026/1/4")
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow("授權", "MIT License")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("功能說明", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("• 快速掃描 QR Code 和條碼", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 透過藍牙傳送至 STM32", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 生成手機條碼 (電子載具)", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 儲存掃描歷史與記帳", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 支援背景自動重連 (離身鎖定)", color = Color.Green, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("© 2024 QR Scanner. All rights reserved.", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}