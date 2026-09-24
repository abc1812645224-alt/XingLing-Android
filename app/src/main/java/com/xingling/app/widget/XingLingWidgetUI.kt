/*
 * 星灵 (XingLing) · 桌面小组件 UI（Jetpack Glance）
 *
 * 4×2 综合版 + 2×2 紧凑版。
 * 视觉语言：彩色线条图标 + 浅灰胶囊 + 超大流量数字 + 圆形刷新按钮，
 * 不再使用饱和色实心方块，保证浅色 / 深色（夜间）下都克制、清晰。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.xingling.app.MainActivity
import com.xingling.app.R

// 星灵 App 配色（iOS 系统色，夜间由 values-night 覆盖）
private val Blue = R.color.xl_widget_accent
private val Green = R.color.xl_widget_green
private val Red = R.color.xl_widget_red
private val Orange = R.color.xl_widget_orange
private val Purple = R.color.xl_widget_purple
private val Cyan = R.color.xl_widget_cyan
private val Label = R.color.xl_widget_label
private val Secondary = R.color.xl_widget_secondary
private val Tertiary = R.color.xl_widget_tertiary
private val CardBg = R.color.xl_widget_bg
private val PillBg = R.color.xl_widget_pill_bg
private val Separator = R.color.xl_widget_separator

// 线条矢量图标资源（路径为白色，统一用 ColorFilter.tint 着色）
private val IconRouter = R.drawable.ic_widget_router
private val IconSignal = R.drawable.ic_widget_signal
private val IconBattery = R.drawable.ic_widget_battery
private val IconCarrier = R.drawable.ic_widget_carrier
private val IconCpu = R.drawable.ic_widget_cpu
private val IconTemp = R.drawable.ic_widget_temp
private val IconWifi = R.drawable.ic_widget_wifi
private val IconNet = R.drawable.ic_widget_net
private val IconClients = R.drawable.ic_widget_clients
private val IconMemory = R.drawable.ic_widget_memory
private val IconSms = R.drawable.ic_widget_sms

@Composable
fun XingLingWidgetSurface(snapshot: WidgetSnapshot, compact: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(CardBg))
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        if (!snapshot.configured) {
            NotConfigured()
        } else if (compact) {
            CompactBody(snapshot)
        } else {
            WideBody(snapshot)
        }
    }
}

/** 未接入设备空态 */
@Composable
private fun NotConfigured() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LineIcon(IconRouter, Blue, 30.dp)
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "星灵",
            style = TextStyle(color = ColorProvider(Label), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = "未接入设备，点击打开星灵完成配置",
            style = TextStyle(color = ColorProvider(Secondary), fontSize = 10.sp),
            maxLines = 2
        )
    }
}

