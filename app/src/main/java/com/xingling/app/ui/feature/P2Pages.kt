/*
 * 星灵 (XingLing) · P2 设备端二进制服务页面
 *
 *  ① AdGuardScreen     ADGuardHome 广告过滤 DNS（:3000 Web 管理）
 *  ② EasyTierScreen    EasyTier 异地组网（easytier-core，虚拟局域网）
 *  ③ EasyConnectScreen 校园网 VPN（无官方静态二进制，需手动推送或自定义 URL）
 *
 * 三页结构一致：状态卡 + 安装 + 配置表单 + 启停 + 日志，均经
 * P2FeatureHelper → UFI-TOOLS /api/root_shell 直连设备后台执行。
 * UI 统一走 FeaturePage / FeatureCard（iOS 玻璃卡片风格）。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.BinaryServiceStatus
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.EasyTierConfig
import com.xingling.app.backend.P2FeatureHelper
import com.xingling.app.backend.UfiToolsBackend
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════
// 共享子组件
// ═══════════════════════════════════════════

/** 打开 Web 管理页（外部浏览器） */
private fun openWeb(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = iOSLabel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = iOSSecondaryLabel,
            modifier = Modifier.width(72.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** 通用服务状态卡：安装 / 运行 / PID / 端口 / 版本 + 可选 Web 管理入口 */
@Composable
private fun BinaryStatusCard(status: BinaryServiceStatus?, adminUrl: String?) {
    FeatureCard(title = "服务状态", subtitle = "rootShell 实时检测（which / ps / /proc/net/tcp）") {
        if (status == null) {
            Text("尚未检测，请点击下方刷新或等待自动检测。", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            return@FeatureCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (status.installed) "已安装" else "未安装", if (status.installed) iOSGreen else iOSRed)
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(if (status.running) "运行中" else "已停止", if (status.running) iOSGreen else iOSOrange)
        }
        Spacer(modifier = Modifier.height(12.dp))
        InfoRow("PID", status.pid.ifBlank { "--" })
        InfoRow("端口", if (status.port > 0) ":${status.port}" else "--")
        InfoRow("版本", status.version.ifBlank { "--" })
        if (!adminUrl.isNullOrBlank() && status.running) {
            Spacer(modifier = Modifier.height(10.dp))
            val context = LocalContext.current
            OutlineActionButton(
                text = "打开 Web 管理页",
                onClick = { openWeb(context, adminUrl) }
            )
        }
    }
}

/** 通用日志卡 */
@Composable
private fun LogCard(log: String, onRefresh: () -> Unit, busy: Boolean) {
    FeatureCard(title = "运行日志", subtitle = "tail -n 200 /data/local/tmp/*.log") {
        CommandOutput(log, 180.dp)
        Spacer(modifier = Modifier.height(10.dp))
        OutlineActionButton(text = "刷新日志", loading = busy, onClick = onRefresh)
    }
}

/** 启停按钮行 */
@Composable
private fun StartStopRow(
    startText: String,
    stopText: String,
    busy: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlineActionButton(text = startText, color = iOSGreen, modifier = Modifier.weight(1f), loading = busy, onClick = onStart)
        OutlineActionButton(text = stopText, color = iOSRed, modifier = Modifier.weight(1f), loading = busy, onClick = onStop)
    }
}

// ═══════════════════════════════════════════
// ① ADGuardHome
// ═══════════════════════════════════════════
@Composable
fun AdGuardScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val p2 = (backend as? UfiToolsBackend)?.p2
    var status by remember { mutableStateOf<BinaryServiceStatus?>(null) }
    var installing by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf(false) }
    var logBusy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            p2?.getAdGuardStatus()
                ?.onSuccess { status = it; msg = ""; msgErr = false }
                ?.onFailure { msg = unsupportedOrMessage(it); msgErr = true }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    FeaturePage(
        title = "ADGuardHome",
        subtitle = "广告过滤 DNS 服务（Web 管理 :3000）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        if (p2 == null) {
            UnsupportedCard("当前设备后台未接入 P2 二进制服务管理能力。")
            return@FeaturePage
        }
        BinaryStatusCard(status, adminUrl = "http://${p2.hostAddress}:3000")

        FeatureCard(title = "安装 / 启停", subtitle = "下载官方静态二进制到 /data/local/tmp") {
            ActionButton(
                text = "下载并安装 AdGuardHome",
                loading = installing,
                onClick = {
                    installing = true
                    scope.launch {
                        p2.installAdGuard()
                            .onSuccess { msg = "安装完成：$it"; msgErr = false; refresh() }
                            .onFailure { msg = "安装失败：${unsupportedOrMessage(it)}"; msgErr = true }
                        installing = false
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            StartStopRow(
                startText = "启动",
                stopText = "停止",
                busy = acting,
                onStart = {
                    acting = true
                    scope.launch {
                        p2.startAdGuard()
                            .onSuccess { msg = "已启动"; msgErr = false; refresh() }
                            .onFailure { msg = "启动失败：${unsupportedOrMessage(it)}"; msgErr = true }
                        acting = false
                    }
                },
                onStop = {
                    acting = true
                    scope.launch {
                        p2.stopAdGuard()
                            .onSuccess { msg = "已停止"; msgErr = false; refresh() }
                            .onFailure { msg = "停止失败：${unsupportedOrMessage(it)}"; msgErr = true }
                        acting = false
                    }
                }
            )
        }

        LogCard(
            log = log,
            busy = logBusy,
            onRefresh = {
                logBusy = true
                scope.launch {
                    p2.readLog("adguard.log").onSuccess { log = it }
                    logBusy = false
                }
            }
        )

        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// ② EasyTier 异地组网
// ═══════════════════════════════════════════
@Composable
fun EasyTierScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val p2 = (backend as? UfiToolsBackend)?.p2
    var status by remember { mutableStateOf<BinaryServiceStatus?>(null) }
    var installing by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf(false) }
    var logBusy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var nodeAddress by remember { mutableStateOf("10.144.144.1") }
    var secret by remember { mutableStateOf("") }
    var peers by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            p2?.getEasyTierStatus()
                ?.onSuccess { status = it; msg = ""; msgErr = false }
                ?.onFailure { msg = unsupportedOrMessage(it); msgErr = true }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    FeaturePage(
        title = "EasyTier 组网",
        subtitle = "异地组网虚拟局域网（easytier-core）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        if (p2 == null) {
            UnsupportedCard("当前设备后台未接入 P2 二进制服务管理能力。")
            return@FeaturePage
        }
        BinaryStatusCard(status, adminUrl = null)

        FeatureCard(title = "安装", subtitle = "下载官方 easytier-linux 静态二进制") {
            ActionButton(
                text = "下载并安装 EasyTier",
                loading = installing,
                onClick = {
                    installing = true
                    scope.launch {
                        p2.installEasyTier()
                            .onSuccess { msg = "安装完成：$it"; msgErr = false; refresh() }
                            .onFailure { msg = "安装失败：${unsupportedOrMessage(it)}"; msgErr = true }
                        installing = false
                    }
                }
            )
        }

        FeatureCard(title = "组网配置", subtitle = "虚拟 IP / 网络密钥 / 对端中继地址") {
            FeatureTextField(
                value = nodeAddress,
                onValueChange = { nodeAddress = it },
                label = "虚拟 IP（节点地址）",
                placeholder = "10.144.144.1"
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(
                value = secret,
                onValueChange = { secret = it },
                label = "网络密钥",
                placeholder = "与对端一致的 network-name / secret"
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(
                value = peers,
                onValueChange = { peers = it },
                label = "对端地址",
                placeholder = "tcp://公网IP:11010"
            )
            Spacer(modifier = Modifier.height(10.dp))
            StartStopRow(
                startText = "启动组网",
                stopText = "停止组网",
                busy = acting,
                onStart = {
                    acting = true
                    scope.launch {
                        p2.startEasyTier(EasyTierConfig(nodeAddress.trim(), secret.trim(), peers.trim()))
                            .onSuccess { msg = "EasyTier 已启动"; msgErr = false; refresh() }
                            .onFailure { msg = "启动失败：${unsupportedOrMessage(it)}"; msgErr = true }
                        acting = false
                    }
                },
                onStop = {
                    acting = true
                    scope.launch {
                        p2.stopEasyTier()
                            .onSuccess { msg = "已停止"; msgErr = false; refresh() }
                            .onFailure { msg = "停止失败：${unsupportedOrMessage(it)}"; msgErr = true }
                        acting = false
                    }
                }
            )
        }

        LogCard(
            log = log,
            busy = logBusy,
            onRefresh = {
                logBusy = true
                scope.launch {
                    p2.readLog("easytier.log").onSuccess { log = it }
                    logBusy = false
                }
            }
        )

        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// ③ EasyConnect 校园网 VPN
// ═══════════════════════════════════════════
@Composable
fun EasyConnectScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val p2 = (backend as? UfiToolsBackend)?.p2
    var status by remember { mutableStateOf<BinaryServiceStatus?>(null) }
    var installing by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf(false) }
    var logBusy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            p2?.getEasyConnectStatus()
                ?.onSuccess { status = it; msg = ""; msgErr = false }
                ?.onFailure { msg = unsupportedOrMessage(it); msgErr = true }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    FeaturePage(
        title = "EasyConnect",
        subtitle = "校园网 VPN 客户端（无官方静态二进制）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        if (p2 == null) {
            UnsupportedCard("当前设备后台未接入 P2 二进制服务管理能力。")
            return@FeaturePage
        }
        BinaryStatusCard(status, adminUrl = null)

        FeatureCard(title = "安装二进制", subtitle = "EasyConnect 无官方静态二进制") {
            Text(
                "EasyConnect 没有官方 Linux 静态二进制。请二选一：\n" +
                    "1) 通过 adb push 将二进制推送到设备 /data/local/tmp/EasyConnect；\n" +
                    "2) 在下方填入可下载的 URL，由设备后台直接下载。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = "自定义下载 URL（可选）",
                placeholder = "https://.../EasyConnect"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineActionButton(
                    text = "检测本地",
                    modifier = Modifier.weight(1f),
                    loading = installing,
                    onClick = {
                        installing = true
                        scope.launch {
                            p2.installEasyConnect()
                                .onSuccess { msg = "已就绪：$it"; msgErr = false; refresh() }
                                .onFailure { msg = "未就绪：${unsupportedOrMessage(it)}"; msgErr = true }
                            installing = false
                        }
                    }
                )
                ActionButton(
                    text = "从 URL 下载",
                    modifier = Modifier.weight(1f),
                    color = iOSBlue,
                    loading = installing,
                    enabled = customUrl.isNotBlank(),
                    onClick = {
                        installing = true
                        scope.launch {
                            p2.installEasyConnectFromUrl(customUrl.trim())
                                .onSuccess { msg = "下载完成：$it"; msgErr = false; refresh() }
                                .onFailure { msg = "下载失败：${unsupportedOrMessage(it)}"; msgErr = true }
                            installing = false
                        }
                    }
                )
            }
        }

        FeatureCard(title = "连接配置", subtitle = "账号 / 密码 / VPN 服务器地址") {
            FeatureTextField(
                value = username,
                onValueChange = { username = it },
                label = "账号",
                placeholder = "校园网账号"
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(
                value = password,
                onValueChange = { password = it },
                label = "密码",
                placeholder = "校园网密码"
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(
                value = server,
                onValueChange = { server = it },
                label = "服务器地址",
                placeholder = "vpn.example.edu.cn"
            )
            Spacer(modifier = Modifier.height(10.dp))
            StartStopRow(
                startText = "连接",
                stopText = "断开",
                busy = acting,
                onStart = {
                    acting = true
                    scope.launch {
                        p2.startEasyConnect(username.trim(), password, server.trim())
                            .onSuccess { msg = "EasyConnect 已连接"; msgErr = false; refresh() }
                            .onFailure { msg = "连接失败：${unsupportedOrMessage(it)}"; msgErr = true }
                        acting = false
                    }
                },
                onStop = {
                    acting = true
                    scope.launch {
                        p2.stopEasyConnect()
                            .onSuccess { msg = "已断开"; msgErr = false; refresh() }
                            .onFailure { msg = "断开失败：${unsupportedOrMessage(it)}"; msgErr = true }
                        acting = false
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "说明：EasyConnect 各厂商 CLI 参数差异较大，当前按 -u / -p / -s 通用形式调用，" +
                    "若厂商参数不同请以设备端实际二进制为准。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSOrange,
                lineHeight = 17.sp
            )
        }

        LogCard(
            log = log,
            busy = logBusy,
            onRefresh = {
                logBusy = true
                scope.launch {
                    p2.readLog("easyconnect.log").onSuccess { log = it }
                    logBusy = false
                }
            }
        )

        ResultMessage(msg, msgErr)
    }
}
