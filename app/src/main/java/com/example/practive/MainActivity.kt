// ⬇️ 1. 檔案層級設定：開啟各種實驗性功能
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalGetImage::class
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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

// 🎨 設計系統 (Cyberpunk 風格配色)
object AppColors {
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceLight = Color(0xFF2C2C2C)
    val Primary = Color(0xFF00E5FF)
    val Secondary = Color(0xFF2979FF)
    val Accent = Color(0xFFFF4081)
    val TextWhite = Color(0xFFEEEEEE)
    val TextGray = Color(0xFFAAAAAA)
    val MainGradient = Brush.horizontalGradient(listOf(Secondary, Primary))
}

class MainActivity : ComponentActivity() {
    private var bleService: BleService? = null
    private var isBound = false
    private val isServiceReady = mutableStateOf(false)

    // Service 連線監聽器
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as BleService.LocalBinder
                bleService = binder.getService()
                isBound = true
                isServiceReady.value = true
            } catch (e: Exception) { e.printStackTrace() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            bleService = null
            isServiceReady.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Primary, background = AppColors.Background, surface = AppColors.Surface)) {
                val context = LocalContext.current

                // 1. 定義權限清單
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(Manifest.permission.CAMERA, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
                }

                // 2. 權限狀態管理
                val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

                // 3. 🛡️ 權限防護罩
                if (permissionsState.allPermissionsGranted) {
                    // 啟動服務
                    LaunchedEffect(Unit) {
                        if (!isBound) {
                            try {
                                val intent = Intent(context, BleService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }

                    if (isServiceReady.value && bleService != null) {
                        QRScannerWithDrawer(bleService!!)
                    } else {
                        LoadingScreen()
                    }
                } else {
                    // 引導授權畫面
                    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Security, null, tint = AppColors.Primary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("需要權限才能運作", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("請授予相機與藍牙權限\n以使用智慧錢包功能", color = Color.Gray, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                                Text("授予權限", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(serviceConnection)
    }
}

// ⏳ 載入畫面
@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppColors.Primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("系統啟動中...", color = AppColors.TextGray, fontSize = 14.sp)
        }
    }
}

// 📦 主架構
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerWithDrawer(bleService: BleService) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("scan") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = AppColors.Surface) {
                DrawerContent(currentScreen) { screen ->
                    currentScreen = screen
                    scope.launch { drawerState.close() }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Box(modifier = Modifier.fillMaxWidth().background(AppColors.MainGradient).statusBarsPadding()) {
                    TopAppBar(
                        title = {
                            Text(text = when (currentScreen) {
                                "scan" -> "智慧掃描"
                                "barcode" -> "載具生成"
                                "stats" -> "生活軌跡"
                                "settings" -> "系統設定"
                                "about" -> "關於系統"
                                else -> "Smart Wallet"
                            }, fontWeight = FontWeight.Bold, color = Color.White)
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "選單", tint = Color.White) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(AppColors.Background)) {
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

// 📜 側邊欄內容
@Composable
fun DrawerContent(currentScreen: String, onScreenSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(AppColors.MainGradient), contentAlignment = Alignment.BottomStart) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(56.dp), tint = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text("AI Smart Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("智慧生活．無感支付", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        listOf(
            Triple("scan", "QR Code 掃描", Icons.Default.CameraAlt),
            Triple("barcode", "手機條碼生成", Icons.Default.QrCode2),
            Triple("stats", "紀錄與統計", Icons.Default.PieChart),
            Triple("settings", "系統設定", Icons.Default.Settings),
            Triple("about", "關於", Icons.Default.Info)
        ).forEach { (id, title, icon) ->
            NavigationDrawerItem(
                label = { Text(title, fontWeight = if(currentScreen == id) FontWeight.Bold else FontWeight.Normal) },
                selected = currentScreen == id,
                onClick = { onScreenSelected(id) },
                icon = { Icon(icon, null) },
                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = AppColors.Primary.copy(alpha = 0.15f), selectedIconColor = AppColors.Primary, selectedTextColor = AppColors.Primary, unselectedIconColor = AppColors.TextGray, unselectedTextColor = AppColors.TextWhite),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// 📷 掃描畫面
// ⬇️ 只要复制这个 ScanScreen 覆盖原本的即可
// ⬇️ 只要複製這個 ScanScreen 覆蓋原本的即可
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(bleService: BleService) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLineY by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "scanLine")

    var scannedText by remember { mutableStateOf("") }
    val isConnected by bleService.isConnected.collectAsState()
    var isCameraEnabled by remember { mutableStateOf(true) }

    // 記帳視窗狀態
    var showPriceDialog by remember { mutableStateOf(false) }
    var pendingScanContent by remember { mutableStateOf("") } // 這裡存原始碼
    var priceInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") } // 這裡存使用者想輸入的中文名

    val savedDeviceAddress = DeviceStorage.loadDeviceAddress(context)

    // 核心邏輯：處理掃描結果
    val handleScanResult: (String) -> Unit = { qrText ->
        scannedText = qrText

        // ✨ 1. 修改點：不管掃到什麼，先把原始碼傳給 STM32！
        // 這樣就確保了 "代碼還在，且給了 STM32"
        bleService.sendStringData(qrText)

        // 接著才判斷要不要跳出手機記帳視窗
        val controlRegex = Regex("(?i)^(v3|v7)[:\\s-]?(\\d+)")
        val isControl = controlRegex.containsMatchIn(qrText)
        val ticketRegex = Regex("(?i)^(THSR|TRA|BUS):.*")
        val isTicket = ticketRegex.matches(qrText)

        if (isControl) {
            android.widget.Toast.makeText(context, "📡 指令已發送 STM32", android.widget.Toast.LENGTH_SHORT).show()
        } else if (isTicket) {
            android.widget.Toast.makeText(context, "🎫 車票已發送 STM32", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            // 既不是指令也不是車票 -> 視為一般商品，跳出記帳視窗
            if (!showPriceDialog && qrText != pendingScanContent) {
                pendingScanContent = qrText // 原始碼暫時存起來
                nameInput = "" // ✨ 名字欄清空，讓你方便輸入
                showPriceDialog = true
            }
        }
    }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scanQRCodeFromUri(context, uri) { resultText ->
                    if (resultText != null) handleScanResult(resultText)
                    else android.widget.Toast.makeText(context, "未發現 QR Code", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // 💰 記帳彈窗
    if (showPriceDialog) {
        AlertDialog(
            onDismissRequest = { showPriceDialog = false },
            containerColor = AppColors.SurfaceLight,
            title = { Text("💰 新增記帳", color = AppColors.Primary) },
            text = {
                Column {
                    // ✨ 這裡的 pendingScanContent 就是原始碼，顯示出來讓你知道它還在
                    Text("原始代碼: $pendingScanContent", color = AppColors.TextGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 名字輸入框 (預設空的)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("商品名稱") },
                        placeholder = { Text("例如: 綠茶") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextWhite,
                            unfocusedTextColor = AppColors.TextWhite,
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = AppColors.TextGray
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 金額輸入框
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) priceInput = it },
                        label = { Text("輸入金額") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextWhite,
                            unfocusedTextColor = AppColors.TextWhite,
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = AppColors.TextGray
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val lat = try { @SuppressLint("MissingPermission") locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.latitude ?: 0.0 } catch (e: Exception) { 0.0 }
                    val lng = try { @SuppressLint("MissingPermission") locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.longitude ?: 0.0 } catch (e: Exception) { 0.0 }

                    val priceToSave = priceInput.toIntOrNull() ?: 0

                    // ✨ 儲存邏輯：
                    // 如果你有打字 -> 存你打的名字 (例如: "綠茶")
                    // 如果你沒打字 -> 存回原始碼 (例如: "471123...")
                    val contentToSave = if (nameInput.isNotBlank()) nameInput else pendingScanContent

                    scope.launch {
                        db.scanDao().insert(ScanRecord(content = contentToSave, price = priceToSave, latitude = lat, longitude = lng))
                    }

                    showPriceDialog = false
                    priceInput = ""
                    nameInput = ""
                    pendingScanContent = ""
                }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) { Text("儲存", color = Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showPriceDialog = false }) { Text("取消", color = AppColors.TextGray) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isCameraEnabled) {
                CameraPreview(onQRCodeScanned = { qrText -> handleScanResult(qrText) })
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSize = 260.dp.toPx(); val left = (size.width - boxSize) / 2; val top = (size.height - boxSize) / 2; val cornerLen = 30.dp.toPx()
                    val stroke = Stroke(width = 4.dp.toPx()); val color = AppColors.Primary
                    drawLine(color, Offset(left, top), Offset(left + cornerLen, top), stroke.width)
                    drawLine(color, Offset(left, top), Offset(left, top + cornerLen), stroke.width)
                    drawLine(color, Offset(left + boxSize, top), Offset(left + boxSize - cornerLen, top), stroke.width)
                    drawLine(color, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLen), stroke.width)
                    drawLine(color, Offset(left, top + boxSize), Offset(left + cornerLen, top + boxSize), stroke.width)
                    drawLine(color, Offset(left, top + boxSize), Offset(left, top + boxSize - cornerLen), stroke.width)
                    drawLine(color, Offset(left + boxSize, top + boxSize), Offset(left + boxSize - cornerLen, top + boxSize), stroke.width)
                    drawLine(color, Offset(left + boxSize, top + boxSize), Offset(left + boxSize, top + boxSize - cornerLen), stroke.width)
                    val lineY = top + (boxSize * scanLineY)
                    drawLine(brush = Brush.horizontalGradient(listOf(Color.Transparent, AppColors.Primary, Color.Transparent)), start = Offset(left, lineY), end = Offset(left + boxSize, lineY), strokeWidth = 2.dp.toPx())
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.VideocamOff, null, tint = AppColors.TextGray, modifier = Modifier.size(48.dp)); Text("相機已暫停", color = AppColors.TextGray) }
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(onClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, containerColor = AppColors.SurfaceLight, contentColor = AppColors.TextWhite) { Icon(Icons.Default.Image, "相簿") }
                FloatingActionButton(onClick = { isCameraEnabled = !isCameraEnabled }, containerColor = AppColors.Primary, contentColor = Color.Black) { Icon(if (isCameraEnabled) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isConnected) AppColors.Primary else AppColors.Accent))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isConnected) "裝置已連線" else "等待連線中...", color = AppColors.TextWhite, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { if(isConnected) bleService.disconnect() else savedDeviceAddress?.let { bleService.connect(it) } }) { Text(if (isConnected) "斷線" else "重新連線", color = AppColors.Secondary) }
                }
                Divider(color = AppColors.TextGray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
                Text("最新掃描內容", color = AppColors.TextGray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(if(scannedText.isEmpty()) "尚未掃描" else scannedText, color = AppColors.TextWhite, maxLines = 2)
            }
        }
    }
}

// 📊 統計頁面 (修復數字顯示)
@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val records by db.scanDao().getAllRecords().collectAsState(initial = emptyList())
    val totalExpense by db.scanDao().getTotalExpense().collectAsState(initial = 0)

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(16.dp)) {
        Text("消費概覽", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.TextWhite)
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth().height(280.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (records.isNotEmpty()) {
                    AndroidView(
                        factory = { ctx ->
                            PieChart(ctx).apply {
                                description.isEnabled = false
                                legend.isEnabled = false
                                setHoleColor(0x00000000)
                                setEntryLabelColor(AndroidColor.WHITE)
                                setEntryLabelTextSize(12f)
                                holeRadius = 45f
                                transparentCircleRadius = 50f
                                animateY(1400)
                            }
                        },
                        update = { chart ->
                            val entries = records.take(5).map { PieEntry(it.price.toFloat(), it.content.take(6)) }
                            val dataSet = PieDataSet(entries, "").apply {
                                colors = listOf(AndroidColor.parseColor("#00E5FF"), AndroidColor.parseColor("#2979FF"), AndroidColor.parseColor("#651FFF"), AndroidColor.parseColor("#FF4081"))
                                valueTextColor = AndroidColor.WHITE
                                valueTextSize = 14f
                                // ✨ 關鍵修正：將浮點數轉為整數顯示
                                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        return value.toInt().toString()
                                    }
                                }
                            }
                            chart.data = PieData(dataSet)
                            chart.invalidate()
                        },
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                } else { Text("尚無消費資料", color = AppColors.TextGray) }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("歷史紀錄", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextWhite); Spacer(modifier = Modifier.weight(1f)); Text("總計: $${totalExpense ?: 0}", fontSize = 18.sp, color = AppColors.Primary, fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(records) { record -> RecordItem(record, onDelete = { scope.launch { db.scanDao().delete(record) } }) }
        }
    }
}

// 修正列表數字顯示
@Composable
fun RecordItem(record: ScanRecord, onDelete: () -> Unit) {
    val context = LocalContext.current
    val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    Card(colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceLight), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.content, color = AppColors.TextWhite, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccessTime, null, tint = AppColors.TextGray, modifier = Modifier.size(12.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(dateStr, color = AppColors.TextGray, fontSize = 12.sp) }
            }
            // ✨ 修正：使用整數顯示
            Text("$${record.price}", color = AppColors.Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = { val uri = Uri.parse("geo:${record.latitude},${record.longitude}?q=${record.latitude},${record.longitude}(地點)"); try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { } }) { Icon(Icons.Default.Map, "地點", tint = AppColors.Secondary) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "刪除", tint = AppColors.TextGray) }
        }
    }
}

