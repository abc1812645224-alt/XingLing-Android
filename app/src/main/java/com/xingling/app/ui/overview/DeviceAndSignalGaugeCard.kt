/*
 * 星灵 (XingLing)
 * 设备总览 · 信号强度环形仪表卡片（自 cpe查看 SignalDashboardUI 迁移复用）
 *
 * 保持原 cpe查看 的 iOS 玻璃卡片风格：RoundedCornerShape(20.dp) 卡片 +
 * iOSCardBackground 底色 + Canvas drawArc 动态变色环形仪表 + 四级信号分级。
 * 迁移自 com.xingling.app.ui.signal.SignalDashboardUI.kt 中同名实现，
 * 仅调整 import 归属到星灵 ui.overview 包，绘制与分级逻辑保持原样。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.signal.DeviceMetrics
import com.xingling.app.signal.SignalMetrics
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSCardBackground
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator

// ================= 信号强度 RSRP 环形仪表卡片 =================
// 右侧 Canvas 动态变色环形仪表（drawArc 270° 弧 + StrokeCap.Round），
// 按 RSRP 四级分级：≥-75 绿 / -76～85 蓝 / -86～100 橙 / <-100 红。
@Composable
fun DeviceAndSignalGaugeCard(signalMetrics: SignalMetrics, deviceMetrics: DeviceMetrics, cpuTemp: Float = -1f, netType: String = "--", uptimeSec: Long = -1L, speedBps: Long = 0L) {
    val primaryCell = signalMetrics.servingCells.firstOrNull()
    val hasSignal = primaryCell != null && primaryCell.rsrp != Int.MIN_VALUE
    val rsrp = if (hasSignal) primaryCell!!.rsrp else Int.MIN_VALUE

    // 4级标准分级逻辑与颜色定义（无数据时显示中性"暂无信号数据"）
    val (statusText, activeColor) = when {
        !hasSignal -> Pair("暂无信号数据", iOSSecondaryLabel)
        rsrp >= -75 -> Pair("信号极佳", Color(0xFF34C759)) // 绿色
        rsrp >= -85 -> Pair("信号良好", Color(0xFF007AFF)) // 蓝色
        rsrp >= -100 -> Pair("信号一般", Color(0xFFFF9500)) // 橙色
        else -> Pair("信号较差", Color(0xFFFF3B30))       // 红色
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
                // 左侧：设备信息与性能占用
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = deviceMetrics.deviceModel,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFC69250) // 金棕/棕橙高亮
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    DeviceInfoRow("设备型号", deviceMetrics.deviceModel)
                    DeviceInfoRow("固件版本", deviceMetrics.firmwareVersion)
                    DeviceInfoRow("CPU使用率", if (deviceMetrics.cpuUsage >= 0) "${deviceMetrics.cpuUsage.toInt()}%" else "--")
                    DeviceInfoRow("内存使用率", if (deviceMetrics.ramUsage >= 0) "${String.format("%.2f", deviceMetrics.ramUsage)}%" else "--")
                    DeviceInfoRow("设备温度", if (cpuTemp > 0) "${cpuTemp.toInt()}°C" else "--")
                    DeviceInfoRow("运行时长", if (uptimeSec > 0) { val h = uptimeSec / 3600; val m = (uptimeSec % 3600) / 60; "${h}小时${m}分" } else "--")
                }

                // 右侧：自定义 Gauge 动态变色仪表盘
                Column(
                    modifier = Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("信号强度", style = MaterialTheme.typography.labelLarge, color = iOSLabel)
                        if (netType.isNotBlank() && netType != "--") {
                            Spacer(modifier = Modifier.width(6.dp))
                            val is5G = netType.contains("NR", ignoreCase = true) || netType.contains("5G", ignoreCase = true)
                            val netColor = if (is5G) iOSGreen else iOSBlue
                            Surface(shape = RoundedCornerShape(6.dp), color = netColor.copy(alpha = 0.15f)) {
                                Text(
                                    if (is5G) "5G" else "4G",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = netColor
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val gaugeTrackColor = iOSSeparator
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(110.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(100.dp)
                                .drawWithCache {
                                    // 静态底色灰弧绘制指令缓存
                                    val strokeWidth = 10.dp.toPx()
                                    val arcSize = Size(size.width.toFloat() - strokeWidth, size.height.toFloat() - strokeWidth)
                                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                                    onDrawBehind {
                                        drawArc(
                                            color = gaugeTrackColor,
                                            startAngle = 135f,
                                            sweepAngle = 270f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )
                                    }
                                }
                        ) {
                            val strokeWidth = 10.dp.toPx()
                            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                            // 绘制动态变色进度弧（无数据时只保留灰色轨道）
                            if (hasSignal) {
                                val rsrpClamped = rsrp.coerceIn(-140, -50)
                                val fraction = (rsrpClamped - (-140)).toFloat() / 90f
                                val sweep = fraction * 270f

                                drawArc(
                                    color = activeColor,
                                    startAngle = 135f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (hasSignal) "$rsrp" else "--",
                                style = MaterialTheme.typography.headlineMedium,
                                color = iOSLabel
                            )
                            Text(
                                text = if (hasSignal) "dBm" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = iOSSecondaryLabel
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = activeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = iOSSeparator, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // 底部 4 级信号标准分级颜色图例注释（全宽独立展示，防止遮挡或被压缩）
            Text("信号等级说明", style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SignalLevelLegendBadge(color = Color(0xFF34C759), label = "极佳", range = "≥75")
                SignalLevelLegendBadge(color = Color(0xFF007AFF), label = "良好", range = "76～85")
                SignalLevelLegendBadge(color = Color(0xFFFF9500), label = "一般", range = "86～100")
                SignalLevelLegendBadge(color = Color(0xFFFF3B30), label = "较差", range = "<100")
            }
        }
    }
}

@Composable
fun SignalLevelLegendBadge(color: Color, label: String, range: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = iOSFill,
        border = androidx.compose.foundation.BorderStroke(1.dp, iOSSeparator)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = iOSLabel, maxLines = 1, softWrap = false)
            Spacer(modifier = Modifier.width(2.dp))
            Text(range, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = iOSSecondaryLabel, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${label}：",
            style = MaterialTheme.typography.bodySmall,
            color = iOSSecondaryLabel,
            modifier = Modifier.width(82.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = iOSLabel
        )
    }
}

fun formatSpeed(bps: Long): String {
    if (bps < 1000) return "${bps} B/s"
    val kbps = bps / 1024.0
    if (kbps < 1000) return String.format("%.1f KB/s", kbps)
    val mbps = kbps / 1024.0
    return String.format("%.2f MB/s", mbps)
}
