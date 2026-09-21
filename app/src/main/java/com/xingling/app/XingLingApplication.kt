/*
 * 星灵 (XingLing)
 * Copyright (C) 2026 XingLing Project
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.xingling.app

import android.app.Application
import com.xingling.app.backend.DeviceNetwork
import com.xingling.app.widget.WidgetRefreshScheduler
import com.xingling.app.widget.WidgetScreenStateWatcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class XingLingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 设备热点无互联网时，把进程网络绑定到 WiFi，保证局域网扫描 / API 请求不走蜂窝或 VPN
        DeviceNetwork.register(this)
        // 桌面小组件分层自适应刷新：
        // 纯后台 15 分钟周期（重复注册不会产生多个任务）
        WidgetRefreshScheduler.schedule(this)
        // 进程存活期间监听屏幕点亮/解锁 → 立即刷新并开启 15 秒高频窗口
        WidgetScreenStateWatcher.register(this)
    }
}