// 📱 載具條碼頁
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(bleService: BleService) {
    var barcodeInput by remember { mutableStateOf("/GDDC7WL") }
    var barcodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isConnected by bleService.isConnected.collectAsState()

    LaunchedEffect(barcodeInput) {
        if (barcodeInput.isNotEmpty()) barcodeBitmap = generateBarcodeBitmap(barcodeInput.uppercase(), 600, 150) else barcodeBitmap = null
    }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("輸入載具代碼", color = AppColors.TextGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = barcodeInput, onValueChange = { barcodeInput = it.uppercase() }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppColors.TextWhite, unfocusedTextColor = AppColors.TextWhite, focusedBorderColor = AppColors.Primary, unfocusedBorderColor = AppColors.TextGray),
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    trailingIcon = { Icon(Icons.Default.Edit, null, tint = AppColors.TextGray) }
                )
                Text("例如：/GDDC7WL", color = AppColors.TextGray.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (barcodeBitmap != null) {
                    Image(bitmap = barcodeBitmap!!.asImageBitmap(), contentDescription = "Barcode", modifier = Modifier.fillMaxWidth().height(100.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(barcodeInput, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                } else { Text("請輸入代碼", color = Color.Gray) }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { if (barcodeInput.isNotEmpty()) bleService.sendStringData(barcodeInput) }, enabled = isConnected && barcodeInput.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, disabledContainerColor = AppColors.SurfaceLight), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Upload, null); Spacer(modifier = Modifier.width(8.dp)); Text(if (isConnected) "上傳至 STM32" else "請先連線裝置", fontSize = 16.sp, color = if(isConnected) Color.Black else AppColors.TextGray)
        }
    }
}

