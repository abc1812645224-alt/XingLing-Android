/*
 * 星灵 (XingLing) · 底部 5 Tab 组网与设置中心子页
 *
 * 对齐官方 UFIPanel 底部导航结构：总览 / 信号 / 频段 / 短信 / 设置。
 * 频段 / 短信 / 设置 Tab 复用 FeaturesHub 的 FeatureRoute 页面与
 * FeatureBase 的 iOS 玻璃卡片组件组网，不新建整套 UI。
 * 设置中心子页：AT 终端 / 设备昵称 / HTTP 代理调试 / 轮询频率。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.CellInfo
import com.xingling.app.backend.UfiToolsBackend
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 设置 Tab 深层子页路由 */
enum class SettingsPage(val title: String) {
    AT("AT 终端"),
    NICKNAME("设备昵称"),
    PROXY("HTTP 代理调试"),
    WIDGET("桌面小组件")
}

// ═══════════════════════════════════════════
// Tab 入口行（iOS 玻璃卡片内列表行）
// ═══════════════════════════════════════════
@Composable
fun FeatureRouteRow(
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = iOSLabel)
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text("›", color = iOSSecondaryLabel, fontSize = 18.sp)
    }
}

@Composable
private fun TabGroupHeader(label: String, color: Color = iOSBlue) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

