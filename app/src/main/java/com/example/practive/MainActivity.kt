// ⬇️ 1. 加入這個「檔案層級」註解，解決所有「實驗性 API」警告
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class
)

package com.example.practive

import androidx.compose.material3.ExperimentalMaterial3Api
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.example.practive.DeviceStorage

import android.os.Build
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import androidx.lifecycle.viewmodel.compose.viewModel
import android.annotation.SuppressLint
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                QRScannerWithDrawer()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerWithDrawer() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("scan") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                DrawerContent(
                    currentScreen = currentScreen,
                    onScreenSelected = { screen ->
                        currentScreen = screen
                        scope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                "scan" -> "QR Code 掃描器"
                                "settings" -> "設定"
                                "about" -> "關於"
                                else -> "QR Code 掃描器"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "選單"
                            )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    "scan" -> ScanScreen()
                    "settings" -> SettingsScreen()
                    "about" -> AboutScreen()
                }
            }
        }
    }
}

@Composable
fun DrawerContent(
    currentScreen: String,
    onScreenSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2196F3))
                .padding(24.dp)
        ) {
            Column {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "QR Scanner",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "版本 1.0.0",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DrawerMenuItem(
            icon = Icons.Default.QrCodeScanner,
            title = "掃描",
            isSelected = currentScreen == "scan",
            onClick = { onScreenSelected("scan") }
        )

        DrawerMenuItem(
            icon = Icons.Default.Settings,
            title = "設定",
            isSelected = currentScreen == "settings",
            onClick = { onScreenSelected("settings") }
        )

        DrawerMenuItem(
            icon = Icons.Default.Info,
            title = "關於",
            isSelected = currentScreen == "about",
            onClick = { onScreenSelected("about") }
        )

        Spacer(modifier = Modifier.weight(1f))

        Divider(color = Color.Gray.copy(alpha = 0.3f))

        Text(
            text = "© 2024 QR Scanner",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) Color(0xFF2196F3).copy(alpha = 0.2f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) Color(0xFF2196F3) else Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                color = if (isSelected) Color(0xFF2196F3) else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(bleViewModel: BleViewModel = viewModel()) {
    var scannedText by remember { mutableStateOf("") }
    var scanHistory by remember { mutableStateOf(listOf<String>()) }
    val isConnected by bleViewModel.isConnected.collectAsState()
    var isCameraEnabled by remember { mutableStateOf(true) }

    // 取得 context 和已儲存的裝置位址
    val context = LocalContext.current
    val savedDeviceAddress = DeviceStorage.loadDeviceAddress(context)

    // 權限定義
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // Android 11-
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)
    val allPermissionsGranted = permissionsState.allPermissionsGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        when {
            // 情況一：權限尚未授予
            !allPermissionsGranted -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "需要相機與藍牙權限",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "掃描 QR Code 和連線藍牙需要權限",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { permissionsState.launchMultiplePermissionRequest() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Text("授予必要權限")
                        }
                    }
                }
            }

            // 情況二：權限已授予 (顯示相機)
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                ) {
                    if (isCameraEnabled) {
                        // ⬇️ [關鍵] 這裡使用了更新後的 CameraPreview 邏輯
                        CameraPreview(
                            onQRCodeScanned = { qrText ->
                                // 1. 更新 UI
                                scannedText = qrText
                                if (!scanHistory.contains(qrText)) {
                                    scanHistory = listOf(qrText) + scanHistory.take(19)
                                }

                                // 2. [核心邏輯] 解析 v3/v7 並打包成 32-bit 整數傳送
                                try {
                                    // Regex 解析: 找 v3 或 v7，後面接數字
                                    val regex = Regex("(?i)(v3|v7)[:\\s-]?(\\d+)")
                                    val match = regex.find(qrText)

                                    if (match != null) {
                                        val typeStr = match.groupValues[1].lowercase() // "v3" 或 "v7"
                                        val valueStr = match.groupValues[2]            // "100"
                                        val value = valueStr.toInt()

                                        // 決定 ID (v3 -> 0x03, v7 -> 0x07)
                                        val id = if (typeStr == "v3") 0x03 else 0x07

                                        // 打包成 32-bit 整數 (Little Endian 的高位邏輯需配合 sendIntData)
                                        val packet32Bit = (id shl 24) or (value and 0xFFFFFF)

                                        // 透過藍牙傳送
                                        bleViewModel.sendIntData(packet32Bit)

                                        println("已傳送 32-bit 指令: $typeStr - $value")
                                    } else {
                                        // 掃描到非 v3/v7 格式，若有需要可在此傳送原始字串
                                        // bleViewModel.sendData(qrText)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .size(250.dp)
                                .align(Alignment.Center)
                                .border(3.dp, Color(0xFF4CAF50), RoundedCornerShape(12.dp))
                        )

                        Text(
                            text = "對準 QR Code",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 32.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2C2C2C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("相機已暫停", color = Color.Gray, fontSize = 16.sp)
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { isCameraEnabled = !isCameraEnabled },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = Color(0xFF2196F3)
                    ) {
                        Icon(
                            imageVector = if (isCameraEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isCameraEnabled) "暫停相機" else "啟動相機",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Divider(color = Color.Gray, thickness = 2.dp)

        // 底部 40% 控制項
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isConnected) Color.Green else Color.Red,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "已連線 STM32" else "未連線",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = {
                        if (isConnected) {
                            bleViewModel.disconnect()
                        } else {
                            savedDeviceAddress?.let { bleViewModel.connect(it) }
                        }
                    },
                    enabled = savedDeviceAddress != null || isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) Color.Red else Color(0xFF4CAF50),
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Text(if (isConnected) "斷線" else "連線")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C2C2C)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "最新掃描:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (scannedText.isEmpty()) "尚未掃描" else scannedText,
                        color = if (scannedText.isEmpty()) Color.Gray else Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "掃描歷史 (${scanHistory.size})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (scanHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { scanHistory = listOf() }
                    ) {
                        Text("清除", color = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (scanHistory.isEmpty()) {
                Text(
                    text = "尚無歷史記錄",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(scanHistory.take(5)) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF3C3C3C)
                            )
                        ) {
                            Text(
                                text = item,
                                color = Color.White,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
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

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )

                                val scanner = BarcodeScanning.getClient()
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            val rawValue = barcode.rawValue ?: continue
                                            val currentTime = System.currentTimeMillis()

                                            if (rawValue != lastScannedText ||
                                                currentTime - lastScanTime > 2000) {
                                                lastScannedText = rawValue
                                                lastScanTime = currentTime
                                                onQRCodeScanned(rawValue)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen(
    bleViewModel: BleViewModel = viewModel()
) {
    var autoConnect by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var saveHistory by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scannedDevices by bleViewModel.scannedDevices.collectAsState()
    val savedDeviceAddress = DeviceStorage.loadDeviceAddress(context)

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "一般設定",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C2C2C)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingItem(
                    title = "自動連線",
                    subtitle = "啟動時自動連線到 STM32",
                    checked = autoConnect,
                    onCheckedChange = { autoConnect = it }
                )
                Divider(
                    color = Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingItem(
                    title = "掃描音效",
                    subtitle = "掃描成功時播放提示音",
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it }
                )
                Divider(
                    color = Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingItem(
                    title = "震動回饋",
                    subtitle = "掃描成功時震動提示",
                    checked = vibrationEnabled,
                    onCheckedChange = { vibrationEnabled = it }
                )
                Divider(
                    color = Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingItem(
                    title = "儲存歷史記錄",
                    subtitle = "保留最近 100 筆掃描記錄",
                    checked = saveHistory,
                    onCheckedChange = { saveHistory = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "藍牙設定",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C2C2C)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "已儲存的 STM32 裝置位址",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = savedDeviceAddress ?: "尚未設定",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (permissionsState.allPermissionsGranted) {
                            bleViewModel.scanDevices()
                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("搜尋藍牙裝置")
                }

                if (scannedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("掃描到的裝置 (點擊以儲存並連線):", color = Color.White, fontWeight = FontWeight.Bold)

                    Column(
                        modifier = Modifier
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    ) {
                        scannedDevices.forEach { result ->
                            val deviceName = result.device.name ?: "未知裝置"
                            val deviceAddress = result.device.address
                            Text(
                                text = "$deviceName ($deviceAddress)",
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        DeviceStorage.saveDeviceAddress(context, deviceAddress)
                                        bleViewModel.stopScan()
                                        bleViewModel.connect(deviceAddress)
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                DeviceStorage.saveDeviceAddress(context, "")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF44336)
            )
        ) {
            Text("清除所有資料")
        }
    }
}
@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2196F3),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF3C3C3C)
            )
        )
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color(0xFF2196F3)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "QR Code 掃描器",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "版本 1.0.0",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C2C2C)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                InfoRow(label = "開發者", value = "Your Name")
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(label = "更新日期", value = "2024/11/01")
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(label = "授權", value = "MIT License")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "功能說明",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C2C2C)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "• 快速掃描 QR Code 和條碼",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 透過藍牙傳送至 STM32",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 在 E-Paper 顯示掃描內容",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 儲存掃描歷史記錄",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "© 2024 QR Scanner. All rights reserved.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}