// ══════════════════════════════════════════════════
// 4×2 主界面
// ══════════════════════════════════════════════════
@Composable
private fun WideBody(s: WidgetSnapshot) {
    // ── 行1：设备名 / 固件版本 / 信号 / 电量 ──
    // 左侧信息用 weight 容器、右侧状态用 wrap 容器分组，
    // 避免 Glance Text 直接加 weight 在部分桌面上把右侧内容顶出卡片。
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineIcon(IconRouter, Blue, 16.dp)
        Spacer(GlanceModifier.width(5.dp))
        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.deviceName,
                style = TextStyle(color = ColorProvider(Label), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            if (s.version.isNotBlank()) {
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = s.version,
                    style = TextStyle(color = ColorProvider(Tertiary), fontSize = 10.sp),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = (if (s.stale) "缓存 " else "") + WidgetFormat.timeText(s.updateAt),
                style = TextStyle(color = ColorProvider(Tertiary), fontSize = 9.sp),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(IconSignal, signalColor(s.signalBars), 14.dp)
            Spacer(GlanceModifier.width(2.dp))
            Text(
                text = if (s.signalBars >= 0) "${s.signalBars}" else "--",
                style = TextStyle(color = ColorProvider(Label), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(GlanceModifier.width(7.dp))
            BatteryIcon(s.battery, s.charging)
            Spacer(GlanceModifier.width(2.dp))
            Text(
                text = when {
                    s.battery < 0 -> "--"
                    s.charging -> "⚡${s.battery}%"
                    else -> "${s.battery}%"
                },
                style = TextStyle(
                    color = ColorProvider(if (s.charging) Green else Label),
                    fontSize = 13.sp,
                      fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }

    Spacer(GlanceModifier.height(7.dp))

    // ── 第二行：今日 / 本月流量（精致卡片 + 竖分隔） ──
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(PillBg))
            .cornerRadius(14.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowColumn("今日流量", s.dailyBytes, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(12.dp))
        Box(
            modifier = GlanceModifier
                .width(1.dp)
                .height(32.dp)
                .background(ColorProvider(Separator))
        ) {}
        Spacer(GlanceModifier.width(12.dp))
        FlowColumn("本月流量", s.monthlyBytes, GlanceModifier.defaultWeight())
    }

    Spacer(GlanceModifier.height(7.dp))

    // ── 行3：指标胶囊行1 ──
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetricPill(IconCarrier, Blue, carrierWithNet(s.carrier, s.netType))
        PillGap()
        MetricPill(IconClients, Purple, "设备 " + clientsText(s.clients))
        PillGap()
        MetricPill(IconCpu, Orange, "CPU ${WidgetFormat.percent(s.cpuUsage)}")
        PillGap()
        MetricPill(IconTemp, Red, "温度 ${WidgetFormat.tempCelsius(s.cpuTemp)}°")
    }

    Spacer(GlanceModifier.height(5.dp))

    // ── 行4：指标胶囊行2（蜂窝频段 / QCI / 内存 / 短信）──
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 蜂窝频段（N78 / B8 / N78+B3 等），无频段时降级显示 WiFi 频点
        MetricPill(IconSignal, Green, "频段 " + bandPillText(s.band, s.wifiBand))
        PillGap()
        MetricPill(IconNet, Blue, WidgetFormat.rsrpText(s.rsrp))
        PillGap()
        MetricPill(IconMemory, Cyan, "内存 ${WidgetFormat.percent(s.memUsage)}")
        PillGap()
        MetricPill(IconSms, Orange, "短信 ${WidgetFormat.unreadText(s.smsUnread)}",
            badgeIconRes = if (s.smsUnread > 0) R.drawable.ic_widget_sms_badge else null)
    }

}

// ══════════════════════════════════════════════════
// 2×2 紧凑版
// ══════════════════════════════════════════════════
@Composable
private fun CompactBody(s: WidgetSnapshot) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // 顶部：路由器 + 设备名 + 电量
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineIcon(IconRouter, Blue, 13.dp)
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = s.deviceName,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(Label), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            BatteryIcon(s.battery, s.charging)
            Spacer(GlanceModifier.width(2.dp))
            Text(
                text = when {
                    s.battery < 0 -> "--"
                    s.charging -> "⚡${s.battery}%"
                    else -> "${s.battery}%"
                },
                style = TextStyle(
                    color = ColorProvider(if (s.charging) Green else Label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.height(4.dp))

        // 流量卡片：今日 与 本月 左右精致卡片并排，避免互相挤占
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左卡片：今日流量
            Box(
                modifier = GlanceModifier.defaultWeight()
                    .background(ColorProvider(PillBg))
                    .cornerRadius(12.dp)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "今日流量",
                        style = TextStyle(color = ColorProvider(Blue), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    FlowBig(s.dailyBytes, 18.sp, unitSize = 10.sp)
                }
            }

            Spacer(GlanceModifier.width(6.dp))

            // 右卡片：本月流量
            Box(
                modifier = GlanceModifier.defaultWeight()
                    .background(ColorProvider(PillBg))
                    .cornerRadius(12.dp)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "本月流量",
                        style = TextStyle(color = ColorProvider(Secondary), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    FlowBig(s.monthlyBytes, 18.sp, unitSize = 10.sp)
                }
            }
        }

        Spacer(GlanceModifier.height(4.dp))

        // 指标胶囊行1：已连接设备数 + RSRP
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricPill(IconClients, Blue, clientsText(s.clients))
            PillGap()
            MetricPill(IconSignal, Green, WidgetFormat.numText(s.rsrp))
        }

        Spacer(GlanceModifier.height(3.dp))

        // 指标胶囊行2：设备温度 + 短信
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricPill(IconTemp, Red, "${WidgetFormat.tempCelsius(s.cpuTemp)}°")
            PillGap()
            MetricPill(IconSms, Orange, WidgetFormat.unreadText(s.smsUnread),
                badgeIconRes = if (s.smsUnread > 0) R.drawable.ic_widget_sms_badge else null)
        }

        Spacer(GlanceModifier.height(4.dp))

        // 底部：运营商（已按要求去掉绿色刷新按钮）
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineIcon(IconCarrier, Blue, 11.dp)
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = s.carrier.ifBlank { "中国电信" },
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(Secondary), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

// ══════════════════════════════════════════════════
// 辅助组件
// ══════════════════════════════════════════════════

/** 流量列：蓝色小标题 + 超大数字 + 斜体单位 */
@Composable
private fun FlowColumn(title: String, bytes: Long, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = TextStyle(color = ColorProvider(Blue), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(2.dp))
        FlowBig(bytes, 26.sp, unitSize = 11.sp)
    }
}

