/*
 * 星灵 (XingLing)
 * Copyright (C) 2026 XingLing Project
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.xingling.app
import java.io.File
import java.io.FileOutputStream
import com.xingling.app.ui.overview.OverviewHome
import com.xingling.app.ui.signal.SignalScreen
import com.xingling.app.ui.widget.WidgetPreviewScreen
import com.xingling.app.ui.feature.BandTabContent
import com.xingling.app.ui.feature.SmsTabContent
import com.xingling.app.ui.feature.SettingsTabContent
import com.xingling.app.ui.feature.FeatureRoute
import com.xingling.app.ui.feature.FeatureRouteHost
import com.xingling.app.ui.feature.SettingsPage
import com.xingling.app.ui.feature.AtTerminalScreen
import com.xingling.app.ui.feature.NicknameScreen
import com.xingling.app.ui.feature.ProxyDebugScreen
import com.xingling.app.ui.setup.DeviceSetupScreen
import com.xingling.app.backend.DeviceStore
import com.xingling.app.backend.UfiToolsBackend
import androidx.compose.material.icons.filled.Email
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.core.app.ActivityCompat
import com.xingling.app.ui.theme.GlassCard
import com.xingling.app.ui.theme.iOSButton
import com.xingling.app.ui.theme.iOSOutlineButton
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSNavUnselected
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSCardBackground
import com.xingling.app.ui.theme.iOSSeparator
import com.xingling.app.ui.theme.XingLingTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.xingling.app.signal.SignalMonitor
import com.xingling.app.signal.SignalDashboardState
import com.xingling.app.signal.SignalMetrics
import com.xingling.app.signal.NetworkMetrics
import com.xingling.app.signal.DeviceMetrics
import com.xingling.app.signal.TrafficMetrics
import com.xingling.app.signal.SystemMetrics
import com.xingling.app.ui.signal.SignalDashboard
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.PrintWriter
import java.io.StringWriter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.material3.MaterialTheme
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    private val batteryLevel = mutableStateOf(0)
    private val batteryTemp = mutableStateOf(0f)
    private val batteryVoltage = mutableStateOf(0)
    private val batteryStatus = mutableStateOf(BatteryManager.BATTERY_STATUS_UNKNOWN)
    private val batteryCurrent = mutableStateOf(0)
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val volt = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                batteryLevel.value = (level * 100 / scale.toFloat()).toInt()
                batteryTemp.value = temp / 10f
                batteryVoltage.value = volt
                batteryStatus.value = status
                android.util.Log.d("BatteryDebug", "status=$status level=$level/$scale temp=$temp volt=$volt")
                // 优先方式 1: BatteryManager.getIntProperty
                try {
                    val bm = context?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    batteryCurrent.value = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
                } catch (_: Exception) {
                    batteryCurrent.value = 0
                }
            }
        }
    }
    private var isBound = false
    // Store crash log to display on next startup
    private var startupCrashLog: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        // 设置全局崩溃捕获日志
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val crashMsg = "--- CRASH LOG ---\nTime: ${java.util.Date()}\nThread: ${thread.name}\nException:\n"
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                val fullLog = crashMsg + sw.toString()
                // 写入私有目录（App 专属，无需权限，作为兜底副本）
                val privateDir = getExternalFilesDir(null)
                if (privateDir != null) {
                    runCatching { java.io.File(privateDir, "crash_$timestamp.txt").writeText(fullLog) }
                }
                // 写入公共 Download：Android 10+ 走 MediaStore（免存储权限、分区存储可靠），Android 9 走旧 File API
                runCatching {
                    val fileName = "XingLing_crash_$timestamp.txt"
                    val bytes = fullLog.toByteArray(Charsets.UTF_8)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        java.io.File(dir, fileName).writeText(fullLog)
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            // 交给系统默认处理器（弹出崩溃框并记录系统日志）
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                kotlin.system.exitProcess(1)
            }
        }
        super.onCreate(savedInstanceState)
        // 保持屏幕常亮（监控设备时屏幕不熄灭）
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 沉浸式透明状态栏与导航栏配置
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 检查是否有最新崩溃日志 (从私有目录读取)
        try {
            val privateDir = getExternalFilesDir(null)
            if (privateDir != null && privateDir.exists()) {
                val crashFiles = privateDir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") }
                if (crashFiles != null && crashFiles.isNotEmpty()) {
                    // 取最新的一个
                    val latestCrash = crashFiles.maxByOrNull { it.lastModified() }
                    if (latestCrash != null) {
                        startupCrashLog = latestCrash.readText()
                        // 为了避免每次启动都提示，可以将它们移到一个 old_crashes 文件夹或者直接删除
                        crashFiles.forEach { it.delete() }
                    }
                }
            }
        } catch (e: Exception) {}
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            XingLingTheme(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                XingLingRoot(
                    batTemp = batteryTemp.value,
                    batVolt = batteryVoltage.value,
                    batteryStatus = batteryStatus.value,
                    batCurrentNA = batteryCurrent.value,
                    initialCrashLog = startupCrashLog
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // 星灵 App 回到前台：立即结束亮屏高频刷新窗口，避免与 App 内刷新重复请求设备
        com.xingling.app.widget.WidgetFastRefreshService.stop(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }

}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XingLingApp(batTemp: Float, batVolt: Int, batteryStatus: Int, batCurrentNA: Int, initialCrashLog: String?) {
    val context = LocalContext.current
    val setupStore = remember { DeviceStore(context) }
    val coroutineScope = rememberCoroutineScope()
    val bgColor = iOSBackground
    val textColor = iOSLabel
    // 全局执行日志
    val executionLogs = remember { mutableStateListOf<String>() }
    val addLog = { msg: String ->
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        executionLogs.add(0, "[$time] $msg")
        if (executionLogs.size > 6) {
            executionLogs.removeLast()
        }
    }
    var crashDialog by remember { mutableStateOf(initialCrashLog) }
    androidx.compose.runtime.LaunchedEffect(initialCrashLog) {
        if (!initialCrashLog.isNullOrEmpty()) {
            addLog("⚠️ 发现上次崩溃日志，已保存至 下载(Download) 文件夹")
        }
    }
    val crashLogText = crashDialog
    if (!crashLogText.isNullOrEmpty()) {
        AlertDialog(
            onDismissRequest = { crashDialog = null },
            title = { Text("检测到上次崩溃", color = iOSLabel, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "崩溃日志已保存到「下载 / Download」文件夹（XingLing_crash_*.txt）。点“分享日志”可直接发送，便于定位问题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            crashLogText.take(4000),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = iOSLabel
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, crashLogText)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "分享崩溃日志")) }
                }) { Text("分享日志", color = iOSBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("XingLing_crash", crashLogText))
                    Toast.makeText(context, "崩溃日志已复制", Toast.LENGTH_SHORT).show()
                }) { Text("复制", color = iOSBlue) }
            }
        )
    }
    var signalMetrics by remember { mutableStateOf(SignalMetrics()) }
    var networkMetrics by remember { mutableStateOf(NetworkMetrics()) }
    var deviceMetrics by remember { mutableStateOf(DeviceMetrics()) }
    var trafficMetrics by remember { mutableStateOf(TrafficMetrics()) }
    var systemMetrics by remember { mutableStateOf(SystemMetrics()) }
    val signalMonitor = remember { SignalMonitor(context) }
    var selectedTab by remember { mutableStateOf(0) }
    // 轮询频率（ms）：设置 Tab 三档（约1s/5s/10s），生效于总览页轮询 delay
    var pollIntervalMs by remember { mutableStateOf(5_000L) }
    // 深层分页路由：频段/短信 Tab 进入 FeatureRoute 页面；设置 Tab 进入设置子页
    var deepRoute by remember { mutableStateOf<FeatureRoute?>(null) }
    var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    // 权限请求标记
    var permissionRequested by remember { mutableStateOf(false) }
    // 权限请求启动
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionRequested = true
        // SignalMonitor flow 会在下一轮自动重新检测权限状态
    }
    // 信号页权限请求
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && !permissionRequested) {
            val perms = mutableListOf<String>()
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_PHONE_STATE)
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (perms.isNotEmpty()) {
                permissionLauncher.launch(perms.toTypedArray())
            } else {
                permissionRequested = true
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(selectedTab, lifecycleOwner) {
        if (selectedTab == 1) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                signalMonitor.startMonitoring().collect { state ->

                signalMetrics = SignalMetrics(
                    servingCells = state.servingCells,
                    neighborCells = state.neighborCells,
                    networkMode = state.networkMode,
                    dataState = state.dataState,
                    carrierName = state.carrierName,
                    serviceState = state.serviceState,
                    aggregatedBands = state.aggregatedBands,
                    caStateText = state.caStateText
                )
                networkMetrics = NetworkMetrics(
                    subscriptionDownlink = state.subscriptionDownlink,
                    subscriptionUplink = state.subscriptionUplink,
                    qci = state.qci
                )
                deviceMetrics = DeviceMetrics(
                    deviceModel = state.deviceModel,
                    firmwareVersion = state.firmwareVersion,
                    cpuUsage = state.cpuUsage,
                    ramUsage = state.ramUsage
                )
                trafficMetrics = TrafficMetrics(
                    todayTraffic = state.todayTraffic,
                    todayDlTraffic = state.todayDlTraffic,
                    todayUlTraffic = state.todayUlTraffic,
                    monthTotalTraffic = state.monthTotalTraffic,
                    monthDlTraffic = state.monthDlTraffic,
                    monthUlTraffic = state.monthUlTraffic,
                    monthDlPercent = state.monthDlPercent,
                    wifiTodayTraffic = state.wifiTodayTraffic,
                    wifiMonthTotalTraffic = state.wifiMonthTotalTraffic,
                    wifiMonthDlTraffic = state.wifiMonthDlTraffic,
                    wifiMonthUlTraffic = state.wifiMonthUlTraffic
                )
                systemMetrics = SystemMetrics(
                    uptimeText = state.uptimeText,
                    lastUpdateTime = state.lastUpdateTime
                )

            }
        }
    }
}
    // Sensors no longer used: 仅保留信号页面
    // ═══════════════════════════════════════════
    // 免责声明检查已移除：仅保留信号页面
    // ═══════════════════════════════════════════
    val backend = BackendProvider.backend
    val onAddDevice = { BackendProvider.requestSetup = true }
    val onOpenFeature: (FeatureRoute) -> Unit = { deepRoute = it }
    val onOpenSettings: (SettingsPage) -> Unit = { settingsPage = it }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // ═══ 5 Tab 主界面（常驻底层；二级页以覆盖层叠加在上层，不销毁主界面，从而保留各 Tab 列表滚动位置）═══
        Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = bgColor
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .padding(paddingValues)
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            when (selectedTab) {
                                0 -> OverviewHome(
                                    backend = backend,
                                    onAddDevice = onAddDevice,
                                    pollIntervalMs = pollIntervalMs
                                )
                                1 -> SignalScreen(
                                    executionLogs,
                                    signalMetrics, networkMetrics, deviceMetrics, trafficMetrics, systemMetrics,
                                    context, coroutineScope, addLog,
                                    backend = backend
                                )
                                2 -> BandTabContent(backend, onOpenFeature = onOpenFeature)
                                3 -> SmsTabContent(backend, onOpenFeature = onOpenFeature)
                                4 -> SettingsTabContent(
                                    backend = backend,
                                    onAddDevice = onAddDevice,
                                    onOpenSettingsPage = onOpenSettings,
                                    onOpenFeature = onOpenFeature,
                                    pollIntervalMs = pollIntervalMs,
                                    onPollIntervalChange = { pollIntervalMs = it }
                                )
                            }
                            // 底部留白避开悬浮胶囊 dock
                            Spacer(modifier = Modifier.height(160.dp))
                        }
                    }
                }
                // ═══ 悬浮胶囊底栏（浮于内容之上，iOS 玻璃质感）═══
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .shadow(12.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(iOSCardBackground.copy(alpha = 0.92f))
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CapsuleNavItem("总览", Icons.Filled.Home, selectedTab == 0) { selectedTab = 0 }
                        CapsuleNavItem("信号", Icons.Filled.Phone, selectedTab == 1) { selectedTab = 1 }
                        CapsuleNavItem("频段", Icons.Filled.Build, selectedTab == 2) { selectedTab = 2 }
                        CapsuleNavItem("短信", Icons.Filled.Email, selectedTab == 3) { selectedTab = 3 }
                        CapsuleNavItem("设置", Icons.Filled.Settings, selectedTab == 4) { selectedTab = 4 }
                    }
                }

        // ═══ 全屏深层分页（覆盖在主界面之上；主界面保留在底层，列表滚动位置与状态不丢失）═══
        if (deepRoute != null) {
            val route: FeatureRoute = deepRoute!!
            // 系统返回手势/按键 → 返回上一级（而非退出整个 App）
            BackHandler { deepRoute = null }
            Box(modifier = Modifier.fillMaxSize()) {
                FeatureRouteHost(
                    route = route,
                    backend = backend,
                    onBack = { deepRoute = null },
                    onAddDevice = onAddDevice
                )
            }
        }
        if (settingsPage != null) {
            // 系统返回手势/按键 → 返回设置 Tab（而非退出整个 App）
            BackHandler { settingsPage = null }
            Box(modifier = Modifier.fillMaxSize()) {
                when (settingsPage) {
                    SettingsPage.AT -> AtTerminalScreen(backend, onBack = { settingsPage = null }, onAddDevice = onAddDevice)
                    SettingsPage.WIDGET -> WidgetPreviewScreen(onBack = { settingsPage = null })
                    SettingsPage.NICKNAME -> NicknameScreen(backend, onBack = { settingsPage = null }, onAddDevice = onAddDevice)
                    SettingsPage.PROXY -> ProxyDebugScreen(backend, onBack = { settingsPage = null }, onAddDevice = onAddDevice)
                    else -> {}
                }
            }
        }

        // ═══ 添加设备覆盖层（保留 Tab 与深层路由栈，返回回到上一级而非退出 App）═══
        if (BackendProvider.requestSetup) {
            BackHandler { BackendProvider.requestSetup = false }
            DeviceSetupScreen(
                store = setupStore,
                onConfigured = {
                    BackendProvider.publish(
                        UfiToolsBackend(
                            setupStore.host,
                            setupStore.port,
                            setupStore.token.ifBlank { "admin" },
                            setupStore.zteToken
                        )
                    )
                    BackendProvider.requestSetup = false
                },
                onBack = { BackendProvider.requestSetup = false }
            )
        }
    }
}

// ======================= 悬浮胶囊导航项 =======================
@Composable
private fun CapsuleNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val fg = if (selected) iOSBlue else iOSNavUnselected
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) iOSBlue.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
// ======================= REUSABLE COMPONENTS =======================
@Composable
fun ExecutionLogCard(executionLogs: List<String>) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("实时执行日志 (全部)", style = MaterialTheme.typography.titleLarge, color = iOSLabel)
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("XingLing_Logs", executionLogs.joinToString("\n"))
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "日志已成功制到剴板！", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("复制日志 📋", color = iOSBlue, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (executionLogs.isEmpty()) {
                        Text("暂无操作日志...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        Column {
                            executionLogs.forEach { log ->
                                Text(log, style = MaterialTheme.typography.bodySmall, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("关闭", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("实时执行日志", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                Text("点击展开全屏 🔍", style = MaterialTheme.typography.bodySmall, color = iOSBlue)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(iOSBackground, shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (executionLogs.isEmpty()) {
                    Text("暂无操作...", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                } else {
                    Column {
                        executionLogs.forEach { log ->
                            Text(log, style = MaterialTheme.typography.bodySmall, color = iOSLabel, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}
// ======================= SCREENS =======================
