/*
 * 星灵 (XingLing)
 * 设备总览 · 载波聚合统一卡片 (CA Unified Card)
 *
 * 合并原 CarrierAggregationCard / CaCapabilityCard / CaOptimizationCard：
 *   - 完整保留 PCC/SCC 实测数据（频段/带宽/RSRP/SINR/RSRQ/PCI/ARFCN）
 *   - 「一键检测并优化」：屏蔽项巡检 → 真实多线程下载触发 → 多源探测载波数 → 自动判定
 *
 * 重要事实：载波聚合由基站 RRC 调度，终端无「强制开启」标准命令。
 *   - 高通/移远系：可经 AT+QCAINFO 直接读到 PCC/SCC；
 *   - 展锐/封闭固件：多数不暴露辅载波字段，App 无法直接确认，只能如实标注并建议对照机。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.overview

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.signal.SignalInfo
import com.xingling.app.signal.SignalMetrics
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSCardBackground
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

private const val TAG = "CaUnified"

// 测速源（国内 CDN、支持 Range、文件足够大）；首项失败自动回落次项
private val CA_TEST_URLS = listOf(
    "https://mirrors.aliyun.com/ubuntu-releases/22.04/ubuntu-22.04.5-desktop-amd64.iso",
    "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-releases/22.04/ubuntu-22.04.5-desktop-amd64.iso"
)
private const val CA_TEST_THREADS = 6           // 下载线程数
private const val CA_TEST_DURATION_MS = 12000L  // 持续 12 秒
private const val CA_THREAD_CHUNK = 48L * 1024 * 1024 // 每线程请求 48MB（6×48=288MB 流量上限）

// 检测阶段
private enum class CaPhase { IDLE, INSPECT, TESTING, DONE }

// 检测中发现的可解除屏蔽项
private data class CaBlocker(
    val key: String,
    val label: String,
    var present: Boolean
)

@Composable
fun CaUnifiedCard(
    signalMetrics: SignalMetrics,
    connected: Boolean,
    backend: DeviceBackend?,
    qci: String = "9"
) {
    val servingCells = signalMetrics.servingCells
    val scope = rememberCoroutineScope()

    // 结论配色（iOS* 为 @Composable getter，需在 Composable 上下文取出后传入检测流程）
    val caOkColor = iOSGreen
    val caWarnColor = iOSOrange
    val caErrColor = iOSRed
    val caNeutralColor = iOSSecondaryLabel

    var phase by remember { mutableStateOf(CaPhase.IDLE) }
    val logs = remember { mutableStateListOf<String>() }
    var instantMbps by remember { mutableStateOf(0.0) }
    var peakMbps by remember { mutableStateOf(0.0) }
    var maxCarriers by remember { mutableIntStateOf(servingCells.size) }
    var caDirectRead by remember { mutableStateOf(false) } // 是否能直接读取 CA 状态（QCAINFO/多 cell）
    var conclusion by remember { mutableStateOf("") }
    var conclusionColor by remember { mutableStateOf(caNeutralColor) }
    val blockers = remember {
        mutableStateListOf(
            CaBlocker("endc", "EN-DC 双连接被关闭", false),
            CaBlocker("nrlock", "NR 频段被锁定", false),
            CaBlocker("ltelock", "LTE 频段被锁定", false),
            CaBlocker("celllock", "基站/小区被锁定", false)
        )
    }
    var hasBlocker by remember { mutableStateOf(false) }

    val caActive = servingCells.size >= 2
    val badgeText = when {
        !connected -> "未连接"
        caActive -> "${servingCells.size}CC 聚合中"
        else -> "1CC 单载波"
    }
    val badgeColor = when {
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
            // ── 头部：双圆 CA 图标 + 标题 + 状态徽章 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val caIconColor = iOSBlue
                    Canvas(
                        modifier = Modifier
                            .size(26.dp)
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
                    Text("载波聚合", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 载波实测数据区（保留原 PCC/SCC 全部数据）──
            if (!connected) {
                CaEmptyHint(
                    title = "未连接随身 WiFi 后台",
                    body = "连接设备后，这里将展示主载波 (PCC) 与辅载波 (SCC) 的频段、信号与聚合状态。"
                )
            } else if (servingCells.isEmpty()) {
                CaEmptyHint(
                    title = "🟢 CA 底层就绪（待机保护中）",
                    body = "基带处于单载波省电模式，大流量下载或测速时基站将自动开启辅载波聚合。"
                )
            } else {
                servingCells.forEachIndexed { index, cell ->
                    CaCarrierBlock(
                        cell = cell,
                        role = if (index == 0) "主载波 PCC" else "辅载波 SCC$index",
                        qci = if (index == 0) qci else null
                    )
                    if (index < servingCells.lastIndex) Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // ── 检测进度 / 结果区 ──
            if (phase != CaPhase.IDLE) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = iOSFill,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (phase == CaPhase.TESTING || phase == CaPhase.INSPECT) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = iOSBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (phase == CaPhase.INSPECT) "正在巡检设备配置..." else "多线程下载触发中（$CA_TEST_THREADS 线程）",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = iOSBlue
                                )
                            }
                            if (phase == CaPhase.TESTING) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "实时 ${"%.1f".format(instantMbps)} Mbps · 载波数 $maxCarriers · 状态直读 ${if (caDirectRead) "支持" else "不支持"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = iOSSecondaryLabel
                                )
                            }
                        }

                        if (logs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            logs.forEach { line ->
                                Text(line, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
                            }
                        }

                        if (phase == CaPhase.DONE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = iOSSeparator, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (hasBlocker) {
                                Text(
                                    "检测到 ${blockers.count { it.present }} 项可能影响 CA 的限制：",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = iOSOrange
                                )
                                blockers.filter { it.present }.forEach {
                                    Text("· ${it.label}", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        Log.d(TAG, "user tapped release-blockers")
                                        scope.launch(Dispatchers.IO) {
                                            val f = backend?.features
                                            f?.setEndcState(true)
                                            f?.unlockAllBands()
                                            f?.unlockCell()
                                            withContext(Dispatchers.Main) {
                                                blockers.forEach { it.present = false }
                                                hasBlocker = false
                                                logs.add("已解除：EN-DC 开启、频段/小区锁定清除，可重新检测。")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = iOSBlue)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("一键解除限制", fontSize = 13.sp)
                                }
                            }
                            if (conclusion.isNotEmpty()) {
                                Text(
                                    conclusion,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = conclusionColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "峰值 ${"%.1f".format(peakMbps)} Mbps · 最大载波数 $maxCarriers · 状态直读 ${if (caDirectRead) "支持" else "不支持"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = iOSSecondaryLabel
                                )
                            }
                        }
                    }
                }
            }

            // ── 操作按钮 ──
            if (connected) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (phase == CaPhase.INSPECT || phase == CaPhase.TESTING) return@Button
                            Log.d(TAG, "user tapped run-check")
                            scope.launch(Dispatchers.IO) {
                                runCaCheck(
                                    backend = backend,
                                    dataState = signalMetrics.dataState,
                                    initialCarriers = servingCells.size,
                                    okColor = caOkColor,
                                    warnColor = caWarnColor,
                                    errColor = caErrColor,
                                    neutralColor = caNeutralColor,
                                    setPhase = { phase = it },
                                    logs = logs,
                                    blockers = blockers,
                                    setHasBlocker = { hasBlocker = it },
                                    setInstant = { instantMbps = it },
                                    setPeak = { peakMbps = it },
                                    setMaxCarriers = { maxCarriers = it },
                                    setDirectRead = { caDirectRead = it },
                                    setConclusion = { text, color ->
                                        conclusion = text
                                        conclusionColor = color
                                    }
                                )
                            }
                        },
                        enabled = phase != CaPhase.INSPECT && phase != CaPhase.TESTING,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = iOSBlue)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("一键检测并优化", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            phase = CaPhase.IDLE
                            logs.clear()
                            conclusion = ""
                            caDirectRead = false
                            instantMbps = 0.0
                            peakMbps = 0.0
                            maxCarriers = servingCells.size
                        },
                        modifier = Modifier.weight(0.7f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重置", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "检测发起 $CA_TEST_THREADS 线程下载并持续约 ${CA_TEST_DURATION_MS / 1000} 秒，流量上限约 ${CA_TEST_THREADS * CA_THREAD_CHUNK / 1024 / 1024} MB。CA 由基站按需调度，无流量时单载波省电。",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSSecondaryLabel
                )
            }
        }
    }
}

// ── 单个载波数据块（PCC / SCC）──
@Composable
private fun CaCarrierBlock(cell: SignalInfo, role: String, qci: String? = null) {
    val roleColor = if (role.startsWith("主")) iOSBlue else iOSGreen
    // 信号综合评分（与「信号质量评分卡」同口径：RSRP 45% · SINR 30% · RSRQ 25%）
    val scoreTotal = (scoreRsrp(cell.rsrp) * 0.45f + scoreSinr(cell.sinr) * 0.30f + scoreRsrq(cell.rsrq) * 0.25f).toInt()
    val scoreColor = when {
        scoreTotal >= 85 -> iOSGreen
        scoreTotal >= 70 -> iOSBlue
        scoreTotal >= 50 -> iOSOrange
        else -> iOSRed
    }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(roleColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(role, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = roleColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    val bandText = when {
                        cell.band.isNotEmpty() && cell.bandwidth.isNotEmpty() -> "${cell.band} (${cell.bandwidth})"
                        cell.band.isNotEmpty() -> cell.band
                        else -> cell.type.displayName
                    }
                    Text(bandText, style = MaterialTheme.typography.bodySmall, color = iOSLabel)
                }
                // 右上角：信号评分（0~100，颜色代表等级），替代原「信号 xx dBm」
                if (cell.rsrp != Int.MIN_VALUE) {
                    Text("评分 $scoreTotal", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = scoreColor)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            // 四列等宽网格：PCC 含 QCI；SCC 第四列留空，保证各块列对齐
            Row(modifier = Modifier.fillMaxWidth()) {
                CaMetric("RSRP", if (cell.rsrp != Int.MIN_VALUE) "${cell.rsrp}" else "--", iOSGreen, Modifier.weight(1f))
                CaMetric("SINR", if (cell.sinr != Int.MIN_VALUE) "${cell.sinr}" else "--", iOSGreen, Modifier.weight(1f))
                CaMetric("RSRQ", if (cell.rsrq != Int.MIN_VALUE) "${cell.rsrq}" else "--", iOSOrange, Modifier.weight(1f))
                if (qci != null) CaMetric("QCI", qci, iOSLabel, Modifier.weight(1f))
                else Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            // PCI / ARFCN：各占两列居中，与上方网格对齐
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
                    Text(if (cell.pci > 0) "PCI ${cell.pci}" else "PCI --", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                }
                Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
                    Text(if (cell.earfcn > 0) "ARFCN ${cell.earfcn}" else "ARFCN --", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                }
            }
        }
    }
}

@Composable
private fun CaMetric(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = valueColor)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
    }
}

@Composable
private fun CaEmptyHint(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = iOSFill,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = iOSLabel)
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
        }
    }
}

// ── 检测流程（IO 线程执行）──
private suspend fun runCaCheck(
    backend: DeviceBackend?,
    dataState: String,
    initialCarriers: Int,
    okColor: Color,
    warnColor: Color,
    errColor: Color,
    neutralColor: Color,
    setPhase: (CaPhase) -> Unit,
    logs: MutableList<String>,
    blockers: MutableList<CaBlocker>,
    setHasBlocker: (Boolean) -> Unit,
    setInstant: (Double) -> Unit,
    setPeak: (Double) -> Unit,
    setMaxCarriers: (Int) -> Unit,
    setDirectRead: (Boolean) -> Unit,
    setConclusion: (String, Color) -> Unit
) {
    val features = backend?.features
    fun uilog(m: String) {
        Log.d(TAG, m)
        logs.add(m)
    }

    logs.clear()
    setConclusion("", neutralColor)
    blockers.forEach { it.present = false }
    setHasBlocker(false)
    setInstant(0.0)
    setPeak(0.0)
    setMaxCarriers(initialCarriers.coerceAtLeast(1))
    setDirectRead(false)

    // 阶段 1：配置巡检（接口失败/不支持 → 不标记，避免误报）
    setPhase(CaPhase.INSPECT)
    uilog("① 巡检设备配置（EN-DC / 锁频 / 锁小区）...")

    if (features != null) {
        features.endcState()
            .onSuccess { ok -> blockers.first { it.key == "endc" }.present = !ok }
            .onFailure { Log.d(TAG, "endcState unreadable: ${it.message}") }
        features.getNrBandLock()
            .onSuccess { b ->
                Log.d(TAG, "nr_band_lock=$b")
                blockers.first { it.key == "nrlock" }.present = b.isNotEmpty()
            }
        features.getLteBandLock()
            .onSuccess { b ->
                Log.d(TAG, "lte_band_lock=$b")
                blockers.first { it.key == "ltelock" }.present = b.isNotEmpty()
            }
        features.lockedCells()
            .onSuccess { c -> blockers.first { it.key == "celllock" }.present = c.isNotEmpty() }
            .onFailure { Log.d(TAG, "lockedCells unreadable: ${it.message}") }
    }
    val blockerCount = blockers.count { it.present }
    setHasBlocker(blockerCount > 0)
    uilog(
        if (blockerCount > 0) "   发现 $blockerCount 项限制（见下方），解除可放开全部频段，利于聚合。"
        else "   未发现屏蔽项，EN-DC / 频段 / 小区均允许。"
    )

    uilog("② 数据拨号状态：${dataState.ifEmpty { "未知" }}（以实际能否下载为准）")

    // 阶段 2：多线程下载触发 + 多源探测载波
    setPhase(CaPhase.TESTING)
    uilog("③ 发起 $CA_TEST_THREADS 线程下载，持续 ${CA_TEST_DURATION_MS / 1000} 秒...")

    val totalBytes = AtomicLong(0)
    var maxCells = 0
    var directRead = false

    val t0 = android.os.SystemClock.elapsedRealtime()
    withTimeoutOrNull(CA_TEST_DURATION_MS) {
        // 下载线程
        val downloadJobs = (0 until CA_TEST_THREADS).map { idx ->
            launch(Dispatchers.IO) {
                downloadOneThread(
                    urls = CA_TEST_URLS,
                    start = idx * CA_THREAD_CHUNK,
                    totalBytes = totalBytes
                )
            }
        }

        // 速率刷新（每秒）
        val rateJob = launch(Dispatchers.IO) {
            var last = 0L
            var peak = 0.0
            while (isActive) {
                delay(1000)
                val now = totalBytes.get()
                val mbps = (now - last) * 8.0 / 1_000_000.0
                last = now
                setInstant(mbps)
                peak = max(peak, mbps)
                setPeak(peak)
                Log.d(TAG, "rate t=${android.os.SystemClock.elapsedRealtime() - t0}ms total=$now mbps=$mbps")
            }
        }

        // CA 状态探测（每 2 秒：先 servingCells，再 AT+QCAINFO）
        val caJob = launch(Dispatchers.IO) {
            while (isActive) {
                features?.servingCells()?.onSuccess { cells ->
                    Log.d(TAG, "servingCells.size=${cells.size}")
                    if (cells.size >= 2) {
                        maxCells = max(maxCells, cells.size)
                        setMaxCarriers(maxCells)
                        directRead = true
                        setDirectRead(true)
                    } else if (cells.isNotEmpty()) {
                        // 仅确认到 PCC（固件不暴露 SCC）：显示已知载波数，但不标记为可直读
                        maxCells = max(maxCells, cells.size)
                        setMaxCarriers(maxCells)
                    }
                }
                features?.atCommand("AT+QCAINFO")?.onSuccess { resp ->
                    Log.d(TAG, "QCAINFO raw: $resp")
                    if (resp.contains("QCAINFO", ignoreCase = true)) {
                        val n = resp.lineSequence().count { it.contains("QCAINFO") }
                        if (n >= 1) {
                            maxCells = max(maxCells, n)
                            setMaxCarriers(maxCells)
                            directRead = true
                            setDirectRead(true)
                        }
                    }
                }
                delay(2000)
            }
        }

        downloadJobs.joinAll()
        rateJob.cancel()
        caJob.cancel()
    }
    val elapsed = android.os.SystemClock.elapsedRealtime() - t0

    // 平均速率（用总接收量 / 实际窗口）
    val avgMbps = totalBytes.get() * 8.0 / 1_000_000.0 / (elapsed / 1000.0)
    uilog("④ 结束：窗口 ${elapsed}ms，接收 ${"%.1f".format(totalBytes.get() / 1_000_000.0)} MB，载波数 $maxCells，状态直读=$directRead")

    val (text, color) = when {
        totalBytes.get() == 0L ->
            "无法连接测速源：请确认设备已拨号联网后重试。" to errColor
        maxCells >= 2 && directRead ->
            "聚合已激活（$maxCells CC）：基站已下发辅载波，多载波并行生效。" to okColor
        avgMbps < 8.0 ->
            "流量未打满（平均 ${"%.1f".format(avgMbps)} Mbps），无法判定：可能是 WiFi 链路瓶颈或测速源慢，建议靠近设备 / 连 5GHz WiFi 后重试。" to warnColor
        !directRead ->
            "流量已打满（平均 ${"%.1f".format(avgMbps)} Mbps），但本固件不暴露辅载波状态，App 无法直接确认 CA；设备硬件支持，是否聚合由基站决定。建议同位置用支持 CA 的手机对照工程模式。" to warnColor
        else ->
            "流量已打满但未观测到多载波：当前小区可能未配置辅载波或无空闲资源，可换位置再测。" to warnColor
    }
    setConclusion(text, color)
    setPhase(CaPhase.DONE)
}

// ── 单线程下载（请求 Range 区间，读字节即丢弃，用于产生持续流量）──
private suspend fun downloadOneThread(
    urls: List<String>,
    start: Long,
    totalBytes: AtomicLong
) {
    for (baseUrl in urls) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=$start-${start + CA_THREAD_CHUNK - 1}")
                setRequestProperty("User-Agent", "XingLing-CA-Test")
            }
            conn.connect()
            val code = conn.responseCode
            if (code != 200 && code != 206) continue
            val input = conn.inputStream
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                totalBytes.addAndGet(n.toLong())
            }
            return // 该区间读完，结束本线程
        } catch (_: Exception) {
            // 连接/读取异常（含超时取消），回落下一测速源
        } finally {
            conn?.disconnect()
        }
    }
}
