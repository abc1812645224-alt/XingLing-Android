/*
 * 星灵 (XingLing) · 广播触发的一次性立即刷新（不依赖前台服务 / WorkManager）
 *
 * 国产 ROM（小米 HyperOS 等）上：
 *  - 屏幕点亮广播在 App 处于后台时启动前台服务会被系统拒绝（Android 12+ 后台 FGS 限制）；
 *  - WorkManager / AlarmManager 周期任务在省电 / 自启动策略下可能被延迟甚至冻结。
 * 而亮屏 / 闹钟广播本身仍会派发，且 BroadcastReceiver.goAsync() 允许在回调返回后
 * 再做约 10 秒的短时工作——手机与随身 WiFi 在同一局域网，一次取数通常 2~4 秒，足够完成。
 * 因此用 goAsync + 严格 9 秒超时当场「取数 → 指纹比对 → 重绘」，保证亮屏 / 兜底闹钟
 * 触发时小组件能真正刷新，而不是只启动一个可能被系统拦截或延迟的调度。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object WidgetImmediateRefresh {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastRunElapsed = 0L

    /**
     * 供 BroadcastReceiver.goAsync() 包裹的一次刷新。
     * @param minIntervalMs 进程内最小间隔（亮屏 + 解锁常连续触发，用于节流）；<=0 表示不节流
     * @param finish 刷新结束（或被节流 / 超时）后必被回调一次，通常传 PendingResult::finish
     */
    fun goAsyncRefresh(context: Context, minIntervalMs: Long, finish: () -> Unit) {
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        if (minIntervalMs > 0 && now - lastRunElapsed < minIntervalMs) {
            finish()
            return
        }
        lastRunElapsed = now
        scope.launch {
            try {
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) { refreshOnce(app) }
            } catch (_: Throwable) {
                // 广播路径保持静默：任何异常都不应导致进程崩溃，后台周期任务仍会兜底
            } finally {
                runCatching { finish() }
            }
        }
    }

    /** 取一次数：与快照指纹比对，有实质变化才落盘并重绘（无变化不打扰设备、不重绘） */
    private suspend fun refreshOnce(app: Context) {
        val previous = WidgetSnapshotStore.read(app)
        val fresh = runCatching { WidgetDataLoader.load(app, commit = false) }.getOrNull() ?: return
        if (WidgetRefreshPolicy.shouldRepaint(previous, fresh)) {
            WidgetSnapshotStore.write(app, fresh)
            WidgetRefreshPolicy.repaint(app)
        }
    }

    /** goAsync 短时工作上限：留足一次局域网取数时间，又不触碰广播 ANR 红线 */
    private const val REFRESH_TIMEOUT_MS = 9_000L
}
