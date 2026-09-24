/*
 * 星灵 (XingLing) · 设备总览页（真实数据流 · 图形化重构版）
 *
 * 数据来源：
 *   - /api/baseDeviceInfo（电量/温度/CPU/内存/存储/流量/8核频率）
 *   - goform network_information（信号 RSRP/SINR/RSRQ/PCI/频段）
 *
 * 卡片严格对齐 DeviceAndSignalGaugeCard / CarrierAggregationCard 风格：
 *   Card(20dp, iOSCardBackground, 2dp) + padding 18dp + iOSFill 内卡 14dp
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.AccessControl
import com.xingling.app.backend.BackendOverview
import com.xingling.app.backend.BackendSignalInfo
import com.xingling.app.backend.CpuCoreStat
import com.xingling.app.backend.DataLimit
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.LanClient
import com.xingling.app.backend.ScheduledTask
import com.xingling.app.backend.WifiApInfo
import com.xingling.app.signal.CellType
import com.xingling.app.signal.DeviceMetrics
import com.xingling.app.signal.SignalInfo
import com.xingling.app.signal.SignalMetrics
import com.xingling.app.ui.feature.FeatureRoute
import com.xingling.app.ui.feature.StatusPill
import com.xingling.app.ui.theme.GlassCard
import com.xingling.app.ui.theme.iOSButton
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSCardBackground
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// ================= 设备总览页 =================
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun OverviewScreen(
    backend: DeviceBackend?,
    onAddDevice: () -> Unit,
    onOpenFeature: (FeatureRoute) -> Unit = {},
    pollIntervalMs: Long = 5_000L
) {
    if (backend == null) {
        EmptyDeviceState(onAddDevice = onAddDevice)
        return
    }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var overview by remember { mutableStateOf<BackendOverview?>(null) }
    var signal by remember { mutableStateOf<BackendSignalInfo?>(null) }
    var loadedOnce by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var showTrafficDialog by remember { mutableStateOf(false) }

    // 当日流量内存采样（最近 60 次）
    val dailyBytesHistory = remember { mutableStateListOf<Long>() }
    // CPU / 内存占用内存采样（最近 60 次）
    val cpuHistory = remember { mutableStateListOf<Float>() }
    val memHistory = remember { mutableStateListOf<Float>() }

    // ---- 实时速率 ----
    // 首选：固件 realtime_rx_thrpt / realtime_tx_thrpt（单位 B/s，与官方前端同口径）
    // 回退：当日累计流量 daily_data 差值估算（固件不支持时）
    var rxBps by remember { mutableLongStateOf(0L) }
    var txBps by remember { mutableLongStateOf(0L) }
    var txKnown by remember { mutableStateOf(false) }
    var firmwareSpeed by remember { mutableStateOf(false) }
    var fallbackBps by remember { mutableLongStateOf(0L) }
    // 差值法回退用：上一次字节计数与时间戳
    var lastDailyBytes by remember { mutableLongStateOf(-1L) }
    var lastTickMs by remember { mutableLongStateOf(0L) }
    // 固件速率 3 点滑动窗口，用于中位数平滑
    val rxSamples = remember { mutableStateListOf<Long>() }
    val txSamples = remember { mutableStateListOf<Long>() }
    var hasDisconnectedForExceed by remember { mutableStateOf(false) }
    var hasAttemptedBackendSync by remember { mutableStateOf(false) }

    // 速率独立采样：与主轮询解耦，固定 1.5s 一采，避免被 fetchOverview 内的 AT/root 调用拖慢
    LaunchedEffect(backend) {
        var failStreak = 0
        var zeroStreak = 0
        while (true) {
            backend?.fetchRealtimeSpeed()
                ?.onSuccess { s ->
                    failStreak = 0
                    // 部分固件不实现 realtime_* 统计，字段存在但恒为 0：
                    // 连续 3 次全 0 判定为不可用，回落差值估算，避免速率卡一直是 0
                    if (s.rxBps <= 0L && s.txBps <= 0L) zeroStreak += 1 else zeroStreak = 0
                    if (zeroStreak >= 3) {
                        if (firmwareSpeed) {
                            firmwareSpeed = false
                            txKnown = false
                            rxSamples.clear()
                            txSamples.clear()
                        }
                    } else {
                        if (!firmwareSpeed) {
                            // 首次/恢复拿到固件数据：清空差值法残留窗口
                            rxSamples.clear()
                            txSamples.clear()
                        }
                        firmwareSpeed = true
                        txKnown = true
                        rxBps = smoothSpeed(rxSamples, s.rxBps)
                        txBps = smoothSpeed(txSamples, s.txBps)
                    }
                }
                ?.onFailure {
                    // 单次失败不切换，连续 3 次失败才判定固件不支持 → 回落差值估算
                    failStreak += 1
                    if (failStreak >= 3 && firmwareSpeed) {
                        firmwareSpeed = false
                        txKnown = false
                        rxSamples.clear()
                        txSamples.clear()
                    }
                }
            delay(SPEED_POLL_MS)
        }
    }

    LaunchedEffect(backend, pollIntervalMs) {
        while (true) {
            val now = System.currentTimeMillis()
            val ov = backend.fetchOverview()
            ov.onSuccess { rawOv ->
                val prefs = context.getSharedPreferences("XingLingPrefs", android.content.Context.MODE_PRIVATE)
                val limitEnabled = if (prefs.contains("local_data_limit_enabled")) prefs.getBoolean("local_data_limit_enabled", false) else rawOv.dataLimitEnabled
                val limitMaxBytes = if (prefs.contains("local_data_limit_max_bytes")) prefs.getLong("local_data_limit_max_bytes", -1L) else rawOv.dataLimitMaxBytes
                val offset = prefs.getLong("local_traffic_offset_bytes", 0L)

                val adjustedOv = rawOv.copy(
                    dataLimitEnabled = limitEnabled,
                    dataLimitMaxBytes = limitMaxBytes,
                    monthlyBytes = if (rawOv.monthlyBytes >= 0) rawOv.monthlyBytes + offset else -1L,
                    dailyBytes = if (rawOv.dailyBytes >= 0) rawOv.dailyBytes + offset else -1L
                )

                overview = adjustedOv
                
                if (limitEnabled && limitMaxBytes > 0 && adjustedOv.monthlyBytes >= limitMaxBytes) {
                    if (!hasDisconnectedForExceed) {
                        hasDisconnectedForExceed = true
                        runCatching { backend.features.toggleCellularData() }
                    }
                } else {
                    hasDisconnectedForExceed = false
                }
                
                // 双保险：静默同步本地配置到高级后台
                if (prefs.contains("local_data_limit_max_bytes") && 
                    (rawOv.dataLimitMaxBytes != limitMaxBytes || rawOv.dataLimitEnabled != limitEnabled)) {
                    if (!hasAttemptedBackendSync) {
                        hasAttemptedBackendSync = true
                        launch {
                            runCatching {
                                backend.features.setDataLimit(
                                    com.xingling.app.backend.DataLimit(
                                        enabled = limitEnabled,
                                        maxLimit = if (limitMaxBytes > 0) limitMaxBytes.toString() else "-1",
                                        period = "monthly",
                                        checkReference = "system",
                                        statusForwardEnabled = limitEnabled
                                    )
                                )
                            }
                        }
                    }
                }

                dailyBytesHistory.add(adjustedOv.dailyBytes)
                while (dailyBytesHistory.size > 60) dailyBytesHistory.removeAt(0)
                if (adjustedOv.cpuUsage >= 0f) {
                    cpuHistory.add(adjustedOv.cpuUsage)
                    while (cpuHistory.size > 60) cpuHistory.removeAt(0)
                }
                if (adjustedOv.memUsage >= 0f) {
                    memHistory.add(adjustedOv.memUsage)
                    while (memHistory.size > 60) memHistory.removeAt(0)
                }
                // 差值法回退：仅在固件实时速率不可用时使用
                // daily_data 为分钟级累计量，短窗差值天然粗糙，故不再作为主数据源
                if (lastDailyBytes >= 0 && lastTickMs > 0 && adjustedOv.dailyBytes >= 0) {
                    val dt = (now - lastTickMs) / 1000.0
                    if (dt >= 1.0) {
                        val db = adjustedOv.dailyBytes - lastDailyBytes
                        if (db >= 0) fallbackBps = (db / dt).toLong()
                    }
                }
                // 仅在计数有效时更新基线，避免 -1 覆盖基线导致后续速率永久停更
                if (adjustedOv.dailyBytes >= 0) {
                    lastDailyBytes = adjustedOv.dailyBytes
                    lastTickMs = now
                }
            }
            ov.onFailure { e -> lastError = e.message }
            backend.fetchSignalInfo().onSuccess { signal = it }
            loadedOnce = true
            delay(pollIntervalMs)
        }
    }

    val ov = overview
    val sg = signal
    val signalMetrics = sg?.toSignalMetrics() ?: SignalMetrics()
    val deviceMetrics = ov?.toDeviceMetrics() ?: DeviceMetrics()

    val isRefreshing = remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing.value,
        onRefresh = { isRefreshing.value = true }
    )

    LaunchedEffect(isRefreshing.value) {
        if (isRefreshing.value) {
            val now = System.currentTimeMillis()
            val ovReq = backend?.fetchOverview()
            ovReq?.onSuccess { rawOv ->
                val prefs = context.getSharedPreferences("XingLingPrefs", android.content.Context.MODE_PRIVATE)
                val limitEnabled = if (prefs.contains("local_data_limit_enabled")) prefs.getBoolean("local_data_limit_enabled", false) else rawOv.dataLimitEnabled
                val limitMaxBytes = if (prefs.contains("local_data_limit_max_bytes")) prefs.getLong("local_data_limit_max_bytes", -1L) else rawOv.dataLimitMaxBytes
                val offset = prefs.getLong("local_traffic_offset_bytes", 0L)

                val adjustedOv = rawOv.copy(
                    dataLimitEnabled = limitEnabled,
                    dataLimitMaxBytes = limitMaxBytes,
                    monthlyBytes = if (rawOv.monthlyBytes >= 0) rawOv.monthlyBytes + offset else -1L,
                    dailyBytes = if (rawOv.dailyBytes >= 0) rawOv.dailyBytes + offset else -1L
                )
                overview = adjustedOv
                if (adjustedOv.dailyBytes >= 0) {
                    lastDailyBytes = adjustedOv.dailyBytes
                    lastTickMs = now
                }
            }
            ovReq?.onFailure { e -> lastError = e.message }
            backend?.fetchSignalInfo()?.onSuccess { signal = it }
            loadedOnce = true
            isRefreshing.value = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // 页面标题 + 设备状态
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("设备总览", style = MaterialTheme.typography.headlineMedium, color = iOSLabel)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    ov?.let { "${it.model} · ${backend.deviceAddress}" } ?: "星灵移动网络控制中枢",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val ok = ov != null
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (ok) iOSGreen else iOSOrange)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))

        if (lastError != null && ov == null) {
            Text(
                "连接异常：$lastError（将自动重试）",
                style = MaterialTheme.typography.labelSmall,
                color = iOSRed
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 0. 网络状态+签约速率合并卡片
        if (ov != null) NetworkStatusCard(
            carrierName = sg?.carrierName ?: "中国移动",
            networkType = sg?.networkType ?: "5G SA",
            bands = sg?.band?.uppercase() ?: "N41+N28",
            rsrp = sg?.rsrp ?: Int.MIN_VALUE,
            signalLevel = ov.signalBars,
            dlMaxMbps = ov.dlMaxMbps,
            ulMaxMbps = ov.ulMaxMbps,
            qci = ov.qci
        )

        // 1. 信号总览卡片（RSRP 环形仪表 + 设备/性能）—— 不动
        DeviceAndSignalGaugeCard(signalMetrics, deviceMetrics, cpuTemp = ov?.cpuTemp ?: -1f, netType = sg?.networkType ?: "--", uptimeSec = ov?.uptimeSec ?: -1L)

        // 2. 载波聚合卡片（PCC + SCC）—— 不动，右上角带评分
        val scoreText = if (sg != null && loadedOnce) {
            val total = (scoreRsrp(sg.rsrp) * 0.45f + scoreSinr(sg.sinr) * 0.30f + scoreRsrq(sg.rsrq) * 0.25f).toInt()
            val label = when {
                total >= 85 -> "信号极佳"
                total >= 70 -> "信号良好"
                total >= 50 -> "信号一般"
                else -> "信号较差"
            }
            "$label $total/100"
        } else ""
        val scoreColor = if (sg != null && loadedOnce) {
            val total = (scoreRsrp(sg.rsrp) * 0.45f + scoreSinr(sg.sinr) * 0.30f + scoreRsrq(sg.rsrq) * 0.25f).toInt()
            when {
                total >= 85 -> iOSGreen
                total >= 70 -> iOSBlue
                total >= 50 -> iOSOrange
                else -> iOSRed
            }
        } else iOSSecondaryLabel
// 3. 载波聚合统一卡（PCC/SCC 实测数据 + 一键检测并优化，合并原 CA 三卡）
CaUnifiedCard(signalMetrics, connected = ov != null, backend = backend, qci = ov?.qci ?: "9")

// 4. 设备监控图形卡
if (ov != null) DeviceMonitorCard(ov, cpuHistory.toList(), memHistory.toList())

// 当前速率卡（固件实时速率优先，失败回落差值估算）
SpeedCard(
    rxBps = if (firmwareSpeed) rxBps else fallbackBps,
    txBps = txBps,
    txKnown = txKnown,
    firmwareSpeed = firmwareSpeed,
    ov = ov,
    backend = backend
)


        // 4. 实时网速卡（上下行箭头 + 大数字）

if (showTrafficDialog && ov != null) {
    TrafficConfigDialog(
        ov = ov,
        backend = backend,
        onDismiss = { showTrafficDialog = false },
        onUpdated = {
            scope.launch {
                backend.fetchOverview().onSuccess { overview = it }
            }
        }
    )
}

// 流量概览+趋势合并卡（点击可弹出设置已用流量/总流量上限/超额关网对话框）
if (ov != null) TrafficCard(ov, dailyBytesHistory.toList(), onOpenTrafficConfig = { showTrafficDialog = true })


// WiFi 热点信息卡（卡片内直接操作：开关 / SSID / 密码 / 连接数 / 广播隔离）
HotspotOverviewCard(backend)




        // 10. CPU / 内存占用趋势折线

        // 5. 电源控制卡（页内直接操作：立即重启 / 立即关机，不跳二级页）
        PowerControlOverviewCard(backend)

        // 7. 定时任务卡（页内直接查看 / 新增 / 删除，不跳二级页）
        ScheduledTasksOverviewCard(backend)
    }
        PullRefreshIndicator(
            refreshing = isRefreshing.value,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = iOSBlue
        )
    }
}

// ================= 空态 / 引导 =================
@Composable
private fun EmptyDeviceState(onAddDevice: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("尚未添加设备", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = iOSLabel)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "请先前往添加设备页，录入设备地址与后台口令，\n连接成功后即可在这里看到实时信号、流量与设备健康数据。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = iOSSecondaryLabel,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onAddDevice,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("去添加设备", fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }
}

// ================= 数据转换 =================
private fun BackendSignalInfo.toSignalMetrics(): SignalMetrics {
    val networkTypeText = if (networkType.isBlank()) "--" else networkType
    val cellType = when {
        networkTypeText.contains("NR", true) || networkTypeText.contains("5G", true) -> CellType.NR
        networkTypeText.contains("LTE", true) || networkTypeText.contains("4G", true) -> CellType.LTE
        else -> CellType.UNKNOWN
    }
    val serving = if (isKnown) {
        listOf(
            SignalInfo(
                type = cellType,
                isRegistered = true,
                pci = pci,
                earfcn = arfcn,
                band = band,
                bandwidth = "",
                rsrp = rsrp,
                sinr = sinr,
                rsrq = rsrq,
                rssi = rssi
            )
        )
    } else emptyList()
    return SignalMetrics(
        servingCells = serving,
        networkMode = networkTypeText,
        carrierName = carrierName.ifBlank { "--" },
        aggregatedBands = if (band.isBlank()) "--" else band,
        caStateText = if (serving.isEmpty()) "--" else "Serving"
    )
}

private fun BackendOverview.toDeviceMetrics(): DeviceMetrics = DeviceMetrics(
    deviceModel = model.ifBlank { "未知设备" },
    firmwareVersion = if (appVer.isBlank()) "--" else "v$appVer",
    cpuUsage = if (cpuUsage >= 0) cpuUsage else -1f,
    ramUsage = if (memUsage >= 0) memUsage else -1f
)

// ================= 3. 信号综合评分卡 =================
@Composable
private fun SignalScoreCard(sg: BackendSignalInfo) {
    // 三级评分
    val rsrpScore = scoreRsrp(sg.rsrp)
    val sinrScore = scoreSinr(sg.sinr)
    val rsrqScore = scoreRsrq(sg.rsrq)
    val total = (rsrpScore * 0.45f + sinrScore * 0.30f + rsrqScore * 0.25f).toInt()

    val (overallText, overallColor) = when {
        total >= 85 -> "信号极佳" to iOSGreen
        total >= 70 -> "信号良好" to iOSBlue
        total >= 50 -> "信号一般" to iOSOrange
        else -> "信号较差" to iOSRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("信号质量评分", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = overallColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        "$overallText $total/100",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = overallColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                ScoreDial("RSRP", sg.rsrp, "dBm", scoreRsrp(sg.rsrp).toInt(), iOSGreen, Modifier.weight(1f))
                ScoreDial("SINR", sg.sinr, "dB", scoreSinr(sg.sinr).toInt(), iOSBlue, Modifier.weight(1f))
                ScoreDial("RSRQ", sg.rsrq, "dB", scoreRsrq(sg.rsrq).toInt(), iOSOrange, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "评分权重：RSRP 45% · SINR 30% · RSRQ 25%",
                style = MaterialTheme.typography.labelSmall,
                color = iOSSecondaryLabel
            )
        }
    }
}

internal fun scoreRsrp(v: Int): Float = when {
    v == Int.MIN_VALUE -> 0f
    v >= -75 -> 100f
    v >= -85 -> 80f
    v >= -95 -> 60f
    v >= -105 -> 40f
    else -> 20f
}
internal fun scoreSinr(v: Int): Float = when {
    v == Int.MIN_VALUE -> 0f
    v >= 20 -> 100f
    v >= 13 -> 75f
    v >= 0 -> 50f
    else -> 25f
}
internal fun scoreRsrq(v: Int): Float = when {
    v == Int.MIN_VALUE -> 0f
    v >= -5 -> 100f
    v >= -10 -> 75f
    v >= -15 -> 50f
    else -> 25f
}

@Composable
private fun ScoreDial(label: String, value: Int, unit: String, score: Int, color: Color, modifier: Modifier) {
    val trackColor = iOSSeparator
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            Canvas(modifier = Modifier.size(72.dp)) {
                val strokeW = 6.dp.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - strokeW, size.height - strokeW
                )
                val topLeft = Offset(strokeW / 2, strokeW / 2)
                drawArc(
                    color = trackColor, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                val fraction = (score / 100f).coerceIn(0f, 1f)
                drawArc(
                    color = color, startAngle = 135f, sweepAngle = 270f * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (value == Int.MIN_VALUE) "--" else "$value",
                    style = MaterialTheme.typography.titleMedium,
                    color = iOSLabel
                )
                Text(unit, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = iOSLabel)
        Text("$score 分", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ================= 4. 实时网速卡 =================

/** 速率独立采样周期：比主轮询快，保证读数跟得上真实速率 */
private const val SPEED_POLL_MS = 1_500L