// ⚙️ 設定頁
@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen(bleService: BleService) {
    var autoConnect by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    val scannedDevices by bleService.scannedDevices.collectAsState()
    val savedDeviceAddress = DeviceStorage.loadDeviceAddress(LocalContext.current)
    val context = LocalContext.current
    val permissionsState = rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))

    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("功能偏好", color = AppColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
                SettingItem("自動連線", "啟動 App 時自動連接", autoConnect) { autoConnect = it }
                SettingItem("音效回饋", "掃描成功時播放音效", soundEnabled) { soundEnabled = it }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("藍牙裝置管理", color = AppColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BluetoothConnected, null, tint = AppColors.TextWhite); Spacer(modifier = Modifier.width(12.dp))
                    Column { Text("綁定裝置", color = AppColors.TextGray, fontSize = 12.sp); Text(savedDeviceAddress ?: "尚未綁定", color = AppColors.TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { if (permissionsState.allPermissionsGranted) bleService.scanDevices() else permissionsState.launchMultiplePermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Secondary), modifier = Modifier.fillMaxWidth()) { Text("搜尋並綁定新裝置") }
                if (scannedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp)); Text("附近的裝置", color = AppColors.TextGray, fontSize = 12.sp); Spacer(modifier = Modifier.height(8.dp))
                    scannedDevices.forEach { result ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { DeviceStorage.saveDeviceAddress(context, result.device.address); bleService.stopScan(); bleService.connect(result.device.address) }.background(AppColors.SurfaceLight).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, null, tint = AppColors.Primary); Spacer(modifier = Modifier.width(12.dp)); Text(result.device.name ?: "Unknown", color = AppColors.TextWhite); Spacer(modifier = Modifier.weight(1f)); Text(result.device.address, color = AppColors.TextGray, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) { Text(title, color = AppColors.TextWhite, fontSize = 16.sp); Text(subtitle, color = AppColors.TextGray, fontSize = 12.sp) }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppColors.Primary, uncheckedThumbColor = AppColors.TextGray, uncheckedTrackColor = AppColors.SurfaceLight))
    }
}

