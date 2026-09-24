/*
 * 星灵 (XingLing) · 屏幕状态触发
 *
 * ACTION_SCREEN_ON / ACTION_USER_PRESENT → 立即刷新一次，并开启 15 秒高频窗口；
 * ACTION_SCREEN_OFF                      → 立即关闭窗口（不再打扰设备与电量）。
 *
 * 两种注册方式并存：
 *   1) 清单静态注册：部分 ROM/系统仍会派发（Android 8+ 对 SCREEN_ON 类隐式广播
 *      存在静态注册限制，不派发也不报错，仅作为兼容路径）；
 *   2) 进程内动态注册（App 启动或小组件被系统唤起时）：进程存活期间可靠生效。
 * 无论哪条路径命中，接收器本身只做极轻量动作（尝试启动窗口服务/降级一次性任务），
 * 绝不在广播回调里做网络取数，避免广播超时。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class WidgetScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT -> {
                // 亮屏/解锁：用 goAsync 当场取数刷新一次，绕过国产 ROM 对后台启动前台服务、
                // WorkManager 的拦截/延迟，保证“点亮屏幕就能看到新数据”。
                // 不再从后台广播启动 WidgetFastRefreshService：Android 12+ 后台 FGS 限制、
                // Android 14 短服务配额会使 startForeground 无法在时限内完成，从而抛出
                // ForegroundServiceDidNotStartInTimeException 崩溃。
                val pending = goAsync()
                WidgetImmediateRefresh.goAsyncRefresh(app, 5_000L) { pending.finish() }
            }

            Intent.ACTION_SCREEN_OFF -> {
                // 熄屏时无需强制杀服务，服务内部的 runWindow() 循环会通过 isScreenOn() 自行检测并在下一个周期安全退出。
                // 这样可以避免由于 startForegroundService 后立刻 stopService 导致系统抛出 ForegroundServiceDidNotStartInTimeException 的致命异常。
            }
        }
    }
}

/** 进程内动态注册辅助（幂等，重复注册不会产生多个监听） */
object WidgetScreenStateWatcher {

    private var receiver: WidgetScreenStateReceiver? = null

    fun register(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val r = WidgetScreenStateReceiver()
        runCatching {
            ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = r
        }
    }

    fun unregister(context: Context) {
        val r = receiver ?: return
        runCatching { context.applicationContext.unregisterReceiver(r) }
        receiver = null
    }
}
