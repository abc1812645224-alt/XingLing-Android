/*
 * 星灵 (XingLing) · 桌面小组件分层自适应刷新策略
 *
 * 目标：既实时（点击/亮屏/停留当场刷新），又不让随身 WiFi 后台全天候承压。
 * 分层（由快到慢，逐层兜底）：
 *   L0 点击         —— 用户点卡片本体/「刷新」按钮，立即取数重绘（0 延迟，单次）
 *   L1 亮屏窗口     —— 屏幕点亮/解锁触发一次刷新，并开启 15s 高频轮询窗口
 *                      （短时前台服务，上限 3 分钟，熄屏或 App 回前台立即停止）
 *   L2 App 内预览页 —— 预览页处于前台（RESUMED）时 15s 刷新，onPause 立即停止
 *   L3 纯后台       —— WorkManager 15 分钟周期（数据无变化则跳过重绘）
 *   L4 系统兜底     —— appwidget-provider 的 updatePeriodMillis（30 分钟）
 *
 * 所有高频路径共用同一「取数 → 与快照比对 → 有变化才重绘」口径，
 * 避免无意义的 notifyAppWidgetViewDataChanged 与设备端重复请求。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetRefreshPolicy {

    /** L1/L2 高频轮询间隔：15 秒 */
    const val POLL_INTERVAL_MS = 15_000L

    /** L1 高频窗口上限：3 分钟（到时自动停止，无需用户干预） */
    const val WINDOW_MAX_MS = 180_000L

    /** L3 纯后台周期：15 分钟（WorkManager 允许的最小周期） */
    const val BACKSTAGE_INTERVAL_MINUTES = 15L

    /** L2 App 内预览页刷新间隔：15 秒 */
    const val APP_FOREGROUND_INTERVAL_MS = 15_000L

    /** 同进程内取数复用窗口：避免同一时刻多路触发重复请求设备 */
    const val CACHE_REUSE_MS = 2_000L

    /**
     * 快照「可见内容」指纹：排除 updateAt（每次取数都会变，不代表界面需要重绘），
     * 但保留 stale（缓存/离线标记变化同样需要让用户看到）。
     */
    fun contentKey(s: WidgetSnapshot): String = buildString {
        append(s.configured).append('|')
        append(s.deviceName).append('|')
        append(s.version).append('|')
        append(s.signalBars).append('|')
        append(s.battery).append('|')
        append(s.charging).append('|')
        append(s.dailyBytes).append('|')
        append(s.monthlyBytes).append('|')
        append(s.carrier).append('|')
        append(s.netType).append('|')
        append(s.band).append('|')
        append(s.cpuUsage).append('|')
        append(s.cpuTemp).append('|')
        append(s.wifiBand).append('|')
        append(s.memUsage).append('|')
        append(s.rsrp).append('|')
        append(s.snr).append('|')
        append(s.smsUnread).append('|')
        append(s.stale)
    }

    /** 是否需要重绘：有实质变化（或首次出图）才重绘，无变化直接跳过 */
    fun shouldRepaint(previous: WidgetSnapshot?, fresh: WidgetSnapshot?): Boolean {
        if (fresh == null) return false
        if (previous == null) return true
        return contentKey(previous) != contentKey(fresh)
    }

    /** 重绘两个尺寸的桌面小组件 */
    suspend fun repaint(context: Context) {
        val app = context.applicationContext
        runCatching { XingLingWidget().updateAll(app) }
        runCatching { XingLingWidgetSmall().updateAll(app) }
    }
}