/** 3 点滑动窗口中位数：压掉固件偶发的 0 值与单点尖峰 */
private fun smoothSpeed(samples: MutableList<Long>, value: Long): Long {
    samples.add(value)
    while (samples.size > 3) samples.removeAt(0)
    val sorted = samples.sorted()
    return sorted[sorted.size / 2]
}

/** B/s → 展示串：MB/s 按 1024²；Mbps 按 10⁶（与运营商口径一致） */
private fun formatSpeed(bps: Long, useMbps: Boolean): String =
    if (useMbps) String.format("%.1f Mbps", bps.toDouble() * 8.0 / 1_000_000.0)
    else String.format("%.2f MB/s", bps.toDouble() / 1_048_576.0)

/**
 * 单位切换分段控件：MB/s ⇄ Mbps。
 * 整块（含两个分段）均可点击，热区足够大，避免纯文字标签点不中的问题；
 * 选中项实心高亮，切换结果一眼可见。
 */
@Composable
private fun UnitSwitch(useMbps: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(iOSBlue.copy(alpha = 0.08f))
            .border(1.dp, iOSBlue.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UnitChip("MB/s", active = !useMbps)
        UnitChip("Mbps", active = useMbps)
    }
}

@Composable
private fun UnitChip(text: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) iOSBlue else Color.Transparent
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else iOSBlue,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SpeedCard(
    rxBps: Long,
    txBps: Long,
    txKnown: Boolean,
    firmwareSpeed: Boolean,
    ov: BackendOverview?,
    backend: DeviceBackend?
) {
    var useMbps by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("当前速率", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (firmwareSpeed) iOSBlue else iOSOrange).copy(alpha = 0.12f)
                ) {
                    Text(
                        if (firmwareSpeed) "固件实时" else "差值估算",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (firmwareSpeed) iOSBlue else iOSOrange
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                UnitSwitch(useMbps) { useMbps = !useMbps }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SpeedStat("⬇ 下行", formatSpeed(rxBps, useMbps), iOSBlue, Modifier.weight(1f))
                SpeedStat("⬆ 上行", if (txKnown) formatSpeed(txBps, useMbps) else "--", iOSGreen, Modifier.weight(1f))
                SpeedStat("今日", formatBytesShort(ov?.dailyBytes ?: -1), iOSLabel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SpeedStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
    }
}


// ================= 5. 电池图形卡（重写） =================
@Composable
private fun DeviceMonitorCard(ov: BackendOverview, cpuHistory: List<Float>, memHistory: List<Float>) {
    val pct = ov.battery
    val (battColor, battLabel) = when {
        pct < 0 -> iOSSecondaryLabel to "--"
        ov.isCharging -> iOSGreen to "充电中"
        pct >= 80 -> iOSGreen to "电池供电"
        pct >= 50 -> iOSBlue to "电池供电"
        pct >= 20 -> iOSOrange to "电池供电"
        else -> iOSRed to "低电量"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("设备监控", style = MaterialTheme.typography.titleLarge, color = Color(0xFFC69250))
                    Spacer(modifier = Modifier.height(2.dp))
                    DeviceInfoRow("CPU温度", if (ov.cpuTemp >= 0) "%.1f ℃".format(ov.cpuTemp) else "--")
                    DeviceInfoRow("电池温度", if (ov.batteryTemp >= 0) "%.1f ℃".format(ov.batteryTemp) else "--")
                    DeviceInfoRow("电池容量", if (ov.batteryCapacityMah > 0) "${ov.batteryCapacityMah} mAh" else "--")
                    DeviceInfoRow("电压", if (ov.batteryVoltageMv > 0) "%.2f V".format(ov.batteryVoltageMv / 1000.0) else "--")
                    DeviceInfoRow("电流", if (ov.batteryCurrentMa == Int.MIN_VALUE) "--" else "${ov.batteryCurrentMa} mA")
                    DeviceInfoRow("当日流量", if (ov.dailyBytes > 0) formatBytesShort(ov.dailyBytes) else "--")
                    DeviceInfoRow("当月流量", if (ov.monthlyBytes > 0) formatBytesShort(ov.monthlyBytes) else "--")
                }
                Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(battLabel, style = MaterialTheme.typography.labelLarge, color = battColor)
                    Spacer(modifier = Modifier.height(10.dp))
                    val trackColor = iOSSeparator
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                        Canvas(modifier = Modifier.size(100.dp)) {
                            val strokeW = 10.dp.toPx()
                            val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeW, size.height - strokeW)
                            val topLeft = Offset(strokeW / 2, strokeW / 2)
                            drawArc(color = trackColor, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                                topLeft = topLeft, size = arcSize, style = Stroke(width = strokeW, cap = StrokeCap.Round))
                            if (pct in 0..100) {
                                val frac = pct / 100f
                                drawArc(color = battColor, startAngle = 135f, sweepAngle = 270f * frac, useCenter = false,
                                    topLeft = topLeft, size = arcSize, style = Stroke(width = strokeW, cap = StrokeCap.Round))
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (pct >= 0) "$pct%" else "--", style = MaterialTheme.typography.headlineMedium, color = iOSLabel)
                            Text("当前电量", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val battStatusText = when {
                        ov.isCharging && pct >= 100 -> "已充满"
                        ov.isCharging -> "充电中"
                        pct >= 95 -> "电量充足"
                        pct >= 60 -> "电量良好"
                        pct >= 30 -> "电量一般"
                        pct >= 15 -> "电量偏低"
                        else -> "电量极低"
                    }
                    val battStatusColor = when {
                        ov.isCharging -> iOSGreen
                        pct >= 60 -> iOSGreen
                        pct >= 30 -> iOSOrange
                        else -> iOSRed
                    }
                    Text(battStatusText, style = MaterialTheme.typography.labelSmall, color = battStatusColor)
                    if (ov.isCharging && ov.batteryCapacityMah > 0 && ov.batteryCurrentMa > 0 && pct in 0..99) {
                        val remainMah = ov.batteryCapacityMah * (100 - pct) / 100.0f
                        // 电池电流已是净充电电流（扣掉系统耗电），直接算
                        val remainMin = (remainMah / ov.batteryCurrentMa * 60).toInt()
                        val h = remainMin / 60
                        val m = remainMin % 60
                        val roundedMin = (m / 10) * 10
                        val timeStr = if (h > 0) "${h}小时${roundedMin}分" else "${roundedMin}分"
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("预计充满 $timeStr", style = MaterialTheme.typography.labelSmall, color = iOSGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = iOSSeparator, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CPU / 内存占用率", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
                Spacer(modifier = Modifier.width(4.dp))
                Text("CPU", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(iOSOrange))
                Spacer(modifier = Modifier.width(4.dp))
                Text("内存", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                // Y轴标签
                Column(verticalArrangement = Arrangement.SpaceBetween) {
                    Text("100", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
                }
                Spacer(modifier = Modifier.width(6.dp))
                val cpuColor = iOSBlue
                val memColor = iOSOrange
                val axisColor = iOSSecondaryLabel
                val gridColor = iOSSeparator
                // 图表
                Canvas(modifier = Modifier.weight(1f).height(100.dp)) {
                    val w = size.width
                    val h = size.height
                    val sw = 2.5f
                    // L形边框（左边+底边）
                    drawLine(axisColor, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1.dp.toPx())
                    drawLine(axisColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
                    // 50%虚线
                    // 20/40/60/80%参考线
                    for (v in listOf(20f, 40f, 60f, 80f)) {
                        drawLine(gridColor, Offset(0f, h - v/100*h), Offset(w, h - v/100*h), strokeWidth = 0.5.dp.toPx())
                    }
                    drawLine(gridColor, Offset(0f, h/2), Offset(w, h/2), strokeWidth = 1.dp.toPx())
                    // CPU线
                    if (cpuHistory.size >= 2) {
                        val p = androidx.compose.ui.graphics.Path()
                        cpuHistory.forEachIndexed { i, v ->
                            val x = w * i / (cpuHistory.size - 1)
                            val y = h - (v.coerceIn(0f, 100f) / 100f) * h
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        drawPath(p, color = cpuColor, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    // 内存线
                    if (memHistory.size >= 2) {
                        val p = androidx.compose.ui.graphics.Path()
                        memHistory.forEachIndexed { i, v ->
                            val x = w * i / (memHistory.size - 1)
                            val y = h - (v.coerceIn(0f, 100f) / 100f) * h
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        drawPath(p, color = memColor, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("最近3分钟", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
            }
        }
    }
}

@Composable
private fun BatteryParam(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
        Text(value, style = MaterialTheme.typography.labelMedium, color = iOSLabel)
    }
}


private fun storagePct(ov: BackendOverview): Float =
    if (ov.storageUsedMb > 0 && ov.storageTotalMb > 0)
        ov.storageUsedMb.toFloat() / ov.storageTotalMb * 100f else -1f

@Composable
private fun GaugeStat(label: String, pct: Float, color: Color, modifier: Modifier) {
    val trackColor = iOSSeparator
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            Canvas(modifier = Modifier.size(64.dp)) {
                val strokeW = 7.dp.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - strokeW, size.height - strokeW
                )
                val topLeft = Offset(strokeW / 2, strokeW / 2)
                drawArc(
                    color = trackColor, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                if (pct >= 0) {
                    val fraction = (pct / 100f).coerceIn(0f, 1f)
                    drawArc(
                        color = color, startAngle = 135f, sweepAngle = 270f * fraction, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                if (pct >= 0) "%.0f%%".format(pct) else "--",
                style = MaterialTheme.typography.titleMedium,
                color = iOSLabel
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = iOSLabel)
    }
}

// ================= 7. 当前服务小区卡 =================
@Composable
private fun ServingCellCard(sg: BackendSignalInfo, dailyBytesHistory: List<Long> = emptyList()) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("当前服务小区", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iOSBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        sg.carrierName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = iOSBlue
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iOSFill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CellInfoItem("网络", sg.networkType)
                        CellInfoItem("频段", sg.band.uppercase().ifEmpty { "--" })
                        CellInfoItem("PCI", if (sg.pci > 0) "${sg.pci}" else "--")
                        CellInfoItem("频点", if (sg.arfcn > 0) "${sg.arfcn}" else "--")
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = iOSSeparator, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text("当日流量趋势", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
            Spacer(modifier = Modifier.height(6.dp))
            val flowColor = iOSBlue
            Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                val w = size.width
                val h = size.height
                val maxBytes = dailyBytesHistory.maxOrNull() ?: 1L
                
                if (dailyBytesHistory.size >= 2) {
                    val p = androidx.compose.ui.graphics.Path()
                    dailyBytesHistory.forEachIndexed { idx, v ->
                        val x = w * idx / (dailyBytesHistory.size - 1)
                        val y = h - (v.toFloat() / maxBytes.toFloat()) * h
                        if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, color = flowColor, style = Stroke(2.5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun CellInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = iOSLabel, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
    }
}

// ================= CPU / 内存占用趋势折线（保留） =================
@Composable
private fun CpuMemTrendCard(cpuHistory: List<Float>, memHistory: List<Float>) {
    val sepColor = iOSSeparator
    val cpuColor = iOSBlue
    val memColor = iOSOrange
    val sampleCount = maxOf(cpuHistory.size, memHistory.size)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("CPU / 内存占用趋势", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "基于轮询 baseDeviceInfo 内存采样（最近 $sampleCount 次）",
                style = MaterialTheme.typography.labelSmall,
                color = iOSSecondaryLabel
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(cpuColor, "CPU 占用")
                Spacer(modifier = Modifier.width(14.dp))
                LegendDot(memColor, "内存占用")
                Text(
                    "CPU ${percentText(cpuHistory.lastOrNull())} · 内存 ${percentText(memHistory.lastOrNull())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSSecondaryLabel
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = f * size.height
                    drawLine(sepColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                if (cpuHistory.size >= 2) drawPercentLine(cpuHistory, cpuColor, 2.5.dp.toPx())
                if (memHistory.size >= 2) drawPercentLine(memHistory, memColor, 2.5.dp.toPx())
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "纵向为 0~100% 占用率，横向为最近 60 次轮询采样；数据来自 /api/baseDeviceInfo。",
                style = MaterialTheme.typography.labelSmall,
                color = iOSSecondaryLabel
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPercentLine(
    history: List<Float>,
    color: Color,
    strokeWidth: Float
) {
    fun yFor(v: Float): Float = (1f - (v / 100f).coerceIn(0f, 1f)) * size.height
    val step = size.width / (history.size - 1).toFloat()
    val path = Path()
    history.forEachIndexed { i, v ->
        val x = i * step
        val y = yFor(v)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    drawCircle(color, 4.dp.toPx(), Offset(0f, yFor(history.first())))
    drawCircle(color, 4.dp.toPx(), Offset(size.width, yFor(history.last())))
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
    }
}

private fun percentText(v: Float?): String =
    if (v == null || v < 0f) "--" else String.format(Locale.US, "%.1f%%", v)

@Composable
private fun TrafficCard(ov: BackendOverview, history: List<Long>, onOpenTrafficConfig: () -> Unit = {}) {
    val monthlyTotal = if (ov.monthlyDlBytes > 0 || ov.monthlyUlBytes > 0) ov.monthlyDlBytes + ov.monthlyUlBytes else ov.monthlyBytes
    val dlPct = if (monthlyTotal > 0 && ov.monthlyDlBytes > 0) (ov.monthlyDlBytes * 100 / monthlyTotal).toInt() else 0
    val ulPct = if (monthlyTotal > 0 && ov.monthlyUlBytes > 0) (ov.monthlyUlBytes * 100 / monthlyTotal).toInt() else 0
    val progress = if (ov.dataLimitMaxBytes > 0 && monthlyTotal > 0) (monthlyTotal.toFloat() / ov.dataLimitMaxBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val uptimeText = if (ov.uptimeSec > 0) {
        val days = ov.uptimeSec / 86400
        val hours = (ov.uptimeSec % 86400) / 3600
        val mins = (ov.uptimeSec % 3600) / 60
        "${days}天 ${hours}小时 ${mins}分"
    } else "--"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTrafficConfig() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTrafficConfig() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("流量详情", style = MaterialTheme.typography.titleMedium, color = iOSLabel, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("今日已用 ${formatBytesShort(ov.dailyBytes)}", style = MaterialTheme.typography.bodyMedium, color = iOSSecondaryLabel)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = iOSBlue.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "设置/校准 ›",
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .clickable { onOpenTrafficConfig() },
                            style = MaterialTheme.typography.labelSmall,
                            color = iOSBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatGb(monthlyTotal),
                            style = MaterialTheme.typography.headlineLarge,
                            color = iOSLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GB", style = MaterialTheme.typography.titleMedium, color = iOSSecondaryLabel, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(iOSBlue))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("本月下载", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${formatGb(ov.monthlyDlBytes)} GB", style = MaterialTheme.typography.bodyMedium, color = iOSLabel, fontWeight = FontWeight.Medium)
                        if (dlPct > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$dlPct%", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(iOSOrange))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("本月上传", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${formatGb(ov.monthlyUlBytes)} GB", style = MaterialTheme.typography.bodyMedium, color = iOSLabel, fontWeight = FontWeight.Medium)
                        if (ulPct > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$ulPct%", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (ov.dataLimitMaxBytes > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(iOSFill)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(if (ov.dataLimitEnabled && progress >= 1f) iOSRed else iOSBlue)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "已用 ${(progress * 100).toInt()}% / 上限 ${formatGb(ov.dataLimitMaxBytes)} GB",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                    if (ov.dataLimitEnabled) {
                        Text(
                            "超额自动关网开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress >= 1f) iOSRed else iOSGreen
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(iOSFill)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "未设置流量上限（点击卡片即可设置月度套餐上限与校准用量）",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("运行时间", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                Text(uptimeText, style = MaterialTheme.typography.bodyMedium, color = iOSLabel, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TrafficConfigDialog(
    ov: BackendOverview,
    backend: DeviceBackend?,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var usedGbText by remember { mutableStateOf(formatGbInput(ov.monthlyBytes)) }
    var limitGbText by remember { mutableStateOf(if (ov.dataLimitMaxBytes > 0) formatGbInput(ov.dataLimitMaxBytes) else "") }
    var disconnectOnExceed by remember { mutableStateOf(ov.dataLimitEnabled) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF007AFF).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("流量管理与校准", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = iOSLabel)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "校准已用流量并设置月度流量上限与超额关网规则。",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 已用流量输入框
                OutlinedTextField(
                    value = usedGbText,
                    onValueChange = { usedGbText = it },
                    label = { Text("本月已用流量 (GB)") },
                    placeholder = { Text("例如：5.2") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 总流量上限输入框
                OutlinedTextField(
                    value = limitGbText,
                    onValueChange = { limitGbText = it },
                    label = { Text("本月总流量上限 (GB)") },
                    placeholder = { Text("例如：100 (留空为不限制)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 超额关闭流量开关
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iOSFill,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("超额关闭流量", style = MaterialTheme.typography.bodyMedium, color = iOSLabel, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "当已用流量达到设定上限时，自动停止/切断流量",
                                style = MaterialTheme.typography.bodySmall,
                                color = iOSSecondaryLabel
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = disconnectOnExceed,
                            onCheckedChange = { disconnectOnExceed = it }
                        )
                    }
                }

                errorMsg?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(err, color = Color(0xFFFF3B30), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    errorMsg = null
                    scope.launch {
                        runCatching {
                            val usedDouble = usedGbText.trim().toDoubleOrNull() ?: 0.0
                            val usedBytes = (usedDouble * 1073741824.0).toLong()

                            val limitDouble = limitGbText.trim().toDoubleOrNull()
                            val limitBytes = if (limitDouble != null && limitDouble > 0) (limitDouble * 1073741824.0).toLong() else -1L

                            // 1. 校准硬件已用流量（仅输入有效值时执行）
                            // 1. 校准硬件已用流量（仅输入有效值时执行）
                            if (usedGbText.isNotBlank()) {
                                // 始终保存本地兜底
                                val prefs = context.getSharedPreferences("XingLingPrefs", android.content.Context.MODE_PRIVATE)
                                val currentHardwareBytes = ov.monthlyBytes
                                val offset = usedBytes - currentHardwareBytes
                                prefs.edit().putLong("local_traffic_offset_bytes", offset).apply()
                                
                                backend?.features?.calibrateFlow(usedBytes)
                            }

                            // 2. 设置流量上限及超额关网规则
                            // 始终保存本地兜底
                            val prefs = context.getSharedPreferences("XingLingPrefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("local_data_limit_enabled", disconnectOnExceed)
                                .putLong("local_data_limit_max_bytes", limitBytes)
                                .apply()
                                
                            backend?.features?.setDataLimit(
                                DataLimit(
                                    enabled = disconnectOnExceed,
                                    maxLimit = if (limitBytes > 0) limitBytes.toString() else "-1",
                                    period = "monthly",
                                    checkReference = "system",
                                    statusForwardEnabled = disconnectOnExceed
                                )
                            )
                        }.onSuccess {
                            onUpdated()
                            onDismiss()
                        }.onFailure { ex ->
                            errorMsg = "保存失败: ${ex.message ?: "操作超时"}"
                            isSaving = false
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = iOSBlue)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("保存设置", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss
            ) {
                Text("取消", color = iOSSecondaryLabel)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = iOSCardBackground
    )
}

private fun formatGbInput(bytes: Long): String {
    if (bytes <= 0) return ""
    val gb = bytes / 1073741824.0
    return if (gb == gb.toLong().toDouble()) String.format(Locale.US, "%.0f", gb) else String.format(Locale.US, "%.2f", gb)
}

private fun formatGb(bytes: Long): String {
    if (bytes <= 0) return "0.00"
    return "%.2f".format(bytes / 1024.0 / 1024.0 / 1024.0)
}

@Composable
private fun DailyTrafficChartCard(history: List<Long>) {
    val sepColor = iOSSeparator
    val lineColor = iOSBlue
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("当日流量趋势", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "基于轮询 dailyBytes 内存采样（最近 ${history.size} 次）",
                style = MaterialTheme.typography.labelSmall,
                color = iOSSecondaryLabel
            )
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                if (history.size < 2) {
                    drawLine(sepColor, start = Offset(0f, size.height / 2f), end = Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
                    return@Canvas
                }
                val maxV = maxOf(history.max(), 1L).toFloat()
                val topPad = 10f
                val bottomPad = 8f
                val chartH = size.height - topPad - bottomPad
                fun yFor(v: Long): Float = topPad + (1f - (v.toFloat() / maxV).coerceIn(0f, 1f)) * chartH
                val step = size.width / (history.size - 1).toFloat()
                drawLine(sepColor, start = Offset(0f, size.height - bottomPad), end = Offset(size.width, size.height - bottomPad), strokeWidth = 1.dp.toPx())
                val path = Path()
                history.forEachIndexed { i, v ->
                    val x = i * step
                    val y = yFor(v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(lineColor, 4.dp.toPx(), Offset(0f, yFor(history.first())))
                drawCircle(lineColor, 4.dp.toPx(), Offset(size.width, yFor(history.last())))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("当前累计 ${formatBytes(history.lastOrNull() ?: 0L)}", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
        }
    }
}


private fun monthlyLimitText(ov: BackendOverview): String {
    if (ov.dataLimitMaxBytes > 0 && ov.monthlyBytes > 0) {
        val pct = (ov.monthlyBytes * 100f / ov.dataLimitMaxBytes)
        return String.format(Locale.US, "阈值 %.0f%%", pct.coerceAtMost(100f))
    }
    return "本月累计"
}

private fun thresholdText(ov: BackendOverview): String {
    if (ov.isReachedFlowLimit) return "警告：当前周期流量已达设定阈值，设备可能已被限速或断网。"
    if (ov.dataLimitEnabled && ov.dataLimitMaxBytes > 0) {
        return "流量阈值提醒已开启：上限 ${formatBytes(ov.dataLimitMaxBytes)}（${if (ov.dataLimitPeriod == "daily") "按日" else "按月"}统计）。"
    }
    return "流量数据来自设备后台 /api/baseDeviceInfo 实时统计。"
}

// ================= 加载占位 =================
@Composable
private fun LoadingPlaceholder(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = iOSSecondaryLabel)
        }
    }
}

// ================= UI 小部件 =================
@Composable
private fun TrafficStatItem(title: String, value: String, detail: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = iOSGreen)
        Spacer(modifier = Modifier.height(2.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = iOSLabel)
        Spacer(modifier = Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

// ================= 工具函数 =================
private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "--"
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb)
    val mb = bytes / 1_048_576.0
    if (mb >= 1) return String.format(Locale.US, "%.1f MB", mb)
    return "${bytes / 1024} KB"
}

private fun formatBytesShort(bytes: Long): String {
    if (bytes < 0) return "--"
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return String.format(Locale.US, "%.1fG", gb)
    val mb = bytes / 1_048_576.0
    if (mb >= 1) return String.format(Locale.US, "%.0fM", mb)
    return "${bytes / 1024}K"
}



// ================= WiFi热点概览卡片（卡片内直接操作，无需进入二级页） =================
private enum class HotspotEdit { SSID, PASSWORD, MAX_STATION }

@Composable
private fun HotspotOverviewCard(backend: DeviceBackend?) {
    var info by remember { mutableStateOf<WifiApInfo?>(null) }
    var connected by remember { mutableStateOf<Int?>(null) }
    var clients by remember { mutableStateOf<List<LanClient>>(emptyList()) }
    var clientsLoaded by remember { mutableStateOf(false) }
    var showClients by remember { mutableStateOf(false) }
    var blackMacs by remember { mutableStateOf<List<String>>(emptyList()) }
    var aclBusy by remember { mutableStateOf(false) }
    var blockTarget by remember { mutableStateOf<LanClient?>(null) }
    var busy by remember { mutableStateOf(false) }
    var powerBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<HotspotEdit?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val feats = backend?.features ?: return
        // 顺序请求：多个接口共用同一个 goform 会话，避免并发登录互相挤掉会话
        feats.getWifiAp().onSuccess { info = it }
        feats.getLanClients()
            .onSuccess {
                clients = it
                connected = it.size
                clientsLoaded = true
            }
            .onFailure { clientsLoaded = true }
        feats.getAccessControl().onSuccess { ac -> blackMacs = ac?.blackMacs ?: emptyList() }
    }

    // 拉黑设备：读取现有名单 → 追加目标 MAC → 以黑名单模式下发
    fun blockClient(client: LanClient) {
        if (aclBusy) return
        val mac = client.mac.trim()
        if (mac.isEmpty()) {
            message = "该设备缺少 MAC 地址，无法拉黑"
            messageError = true
            return
        }
        scope.launch {
            aclBusy = true
            message = null
            val feats = backend?.features
            if (feats == null) {
                message = "设备未连接"; messageError = true; aclBusy = false; return@launch
            }
            val cur = feats.getAccessControl().getOrNull() ?: AccessControl()
            if (cur.blackMacs.any { it.equals(mac, ignoreCase = true) }) {
                blackMacs = cur.blackMacs
                message = "${hotspotClientLabel(client)} 已在黑名单中"; messageError = false
                aclBusy = false
                return@launch
            }
            val newBlack = cur.blackMacs + mac
            val newNames = cur.blackNames + client.name.ifBlank { mac }
            feats.setAccessControl(cur.copy(mode = "2", blackMacs = newBlack, blackNames = newNames))
                .onSuccess {
                    blackMacs = newBlack
                    message = "已拉黑 ${hotspotClientLabel(client)}，正在断开该设备…"
                    messageError = false
                    delay(1500)
                    refresh()
                    message = "已拉黑 ${hotspotClientLabel(client)}"
                }
                .onFailure { e ->
                    message = "拉黑失败：${e.message ?: "未知错误"}"
                    messageError = true
                }
            aclBusy = false
        }
    }

    LaunchedEffect(backend) { refresh() }

    // 热点参数修改：立即下发 setAccessPointInfo
    fun applyChange(newInfo: WifiApInfo, okMsg: String) {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            backend?.features?.setWifiAp(newInfo)
                ?.onSuccess { info = newInfo; message = okMsg; messageError = false }
                ?.onFailure { message = "保存失败：${it.message ?: "未知错误"}"; messageError = true }
            busy = false
        }
    }

    // 热点总开关：switchWiFiChip / switchWiFiModule
    fun togglePower(target: Boolean) {
        if (powerBusy) return
        scope.launch {
            powerBusy = true
            message = null
            val cur = info
            val chip = if (cur?.chipIndex == 1) "chip2" else "chip1"
            backend?.features?.setWifiEnabled(target, chip)
                ?.onSuccess {
                    if (target) {
                        message = "热点开启中…"; messageError = false
                        delay(2500)
                        refresh()
                    } else {
                        info = cur?.copy(enabled = false)
                        message = "热点关闭指令已下发，WiFi 将断开"; messageError = false
                    }
                }
                ?.onFailure { e ->
                    if (!target) {
                        // 关闭热点会断开当前 WiFi，HTTP 可能收不到响应，指令通常已生效
                        info = cur?.copy(enabled = false)
                        message = "热点关闭指令已下发，WiFi 将断开"; messageError = false
                    } else {
                        message = "操作失败：${e.message ?: "未知错误"}"; messageError = true
                    }
                }
            powerBusy = false
        }
    }

    val cur = info
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 标题行 + 总开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iOSBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = iOSBlue, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "WiFi 热点",
                    style = MaterialTheme.typography.titleMedium,
                    color = iOSLabel,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = info?.enabled == true,
                    enabled = !powerBusy && info != null,
                    onCheckedChange = { on -> if (on) togglePower(true) else confirmOff = true }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 热点名称（点击改名）
            HotspotActionRow(
                label = "热点名称",
                value = cur?.ssid?.ifBlank { "未命名网络" } ?: "读取中…",
                enabled = cur != null && !busy
            ) { edit = HotspotEdit.SSID }
            Divider(color = iOSSeparator, thickness = 1.dp)
            // 密码（点击改密）
            HotspotActionRow(
                label = "密码",
                value = if (cur != null) "••••••••" else "--",
                enabled = cur != null && !busy
            ) { edit = HotspotEdit.PASSWORD }
            Divider(color = iOSSeparator, thickness = 1.dp)
            // 最大连接数
            HotspotActionRow(
                label = "最大连接数",
                value = cur?.let { "${it.maxStation} 台" } ?: "--",
                enabled = cur != null && !busy
            ) { edit = HotspotEdit.MAX_STATION }
            Divider(color = iOSSeparator, thickness = 1.dp)
            HotspotStaticRow("加密方式", cur?.let { wifiAuthText(it.authMode, it.encrypType) } ?: "--")
            Divider(color = iOSSeparator, thickness = 1.dp)
            // 已连接设备（点击就地展开/收起设备明细，无需进入二级页）
            HotspotActionRow(
                label = "已连接设备",
                value = if (cur == null) "--" else "${connected ?: "--"} / ${cur.maxStation} 台",
                enabled = cur != null
            ) {
                showClients = !showClients
                if (showClients) scope.launch { refresh() } // 展开时拉取最新在线设备
            }

            if (showClients) {
                Spacer(modifier = Modifier.height(4.dp))
                HotspotClientList(
                    clients = clients,
                    loaded = clientsLoaded,
                    blackMacs = blackMacs,
                    busy = aclBusy
                ) { target -> blockTarget = target }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 广播 SSID / AP 隔离 两个内联开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HotspotMiniSwitch(
                    label = "广播 SSID",
                    checked = cur?.broadcastDisabled != true,
                    enabled = cur != null && !busy
                ) { on ->
                    cur?.let { applyChange(it.copy(broadcastDisabled = !on), "广播设置已保存") }
                }
                HotspotMiniSwitch(
                    label = "AP 隔离",
                    checked = cur?.isolate == true,
                    enabled = cur != null && !busy
                ) { on ->
                    cur?.let { applyChange(it.copy(isolate = on), "隔离设置已保存") }
                }
            }

            if (message != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    message!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (messageError) iOSRed else iOSSecondaryLabel
                )
            }
        }
    }

    // 拉黑确认对话框（拉黑后该设备会被禁止接入，当前连接随之断开）
    val blocking = blockTarget
    if (blocking != null) {
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("拉黑该设备？") },
            text = {
                Text(
                    "${hotspotClientLabel(blocking)}\n${blocking.mac.ifBlank { "未知 MAC" }}" +
                        "\n\n拉黑后该设备将无法接入本热点，若当前正在连接会立即断开。"
                )
            },
            confirmButton = {
                TextButton(onClick = { blockTarget = null; blockClient(blocking) }) {
                    Text("拉黑", color = iOSRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTarget = null }) { Text("取消") }
            }
        )
    }

    // 编辑对话框（SSID / 密码 / 最大连接数）
    val editing = edit
    if (editing != null && cur != null) {
        HotspotEditDialog(
            editing = editing,
            initial = when (editing) {
                HotspotEdit.SSID -> cur.ssid
                HotspotEdit.PASSWORD -> cur.password
                HotspotEdit.MAX_STATION -> cur.maxStation.toString()
            },
            onDismiss = { edit = null },
            onConfirm = { value ->
                when (editing) {
                    HotspotEdit.SSID -> {
                        val v = value.trim()
                        when {
                            v.isBlank() -> false
                            v.length > 32 -> false
                            else -> { applyChange(cur.copy(ssid = v), "热点名称已保存"); true }
                        }
                    }
                    HotspotEdit.PASSWORD -> {
                        val v = value.trim()
                        if (cur.authMode != "OPEN" && v.length !in 8..63) false
                        else {
                            applyChange(
                                cur.copy(password = v, authMode = if (v.isBlank()) "OPEN" else cur.authMode),
                                "热点密码已保存"
                            )
                            true
                        }
                    }
                    HotspotEdit.MAX_STATION -> {
                        val n = value.trim().toIntOrNull()
                        if (n == null || n < 1) false
                        else { applyChange(cur.copy(maxStation = n.coerceAtMost(128)), "最大连接数已保存"); true }
                    }
                }
            }
        )
    }

    // 关闭热点二次确认（会断开当前 WiFi 连接）
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("关闭 WiFi 热点？", color = iOSLabel, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "关闭后手机将断开与设备的 WiFi 连接，需要通过其他网络才能重新连接后台。",
                    color = iOSSecondaryLabel,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmOff = false; togglePower(false) }) {
                    Text("关闭", color = iOSRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOff = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HotspotActionRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("›", style = MaterialTheme.typography.titleMedium, color = iOSBlue)
        }
    }
}

@Composable
private fun HotspotStaticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = iOSSecondaryLabel,
            maxLines = 1
        )
    }
}

private fun hotspotClientLabel(c: LanClient): String =
    c.name.ifBlank { c.mac.ifBlank { "未知设备" } }

/**
 * 热点卡片内联的在线设备明细（就地展开，不进入二级页）。
 * 每行显示名称 / MAC / IP / 在线状态，右侧可直接拉黑（写入 MAC 黑名单）。
 */
@Composable
private fun HotspotClientList(
    clients: List<LanClient>,
    loaded: Boolean,
    blackMacs: List<String>,
    busy: Boolean,
    onBlock: (LanClient) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(iOSFill)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        when {
            !loaded -> Text(
                "正在读取在线设备…",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
            clients.isEmpty() -> Text(
                "暂无在线设备",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
            else -> {
                clients.forEachIndexed { idx, c ->
                    if (idx > 0) Divider(color = iOSSeparator, thickness = 1.dp)
                    val blocked = blackMacs.any { it.equals(c.mac.trim(), ignoreCase = true) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                hotspotClientLabel(c),
                                style = MaterialTheme.typography.bodyMedium,
                                color = iOSLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                listOf(c.mac, c.ip, if (c.online) "在线" else "离线")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = iOSSecondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (blocked) {
                            Text(
                                "已拉黑",
                                style = MaterialTheme.typography.labelSmall,
                                color = iOSSecondaryLabel
                            )
                        } else {
                            Text(
                                "拉黑",
                                style = MaterialTheme.typography.labelMedium,
                                color = iOSRed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(iOSRed.copy(alpha = 0.12f))
                                    .clickable(enabled = !busy, onClick = { onBlock(c) })
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (blackMacs.isNotEmpty())
                        "黑名单已拉黑 ${blackMacs.size} 台设备；拉黑后设备无法接入热点，当前连接会断开。"
                    else
                        "拉黑后该设备将无法接入热点，当前连接会断开。",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSSecondaryLabel
                )
            }
        }
    }
}

@Composable
private fun HotspotMiniSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) iOSLabel else iOSSecondaryLabel,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun HotspotEditDialog(
    editing: HotspotEdit,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var text by remember(editing) { mutableStateOf(initial) }
    var error by remember(editing) { mutableStateOf<String?>(null) }
    val title = when (editing) {
        HotspotEdit.SSID -> "修改热点名称"
        HotspotEdit.PASSWORD -> "修改热点密码"
        HotspotEdit.MAX_STATION -> "修改最大连接数"
    }
    val hint = when (editing) {
        HotspotEdit.SSID -> "1~32 位字符，保存后设备将重新应用热点配置"
        HotspotEdit.PASSWORD -> "WPA 加密密码需 8~63 位；留空则切换为开放网络"
        HotspotEdit.MAX_STATION -> "允许同时接入的设备数量（1~128）"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = iOSLabel, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(error!!, style = MaterialTheme.typography.labelSmall, color = iOSRed)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(hint, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ok = onConfirm(text)
                if (ok) {
                    onDismiss()
                } else {
                    error = when (editing) {
                        HotspotEdit.SSID -> "请输入 1~32 位名称"
                        HotspotEdit.PASSWORD -> "密码需 8~63 位（或留空设为开放网络）"
                        HotspotEdit.MAX_STATION -> "请输入大于 0 的数字"
                    }
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 官方后台认证 / 加密字段转友好文案 */
private fun wifiAuthText(authMode: String, encrypType: String): String {
    val auth = when (authMode.uppercase()) {
        "OPEN" -> "开放网络"
        "WPAPSK" -> "WPA-PSK"
        "WPA2PSK" -> "WPA2-PSK"
        "WPA3PSK" -> "WPA3-PSK"
        "WPAPSKWPA2PSK", "WPA/WPA2PSK" -> "WPA/WPA2-PSK"
        else -> authMode.ifBlank { "--" }
    }
    if (auth == "开放网络") return auth
    val enc = when (encrypType.uppercase()) {
        "TKIPAES" -> "TKIP/AES"
        "AES", "TKIP" -> encrypType.uppercase()
        "NONE", "" -> ""
        else -> encrypType
    }
    return if (enc.isBlank()) auth else "$auth · $enc"
}

// ================= 电源控制卡片（页内直接操作：立即重启 / 立即关机） =================
private enum class PowerAction(val label: String, val hint: String) {
    REBOOT("立即重启", "重启后设备将短暂离线，约 1~3 分钟恢复联网"),
    SHUTDOWN("立即关机", "关机后设备将断电，需要现场重新通电")
}

@Composable
private fun OverviewActionRow(
    label: String,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (label.contains("关机")) iOSRed else iOSLabel, fontWeight = FontWeight.SemiBold)
            if (desc.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
            }
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = iOSSecondaryLabel, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OverviewQuickToggleRow(
    label: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = iOSLabel, fontWeight = FontWeight.SemiBold)
            if (desc.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iOSBlue
            )
        )
    }
}

@Composable
private fun PowerControlOverviewCard(backend: DeviceBackend?) {
    val feats = backend?.features
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PowerAction?>(null) }
    val scope = rememberCoroutineScope()

    // 快捷开关状态
    var highRailModeEnabled by remember { mutableStateOf(false) }
    var promptHighRailReboot by remember { mutableStateOf(false) }
    var dataEnabled by remember { mutableStateOf(true) }
    var roamingEnabled by remember { mutableStateOf(false) }
    var indicatorEnabled by remember { mutableStateOf(true) }
    var perfModeEnabled by remember { mutableStateOf(false) }
    var powerForwardEnabled by remember { mutableStateOf(false) }
    var adguardRunning by remember { mutableStateOf(false) }

    LaunchedEffect(backend) {
        if (feats != null) {
            feats.getPerformanceMode().onSuccess { perfModeEnabled = it }
        }
        (backend as? com.xingling.app.backend.UfiToolsBackend)?.let { ufi ->
            runCatching { ufi.p0.getPowerForwardEnabled().onSuccess { powerForwardEnabled = it } }
            runCatching { ufi.p2.getAdGuardStatus().onSuccess { adguardRunning = it.running } }
        }
    }

    fun run(action: PowerAction) {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            if (feats == null) {
                message = "设备未连接，无法下发指令"
                messageError = true
                busy = false
                return@launch
            }
            val result = when (action) {
                PowerAction.REBOOT -> feats.rebootDevice()
                PowerAction.SHUTDOWN -> feats.shutdownDevice()
            }
            result
                .onSuccess {
                    message = when (action) {
                        PowerAction.REBOOT -> "重启指令已下发，设备约 1~3 分钟恢复联网"
                        PowerAction.SHUTDOWN -> "关机指令已下发，设备即将断电"
                    }
                    messageError = false
                }
                .onFailure { e ->
                    message = "下发失败：${e.message ?: "未知错误"}"
                    messageError = true
                }
            busy = false
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF9500).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Color(0xFFFF9500),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "电源与快捷控制",
                    style = MaterialTheme.typography.titleMedium,
                    color = iOSLabel,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "即时生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSSecondaryLabel
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. 电源操作（快捷列表风格）
            OverviewActionRow("立即重启", "重启设备，约 1~3 分钟恢复", !busy) { pending = PowerAction.REBOOT }
            Divider(color = iOSSeparator.copy(alpha = 0.5f), thickness = 0.5.dp)
            OverviewActionRow("立即关机", "关机后设备断电，需手动开机", !busy) { pending = PowerAction.SHUTDOWN }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = iOSSeparator, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // 2. 快捷开关列表
            Text("快捷开关", style = MaterialTheme.typography.labelMedium, color = iOSSecondaryLabel, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "⚠️ 提示：高铁模式、性能模式、聚合提速只能同时开启一项，开启其一将自动关闭另外两项。",
                style = MaterialTheme.typography.labelSmall,
                color = iOSSecondaryLabel,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            OverviewQuickToggleRow("高铁模式", "通过底层命令优化信令，在高速移动环境减少断流卡顿 (重启生效)", highRailModeEnabled, !busy) { on ->
                highRailModeEnabled = on
                if (on) {
                    if (perfModeEnabled) {
                        perfModeEnabled = false
                        scope.launch { runCatching { feats?.setPerformanceMode(false) } }
                    }
                }
                scope.launch {
                    feats?.atCommand("AT+SP5GCMDS=\"set nr param\",35,${if (on) 1 else 0}", 0)
                        ?.onSuccess { promptHighRailReboot = true }
                        ?.onFailure { e -> highRailModeEnabled = !on; message = "操作失败：${e.message}"; messageError = true }
                }
            }
            Divider(color = iOSSeparator.copy(alpha = 0.5f), thickness = 0.5.dp)

            OverviewQuickToggleRow("蜂窝数据", "切换移动网络连接状态", dataEnabled, !busy) { on ->
                dataEnabled = on
                scope.launch {
                    feats?.toggleCellularData()
                        ?.onSuccess { message = "蜂窝数据已${if (on) "开启" else "关闭"}"; messageError = false }
                        ?.onFailure { e -> dataEnabled = !on; message = "操作失败：${e.message}"; messageError = true }
                }
            }
            Divider(color = iOSSeparator.copy(alpha = 0.5f), thickness = 0.5.dp)

            OverviewQuickToggleRow("网络漫游", "异地/漫游数据连接开关", roamingEnabled, !busy) { on ->
                roamingEnabled = on
                scope.launch {
                    feats?.toggleRoaming()
                        ?.onSuccess { message = "网络漫游已切换"; messageError = false }
                        ?.onFailure { e -> roamingEnabled = !on; message = "漫游设置失败：${e.message}"; messageError = true }
                }
            }
            Divider(color = iOSSeparator.copy(alpha = 0.5f), thickness = 0.5.dp)

            OverviewQuickToggleRow("指示灯", "设备 LED 状态灯开关", indicatorEnabled, !busy) { on ->
                indicatorEnabled = on
                scope.launch {
                    feats?.toggleIndicatorLight()
                        ?.onSuccess { message = "指示灯已切换"; messageError = false }
                        ?.onFailure { e -> indicatorEnabled = !on; message = "操作失败：${e.message}"; messageError = true }
                }
            }
            Divider(color = iOSSeparator.copy(alpha = 0.5f), thickness = 0.5.dp)

            OverviewQuickToggleRow("性能模式", "极客高性能 / 满频调度", perfModeEnabled, !busy) { on ->
                perfModeEnabled = on
                if (on) {
                    if (highRailModeEnabled) {
                        highRailModeEnabled = false
                        scope.launch { runCatching { feats?.atCommand("AT+SP5GCMDS=\"set nr param\",35,0", 0) } }
                    }
                }
                scope.launch {
                    feats?.setPerformanceMode(on)
                        ?.onSuccess { message = "性能模式已${if (on) "开启" else "恢复标准"}"; messageError = false }
                        ?.onFailure { e -> perfModeEnabled = !on; message = "操作失败：${e.message}"; messageError = true }
                }
            }
            Divider(color = iOSSeparator.copy(alpha = 0.5f), thickness = 0.5.dp)

            Text(
                text = "原理：开启后将自动配置 iptables 强制劫持连接到此 WiFi 的所有设备的 53 端口流量至内置的 AdGuard 服务进行广告过滤。\n注意：AdGuard 运行会占用随身 WiFi 大量的 CPU 和内存，长时间开启可能导致设备严重发热、网速下降或死机！若不需要请勿开启。",
                style = MaterialTheme.typography.labelSmall,
                color = iOSRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            val msg = message
            if (msg != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (messageError) iOSRed else iOSGreen
                )
            }
        }
    }

    
    if (promptHighRailReboot) {
        AlertDialog(
            onDismissRequest = { promptHighRailReboot = false },
            title = { Text("设置已下发", color = iOSLabel, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "高铁模式状态已改变。此功能需要重启随身 WiFi 才能在基带底层生效。是否立即重启？",
                    color = iOSSecondaryLabel,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { promptHighRailReboot = false; pending = PowerAction.REBOOT }) {
                    Text(
                        "立即重启",
                        color = Color(0xFFFF9500)
                    )
                }
            },
            dismissButton = { TextButton(onClick = { promptHighRailReboot = false }) { Text("稍后重启") } }
        )
    }

    val target = pending

    if (target != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(target.label, color = iOSLabel, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "${target.hint}。确认立即执行？",
                    color = iOSSecondaryLabel,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { pending = null; run(target) }) {
                    Text(
                        "确认执行",
                        color = if (target == PowerAction.SHUTDOWN) iOSRed else Color(0xFFFF9500)
                    )
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun PowerActionButton(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(iOSFill)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = if (busy) 0.45f else 1f)),
                contentAlignment = Alignment.Center
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (busy) iOSSecondaryLabel else iOSLabel
            )
        }
    }
}

// ================= 定时任务卡片（页内直接查看 / 新增 / 删除） =================
private val OverviewTaskActions = listOf(
    "REBOOT_DEVICE" to "定时重启",
    "SHUTDOWN_DEVICE" to "定时关机",
    "PERFORMANCE_MODE_SETTING" to "切换性能模式"
)

private fun taskActionLabel(actionMap: Map<String, String>): String {
    val raw = actionMap["goformId"] ?: return "未知动作"
    return OverviewTaskActions.firstOrNull { it.first == raw }?.second ?: raw
}

@Composable
private fun ScheduledTasksOverviewCard(backend: DeviceBackend?) {
    var tasks by remember { mutableStateOf<List<ScheduledTask>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val feats = backend?.features
        if (feats == null) {
            tasks = emptyList()
            return
        }
        feats.listTasks()
            .onSuccess {
                tasks = it
                message = null
                messageError = false
            }
            .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                val emsg = e.message ?: ""
                if (emsg.contains("Coroutine", ignoreCase = true) || emsg.contains("cancel", ignoreCase = true) || e is java.io.InterruptedIOException) {
                    return@onFailure // 忽略因为协程取消导致的偶发异常
                }
                tasks = emptyList()
                message = "任务读取失败：${emsg.ifBlank { "未知错误" }}"
                messageError = true
            }
    }

    LaunchedEffect(backend) { refresh() }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF5856D6).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFF5856D6),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "定时任务",
                    style = MaterialTheme.typography.titleMedium,
                    color = iOSLabel,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "共 ${tasks?.size ?: 0} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSSecondaryLabel
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val list = tasks
            when {
                list == null -> Text(
                    "读取中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                list.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无定时任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                }
                else -> list.forEachIndexed { idx, t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t.time,
                                style = MaterialTheme.typography.bodyMedium,
                                color = iOSLabel,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${taskActionLabel(t.actionMap)} · ${if (t.repeatDaily) "每天重复" else "单次执行"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = iOSSecondaryLabel
                            )
                        }
                        Text(
                            "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = iOSRed,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !busy) {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        val feats = backend?.features
                                        if (feats == null) {
                                            message = "设备未连接，无法删除任务"
                                            messageError = true
                                        } else {
                                            feats.removeTask(t.id)
                                                .onSuccess {
                                                    message = "已删除任务 ${t.id}"
                                                    messageError = false
                                                    refresh()
                                                }
                                                .onFailure { e ->
                                                    message = "删除失败：${e.message ?: "未知错误"}"
                                                    messageError = true
                                                }
                                        }
                                        busy = false
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    if (idx < list.size - 1) {
                        Divider(color = iOSSeparator, thickness = 1.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            iOSButton(
                onClick = { showNew = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("＋ 新建任务", fontSize = 15.sp, color = Color.White)
            }

            val msg = message
            if (msg != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (messageError) iOSRed else iOSGreen
                )
            }
        }
    }

    if (showNew) {
        var hour by remember { mutableStateOf("02") }
        var minute by remember { mutableStateOf("00") }
        var action by remember { mutableStateOf("REBOOT_DEVICE") }
        var repeatDaily by remember { mutableStateOf(true) }
        var perfOn by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!busy) showNew = false },
            title = { Text("新建定时任务", color = iOSLabel, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hour,
                            onValueChange = { v -> hour = v.filter { it.isDigit() }.take(2); error = null },
                            singleLine = true,
                            label = { Text("时") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedTextField(
                            value = minute,
                            onValueChange = { v -> minute = v.filter { it.isDigit() }.take(2); error = null },
                            singleLine = true,
                            label = { Text("分") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OverviewTaskActions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !busy) { action = value },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = action == value,
                                onClick = { action = value },
                                enabled = !busy
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
                        }
                    }

                    if (action == "PERFORMANCE_MODE_SETTING") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "执行后开启性能模式",
                                style = MaterialTheme.typography.bodySmall,
                                color = iOSLabel
                            )
                            Switch(
                                checked = perfOn,
                                enabled = !busy,
                                onCheckedChange = { perfOn = it }
                            )
                        }
                    }

                    Divider(color = iOSSeparator, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("每天重复", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
                        Switch(
                            checked = repeatDaily,
                            enabled = !busy,
                            onCheckedChange = { repeatDaily = it }
                        )
                    }

                    val err = error
                    if (err != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(err, style = MaterialTheme.typography.labelSmall, color = iOSRed)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "时间范围 00~23 时 / 00~59 分；任务标识与「定时任务」页保持一致",
                        style = MaterialTheme.typography.labelSmall,
                        color = iOSSecondaryLabel
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = hour.trim().padStart(2, '0').takeLast(2)
                    val m = minute.trim().padStart(2, '0').takeLast(2)
                    val hi = h.toIntOrNull()
                    val mi = m.toIntOrNull()
                    if (hi == null || hi !in 0..23 || mi == null || mi !in 0..59) {
                        error = "请输入 00~23 时 / 00~59 分"
                    } else {
                        val actionMap = if (action == "PERFORMANCE_MODE_SETTING") {
                            mapOf("goformId" to action, "performance_mode" to if (perfOn) "1" else "0")
                        } else {
                            mapOf("goformId" to action)
                        }
                        busy = true
                        message = null
                        scope.launch {
                            val feats = backend?.features
                            if (feats == null) {
                                message = "设备未连接，无法新建任务"
                                messageError = true
                            } else {
                                feats.addTask("$action-$h$m", "$h:$m", repeatDaily, actionMap)
                                    .onSuccess {
                                        message = "任务已添加（${if (repeatDaily) "每天" else "单次"} $h:$m）"
                                        messageError = false
                                        showNew = false
                                        refresh()
                                    }
                                    .onFailure { e ->
                                        message = "新建失败：${e.message ?: "未知错误"}"
                                        messageError = true
                                    }
                            }
                            busy = false
                        }
                    }
                }) { Text("提交") }
            },
            dismissButton = {
                TextButton(onClick = { if (!busy) showNew = false }) { Text("取消") }
            }
        )
    }
}
// ================= 网络状态卡片 =================
@Composable
private fun NetworkStatusCard(
    carrierName: String,
    networkType: String,
    bands: String,
    rsrp: Int = Int.MIN_VALUE,
    signalLevel: Int = -1,
    dlMaxMbps: Int,
    ulMaxMbps: Int,
    qci: String
) {
    // 根据 RSRP 计算信号格数（优先使用 RSRP，其次使用 signalLevel）
    val effectiveSignalLevel = when {
        rsrp != Int.MIN_VALUE && rsrp >= -85 -> 4
        rsrp != Int.MIN_VALUE && rsrp >= -95 -> 3
        rsrp != Int.MIN_VALUE && rsrp >= -105 -> 2
        rsrp != Int.MIN_VALUE && rsrp >= -115 -> 1
        signalLevel > 0 -> signalLevel
        else -> 0
    }
    val signalColor = when {
        effectiveSignalLevel >= 4 -> iOSGreen
        effectiveSignalLevel >= 3 -> iOSBlue
        effectiveSignalLevel >= 2 -> iOSOrange
        else -> iOSRed
    }
    // 速率单位：false=Mbps（兆比特/秒），true=MB/s（兆字节/秒，1 MB/s = 8 Mbps）；上下行同步切换
    var asMBps by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 第一行：网络状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 绿色圆点
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(iOSGreen)
                )
                Spacer(modifier = Modifier.width(10.dp))
                // 运营商
                Text(
                    carrierName,
                    style = MaterialTheme.typography.titleLarge,
                    color = iOSLabel,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(10.dp))
                // 5G SA 标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(iOSBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        networkType,
                        style = MaterialTheme.typography.labelMedium,
                        color = iOSBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 频段标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(iOSSecondaryLabel.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        bands,
                        style = MaterialTheme.typography.labelMedium,
                        color = iOSSecondaryLabel,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // 右边信号图标（4格）
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (i in 1..4) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height((6 + i * 4).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i <= effectiveSignalLevel) signalColor else iOSSecondaryLabel.copy(alpha = 0.3f))
                        )
                    }
                }
            }
            // 分割线
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = iOSSeparator, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            // 第二行：签约速率三列大数字
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 签约下行（点击数字可在 Mbps / MB·s⁻¹ 间切换）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { asMBps = !asMBps },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            fmtRate(dlMaxMbps, asMBps),
                            style = MaterialTheme.typography.headlineMedium,
                            color = iOSLabel,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (asMBps) "MB/s" else "Mbps",
                            style = MaterialTheme.typography.bodySmall,
                            color = iOSBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "签约下行",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                }
                // 分割线
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(iOSSeparator)
                )
                // 签约上行（点击数字可在 Mbps / MB·s⁻¹ 间切换）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { asMBps = !asMBps },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            fmtRate(ulMaxMbps, asMBps),
                            style = MaterialTheme.typography.headlineMedium,
                            color = iOSLabel,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (asMBps) "MB/s" else "Mbps",
                            style = MaterialTheme.typography.bodySmall,
                            color = iOSBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "签约上行",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                }
                // 分割线
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(iOSSeparator)
                )
                // QCI
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        qci,
                        style = MaterialTheme.typography.headlineMedium,
                        color = iOSLabel,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "QCI",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                }
            }
        }
    }
}

/** 速率格式化：Mbps 显示整数；切到 MB/s 时按 1 MB/s = 8 Mbps 换算（整除取整，否则保留一位小数） */
private fun fmtRate(mbps: Int, asMBps: Boolean): String {
    if (mbps <= 0) return "--"
    if (!asMBps) return mbps.toString()
    val v = mbps / 8.0
    return if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
}

// ================= 签约速率卡片 =================
@Composable
private fun ContractRateCard(ov: BackendOverview) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 签约下行
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${ov.dlMaxMbps}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = iOSLabel,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Mbps",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "签约下行",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            }
            // 分割线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(iOSSeparator)
            )
            // 签约上行
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${ov.ulMaxMbps}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = iOSLabel,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Mbps",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "签约上行",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            }
            // 分割线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(iOSSeparator)
            )
            // QCI
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    ov.qci,
                    style = MaterialTheme.typography.headlineLarge,
                    color = iOSLabel,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "QCI",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            }
        }
    }
}

