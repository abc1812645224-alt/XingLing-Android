/*
 * 星灵 (XingLing) · 高级功能批页面（二）
 *  OTA 更新 · 插件系统 · 测速 · 局域网 DHCP/黑白名单 · SMB
 * 通过 check_update / download_apk / plugins_store / speedtest /
 * setDeviceAccessControlList / DHCP_SETTING / smbPath 等真实调用。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.AccessControl
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.DhcpConfig
import com.xingling.app.backend.DownloadStatus
import com.xingling.app.backend.LanClient
import com.xingling.app.backend.PluginItem
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════
// OTA 更新
// ═══════════════════════════════════════════
@Composable
fun OtaScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var versions by remember { mutableStateOf<List<String>>(emptyList()) }
    var changelog by remember { mutableStateOf("") }
    var baseUri by remember { mutableStateOf("") }
    var arch by remember { mutableStateOf("") }
    var forceUpdate by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<DownloadStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun doCheck() {
        busy = true
        feats?.checkUpdate()?.onSuccess { info ->
            if (info != null) {
                versions = info.versions
                changelog = info.changelog
                baseUri = info.baseUri
                latest = info.versions.lastOrNull() ?: ""
                msg = "检查完成"
                msgErr = false
            } else {
                msg = "后台未返回检查结果（当前固件可能不支持 OTA）"
                msgErr = true
            }
        }?.onFailure { e ->
            msg = unsupportedOrMessage(e); msgErr = true
        }
        busy = false
    }
    LaunchedEffect(Unit) { doCheck() }

    FeaturePage(
        title = "OTA 更新",
        subtitle = "固件检查 / APK 下载 / 安装 / 禁用 FOTA",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "检查更新", subtitle = "check_update") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (versions.isEmpty()) "尚未检查 / 无可用版本" else "最新版本：${latest}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = iOSLabel
                )
                Spacer(modifier = Modifier.weight(1f))
                ActionButton(
                    text = "检查",
                    modifier = Modifier.padding(0.dp),
                    loading = busy,
                    onClick = { scope.launch { doCheck() } }
                )
            }
            if (baseUri.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("下载源：$baseUri", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
            if (changelog.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("更新日志：", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                Text(changelog, style = MaterialTheme.typography.bodySmall, color = iOSLabel, lineHeight = 18.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            FeatureTextField(apkUrl, { apkUrl = it }, "APK 直链（留空用后台默认源）", "")
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "下载并安装",
                loading = busy,
                onClick = {
                    if (busy) return@ActionButton
                    busy = true
                    scope.launch {
                        feats?.downloadApk(apkUrl)
                            ?.onSuccess { msg = "开始下载…"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        // 轮询下载状态
                        var finished = false
                        repeat(60) { i ->
                            if (finished) return@repeat
                            delay(1000 + i * 500L)
                            val st = feats?.downloadStatus()?.getOrNull() ?: return@repeat
                            status = st
                            if (st.status == "done" || st.status == "error") {
                                if (st.status == "done") {
                                    feats?.installApk()
                                        ?.onSuccess { msg = "安装请求已提交，等待设备完成"; msgErr = false }
                                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                                }
                                finished = true
                                return@repeat
                            }
                        }
                        busy = false
                    }
                }
            )
        }

        status?.let { st ->
            if (st.status != "idle") {
                FeatureCard(title = "下载进度", subtitle = st.error.ifBlank { st.status }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${st.percent}%", style = MaterialTheme.typography.titleLarge, color = iOSBlue)
                        Spacer(modifier = Modifier.weight(1f))
                        StatusPill(st.status, if (st.status == "error") iOSRed else iOSBlue)
                    }
                }
            }
        }

        FeatureCard(title = "禁用 FOTA", subtitle = "关闭固件自动更新推送（disableFota）") {
            Text(
                "关闭后设备将不再自动下载 / 提示固件升级，仅保留手动 OTA。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlineActionButton(
                text = "禁用 FOTA 自动更新",
                color = iOSRed,
                onClick = {
                    scope.launch {
                        feats?.disableFota()?.onSuccess { msg = "已禁用 FOTA"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// 插件系统
// ═══════════════════════════════════════════
@Composable
fun PluginScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var plugins by remember { mutableStateOf<List<PluginItem>>(emptyList()) }
    var storeUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var headText by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refreshStore() {
        feats?.pluginsStore()?.onSuccess { store ->
            if (store != null) {
                plugins = store.plugins
                storeUrl = store.downloadUrl
            }
            loading = false
        }?.onFailure {
            loading = false
        }
        feats?.getCustomHead()?.onSuccess { headText = it }
    }
    LaunchedEffect(Unit) { refreshStore() }

    FeaturePage(
        title = "插件系统",
        subtitle = "插件市场 / 已装插件 / 欢迎语（plugins_store · set_custom_head）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "插件市场", subtitle = if (storeUrl.isBlank()) "plugins_store" else storeUrl) {
            if (loading) {
                Text("加载中…", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else if (plugins.isEmpty()) {
                Text(
                    "后台未返回插件列表（可能为空或该固件不支持插件市场）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            } else {
                plugins.forEachIndexed { idx, p ->
                    if (idx > 0) {
                        Divider(color = iOSSeparator, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = iOSLabel)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${p.modified} · ${formatSize(p.size)}${if (p.md5.isNotBlank()) " · md5 ${p.md5.take(8)}…" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = iOSSecondaryLabel
                            )
                        }
                    }
                }
            }
        }

        FeatureCard(title = "欢迎语（自定义抬头）", subtitle = "set_custom_head / get_custom_head") {
            FeatureTextField(headText, { headText = it }, "欢迎语文本", "例如：欢迎使用 星灵")
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "保存",
                onClick = {
                    scope.launch {
                        feats?.setCustomHead(headText)
                            ?.onSuccess { msg = "欢迎语已保存"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
    }
}

private fun formatSize(bytes: Long): String = com.xingling.app.backend.formatBytes(bytes)

// ═══════════════════════════════════════════
// 测速
// ═══════════════════════════════════════════
@Composable
fun SpeedtestScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(ckSize: Int) {
        if (running) return
        running = true
        result = "测速中…"
        msg = ""
        scope.launch {
            feats?.speedtest(ckSize)?.onSuccess { r ->
                val dur = if (r.elapsedMs > 0) "${(r.elapsedMs / 1000.0).let { "%.1f".format(it) }} s" else "--"
                result = "下载数据：${r.humanSize}\n" +
                    "耗时：$dur\n" +
                    "平均速率：${"%.2f".format(r.mbps)} Mbps（${r.humanSpeed}）"
                msg = "测速完成"
                msgErr = false
            }?.onFailure { e ->
                result = ""
                msg = unsupportedOrMessage(e)
                msgErr = true
            }
            running = false
        }
    }

    FeaturePage(
        title = "测速",
        subtitle = "speedtest（后台流式下载测速，防止前台超时）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "开始测速", subtitle = "选择下载数据包大小") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(text = "4 MB", modifier = Modifier.weight(1f), loading = running, onClick = { if (!running) run(4) })
                ActionButton(text = "10 MB", modifier = Modifier.weight(1f), onClick = { if (!running) run(10) })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "测速由设备后台发起并流式传输，结果立即显示，不影响前台超时。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
        }
        if (result.isNotEmpty()) {
            CommandOutput(result)
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// 局域网管理（DHCP / 客户端 / 黑白名单）
// ═══════════════════════════════════════════
@Composable
fun LanScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var clients by remember { mutableStateOf<List<LanClient>>(emptyList()) }
    var mode by remember { mutableStateOf("0") } // 0 关 1 白 2 黑
    var whiteText by remember { mutableStateOf("") }
    var blackText by remember { mutableStateOf("") }
    var dhcp by remember { mutableStateOf<DhcpConfig?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        feats?.getLanClients()?.onSuccess { clients = it }
        val ac = feats?.getAccessControl()?.getOrNull()
        if (ac != null) {
            mode = ac.mode
            whiteText = ac.whiteMacs.joinToString("\n")
            blackText = ac.blackMacs.joinToString("\n")
        }
        dhcp = feats?.getDhcp()?.getOrNull()
    }
    LaunchedEffect(Unit) { refresh() }

    FeaturePage(
        title = "局域网管理",
        subtitle = "在线客户端 · 访问控制（DHCP 黑白名单）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "在线客户端", subtitle = "getLanClients（在线设备列表）") {
            if (clients.isEmpty()) {
                Text("暂无在线客户端。", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else {
                clients.forEachIndexed { idx, c ->
                    if (idx > 0) {
                        Divider(color = iOSSeparator, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.name.ifBlank { c.mac }, style = MaterialTheme.typography.bodyLarge, color = iOSLabel)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${c.mac} · ${c.ip} · ${if (c.online) "在线" else "离线"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = iOSSecondaryLabel
                            )
                        }
                    }
                }
            }
        }

        FeatureCard(title = "访问控制模式", subtitle = "0=关闭 1=白名单 2=黑名单") {
            SelectRow("关闭", desc = "不限制访客接入", selected = mode == "0") { mode = "0" }
            SelectRow("白名单", desc = "仅允许白名单 MAC 接入", selected = mode == "1") { mode = "1" }
            SelectRow("黑名单", desc = "禁止黑名单 MAC 接入", selected = mode == "2") { mode = "2" }
        }

        FeatureCard(title = "名单（MAC 每行一个）", subtitle = "setDeviceAccessControlList") {
            FeatureTextField(
                whiteText, { whiteText = it }, "白名单",
                "AA:BB:CC:DD:EE:01", singleLine = false, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureTextField(
                blackText, { blackText = it }, "黑名单",
                "AA:BB:CC:DD:EE:02", singleLine = false, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "保存访问控制",
                loading = busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val ac = AccessControl(
                            mode = mode,
                            whiteMacs = whiteText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                            blackMacs = blackText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        )
                        feats?.setAccessControl(ac)?.onSuccess { msg = "访问控制已更新"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }

        FeatureCard(title = "DHCP 设置", subtitle = "dhcp_setting") {
            val d = dhcp
            if (d == null) {
                Text("DHCP 配置读取失败（可能后台不支持）。", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else {
                ToggleRow(
                    "DHCP 服务",
                    desc = if (d.dhcpEnabled) "已开启（自动分配地址）" else "已关闭（客户端需手动配置）",
                    checked = d.dhcpEnabled
                ) { v ->
                    scope.launch {
                        feats?.setDhcp(d.copy(dhcpEnabled = v))?.onSuccess { dhcp = d.copy(dhcpEnabled = v); msg = "DHCP 已更新"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    }
                }
                Divider(color = iOSSeparator, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                FeatureTextField(d.lanIp, { dhcp = d.copy(lanIp = it) }, "LAN IP", "192.168.1.1")
                Spacer(modifier = Modifier.height(8.dp))
                FeatureTextField(d.netmask, { dhcp = d.copy(netmask = it) }, "子网掩码", "255.255.255.0")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureTextField(d.start, { dhcp = d.copy(start = it) }, "起始 IP", "192.168.1.100", modifier = Modifier.weight(1f))
                    FeatureTextField(d.end, { dhcp = d.copy(end = it) }, "结束 IP", "192.168.1.200", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                ActionButton(
                    text = "保存 DHCP",
                    onClick = {
                        scope.launch {
                            feats?.setDhcp(d)?.onSuccess { msg = "DHCP 已保存"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        }
                    }
                )
            }
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// 文件共享 SMB
// ═══════════════════════════════════════════
@Composable
fun SambaScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var enabled by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.getSambaSwitch()
            ?.onSuccess { enabled = it; loaded = true }
            ?.onFailure { loaded = true }
    }

    FeaturePage(
        title = "文件共享 SMB",
        subtitle = "SMB/Samba 开关（smbPath enable=1）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "SMB 服务", subtitle = "设备上 U 盘 / 存储经网络共享给局域网") {
            if (!loaded) {
                Text("读取中…", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else {
                ToggleRow(
                    "启用 SMB 共享",
                    desc = if (enabled) "已开启，局域网内可通过 \\\\设备IP 访问" else "已关闭",
                    checked = enabled,
                    enabled = !busy
                ) { v ->
                    busy = true
                    scope.launch {
                        feats?.setSamba(v)?.onSuccess { enabled = v; msg = if (v) "SMB 已开启" else "SMB 已关闭"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "说明：开启后设备通过 smbPath 接口注册共享根目录，默认账号密码通常与设备后台一致，具体以固件说明为准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel,
                    lineHeight = 18.sp
                )
            }
        }
        ResultMessage(msg, msgErr)
    }
}
