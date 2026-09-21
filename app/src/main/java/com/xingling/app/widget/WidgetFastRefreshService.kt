/*
 * 星灵 (XingLing) · 亮屏高频刷新窗口（短时前台服务）
 *
 * 职责：屏幕点亮/解锁后开启一个「最多 3 分钟」的 15 秒轮询窗口。
 *   - Android 14+（API 34）走 FOREGROUND_SERVICE_TYPE_SHORT_SERVICE + 对应权限，
 *     由系统保证 3 分钟上限（超时回调 onTimeout 停止）；
 *   - 低版本（API 28~33）走普通前台服务 + IMPORTANCE_MIN 静默通知渠道，
 *     由本服务内部的 3 分钟 deadline 自行停止。
 *   - 通知仅在窗口期内存在，停止时立即撤销。
 *   - 熄屏（ACTION_SCREEN_OFF）或星灵 App 回到前台时立即停止。
 *   - 每 15 秒取一次数，先与快照比对：无变化跳过重绘，有变化才重绘。
 *
 * 生命周期边界：进程被杀 → 窗口随之结束（不常驻、不闹钟续跑），
 * 这是「不给随身 WiFi 后台造成全天候压力」的关键约束。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xingling.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WidgetFastRefreshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        active = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWindow()
            return START_NOT_STICKY
        }
        promoteToForeground()
        if (pollJob?.isActive != true) {
            pollJob = scope.launch { runWindow() }
        }
        return START_NOT_STICKY
    }

    /** Android 14+ 短时前台服务到点回调（系统级 3 分钟硬上限） */
    override fun onTimeout(startId: Int) {
        stopWindow()
    }

    override fun onDestroy() {
        active = false
        scope.cancel()
        super.onDestroy()
    }

    // ───────────────────────── 窗口主体 ─────────────────────────

    private suspend fun runWindow() {
        val deadline = SystemClock.elapsedRealtime() + WidgetRefreshPolicy.WINDOW_MAX_MS
        var previous = WidgetSnapshotStore.read(this)

        while (scope.isActive && SystemClock.elapsedRealtime() < deadline) {
            if (!isScreenOn()) break // 熄屏：立即退出窗口
            val fresh = runCatching {
                WidgetDataLoader.load(this, commit = false)
            }.getOrNull()
            if (WidgetRefreshPolicy.shouldRepaint(previous, fresh) && fresh != null) {
                WidgetSnapshotStore.write(this, fresh)
                WidgetRefreshPolicy.repaint(this)
                previous = fresh
            }
            delay(WidgetRefreshPolicy.POLL_INTERVAL_MS)
        }
        stopWindow()
    }

    private fun stopWindow() {
        pollJob?.cancel()
        pollJob = null
        stopForegroundCompat()
        stopSelf()
    }

    private fun isScreenOn(): Boolean = runCatching {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isInteractive ?: false
    }.getOrDefault(false)

    // ───────────────────────── 前台化 / 通知 ─────────────────────────

    private fun promoteToForeground() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+：短时前台服务（系统保证 3 分钟上限）
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "小组件实时刷新",
            NotificationManager.IMPORTANCE_MIN // 静默：不响铃、不亮屏、不进状态栏提示
        ).apply {
            description = "屏幕点亮时短暂刷新桌面小组件，最长 3 分钟"
            setShowBadge(false)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("正在刷新小组件")
            .setContentText("屏幕点亮期间实时同步设备状态（最长 3 分钟）")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

    companion object {

        private const val CHANNEL_ID = "xingling_widget_fast_refresh"
        private const val NOTIFICATION_ID = 0x5747 // "WG"

        const val ACTION_STOP = "com.xingling.app.widget.action.STOP_FAST_REFRESH"

        @Volatile
        var active: Boolean = false
            private set

        /**
         * 开启刷新窗口。若系统禁止后台启动前台服务（Android 12+ 的后台限制），
         * 降级为一次性 WorkManager 立即刷新，保证「亮屏当场刷一次」不落空。
         */
        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, WidgetFastRefreshService::class.java)
            runCatching { ContextCompat.startForegroundService(app, intent) }
                .onFailure { WidgetRefreshScheduler.enqueueImmediate(app) }
        }

        /** 停止刷新窗口（幂等；App 回前台、熄屏时调用） */
        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, WidgetFastRefreshService::class.java)
                )
            }
        }
    }
}