// ================= 载波聚合功能卡（本设备可以用载波聚合） =================
@Composable
private fun CaCapabilityCard(signalMetrics: SignalMetrics, connected: Boolean = true) {
    val servingCells = signalMetrics.servingCells
    val caActive = servingCells.size >= 2
    val stateText = when {
        !connected -> "未连接设备"
        caActive -> "多载波聚合已激活（${servingCells.size}CA）"
        else -> "1NR 单载波（省电中）"
    }
    val stateColor = when {
        !connected -> iOSSecondaryLabel
        caActive -> iOSGreen
        else -> iOSOrange
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 头部：双圆 CA 图标 + 标题 + 状态徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val caIconColor = iOSBlue
                    Canvas(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(caIconColor.copy(alpha = 0.12f))
                    ) {
                        val strokeW = size.minDimension * 0.10f
                        val r = size.minDimension * 0.20f
                        val dx = size.width * 0.12f
                        val cx1 = size.width / 2 - dx
                        val cx2 = size.width / 2 + dx
                        val cy = size.height / 2
                        drawCircle(caIconColor, r, Offset(cx1, cy), style = Stroke(width = strokeW, cap = StrokeCap.Round))
                        drawCircle(caIconColor, r, Offset(cx2, cy), style = Stroke(width = strokeW, cap = StrokeCap.Round))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("载波聚合功能", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!connected) iOSSeparator.copy(alpha = 0.15f) else iOSGreen.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (!connected) iOSSecondaryLabel else iOSGreen)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            if (!connected) "未连接" else "✅ 可以用",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (!connected) iOSSecondaryLabel else iOSGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 主体：当前状态 + 三项能力
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iOSFill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("当前状态", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                        Text(stateText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = stateColor)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = iOSSeparator, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CaCapabilityItem("NR 载波聚合", "✅ 支持")
                        CaCapabilityItem("LTE 载波聚合", "✅ 支持")
                        CaCapabilityItem("EN-DC 双连接", "✅ 支持")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "提示：本设备硬件支持载波聚合，由基站按需分配——无大流量时保持 1NR 单载波省电，大流量下载或测速时自动开启多载波聚合提速。",
                style = MaterialTheme.typography.labelSmall,
                color = iOSSecondaryLabel
            )
        }
    }
}

@Composable
private fun CaCapabilityItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = iOSGreen)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
    }
}
