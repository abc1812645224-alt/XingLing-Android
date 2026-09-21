/*
 * 星灵 (XingLing)
 * 设备总览 · 5G 载波聚合卡片（自 cpe查看 SignalDashboardUI 迁移复用）
 *
 * 保持原 cpe查看 的 iOS 玻璃卡片风格与完整逻辑：PCC 主载波 + SCC 辅载波
 * 分块展示、状态 Badge（多载波聚合已激活 / 1NR 单载波省电）、参数说明弹窗。
 * 迁移自 com.xingling.app.ui.signal.SignalDashboardUI.kt，布局与样式保持原样。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

// ================= 5G 载波聚合 (CA) 卡片 =================
// 头部：双圆 Canvas 图标 + 标题 + 说明入口；状态 Badge 按 servingCells.size 动态着色。
// 主体：无服务载波时展示"待机保护"空态；否则展示 PCC 主载波指标 + 各 SCC 辅载波列表。
@Composable
fun CarrierAggregationCard(signalMetrics: SignalMetrics, connected: Boolean = true, qci: String = "9", scoreText: String = "", scoreColor: Color = iOSSecondaryLabel) {
    val servingCells = signalMetrics.servingCells
    val caStateText = signalMetrics.caStateText
    var showCaHelpDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isTestingCa by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 头部标题栏与状态 Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconColor = if (servingCells.size >= 2) iOSGreen else iOSBlue
                    Canvas(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.12f))
                    ) {
                        val strokeW = size.minDimension * 0.10f
                        val r = size.minDimension * 0.20f
                        val dx = size.width * 0.12f
                        val cx1 = size.width / 2 - dx
                        val cx2 = size.width / 2 + dx
                        val cy = size.height / 2
                        drawCircle(iconColor, r, Offset(cx1, cy), style = Stroke(width = strokeW, cap = StrokeCap.Round))
                        drawCircle(iconColor, r, Offset(cx2, cy), style = Stroke(width = strokeW, cap = StrokeCap.Round))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("5G 载波聚合 (CA)", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "说明",
                        tint = iOSSecondaryLabel.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { showCaHelpDialog = true }
                    )
                }

                // 动态高亮状态标签（未连接后台时显示中性"未连接"）
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!connected) iOSSeparator.copy(alpha = 0.15f)
                    else if (servingCells.size >= 2) iOSGreen.copy(alpha = 0.12f) else iOSOrange.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (!connected) iOSSecondaryLabel
                                    else if (servingCells.size >= 2) iOSGreen else iOSOrange
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when {
                                !connected -> "未连接"
                                servingCells.size >= 2 -> "🔥 多载波聚合已激活 (${servingCells.size}CA)"
                                scoreText.isNotBlank() -> scoreText
                                else -> "⚡ 1NR 单载波 (省电中)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (!connected) iOSSecondaryLabel
                            else if (servingCells.size >= 2) iOSGreen else scoreColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!connected) {
                // 未连接后台：明确提示，不再伪装成"待机保护"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = iOSFill,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("未连接随身 WiFi 后台", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = iOSSecondaryLabel)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("连接设备后，这里将展示主载波 (PCC) 与辅载波 (SCC) 聚合状态。", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel, textAlign = TextAlign.Center)
                    }
                }
            } else if (servingCells.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = iOSFill,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🟢 5G CA 底层就绪 (待机保护中)", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = iOSLabel)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("基带处于单载波省电模式，开启大流量下载或测速时基站将自动开启 2NR-CA 辅载波聚合。", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel, textAlign = TextAlign.Center)
                    }
                }
            } else {
                // PCC 主载波区域
                val primaryCell = servingCells[0]
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
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = iOSBlue.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "PCC 主载波",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = iOSBlue
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val pccLabel = if (primaryCell.band.isNotEmpty()) {
                                    val bw = primaryCell.bandwidth
                                    if (bw.isNotEmpty()) "${primaryCell.band} ($bw)" else primaryCell.band
                                } else {
                                    "5G SA (n78)"
                                }
                                Text(
                                    pccLabel,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = iOSLabel
                                )
                            }
                            Text("信号: ${primaryCell.rsrp} dBm", style = MaterialTheme.typography.labelMedium, color = iOSGreen)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBadge("RSRP", if (primaryCell.rsrp != Int.MIN_VALUE) "${primaryCell.rsrp}" else "--", iOSGreen)
                            MetricBadge("SINR", if (primaryCell.sinr != Int.MIN_VALUE) "${primaryCell.sinr}" else "--", iOSGreen)
                            MetricBadge("RSRQ", if (primaryCell.rsrq != Int.MIN_VALUE) "${primaryCell.rsrq}" else "--", iOSOrange)
                            MetricBadge("QCI", qci, iOSLabel)
                        }


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (primaryCell.pci > 0) "PCI ${primaryCell.pci}" else "PCI --", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                            Text(if (primaryCell.earfcn > 0) "ARFCN ${primaryCell.earfcn}" else "ARFCN --", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                        }
                    }
                }

                // SCC 辅载波列表 (展示所有 SCell)

                for (idx in 1 until servingCells.size) {
                    val sccCell = servingCells[idx]
                    Spacer(modifier = Modifier.height(10.dp))

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
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = iOSRed.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "SCC$idx 辅载波",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = iOSRed
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        sccCell.band.ifEmpty { "5G 辅载波" },
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = iOSLabel
                                    )
                                    if (sccCell.bandwidth.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(${sccCell.bandwidth})", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                                    }
                                }
                                Text("信号: ${sccCell.rsrp} dBm", style = MaterialTheme.typography.labelMedium, color = iOSGreen)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (sccCell.pci > 0) "PCI ${sccCell.pci}" else "PCI --", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                                Text(if (sccCell.earfcn > 0) "ARFCN ${sccCell.earfcn}" else "ARFCN --", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                            }
                        }
                    }
                }
            }


            // 底部提示说明
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = iOSSeparator, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "提示：载波聚合 (CA) 由基站根据即时网络流量按需分配，在没有大流量任务时自动保持 1NR 省电，流量拉高时瞬间开启 2NR/3NR 聚合。",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSSecondaryLabel
                )
            }
    }
}


}
@Composable
private fun MetricBadge(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
    }
}
