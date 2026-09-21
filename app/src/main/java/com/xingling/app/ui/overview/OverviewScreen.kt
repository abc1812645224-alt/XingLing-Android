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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.BackendOverview
import com.xingling.app.backend.BackendSignalInfo
import com.xingling.app.backend.CpuCoreStat
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.WifiApInfo
import com.xingling.app.signal.CellType
import com.xingling.app.signal.DeviceMetrics
import com.xingling.app.signal.SignalInfo
import com.xingling.app.signal.SignalMetrics
import com.xingling.app.ui.feature.FeatureRoute
import com.xingling.app.ui.feature.QuickFeatureCard
import com.xingling.app.ui.feature.StatusPill
import com.xingling.app.ui.theme.GlassCard
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
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

    var overview by remember { mutableStateOf<BackendOverview?>(null) }
    var signal by remember { mutableStateOf<BackendSignalInfo?>(null) }
    var loadedOnce by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    // 当日流量内存采样（最近 60 次）
    val dailyBytesHistory = remember { mutableStateListOf<Long>() }
    // CPU / 内存占用内存采样（最近 60 次）
    val cpuHistory = remember { mutableStateListOf<Float>() }
    val memHistory = remember { mutableStateListOf<Float>() }
    // 实时网速计算：上一次字节计数与时间戳
    var lastDailyBytes by remember { mutableLongStateOf(-1L) }
    var lastTickMs by remember { mutableLongStateOf(0L) }
    var speedBps by remember { mutableLongStateOf(0L) }

    LaunchedEffect(backend, pollIntervalMs) {
        while (true) {
            val now = System.currentTimeMillis()
            val ov = backend.fetchOverview()
            ov.onSuccess {
                overview = it
                dailyBytesHistory.add(it.dailyBytes)
                while (dailyBytesHistory.size > 60) dailyBytesHistory.removeAt(0)
                if (it.cpuUsage >= 0f) {
                    cpuHistory.add(it.cpuUsage)
                    while (cpuHistory.size > 60) cpuHistory.removeAt(0)
                }
                if (it.memUsage >= 0f) {
                    memHistory.add(it.memUsage)
                    while (memHistory.size > 60) memHistory.removeAt(0)
                }
                // 计算实时速率（字节/秒）
                if (lastDailyBytes >= 0 && lastTickMs > 0 && it.dailyBytes >= 0) {
                    val dt = (now - lastTickMs) / 1000.0
                    if (dt >= 1.0) {
                        val db = it.dailyBytes - lastDailyBytes
                        if (db >= 0) speedBps = (db / dt).toLong()
                    }
                }
                lastDailyBytes = it.dailyBytes
                lastTickMs = now
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
            bands = sg?.band ?: "N41+N28",
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
// 3. 5G载波聚合卡
CarrierAggregationCard(signalMetrics, connected = ov != null, qci = ov?.qci ?: "9", scoreText = scoreText, scoreColor = scoreColor)

// 3. 设备监控图形卡
if (ov != null) DeviceMonitorCard(ov, cpuHistory.toList(), memHistory.toList())

// 当前速率卡
SpeedCard(speedBps = speedBps, ov, backend)


        // 4. 实时网速卡（上下行箭头 + 大数字）

// 流量概览+趋势合并卡
if (ov != null) TrafficCard(ov, dailyBytesHistory.toList())

// WiFi 热点信息卡（卡片内直接操作：开关 / SSID / 密码 / 连接数 / 广播隔离）
HotspotOverviewCard(backend)




        // 10. CPU / 内存占用趋势折线

        // 11. 常用功能入口
        QuickFeatureCard(onOpen = onOpenFeature)
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
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var resultText by remember { mutableStateOf("") }
    var useMbps by remember { mutableStateOf(false) }
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

private fun scoreRsrp(v: Int): Float = when {
    v == Int.MIN_VALUE -> 0f
    v >= -75 -> 100f
    v >= -85 -> 80f
    v >= -95 -> 60f
    v >= -105 -> 40f
    else -> 20f
}
private fun scoreSinr(v: Int): Float = when {
    v == Int.MIN_VALUE -> 0f
    v >= 20 -> 100f
    v >= 13 -> 75f
    v >= 0 -> 50f
    else -> 25f
}
private fun scoreRsrq(v: Int): Float = when {
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
@Composable
private fun SpeedCard(speedBps: Long, ov: BackendOverview?, backend: DeviceBackend?) {
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var resultText by remember { mutableStateOf("") }
    var useMbps by remember { mutableStateOf(false) }
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
                    color = iOSBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        "实时采样",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = iOSBlue
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (useMbps) "Mbps" else "MB/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSBlue,
                    modifier = Modifier.clickable { useMbps = !useMbps }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SpeedStat("⬇ 下行", if (useMbps) String.format("%.1f Mbps", speedBps.toDouble() * 8.0 / 1_000_000.0) else String.format("%.2f MB/s", speedBps.toDouble() / 1_048_576.0), iOSBlue, Modifier.weight(1f))
                SpeedStat("⬆ 上行", if (useMbps) String.format("%.1f Mbps", speedBps.toDouble() / 1_000_000.0) else String.format("%.2f MB/s", speedBps.toDouble() / 8.0 / 1_048_576.0), iOSGreen, Modifier.weight(1f))
                SpeedStat("今日", formatBytesShort(ov?.dailyBytes ?: -1), iOSLabel, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    if (!isTesting) {
                        isTesting = true
                        resultText = "测速中…"
                        coroutineScope.launch {
                            val feats = backend?.features
                            if (feats == null) {
                                resultText = "错误：未连接设备"
                                isTesting = false
                            } else {
                                feats.speedtest(10).onSuccess { r ->
                                    resultText = "下行：" + r.humanSpeed
                                    isTesting = false
                                }.onFailure {
                                    resultText = "测速失败：" + it.message
                                    isTesting = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = iOSBlue)
            ) {
                Text(if (isTesting) "测速中…" else "开始测速", color = Color.White)
            }
            if (resultText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(resultText, style = MaterialTheme.typography.labelMedium, color = iOSSecondaryLabel)
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
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var resultText by remember { mutableStateOf("") }

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

            if (cpuHistory.size >= 2) {
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
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var resultText by remember { mutableStateOf("") }
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
                        CellInfoItem("频段", sg.band.ifEmpty { "--" })
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
            if (dailyBytesHistory.size >= 2) {
                val flowColor = iOSBlue
                Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    val w = size.width
                    val h = size.height
                    val maxBytes = dailyBytesHistory.maxOrNull() ?: 1L
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
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var resultText by remember { mutableStateOf("") }
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
private fun TrafficCard(ov: BackendOverview, history: List<Long>) {
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                Text("今日已用 ${formatBytesShort(ov.dailyBytes)}", style = MaterialTheme.typography.bodyMedium, color = iOSSecondaryLabel)
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
                            .background(iOSBlue)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "已用 ${(progress * 100).toInt()}% / 上限 ${formatGb(ov.dataLimitMaxBytes)} GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(iOSBlue.copy(alpha = 0.3f))
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
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var resultText by remember { mutableStateOf("") }
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
    var busy by remember { mutableStateOf(false) }
    var powerBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<HotspotEdit?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val feats = backend?.features ?: return
        // 顺序请求：两个接口共用同一个 goform 会话，避免并发登录互相挤掉会话
        feats.getWifiAp().onSuccess { info = it }
        feats.getLanClients().onSuccess { connected = it.size }
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
            HotspotStaticRow("已连接设备", cur?.let { "${connected ?: "--"} / ${it.maxStation} 台" } ?: "--")

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

// ================= 关机重启卡片 =================
@Composable
private fun PowerControlCard(backend: DeviceBackend?, onOpenFeature: (FeatureRoute) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // 重启卡片
        GlassCard(modifier = Modifier
            .weight(1f)
            .clickable(enabled = !busy) {
                busy = true
                scope.launch {
                    backend?.features?.rebootDevice()
                    busy = false
                }
            }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFF9500)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("立即重启", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
            }
        }
        // 关机卡片
        GlassCard(modifier = Modifier
            .weight(1f)
            .clickable(enabled = !busy) {
                busy = true
                scope.launch {
                    backend?.features?.shutdownDevice()
                    busy = false
                }
            }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iOSRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("立即关机", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
            }
        }
    }
}

// ================= 定时任务概览卡片 =================
@Composable
private fun TasksOverviewCard(backend: DeviceBackend?, onOpenFeature: (FeatureRoute) -> Unit) {
    var taskCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        backend?.features?.listTasks()?.onSuccess { taskCount = it.size }
    }
    
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF5856D6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("定时任务", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "已设置 $taskCount 个任务",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            }
            Text("›", color = iOSSecondaryLabel, fontSize = 20.sp)
        }
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
                            if (dlMaxMbps > 0) "$dlMaxMbps" else "--",
                            style = MaterialTheme.typography.headlineMedium,
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
                            if (ulMaxMbps > 0) "$ulMaxMbps" else "--",
                            style = MaterialTheme.typography.headlineMedium,
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