@Composable
fun AboutScreen() {
    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.QrCodeScanner, null, tint = AppColors.Primary, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("AI Smart Wallet", color = AppColors.TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("v1.4.0", color = AppColors.TextGray)
        }
    }
}

// 🔧 工具函式區

// 1. 強力修復版 CameraPreview (解決黑屏)
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(onQRCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var lastScannedText by remember { mutableStateOf("") }
    var lastScanTime by remember { mutableStateOf(0L) }

    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        // 強制使用 COMPATIBLE 模式，解決模擬器與部分實體機黑屏問題
        PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
    }, update = { previewView ->
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
                                    lastScannedText = rawValue; lastScanTime = System.currentTimeMillis(); onQRCodeScanned(rawValue)
                                }
                            }
                        }.addOnCompleteListener { imageProxy.close() }
                    } else { imageProxy.close() }
                }
            }
            try { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer) } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(context))
    })
}

// 2. 穩健版圖片讀取 (解決模擬器 crash)
fun scanQRCodeFromUri(context: Context, uri: Uri, onComplete: (String?) -> Unit) {
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                val image = InputImage.fromBitmap(bitmap, 0)
                BarcodeScanning.getClient().process(image).addOnSuccessListener { barcodes -> onComplete(barcodes.firstOrNull()?.rawValue) }.addOnFailureListener { onComplete(null) }
            } else { onComplete(null) }
        } ?: run { onComplete(null) }
    } catch (e: Exception) { e.printStackTrace(); onComplete(null) }
}

fun generateBarcodeBitmap(content: String, width: Int, height: Int): Bitmap? {
    return try {
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.CODE_39, width, height)
        val pixels = IntArray(bitMatrix.width * bitMatrix.height)
        for (y in 0 until bitMatrix.height) { val offset = y * bitMatrix.width; for (x in 0 until bitMatrix.width) pixels[offset + x] = if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE }
        Bitmap.createBitmap(pixels, bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) { null }
}

