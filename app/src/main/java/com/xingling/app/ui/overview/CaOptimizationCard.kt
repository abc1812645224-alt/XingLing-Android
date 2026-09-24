/*
 * 星灵 (XingLing)
 * 设备总览 · 5G 载波聚合调优与测试卡片 (新增独立卡片)
 *
 * 提供一键发包激活测试、载波聚合总带宽统计与频段快捷优化入口。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.signal.SignalInfo
import com.xingling.app.signal.SignalMetrics
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSCardBackground
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSSecondaryLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// 计算多载波累计带宽
private fun calculateTotalBandwidth(servingCells: List<SignalInfo>): String {
    var totalMHz = 0
    var hasValidBw = false
    for (cell in servingCells) {
        val bw = cell.bandwidth
        if (bw.isNotBlank()) {
            val digits = bw.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                val mhz = digits.toIntOrNull() ?: 0
                if (mhz > 0) {
                    totalMHz += mhz
                    hasValidBw = true
                }
            }
        }
    }
    return if (hasValidBw && totalMHz > 0) "${totalMHz} MHz" else ""
}

@Composable
fun CaOptimizationCard(
    signalMetrics: SignalMetrics,
    connected: Boolean = true,
    onNavigateToBandLock: (() -> Unit)? = null
) {
    val servingCells = signalMetrics.servingCells
    val coroutineScope = rememberCoroutineScope()
    var isTestingCa by remember { mutableStateOf(false) }

    val isMultiCarrier = servingCells.size >= 2
    val totalBwStr = remember(servingCells) { calculateTotalBandwidth(servingCells) }

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
                Text("⚡ 5G CA 聚合调优与测试", style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                if (isMultiCarrier && totalBwStr.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = iOSGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "组合带宽 $totalBwStr",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = iOSGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isTestingCa)
                    "正在高频并发发包触发数据流，请观察上方载波聚合卡片是否被激活..."
                else if (isMultiCarrier)
                    "基站已自动激活 ${servingCells.size}CA 载波聚合，正处于多频段并行并发传输状态。"
                else
                    "模组当前处于 1NR 单载波省电模式。点击下方「一键测试 CA」可触发流量验证基站辅载波激活状态。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )

            if (connected) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isTestingCa) {
                                isTestingCa = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val start = System.currentTimeMillis()
                                        while (System.currentTimeMillis() - start < 3500) {
                                            try {
                                                val url = URL("http://www.qualcomm.com")
                                                val conn = url.openConnection() as HttpURLConnection
                                                conn.connectTimeout = 1000
                                                conn.readTimeout = 1000
                                                conn.requestMethod = "HEAD"
                                                conn.responseCode
                                                conn.disconnect()
                                            } catch (_: Exception) {}
                                            kotlinx.coroutines.delay(150)
                                        }
                                    } catch (_: Exception) {}
                                    withContext(Dispatchers.Main) {
                                        isTestingCa = false
                                    }
                                }
                            }
                        },
                        enabled = !isTestingCa,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTestingCa) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = iOSBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("发包中...", fontSize = 13.sp)
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("一键测试 CA", fontSize = 13.sp)
                        }
                    }

                    if (onNavigateToBandLock != null) {
                        OutlinedButton(
                            onClick = { onNavigateToBandLock() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("优化 CA 频段", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
