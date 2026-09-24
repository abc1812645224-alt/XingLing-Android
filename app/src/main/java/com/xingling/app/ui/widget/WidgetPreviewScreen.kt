/*
 * 星灵 (XingLing) · 桌面小组件预览页（App 内）
 *
 * 这是「App 内前台 15 秒刷新」的承载页：
 *   - 页面处于前台（RESUMED）时每 15 秒取一次数并刷新预览；
 *   - onPause 立即停止轮询（repeatOnLifecycle(RESUMED) 在 onPause 时取消协程）；
 *   - 预览与桌面小组件完全同源：同一份快照缓存（WidgetSnapshotStore）、同一套字段口径
 *     与同一套视觉语言（线条图标 + 浅灰胶囊 + 超大流量数字 + 圆形刷新按钮）。
 *
 * 4×2 保留全字段（含未读短信），2×2 展示流量 / 设备数 / 信号 / 温度 / 短信。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.xingling.app.R
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.widget.WidgetDataLoader
import com.xingling.app.widget.WidgetFormat
import com.xingling.app.widget.WidgetRefreshPolicy
import com.xingling.app.widget.WidgetSnapshot
import com.xingling.app.widget.WidgetSnapshotStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WidgetPreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf(WidgetSnapshotStore.read(context) ?: WidgetSnapshot()) }
    var lastRefreshAt by remember { mutableStateOf(0L) }
    var refreshing by remember { mutableStateOf(false) }

    fun refreshNow(force: Boolean) {
        if (refreshing) return
        scope.launch {
            refreshing = true
            val fresh = runCatching {
                WidgetDataLoader.load(context, commit = true, force = force)
            }.getOrNull()
            if (fresh != null) {
                snapshot = fresh
                lastRefreshAt = System.currentTimeMillis()
            }
            refreshing = false
        }
    }

    // 前台（RESUMED）每 15 秒刷新；onPause 立即停止
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refreshing = true
                val fresh = runCatching { WidgetDataLoader.load(context) }.getOrNull()
                if (fresh != null) {
                    snapshot = fresh
                    lastRefreshAt = System.currentTimeMillis()
                }
                refreshing = false
                delay(WidgetRefreshPolicy.APP_FOREGROUND_INTERVAL_MS)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ 返回", color = iOSBlue, fontSize = 15.sp)
            }
            Text(
                "桌面小组件",
                style = MaterialTheme.typography.headlineSmall,
                color = iOSLabel
            )
        }

        Text(
            "预览与桌面小组件同源（同一快照缓存、同一字段口径）。本页在前台时每 15 秒刷新一次，" +
                "离开本页（onPause）立即停止轮询，不会给随身 WiFi 后台造成全天候压力。",
            style = MaterialTheme.typography.bodySmall,
            color = iOSSecondaryLabel,
            lineHeight = 18.sp
        )

        PreviewLabel("4×2 横条 · 全字段（含未读短信）")
        WidePreview(snapshot, onRefresh = { refreshNow(force = true) })

        PreviewLabel("2×2 迷你 · 流量 / 设备数 / 温度 / 短信")
        CompactPreview(snapshot, onRefresh = { refreshNow(force = true) })

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (refreshing) "刷新中…"
                else "最近刷新 ${WidgetFormat.timeText(lastRefreshAt)} · 每 15 秒自动刷新",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
            TextButton(onClick = { refreshNow(force = true) }) {
                Text("立即刷新", color = iOSBlue, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PreviewLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = iOSBlue,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ══════════════════════════════════════════════════
// 与桌面 Glance 小组件一致的视觉组件（Compose 复刻）
// ══════════════════════════════════════════════════

private val cBlue: Color
    @Composable get() = colorResource(R.color.xl_widget_accent)
private val cGreen: Color
    @Composable get() = colorResource(R.color.xl_widget_green)
private val cRed: Color
    @Composable get() = colorResource(R.color.xl_widget_red)
private val cOrange: Color
    @Composable get() = colorResource(R.color.xl_widget_orange)
private val cPurple: Color
    @Composable get() = colorResource(R.color.xl_widget_purple)
private val cCyan: Color
    @Composable get() = colorResource(R.color.xl_widget_cyan)
private val cLabel: Color
    @Composable get() = colorResource(R.color.xl_widget_label)
private val cSecondary: Color
    @Composable get() = colorResource(R.color.xl_widget_secondary)
private val cTertiary: Color
    @Composable get() = colorResource(R.color.xl_widget_tertiary)
private val cCardBg: Color
    @Composable get() = colorResource(R.color.xl_widget_bg)
private val cPillBg: Color
    @Composable get() = colorResource(R.color.xl_widget_pill_bg)
private val cSeparator: Color
    @Composable get() = colorResource(R.color.xl_widget_separator)

/** 4×2 预览：字段、布局、配色与桌面完全一致 */
@Composable
private fun WidePreview(s: WidgetSnapshot, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cCardBg)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        // 设备名 / 固件版本 / 信号 / 电量
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LineIcon(R.drawable.ic_widget_router, cBlue, 16.dp)
            Spacer(Modifier.width(5.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.deviceName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = cLabel,
                    maxLines = 1
                )
                if (s.version.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.version,
                        fontSize = 9.sp,
                        color = cTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(R.drawable.ic_widget_signal, signalColor(s.signalBars), 14.dp)
                Spacer(Modifier.width(2.dp))
                Text(
                    if (s.signalBars >= 0) "${s.signalBars}" else "--",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = cLabel
                )
                Spacer(Modifier.width(7.dp))
                LineIcon(R.drawable.ic_widget_battery, if (s.charging) cGreen else cLabel, 19.dp)
                Spacer(Modifier.width(2.dp))
                Text(
                    when {
                        s.battery < 0 -> "--"
                        s.charging -> "⚡${s.battery}%"
                        else -> "${s.battery}%"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (s.charging) cGreen else cLabel
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        // 今日 / 本月流量
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FlowColumn("今日流量", s.dailyBytes, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Spacer(
                Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(cSeparator)
            )
            Spacer(Modifier.width(8.dp))
            FlowColumn("本月流量", s.monthlyBytes, Modifier.weight(1f))
        }

        Spacer(Modifier.height(7.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MetricPill(R.drawable.ic_widget_carrier, cBlue, carrierWithNet(s.carrier, s.netType))
            PillGap()
            MetricPill(R.drawable.ic_widget_clients, cPurple, clientsText(s.clients))
            PillGap()
            MetricPill(R.drawable.ic_widget_cpu, cOrange, WidgetFormat.percent(s.cpuUsage))
            PillGap()
            MetricPill(R.drawable.ic_widget_temp, cRed, "${WidgetFormat.tempCelsius(s.cpuTemp)}°")
        }

        Spacer(Modifier.height(5.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MetricPill(R.drawable.ic_widget_signal, cGreen, s.band.uppercase())
            PillGap()
            MetricPill(R.drawable.ic_widget_net, cBlue, qciText(s.qci))
            PillGap()
            MetricPill(R.drawable.ic_widget_memory, cCyan, WidgetFormat.percent(s.memUsage))
            PillGap()
            MetricPill(
                R.drawable.ic_widget_sms, cOrange,
                WidgetFormat.unreadText(s.smsUnread),
                badgeIconRes = if (s.smsUnread > 0) R.drawable.ic_widget_sms_badge else null
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LineIcon(R.drawable.ic_widget_signal, cBlue, 12.dp)
            Spacer(Modifier.width(3.dp))
            Text(
                "RSRP ${WidgetFormat.numText(s.rsrp)}dBm · SNR ${WidgetFormat.numText(s.snr)}",
                modifier = Modifier.weight(1f),
                fontSize = 9.sp,
                color = cSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Text(
                (if (s.stale) "缓存 " else "") + WidgetFormat.timeText(s.updateAt),
                fontSize = 9.sp,
                color = cTertiary,
                maxLines = 1
            )
            Spacer(Modifier.width(5.dp))
            RefreshCircle(22.dp, iconSize = 12.dp, onRefresh)
        }
    }
}

/** 2×2 预览：流量 / 设备数 / 信号 / 温度 / 短信 */
@Composable
private fun CompactPreview(s: WidgetSnapshot, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .width(170.dp)
            .height(170.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(cCardBg)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LineIcon(R.drawable.ic_widget_router, cBlue, 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                s.deviceName,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = cLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LineIcon(R.drawable.ic_widget_battery, if (s.charging) cGreen else cLabel, 15.dp)
            Spacer(Modifier.width(2.dp))
            Text(
                when {
                    s.battery < 0 -> "--"
                    s.charging -> "⚡${s.battery}%"
                    else -> "${s.battery}%"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (s.charging) cGreen else cLabel
            )
        }

        Spacer(Modifier.height(5.dp))

        // 今日与本月流量精致并排卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cPillBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("今日流量", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = cBlue)
                Spacer(Modifier.height(2.dp))
                FlowBig(s.dailyBytes, big = 16.sp, unitSize = 9.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(cSecondary.copy(alpha = 0.3f)))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("本月流量", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = cLabel)
                Spacer(Modifier.height(2.dp))
                FlowBig(s.monthlyBytes, big = 16.sp, unitSize = 9.sp)
            }
        }

        Spacer(Modifier.height(5.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MetricPill(R.drawable.ic_widget_clients, cBlue, clientsText(s.clients))
            PillGap()
            MetricPill(R.drawable.ic_widget_signal, cGreen, WidgetFormat.numText(s.rsrp))
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MetricPill(R.drawable.ic_widget_temp, cRed, "${WidgetFormat.tempCelsius(s.cpuTemp)}°")
            PillGap()
            MetricPill(
                R.drawable.ic_widget_sms, cOrange,
                WidgetFormat.unreadText(s.smsUnread),
                badgeIconRes = if (s.smsUnread > 0) R.drawable.ic_widget_sms_badge else null
            )
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LineIcon(R.drawable.ic_widget_carrier, cBlue, 11.dp)
            Spacer(Modifier.width(2.dp))
            Text(
                s.carrier,
                modifier = Modifier.weight(1f),
                fontSize = 9.sp,
                color = cSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RefreshCircle(20.dp, iconSize = 11.dp, onRefresh)
        }
    }
}

@Composable
private fun FlowColumn(title: String, bytes: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cBlue, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        FlowBig(bytes, big = 30.sp, unitSize = 12.sp)
    }
}

@Composable
private fun FlowBig(bytes: Long, big: androidx.compose.ui.unit.TextUnit, unitSize: androidx.compose.ui.unit.TextUnit) {
    val (value, unit) = WidgetFormat.flow(bytes)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = big, fontWeight = FontWeight.Bold, color = cLabel, maxLines = 1)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(2.dp))
            Text(
                unit,
                fontSize = unitSize,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                color = cSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RowScope.PillGap() {
    Spacer(Modifier.width(5.dp))
}

@Composable
private fun RowScope.MetricPill(
    iconRes: Int,
    tint: Color,
    text: String,
    badgeIconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(100.dp))
            .background(cPillBg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badgeIconRes != null) {
            Icon(
                painter = painterResource(badgeIconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(15.dp)
            )
        } else {
            LineIcon(iconRes, tint, 12.dp)
        }
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = cLabel,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun LineIcon(iconRes: Int, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

@Composable
private fun RefreshCircle(size: Dp, iconSize: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(cPillBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(R.drawable.ic_widget_refresh, cGreen, iconSize)
    }
}

private fun flowJoined(bytes: Long): String {
    val (value, unit) = WidgetFormat.flow(bytes)
    return "$value$unit"
}

private fun clientsText(n: Int): String = if (n < 0) "--" else "$n 台"

/** 运营商简称 + 网络制式：「中国移动」+「5G」→「移动5G」 */
private fun carrierWithNet(carrier: String, netType: String): String {
    val short = when {
        carrier.contains("移动") -> "移动"
        carrier.contains("联通") -> "联通"
        carrier.contains("电信") -> "电信"
        carrier.contains("广电") -> "广电"
        carrier.isBlank() || carrier == "--" -> ""
        else -> carrier
    }
    val net = netType.takeIf { it.isNotBlank() && it != "--" }.orEmpty()
    return when {
        short.isNotEmpty() -> "$short$net"
        net.isNotEmpty() -> net
        else -> "--"
    }
}

/** QCI 等级胶囊文案 */
private fun qciText(qci: String): String =
    "QCI " + (qci.takeIf { it.isNotBlank() && it != "未知" && it != "--" } ?: "--")

@Composable
private fun signalColor(bars: Int): Color = colorResource(
    when {
        bars < 0 -> R.color.xl_widget_tertiary
        bars >= 4 -> R.color.xl_widget_green
        bars >= 2 -> R.color.xl_widget_accent
        else -> R.color.xl_widget_red
    }
)
