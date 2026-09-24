/*
 * 星灵 (XingLing) · 总览一屏 → 深层分页导航容器
 *
 * 总览页底部提供「全部功能」入口卡片，点击进入对应功能页；
 * 功能页自带返回栏，返回后回到总览。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.ui.feature.FeatureRoute
import com.xingling.app.ui.feature.FeatureRouteHost

@Composable
fun OverviewHome(backend: DeviceBackend?, onAddDevice: () -> Unit, pollIntervalMs: Long = 5_000L) {
    var route by remember { mutableStateOf<FeatureRoute?>(null) }
    // 总览页常驻底层，二级页覆盖在上层，返回后总览滚动位置与状态保留
    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        OverviewScreen(
            backend = backend,
            onAddDevice = onAddDevice,
            onOpenFeature = { route = it },
            pollIntervalMs = pollIntervalMs
        )
        val current = route
        if (current != null) {
            Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                FeatureRouteHost(
                    route = current,
                    backend = backend,
                    onBack = { route = null },
                    onAddDevice = onAddDevice
                )
            }
        }
    }
}
