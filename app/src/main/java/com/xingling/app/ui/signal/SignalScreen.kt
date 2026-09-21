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

package com.xingling.app.ui.signal

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.CellInfo
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.signal.DeviceMetrics
import com.xingling.app.signal.NetworkMetrics
import com.xingling.app.signal.SignalMetrics
import com.xingling.app.signal.SystemMetrics
import com.xingling.app.signal.TrafficMetrics
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ── 信号质量颜色判断 ──
@Composable
private fun rsrpColor(rsrp: Int): Color = when {
    rsrp == Int.MIN_VALUE -> iOSSecondaryLabel
    rsrp >= -85 -> iOSGreen
    rsrp >= -100 -> iOSOrange
    else -> iOSRed
}

@Composable
private fun sinrColor(sinr: Int): Color = when {
    sinr == Int.MIN_VALUE -> iOSSecondaryLabel
    sinr >= 10 -> iOSGreen
    sinr >= 0 -> iOSOrange
    else -> iOSRed
}

@Composable
private fun rsrqColor(rsrq: Int): Color = when {
    rsrq == Int.MIN_VALUE -> iOSSecondaryLabel
    rsrq >= -10 -> iOSGreen
    rsrq >= -15 -> iOSOrange
    else -> iOSRed
}

/** 频段显示：NR → N41，LTE → B3；数据已带前缀时原样规范化 */
private fun bandLabel(cell: CellInfo): String {
    val b = cell.band.trim()
    if (b.isEmpty()) return "--"
    val upper = b.uppercase()
    if (upper.startsWith("N") || upper.startsWith("B")) return upper
    val prefix = when {
        cell.rat.contains("NR", ignoreCase = true) || cell.rat.contains("5G") -> "N"
        cell.rat.contains("LTE", ignoreCase = true) -> "B"
        else -> "N"
    }
    return prefix + upper
}

/** 拼接 "PCI x | EARFCN y | 100MHz | 2CC" 样式副标题，缺省字段自动省略 */
private fun cellDetail(cell: CellInfo, ccCount: Int = -1): String = buildList {
    if (cell.pci >= 0) add("PCI ${cell.pci}")
    if (cell.earfcn >= 0) add("EARFCN ${cell.earfcn}")
    if (cell.bandwidth.isNotBlank()) add(cell.bandwidth)
    if (ccCount > 0) add("${ccCount}CC")
}.joinToString(" | ")

// ── 分组标题 ──
@Composable
private fun SectionHeader(title: String, subtitle: String, color: Color) {
    Text(
        text = "$title ($subtitle)",
        fontSize = 13.sp,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

// ── 状态圆点 ──
@Composable
private fun StatusDot(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp)
        )
    }
}

// ── 信号指标（四列等宽）──
@Composable
private fun SignalMetricRow(cell: CellInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SignalMetric(Modifier.weight(1f), "BAND", bandLabel(cell), iOSBlue)
        SignalMetric(
            Modifier.weight(1f),
            "RSRP",
            if (cell.rsrp != Int.MIN_VALUE) "${cell.rsrp} dBm" else "--",
            rsrpColor(cell.rsrp)
        )
        SignalMetric(
            Modifier.weight(1f),
            "SINR",
            if (cell.sinr != Int.MIN_VALUE) "${cell.sinr} dB" else "--",
            sinrColor(cell.sinr)
        )
        SignalMetric(
            Modifier.weight(1f),
            "RSRQ",
            if (cell.rsrq != Int.MIN_VALUE) "${cell.rsrq} dB" else "--",
            rsrqColor(cell.rsrq)
        )
    }
}

@Composable
private fun SignalMetric(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            maxLines = 1
        )
        Text(
            text = label,
            color = iOSSecondaryLabel,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

// ── 小区卡片通用容器（紧凑精致）──
@Composable
private fun CompactCellCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 9.dp),
            content = content
        )
    }
}

