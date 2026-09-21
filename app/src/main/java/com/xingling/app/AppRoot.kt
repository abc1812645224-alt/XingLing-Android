/*
 * 星灵 (XingLing) · 根导航状态机
 *
 * 首次启动 → 致谢页 →（未配置）设备接入设置页 →（已配置）主界面。
 * 已配置时在后台构造 DeviceBackend 并注入 BackendProvider，供总览等页面轮询。
 *
 * 已配置后从设置/功能页再次进入「添加设备」时，DeviceSetupScreen 作为
 * XingLingApp 内部覆盖层显示（见 MainActivity），保留 Tab 与深层路由栈，
 * 返回键回到上一级而非退出 App。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.DeviceStore
import com.xingling.app.backend.UfiToolsBackend
import com.xingling.app.ui.setup.DeviceSetupScreen
import com.xingling.app.ui.thanks.ThanksScreen

/**
 * 全局可读的当前设备后台实例。
 * 主界面（XingLingApp）内 OverviewScreen 等页面通过该单例取 backend 轮询数据。
 */
object BackendProvider {
    var backend: DeviceBackend? by mutableStateOf(null)
        private set

    /** 主界面请求重新进入设备设置页（如总览空态「去添加设备」），作为 App 内覆盖层 */
    var requestSetup by mutableStateOf(false)

    fun publish(instance: DeviceBackend?) {
        backend = instance
    }
}

@Composable
fun XingLingRoot(
    batTemp: Float,
    batVolt: Int,
    batteryStatus: Int,
    batCurrentNA: Int,
    initialCrashLog: String?
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val store = remember { DeviceStore(appContext) }

    var thanksDone by remember { mutableStateOf(store.thanksShown) }
    var configured by remember { mutableStateOf(store.configured) }
    var backendReady by remember { mutableStateOf(false) }

    // 已配置：构造 backend 并发布到 BackendProvider
    LaunchedEffect(configured) {
        if (configured && !backendReady) {
            val b = UfiToolsBackend(store.host, store.port, store.token.ifBlank { "admin" }, store.zteToken)
            BackendProvider.publish(b)
            backendReady = true
        }
    }

    when {
        !thanksDone -> ThanksScreen(onDone = {
            store.thanksShown = true
            thanksDone = true
        })
        // 首次启动、从未配置设备：全屏设置页，返回键退出 App（默认行为）
        !configured -> DeviceSetupScreen(
            store = store,
            onConfigured = {
                configured = true
                BackendProvider.requestSetup = false
            },
            onBack = null
        )
        // 已配置：始终挂载主界面；requestSetup 时由 XingLingApp 内部覆盖设置页，保留导航栈
        else -> XingLingApp(
            batTemp = batTemp,
            batVolt = batVolt,
            batteryStatus = batteryStatus,
            batCurrentNA = batCurrentNA,
            initialCrashLog = initialCrashLog
        )
    }
}
