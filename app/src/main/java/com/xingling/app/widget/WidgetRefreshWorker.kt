/*
 * 星灵 (XingLing) · 桌面小组件后台周期刷新（分层策略最底层）
 *
 * 系统 updatePeriodMillis（30 分钟）作为系统级兜底保留；
 * 真正驱动后台刷新的 WorkManager 周期任务由 30 分钟下调为 15 分钟
 * （系统允许的最小周期），并统一走「取数 → 与快照比对 → 有实质变化才重绘」，
 * 数据无变化时不重绘、不唤醒界面。
 *
 * 另提供一次性立即刷新通道，用于「亮屏但系统禁止后台启动前台服务」时的降级。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        // 自愈：国产 ROM 可能清掉 AlarmManager 闹钟，每次 Worker 跑都补挂一次
        runCatching { WidgetAlarmScheduler.schedule(app) }
        val previous = WidgetSnapshotStore.read(app)
        val fresh = runCatching { WidgetDataLoader.load(app, commit = false) }.getOrNull()
        // 取数失败（网络不通/设备不在线）：交给 WorkManager 退避重试，
        // 避免进程死后调度链彻底断掉；未配置设备则不重试。
        if (fresh == null) {
            val configured = runCatching {
                com.xingling.app.backend.DeviceStore(app).configured
            }.getOrDefault(false)
            return if (configured) Result.retry() else Result.success()
        }
        if (WidgetRefreshPolicy.shouldRepaint(previous, fresh)) {
            WidgetSnapshotStore.write(app, fresh)
            WidgetRefreshPolicy.repaint(app)
        }
        return Result.success()
    }
}

object WidgetRefreshScheduler {

    private const val WORK_NAME = "xingling_widget_refresh"
    private const val ONE_SHOT_NAME = "xingling_widget_refresh_once"

    /** 纯后台周期刷新：15 分钟（UPDATE 策略保证周期调整在升级后立即生效） */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            WidgetRefreshPolicy.BACKSTAGE_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setInitialDelay(2, TimeUnit.MINUTES)
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    /** 一次性立即刷新（亮屏触发的降级通道，不持有前台服务） */
    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