/** 卡片标题块：圆标 + 标题/副标题 */
@Composable
private fun CardTitle(
    dotColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(dotColor, icon)
        Spacer(modifier = Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                color = iOSLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    color = iOSSecondaryLabel,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── 主载波卡片：标题 + 副标题 + 四宫格指标 ──
@Composable
private fun PrimaryCellCard(cell: CellInfo, ccCount: Int) {
    CompactCellCard {
        CardTitle(iOSGreen, Icons.Filled.Check, "主载波", cellDetail(cell, ccCount = ccCount))
        Spacer(modifier = Modifier.height(8.dp))
        SignalMetricRow(cell)
    }
}

// ── 从载波卡片：单行横排（频段 + 带宽/PCI/ARFCN），不重复四宫格 ──
@Composable
private fun SecondaryCellCard(cell: CellInfo) {
    CompactCellCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(iOSGreen, Icons.Filled.Check)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "从载波",
                color = iOSLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = bandLabel(cell),
                color = iOSLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = buildList {
                    if (cell.bandwidth.isNotBlank()) add(cell.bandwidth)
                    if (cell.pci >= 0) add("PCI ${cell.pci}")
                    if (cell.earfcn >= 0) add("ARFCN ${cell.earfcn}")
                }.joinToString("   "),
                color = iOSSecondaryLabel,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── 邻小区卡片 ──
@Composable
private fun NeighborCellCard(cell: CellInfo) {
    CompactCellCard {
        CardTitle(iOSOrange, Icons.Filled.Info, "邻小区", cellDetail(cell))
        Spacer(modifier = Modifier.height(8.dp))
        SignalMetricRow(cell)
    }
}

// ── 空状态 ──
@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = iOSSecondaryLabel
        )
    }
}

@Composable
fun SignalScreen(
    executionLogs: List<String>,
    signalMetrics: SignalMetrics,
    networkMetrics: NetworkMetrics,
    deviceMetrics: DeviceMetrics,
    trafficMetrics: TrafficMetrics,
    systemMetrics: SystemMetrics,
    context: Context,
    coroutineScope: CoroutineScope,
    addLog: (String) -> Unit,
    backend: DeviceBackend? = null
) {
    var servingCells by remember { mutableStateOf<List<CellInfo>>(emptyList()) }
    var neighborCells by remember { mutableStateOf<List<CellInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableStateOf(0) }

    // 从设备后台轮询小区数据；refreshTick 变化时立即重新拉取（手动刷新）
    LaunchedEffect(backend, refreshTick) {
        while (isActive) {
            val feats = backend?.features
            if (feats != null) {
                loading = true
                feats.servingCells().onSuccess { servingCells = it }
                feats.neighborCells().onSuccess { neighborCells = it }
                loading = false
            }
            delay(5000) // 5秒刷新一次
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 顶部：固定页面标题 + 设备侧网络制式（不再显示手机本机运营商，避免无 SIM 时出现“未知”）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "小区",
                    style = MaterialTheme.typography.headlineSmall,
                    color = iOSLabel,
                    fontWeight = FontWeight.Bold
                )
                val ratText = servingCells.firstOrNull()?.rat?.takeIf { it.isNotBlank() }
                if (ratText != null) {
                    Text(
                        text = ratText,
                        fontSize = 13.sp,
                        color = iOSSecondaryLabel
                    )
                }
            }
            IconButton(onClick = {
                loading = true
                refreshTick++
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = iOSBlue)
            }
        }

        // 服务小区
        SectionHeader("服务小区", "SERVING CELLS", iOSBlue)
        if (loading && servingCells.isEmpty()) {
            EmptyState("加载中…")
        } else if (servingCells.isEmpty()) {
            EmptyState("暂无服务小区数据")
        } else {
            servingCells.forEachIndexed { index, cell ->
                if (index == 0) {
                    PrimaryCellCard(cell = cell, ccCount = servingCells.size)
                } else {
                    SecondaryCellCard(cell = cell)
                }
            }
        }

        // 邻小区
        SectionHeader("邻小区", "NEIGHBOR CELLS", iOSOrange)
        if (loading && neighborCells.isEmpty()) {
            EmptyState("加载中…")
        } else if (neighborCells.isEmpty()) {
            EmptyState("暂无邻小区数据")
        } else {
            neighborCells.forEach { cell ->
                NeighborCellCard(cell = cell)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
