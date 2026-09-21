/*
 * 星灵 (XingLing) · 桌面小组件入口
 *
 * 两个尺寸共享同一取数链路与视觉规范：
 *   - XingLingWidget（4×2 横条，主图案）
 *   - XingLingWidgetSmall（2×2 迷你）
 * 渲染策略：先用缓存快照即时出图，再后台取数刷新（失败保留缓存并标注「缓存」）。
 * 点击卡片打开 App；卡片内「刷新」按钮触发一次即时刷新（强制取数 + 重绘）。
 *
 * 分层自适应刷新（详见 WidgetRefreshPolicy）：
 *   点击即时 → 亮屏触发并开启 15 秒高频窗口（短时前台服务，3 分钟上限）
 *   → App 内预览页前台 15 秒 → 纯后台 WorkManager 15 分钟 → updatePeriodMillis 兜底。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.ActionParameters

/** 两种尺寸的公共实现，compact 区分 2×2 / 4×2 布局 */
abstract class BaseXingLingWidget(private val compact: Boolean) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = mutableStateOf(WidgetSnapshotStore.read(context) ?: WidgetSnapshot())

        provideContent {
            XingLingWidgetSurface(state.value, compact)
        }

        // 后台取数：成功则刷新界面（失败时 load 内部已降级为缓存快照）
        val fresh = WidgetDataLoader.load(context)
        if (fresh.updateAt > 0L) state.value = fresh
    }
}

/** 4×2 横条小组件 */
class XingLingWidget : BaseXingLingWidget(compact = false)

/** 2×2 迷你小组件 */
class XingLingWidgetSmall : BaseXingLingWidget(compact = true)

/** 卡片内「刷新」按钮：一次点击刷新全部星灵小组件 */
class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // 点击即拉取：强制真实取数并落缓存，再重绘两个尺寸（保证「点了就动」）
        runCatching { WidgetDataLoader.load(context, commit = true, force = true) }
        WidgetRefreshPolicy.repaint(context)
    }
}

class XingLingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = XingLingWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context)
        // 进程被系统唤起时补挂屏幕状态监听（进程存活期间亮屏即触发刷新）
        WidgetScreenStateWatcher.register(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetFastRefreshService.stop(context)
    }
}

class XingLingWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = XingLingWidgetSmall()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context)
        WidgetScreenStateWatcher.register(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetFastRefreshService.stop(context)
    }
}
