/*
 * 星灵 (XingLing) · 桌面小组件数据快照
 *
 * 字段对齐 UfiPeek 参考小组件：设备名/型号、信号格、电量、今日与本月流量、
 * 运营商、频段、CPU 占用与温度、WiFi 频段、内存占用、RSRP/SNR、未读短信、更新时间。
 * 快照可序列化为 JSON 落盘，作为刷新失败时的缓存回显来源。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class WidgetSnapshot(
    val configured: Boolean = false,
    val deviceName: String = "未接入设备",
    val version: String = "",
    val signalBars: Int = -1,
    val battery: Int = -1,
    val charging: Boolean = false,
    val dailyBytes: Long = -1L,
    val monthlyBytes: Long = -1L,
    val carrier: String = "--",
    val netType: String = "--",
    val band: String = "--",
    val qci: String = "--",
    val cpuUsage: Float = -1f,
    val cpuTemp: Float = -1f,
    val wifiBand: String = "--",
    val memUsage: Float = -1f,
    val rsrp: Int = Int.MIN_VALUE,
    val snr: Int = Int.MIN_VALUE,
    val smsUnread: Int = -1,
    val clients: Int = -1,
    val updateAt: Long = 0L,
    val stale: Boolean = false
) {
    fun toJson(): String = JSONObject()
        .put("configured", configured)
        .put("deviceName", deviceName)
        .put("version", version)
        .put("signalBars", signalBars)
        .put("battery", battery)
        .put("charging", charging)
        .put("dailyBytes", dailyBytes)
        .put("monthlyBytes", monthlyBytes)
        .put("carrier", carrier)
        .put("netType", netType)
        .put("band", band)
        .put("qci", qci)
        .put("cpuUsage", cpuUsage.toDouble())
        .put("cpuTemp", cpuTemp.toDouble())
        .put("wifiBand", wifiBand)
        .put("memUsage", memUsage.toDouble())
        .put("rsrp", rsrp)
        .put("snr", snr)
        .put("smsUnread", smsUnread)
        .put("clients", clients)
        .put("updateAt", updateAt)
        .toString()

    companion object {
        fun fromJson(text: String?): WidgetSnapshot? {
            if (text.isNullOrBlank()) return null
            return try {
                val o = JSONObject(text)
                WidgetSnapshot(
                    configured = o.optBoolean("configured", false),
                    deviceName = o.optString("deviceName", "--"),
                    version = o.optString("version", ""),
                    signalBars = o.optInt("signalBars", -1),
                    battery = o.optInt("battery", -1),
                    charging = o.optBoolean("charging", false),
                    dailyBytes = o.optLong("dailyBytes", -1L),
                    monthlyBytes = o.optLong("monthlyBytes", -1L),
                    carrier = o.optString("carrier", "--"),
                    netType = o.optString("netType", "--"),
                    band = o.optString("band", "--"),
                    qci = o.optString("qci", "--"),
                    cpuUsage = o.optDouble("cpuUsage", -1.0).toFloat(),
                    cpuTemp = o.optDouble("cpuTemp", -1.0).toFloat(),
                    wifiBand = o.optString("wifiBand", "--"),
                    memUsage = o.optDouble("memUsage", -1.0).toFloat(),
                    rsrp = o.optInt("rsrp", Int.MIN_VALUE),
                    snr = o.optInt("snr", Int.MIN_VALUE),
                    smsUnread = o.optInt("smsUnread", -1),
                    clients = o.optInt("clients", -1),
                    updateAt = o.optLong("updateAt", 0L),
                    stale = false
                )
            } catch (e: Throwable) {
                null
            }
        }
    }
}

/** 小组件文本格式化（与参考实现保持一致的显示口径） */
object WidgetFormat {

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.US)

    /** 流量：返回「数值 + 单位」，>=1GB 用 GB，否则 MB */
    fun flow(bytes: Long): Pair<String, String> {
        if (bytes < 0L) return "--" to ""
        val gb = bytes / 1073741824.0
        if (gb >= 1.0) {
            val s = if (gb >= 10) String.format(Locale.US, "%.1f", gb)
            else String.format(Locale.US, "%.2f", gb)
            return trimZero(s) to "GB"
        }
        val mb = bytes / 1048576.0
        val s = if (mb >= 10) String.format(Locale.US, "%.0f", mb)
        else String.format(Locale.US, "%.1f", mb)
        return trimZero(s) to "MB"
    }

    private fun trimZero(s: String): String =
        s.trimEnd('0').trimEnd('.').ifEmpty { "0" }

    /** 信号格：实心 + 空心方块（无信号/未知显示 --） */
    fun signal(bars: Int): String {
        if (bars < 0) return "--"
        val n = bars.coerceIn(0, 5)
        return "■".repeat(n) + "□".repeat(5 - n)
    }

    fun battery(level: Int, charging: Boolean): String = when {
        level < 0 -> "电量 --"
        charging -> "电量 $level%（充电）"
        else -> "电量 $level%"
    }

    /** 紧凑电量文案（2×2 用，压缩宽度避免截断）：充电态用「·充」后缀，杜绝括号撑宽 */
    fun batteryCompact(level: Int, charging: Boolean): String = when {
        level < 0 -> "电量 --"
        charging -> "电量 $level%·充"
        else -> "电量 $level%"
    }

    /** 纯数值文本（不带单位，用于 4×2 紧凑技术行） */
    fun numText(v: Int): String = if (v == Int.MIN_VALUE) "--" else v.toString()

    fun tempCelsius(v: Float): String =
        if (v < 0f) "--" else String.format(Locale.US, "%.1f", v)

    fun percent(v: Float): String = if (v < 0f) "--" else "${v.roundToInt()}%"

    fun rsrpText(v: Int): String = if (v == Int.MIN_VALUE) "--" else "$v dBm"

    fun snrText(v: Int): String = if (v == Int.MIN_VALUE) "--" else "$v dB"

    fun unreadText(v: Int): String = if (v < 0) "--" else v.toString()

    fun timeText(ms: Long): String =
        if (ms <= 0L) "--" else timeFormat.format(Date(ms))
}
