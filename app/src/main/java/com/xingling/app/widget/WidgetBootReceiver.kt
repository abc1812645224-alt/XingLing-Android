/*
 * 星灵 (XingLing) · 小组件开机/升级/闹钟兜底刷新
 *
 * 解决国产 ROM（小米 HyperOS 等）杀后台后小组件不再更新的问题：
 *   1. BOOT_COMPLETED / MY_PACKAGE_REPLACED / 厂商快速开机：
 *      开机或 App 升级后重新调度 WorkManager + AlarmManager，并立即刷新一次。
 *   2. AlarmManager 非精确重复闹钟（ACTION_WIDGET_ALARM）：
 *      进程死亡后由系统直接派发给本接收器（显式广播，不受隐式广播限制），
 *      比 WorkManager/JobScheduler 在国产 ROM 上更可靠；收到后走一次性 Worker 取数。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.TimeUnit

/** 开机 / App 升级 / 厂商快速开机后：重建全部刷新调度，并立即取一次数 */
class WidgetBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action in BOOT_ACTIONS) {
            val app = context.applicationContext
            // 重新调度 WorkManager 15 分钟周期任务
            WidgetRefreshScheduler.schedule(app)
            // 重建 AlarmManager 兜底闹钟
            WidgetAlarmScheduler.schedule(app)
            // 立即刷新一次（开机后用户很快会看到桌面）
            WidgetRefreshScheduler.enqueueImmediate(app)
        }
    }

    companion object {
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.QUICKBOOT_POWERON",
            "com.miui.intent.action.BOOT_COMPLETED"
        )
    }
}

/**
 * AlarmManager 兜底：每 [INTERVAL_MINUTES] 分钟一次非精确闹钟。
 *
 * - 用 setAndAllowWhileIdle / setExactAndAllowWhileIdle 的链式一次性闹钟在国产 ROM
 *   上容易被吞；这里用 setInexactRepeating，系统在 Doze 维护窗口批量派发，
 *   虽然不精确但不会像 JobScheduler 那样被彻底冻结。
 * - 闹钟广播是显式派发给本 App 的 WidgetAlarmReceiver，进程不存在时会被拉起。
 */
class WidgetAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_WIDGET_ALARM) return
        val app = context.applicationContext
        // 闹钟已把进程拉起：直接 goAsync 当场取数（比再投递一个 WorkManager 任务更即时、
        // 更不容易被国产 ROM 的省电策略延迟），并顺手补挂可能被清掉的周期任务 / 闹钟。
        val pending = goAsync()
        WidgetImmediateRefresh.goAsyncRefresh(app, 0L) { pending.finish() }
        runCatching { WidgetRefreshScheduler.schedule(app) }
        runCatching { WidgetAlarmScheduler.schedule(app) }
    }

    companion object {
        const val ACTION_WIDGET_ALARM = "com.xingling.app.widget.action.ALARM_REFRESH"
        const val ONE_SHOT_NAME = "xingling_widget_refresh_once"
    }
}

/** AlarmManager 调度封装（幂等，重复调用不会产生多个闹钟） */
object WidgetAlarmScheduler {

    private const val INTERVAL_MINUTES = 20L

    fun schedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val pi = buildPendingIntent(app)

        val intervalMs = TimeUnit.MINUTES.toMillis(INTERVAL_MINUTES)
        val firstAt = SystemClock.elapsedRealtime() + intervalMs

        runCatching {
            // 非精确重复闹钟：Doze 下合并到维护窗口执行，但不会被永久冻结
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                firstAt,
                intervalMs,
                pi
            )
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(buildPendingIntent(app)) }
    }

    private fun buildPendingIntent(app: Context): PendingIntent {
        val intent = Intent(app, WidgetAlarmReceiver::class.java).apply {
            action = WidgetAlarmReceiver.ACTION_WIDGET_ALARM
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(app, REQUEST_CODE, intent, flags)
    }

    private const val REQUEST_CODE = 0xA101
}