/** 大数字流量值 + 斜体单位 */
@Composable
private fun FlowBig(bytes: Long, big: TextUnit = 30.sp, unitSize: TextUnit = 12.sp) {
    val (value, unit) = WidgetFormat.flow(bytes)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = TextStyle(color = ColorProvider(Label), fontSize = big, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        if (unit.isNotEmpty()) {
            Spacer(GlanceModifier.width(2.dp))
            Text(
                text = unit,
                style = TextStyle(
                    color = ColorProvider(Secondary),
                    fontSize = unitSize,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic
                ),
                maxLines = 1
            )
        }
    }
}

/** 胶囊之间的固定间隙（胶囊均分剩余宽度） */
@Composable
private fun RowScope.PillGap() {
    Spacer(GlanceModifier.width(5.dp))
}

/** 浅灰胶囊指标：线条彩色图标 + 深色文字，均分一行；badgeIconRes 非空时显示多色角标图标 */
@Composable
private fun RowScope.MetricPill(
    iconRes: Int,
    tintColor: Int,
    text: String,
    badgeIconRes: Int? = null
) {
    Row(
        modifier = GlanceModifier
            .defaultWeight()
            .background(ColorProvider(PillBg))
            .cornerRadius(100.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badgeIconRes != null) {
            Image(
                provider = ImageProvider(badgeIconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(15.dp)
            )
        } else {
            LineIcon(iconRes, tintColor, 12.dp)
        }
        Spacer(GlanceModifier.width(3.dp))
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(Label), fontSize = 11.sp, fontWeight = FontWeight.Medium),
            maxLines = 1
        )
    }
}

/** 线条图标（白色矢量路径，tint 成目标色） */
@Composable
private fun LineIcon(iconRes: Int, tintColor: Int, size: Dp) {
    Image(
        provider = ImageProvider(iconRes),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(ColorProvider(tintColor))
    )
}

private fun flowJoined(bytes: Long): String {
    val (value, unit) = WidgetFormat.flow(bytes)
    return "$value$unit"
}

private fun signalColor(bars: Int): Int = when {
    bars < 0 -> Tertiary
    bars >= 4 -> Green
    bars >= 2 -> Blue
    else -> Red
}

/** 已连接设备数文案 */
private fun clientsText(n: Int): String = if (n < 0) "--" else "$n"

/**
 * 运营商简称 + 网络制式：「中国移动」+「5G」→「移动5G」。
 * 去掉「中国」前缀，展示品牌核心字。
 */
private fun carrierWithNet(carrier: String, netType: String): String {
    val short = when {
        carrier.contains("移动") -> "移动"
        carrier.contains("联通") -> "联通"
        carrier.contains("电信") -> "电信"
        carrier.contains("广电") -> "广电"
        carrier.contains("China Mobile", ignoreCase = true) -> "移动"
        carrier.contains("China Unicom", ignoreCase = true) -> "联通"
        carrier.contains("China Telecom", ignoreCase = true) -> "电信"
        carrier.isBlank() || carrier == "--" -> ""
        else -> carrier.removePrefix("中国").trim()
    }
    val net = netType.takeIf { it.isNotBlank() && it != "--" }.orEmpty()
    return when {
        short.isNotEmpty() -> "$short$net"
        net.isNotEmpty() -> net
        else -> "--"
    }
}

/**
 * 频段胶囊文字：优先显示蜂窝频段（N78 / B8 / N78+B3），
 * 无蜂窝频段时降级显示 WiFi 频点（如 5G / 2.4G）。
 */
private fun bandPillText(band: String, wifiBand: String): String {
    val b = band.takeIf { it.isNotBlank() && it != "--" }
    if (b != null) return b.uppercase()
    val w = wifiBand.takeIf { it.isNotBlank() && it != "--" }
    return if (w != null) "WiFi$w" else "--"
}

/** QCI 等级胶囊文案 */
private fun qciText(qci: String): String =
    "QCI " + (qci.takeIf { it.isNotBlank() && it != "未知" && it != "--" } ?: "--")

/** 拟物风格动态电池图标 */
@Composable
private fun BatteryIcon(level: Int, charging: Boolean, modifier: GlanceModifier = GlanceModifier) {
    val color = if (charging) Green else Label
    val percent = level.coerceIn(0, 100) / 100f
    val fillWidthDp = (22 * percent).dp
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = GlanceModifier
                .size(26.dp, 13.dp)
                .background(ColorProvider(color))
                .cornerRadius(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(24.dp, 11.dp)
                    .background(ColorProvider(CardBg))
                    .cornerRadius(2.dp)
                    .padding(1.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (level > 0) {
                    Box(
                        modifier = GlanceModifier
                            .size(fillWidthDp, 9.dp)
                            .background(ColorProvider(color))
                            .cornerRadius(1.dp)
                    ) {}
                }
            }
        }
        Box(
            modifier = GlanceModifier
                .size(2.dp, 5.dp)
                .background(ColorProvider(color))
        ) {}
    }
}