// ═══════════════════════════════════════════
// 频段 Tab（对齐官方 Band：网络选择/锁频/锁小区）
// 入口唯一性：网络控制批 6 项 + 流量校准 / 性能模式归此；总览页仅保留 ≤6 项高频快捷入口，不重复全量列表。
// ═══════════════════════════════════════════
@Composable
fun BandTabContent(
    backend: DeviceBackend?,
    onOpenFeature: (FeatureRoute) -> Unit
) {
    val scope = rememberCoroutineScope()
    var volteEnabled by remember { mutableStateOf(false) }
    var vonrEnabled by remember { mutableStateOf(false) }
    var performanceMode by remember { mutableStateOf(false) }
    var perfBusy by remember { mutableStateOf(false) }
    var perfMsg by remember { mutableStateOf<String?>(null) }
    var perfErr by remember { mutableStateOf(false) }
    var caEnabled by remember { mutableStateOf(false) }
    var nrBandLock by remember { mutableStateOf<List<Int>>(emptyList()) }
    var lteBandLock by remember { mutableStateOf<List<Int>>(emptyList()) }
    var lockedCell by remember { mutableStateOf<CellInfo?>(null) }
    var bearerPref by remember { mutableStateOf("") }
    var simSlot by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    // 加载状态
    LaunchedEffect(backend) {
        val feats = backend?.features ?: return@LaunchedEffect
        loading = true
        feats.volteEnabled().onSuccess { volteEnabled = it }
        feats.vonrEnabled().onSuccess { vonrEnabled = it }
        feats.getPerformanceMode().onSuccess { performanceMode = it }
        feats.endcState().onSuccess { caEnabled = it }
        feats.getNrBandLock().onSuccess { nrBandLock = it }
        feats.getLteBandLock().onSuccess { lteBandLock = it }
        feats.lockedCells().onSuccess { lockedCell = it.firstOrNull() }
        feats.getBearerPreference().onSuccess { bearerPref = it }
        feats.getSimSlot().onSuccess { simSlot = it }
        loading = false
    }

    val bandLockText = when {
        nrBandLock.isNotEmpty() && lteBandLock.isNotEmpty() -> "N${nrBandLock.size}+B${lteBandLock.size}"
        nrBandLock.isNotEmpty() -> "N${nrBandLock.joinToString("+")}"
        lteBandLock.isNotEmpty() -> "${lteBandLock.size}个频段"
        else -> "未锁定"
    }
    val cellLockText = if (lockedCell != null && lockedCell!!.pci >= 0) "PCI ${lockedCell!!.pci}" else "未锁定"
    val bearerText = when (bearerPref) {
        "0" -> "5G SA"
        "1" -> "5G NSA"
        "2" -> "4G Only"
        "3" -> "5G+4G"
        else -> when (bearerPref) { "WL_AND_5G" -> "5G+4G"; "WL_5G" -> "5G Only"; "WL_LTE" -> "4G Only"; else -> if (bearerPref.isNotEmpty()) bearerPref else "5G SA" }
    }
    val simText = when (simSlot) {
        "0" -> "主卡"
        "1" -> "副卡"
        else -> if (simSlot.isNotEmpty()) simSlot else "主卡"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 网络控制
        GroupHeader("网络控制")
        FeatureCard {
            IconRow(Icons.Filled.Lock, Color(0xFF007AFF), "锁频段", "锁定指定 5G/4G 射频频段",
                trailing = { Text(bandLockText, color = iOSSecondaryLabel, fontSize = 14.sp); Spacer(Modifier.width(8.dp)); ChevronRight() }
            ) { onOpenFeature(FeatureRoute.BANDS) }
            IconRow(Icons.Filled.Person, Color(0xFF5856D6), "锁小区", "锁定指定物理 PCI/ARFCN",
                trailing = { Text(cellLockText, color = iOSSecondaryLabel, fontSize = 14.sp); Spacer(Modifier.width(8.dp)); ChevronRight() }
            ) { onOpenFeature(FeatureRoute.CELL_LOCK) }
            IconRow(Icons.Filled.Settings, Color(0xFF007AFF), "网络模式", "锁定指定 5G/4G 模组优先权",
                trailing = { Text(bearerText, color = iOSSecondaryLabel, fontSize = 14.sp); Spacer(Modifier.width(8.dp)); ChevronRight() }
            ) { onOpenFeature(FeatureRoute.NETWORK_MODE) }
            IconRow(Icons.Filled.Build, Color(0xFF5856D6), "组网模式", "选择 SA 独立或 NSA 非独立组网",
                trailing = { Text("SA+NSA", color = iOSSecondaryLabel, fontSize = 14.sp); Spacer(Modifier.width(8.dp)); ChevronRight() }
            ) { onOpenFeature(FeatureRoute.NETWORK_MODE) }
            IconToggleRow(Icons.Filled.Star, Color(0xFFFF3B30), "载波聚合", "聚合多载波频段，提升下载速率",
                checked = caEnabled, onCheckedChange = { v ->
                    caEnabled = v
                    scope.launch { runCatching { backend?.features?.setEndcState(v) } }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 网络设置
        GroupHeader("网络设置")
        FeatureCard {
            IconToggleRow(Icons.Filled.Refresh, Color(0xFF34C759), "VoLTE / VoNR", "高清语音与 5G 通话开关",
                checked = volteEnabled || vonrEnabled, onCheckedChange = { v ->
                    volteEnabled = v
                    scope.launch { runCatching { backend?.features?.setVolte(v) } }
                }
            )
            IconRow(Icons.Filled.Call, Color(0xFFFF9500), "SIM 卡槽", "主卡 / 副卡 / 双卡待机",
                trailing = { Text(simText, color = iOSSecondaryLabel, fontSize = 14.sp); Spacer(Modifier.width(8.dp)); ChevronRight() }
            ) { onOpenFeature(FeatureRoute.SIM_SLOT) }
            IconRow(Icons.Filled.Email, Color(0xFF007AFF), "APN 配置", "配置 APN 接入点及拨号协议",
                trailing = { ChevronRight() }
            ) { onOpenFeature(FeatureRoute.APN) }
            IconToggleRow(Icons.Filled.Build, Color(0xFFFF9500), "性能模式", "高性能 / 省电切换",
                checked = performanceMode, enabled = !perfBusy, onCheckedChange = { v ->
                    perfBusy = true
                    scope.launch {
                        val r = runCatching { backend?.features?.setPerformanceMode(v) }
                        val ex = r.exceptionOrNull()
                        if (ex != null) {
                            perfErr = true
                            perfMsg = "性能模式切换失败：${ex.message ?: "请确认设备在线及官方后台口令"}"
                        } else {
                            performanceMode = v
                            perfErr = false
                            perfMsg = "性能模式已${if (v) "开启" else "关闭"}"
                        }
                        perfBusy = false
                    }
                }
            )
        }

        perfMsg?.let { m ->
            Text(
                m,
                color = if (perfErr) Color(0xFFFF3B30) else iOSSecondaryLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

// ═══════════════════════════════════════════
// 短信 Tab（对齐官方 Sms：列表/发送/转发）
// ═══════════════════════════════════════════
@Composable
fun SmsTabContent(
    backend: DeviceBackend?,
    onOpenFeature: (FeatureRoute) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("短信", style = MaterialTheme.typography.headlineMedium, color = iOSLabel)
                Spacer(modifier = Modifier.height(2.dp))
                Text("短信收发与多通道转发", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }

        FeatureCard(
            title = "消息",
            subtitle = if (backend == null) "尚未连接设备" else "当前设备：${backend.deviceAddress}"
        ) {
            FeatureRouteRow("短信收发", "发短信 / 收件箱 / 删除") { onOpenFeature(FeatureRoute.SMS) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("SMS 转发", "邮件 / 钉钉 / curl 通道与黑名单") { onOpenFeature(FeatureRoute.FORWARD) }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

// ═══════════════════════════════════════════
// 设置 Tab（对齐官方 Settings：连接/外观/系统工具 + 本轮补齐项）
// ═══════════════════════════════════════════
@Composable
fun SettingsTabContent(
    backend: DeviceBackend?,
    onAddDevice: () -> Unit,
    onOpenSettingsPage: (SettingsPage) -> Unit,
    onOpenFeature: (FeatureRoute) -> Unit,
    pollIntervalMs: Long,
    onPollIntervalChange: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf<String?>(null) }
    var quickActionBusy by remember { mutableStateOf<String?>(null) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    var quickActionMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backend) {
        backend?.features?.getNickname()?.onSuccess { nickname = it.ifBlank { null } }
    }

    // 快捷操作消息自动消失
    LaunchedEffect(quickActionMsg) {
        if (quickActionMsg != null) {
            delay(2500)
            quickActionMsg = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("设置", style = MaterialTheme.typography.headlineMedium, color = iOSLabel)
                Spacer(modifier = Modifier.height(2.dp))
                Text("连接 · 轮询 · 调试工具", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }

        // 1. 设备连接
        FeatureCard(
            title = "设备连接",
            subtitle = if (backend == null) "尚未连接设备" else "当前设备：${backend.deviceAddress}${nickname?.let { " · $it" } ?: ""}"
        ) {
            FeatureRouteRow("去设备设置页", "录入地址 / 端口 / 后台口令") { onAddDevice() }
        }

        // 1.5 设备标识信息
        if (backend != null) {
            var identity by remember { mutableStateOf<Map<String, String>?>(null) }
            LaunchedEffect(backend) {
                backend.features.deviceIdentity().onSuccess { identity = it }
            }
            identity?.let { idMap ->
                FeatureCard(
                    title = "设备标识",
                    subtitle = "IMEI / IMSI / ICCID 等设备与 SIM 卡信息"
                ) {
                    idMap.forEach { (k, v) ->
                        IdentityRow(k, v)
                        Divider(color = iOSSeparator, thickness = 1.dp)
                    }
                }
            }
        }

        // 2. 轮询频率（三档，实际生效于总览页轮询 delay）
        FeatureCard(
            title = "轮询频率",
            subtitle = "影响总览 / 信号页数据刷新间隔（当前 ${pollIntervalMs / 1000}s）"
        ) {
            listOf(
                1000L to "极速（约 1 秒）",
                2000L to "高速（约 2 秒）",
                3000L to "快速（约 3 秒）",
                5000L to "标准（约 5 秒）",
                10000L to "省电（约 10 秒）"
            ).forEach { (ms, label) ->
                SelectRow(
                    label = label,
                    desc = if (ms == pollIntervalMs) "使用中" else null,
                    selected = ms == pollIntervalMs,
                    onClick = { onPollIntervalChange(ms) }
                )
                Divider(color = iOSSeparator, thickness = 1.dp)
            }
            Text(
                "轮询频率越高越耗电；仅影响客户端轮询间隔，不改动设备端上报。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
        }

        // 3. 本轮补齐的调试工具
        FeatureCard(
            title = "调试工具",
            subtitle = "官方 UFIPanel 同款能力"
        ) {
            FeatureRouteRow("AT 终端", "执行任意 AT 指令，查看设备输出") { onOpenSettingsPage(SettingsPage.AT) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("设备昵称", "设置设备显示别名（/api/set_nickname）") { onOpenSettingsPage(SettingsPage.NICKNAME) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("HTTP 代理调试", "经设备代理请求目标 URL，查看响应") { onOpenSettingsPage(SettingsPage.PROXY) }
        }

        // 4. 设备维护（原首页「全部功能」列表下放，按类归入设置 Tab）
        FeatureCard(
            title = "设备维护",
            subtitle = "固件升级与系统级维护（真实后台操作）"
        ) {
            FeatureRouteRow("OTA 更新", "固件 / APK 升级") { onOpenFeature(FeatureRoute.OTA) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("系统更新治理", "一键禁用 ZTE 更新组件") { onOpenFeature(FeatureRoute.SYS_UPDATE) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("设备分区 · BOOT", "AB 分区展示 / 提取镜像下载") { onOpenFeature(FeatureRoute.PARTITION) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("DIAG 工具", "展锐 DIAG 通道查 IMEI") { onOpenFeature(FeatureRoute.DIAG) }
        }

        // 5. 高级工具（原首页「全部功能」列表下放，按类归入设置 Tab）
        FeatureCard(
            title = "高级工具",
            subtitle = "root / 远程终端 / 局域网 / 插件"
        ) {
            FeatureRouteRow("root Shell", "任意命令 / root 交互") { onOpenFeature(FeatureRoute.SHELL) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("ttyd 终端", "内嵌 Web 终端") { onOpenFeature(FeatureRoute.TTYD) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("SSH", "服务状态与远程命令") { onOpenFeature(FeatureRoute.SSH) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("无线 ADB", "ADB over WiFi 自启") { onOpenFeature(FeatureRoute.ADB) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("插件系统", "插件市场与欢迎语") { onOpenFeature(FeatureRoute.PLUGIN) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("局域网管理", "DHCP / 黑白名单 / 客户端") { onOpenFeature(FeatureRoute.LAN) }
            Divider(color = iOSSeparator, thickness = 1.dp)
            FeatureRouteRow("SMB 共享", "局域网文件共享开关") { onOpenFeature(FeatureRoute.SAMBA) }
        }

        // 5.7 更多功能（需要二级页面配置）
        GroupHeader("更多功能")
        FeatureCard {
            IconRow(Icons.Filled.Star, Color(0xFF007AFF), "主题定制", "设备 Web 后台主题与背景", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.THEME_CUSTOM) }
            IconRow(Icons.Filled.List, Color(0xFF007AFF), "文件管理", "上传 / 下载 / 删除设备文件", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.FILE_MANAGER) }
            IconRow(Icons.Filled.Person, Color(0xFFAF52DE), "自定义插件源", "第三方插件仓库接入", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.CUSTOM_PLUGIN) }
            IconRow(Icons.Filled.Build, Color(0xFFFF9500), "CPU 核心控制", "核心开关 / 调频 / 调度策略", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.CPU_CONTROL) }
            IconRow(Icons.Filled.Refresh, Color(0xFF007AFF), "开机自启脚本", "init.d 脚本管理与运行", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.BOOT_SCRIPTS) }
            IconRow(Icons.Filled.DateRange, Color(0xFFFF9500), "Crontab 定时", "系统 crontab 表达式编辑", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.CRONTAB) }
            IconRow(Icons.Filled.Settings, Color(0xFF007AFF), "EasyTier 组网", "异地组网虚拟局域网", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.EASYTIER) }
            IconRow(Icons.Filled.Person, Color(0xFFAF52DE), "EasyConnect", "校园网 VPN 客户端", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.EASYCONNECT) }
            IconRow(Icons.Filled.Email, Color(0xFF007AFF), "多设备管理", "设备集群添加 / 切换 / 删除", trailing = { ChevronRight() }) { onOpenFeature(FeatureRoute.DEVICE_MANAGER) }
        }

        // 重启确认对话框
        if (showRebootConfirm) {
            AlertDialog(
                onDismissRequest = { showRebootConfirm = false },
                title = { Text("确认重启") },
                text = { Text("确定要重启设备吗？重启期间网络将短暂中断。") },
                confirmButton = {
                    TextButton(onClick = {
                        showRebootConfirm = false
                        quickActionBusy = "reboot"
                        scope.launch {
                            val r = runCatching { backend?.features?.rebootDevice() }
                            quickActionMsg = if (r.exceptionOrNull() != null)
                                "重启失败：${r.exceptionOrNull()?.message}"
                            else "重启指令已下发，设备约 1~3 分钟恢复联网"
                            quickActionBusy = null
                        }
                    }) { Text("重启") }
                },
                dismissButton = {
                    TextButton(onClick = { showRebootConfirm = false }) { Text("取消") }
                }
            )
        }

        // 快捷操作结果提示
        quickActionMsg?.let { msg ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }

        // 6. 桌面小组件（预览 + 分层自适应刷新说明）
        FeatureCard(
            title = "桌面小组件",
            subtitle = "分层自适应刷新：亮屏即刷 · 停留 15 秒轮询 · 离屏即停"
        ) {
            FeatureRouteRow(
                "小组件预览与刷新状态",
                "4×2 全字段 / 2×2 精简 · 前台 15 秒刷新"
            ) { onOpenSettingsPage(SettingsPage.WIDGET) }
        }

        // 7. 关于
        FeatureCard(title = "关于", subtitle = "星灵 · 移动网络控制中枢") {
            Text(
                "版本 0.2.1 · 兼容 UFI-TOOLS 后台与官方 UFIPanel 客户端协议（/api/* REST + goform 反代 + kano 签名）。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel,
                lineHeight = 19.sp
            )
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

// ═══════════════════════════════════════════
// 设置子页 1：AT 终端
// ═══════════════════════════════════════════
@Composable
fun AtTerminalScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var command by remember { mutableStateOf("AT+CSQ") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FeaturePage(
        title = "AT 终端",
        subtitle = "/api/AT · 任意 AT 指令（执行真实后台指令）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "指令输入", subtitle = "示例：AT+CSQ / AT+COPS? / AT+CGDCONT?") {
            FeatureTextField(
                value = command,
                onValueChange = { command = it },
                label = "AT 指令",
                placeholder = "如 AT+CSQ",
                keyboard = androidx.compose.ui.text.input.KeyboardType.Text
            )
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "执行指令",
                loading = busy,
                enabled = !busy,
                onClick = {
                    val cmd = command.trim()
                    if (cmd.isEmpty()) { msg = "请输入 AT 指令"; msgErr = true; return@ActionButton }
                    busy = true; msg = ""
                    scope.launch {
                        feats?.atCommand(cmd)
                            ?.onSuccess { out -> output = out; msg = "执行成功"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
        FeatureCard(title = "设备输出", subtitle = "AT 指令执行结果") {
            CommandOutput(output, height = 260.dp)
        }
    }
}

// ═══════════════════════════════════════════
// 设置子页 2：设备昵称
// ═══════════════════════════════════════════
@Composable
fun NicknameScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var current by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.getNickname()?.onSuccess {
            current = it
            value = it
            loaded = true
        }
    }

    FeaturePage(
        title = "设备昵称",
        subtitle = "/api/set_nickname · 设备显示别名",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "当前昵称",
            subtitle = if (loaded) (current.ifBlank { "未设置（使用默认设备名）" }) else "读取中…"
        ) {
            FeatureTextField(
                value = value,
                onValueChange = { value = it },
                label = "新昵称",
                placeholder = "如 客厅随身WiFi"
            )
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "保存昵称",
                loading = busy,
                enabled = !busy && value.isNotBlank(),
                onClick = {
                    busy = true; msg = ""
                    scope.launch {
                        feats?.setNickname(value.trim())
                            ?.onSuccess { current = value.trim(); msg = "昵称已保存"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// 设置子页 3：HTTP 代理调试
// ═══════════════════════════════════════════
@Composable
fun ProxyDebugScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("http://example.com/") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<Int?>(null) }
    var contentType by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    FeaturePage(
        title = "HTTP 代理调试",
        subtitle = "/api/proxy/--<URL> · 经设备代理请求，仅查看响应文本",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "请求", subtitle = "低风险只读为主，GET 请填目标 URL") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("GET", "POST").forEach { m ->
                    SelectRow(
                        label = m,
                        selected = method == m,
                        onClick = { method = m }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(
                value = url,
                onValueChange = { url = it },
                label = "目标 URL",
                placeholder = "http://example.com/",
                keyboard = androidx.compose.ui.text.input.KeyboardType.Uri
            )
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "发送请求",
                loading = busy,
                enabled = !busy,
                onClick = {
                    val target = url.trim()
                    if (!target.startsWith("http://") && !target.startsWith("https://")) {
                        msg = "目标 URL 必须以 http:// 或 https:// 开头"; msgErr = true; return@ActionButton
                    }
                    busy = true; msg = ""; body = ""
                    scope.launch {
                        feats?.proxyDebug(method, target)
                            ?.onSuccess { r ->
                                code = r.httpCode
                                contentType = r.contentType
                                body = r.body
                                msg = "HTTP ${r.httpCode}"; msgErr = r.httpCode !in 200..299
                            }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
        FeatureCard(
            title = "响应",
            subtitle = when {
                code == null -> "尚未请求"
                contentType.isBlank() -> "HTTP $code"
                else -> "HTTP $code · $contentType"
            }
        ) {
            CommandOutput(body.ifBlank { "(空响应体)" }, height = 260.dp)
        }
    }
}

// ═══════════════════════════════════════════
// 快捷开关行（点击即执行，无跳转）
// ═══════════════════════════════════════════
@Composable
private fun QuickToggleRow(title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = iOSLabel)
            Spacer(modifier = Modifier.height(1.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        Text("切换 ›", color = iOSBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ═══════════════════════════════════════════
// 设备标识信息行（label 左 + value 右，等宽数字）
// ═══════════════════════════════════════════
@Composable
private fun IdentityRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel, modifier = Modifier.width(90.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = iOSLabel,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
