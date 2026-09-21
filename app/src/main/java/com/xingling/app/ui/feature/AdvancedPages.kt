/*
 * 星灵 (XingLing) · 高级功能批页面（一）
 *  root Shell · ttyd 终端 · SSH · 无线 ADB
 * 通过 /api/root_shell、/api/user_shell、/api/hasTTYD、/api/smbPath、/api/adb_wifi_setting 真实调用。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════
// root Shell（任意命令）
// ═══════════════════════════════════════════
@Composable
fun ShellScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var rootMode by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runCmd(cmd: String) {
        if (cmd.isBlank()) return
        busy = true
        scope.launch {
            val res = if (rootMode) feats?.rootShell(cmd) else feats?.userShell(cmd)
            res?.onSuccess { out ->
                output = if (output.isEmpty()) out else output + "\n$out"
                msg = ""
                msgErr = false
            }?.onFailure { e ->
                msg = unsupportedOrMessage(e)
                msgErr = true
            }
            busy = false
        }
    }

    FeaturePage(
        title = "root Shell",
        subtitle = "后台 shell 任意命令（/api/root_shell · /api/user_shell）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectTab2(active = rootMode, text = "root 模式") { rootMode = true }
            SelectTab2(active = !rootMode, text = "user 模式") { rootMode = false }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            SelectRow(
                label = "历史命令",
                desc = "常用命令快捷执行",
                selected = true,
                enabled = false,
                onClick = {}
            )
        }
        Divider(color = iOSBlue, thickness = 1.dp)
        listOf(
            "uptime; free -m",
            "cat /proc/version",
            "ifconfig | grep -E 'inet |HWaddr'",
            "netstat -tunlp | grep -v grep",
            "ps -ef | grep -v grep"
        ).forEach { c ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                OutlineActionButton(
                    text = c,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        input = c
                        runCmd(c)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        FeatureCard(title = "执行命令", subtitle = "每条命令自带 100s 超时，避免卡死") {
            FeatureTextField(
                input, { input = it }, "命令", "ls -la",
                singleLine = false
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "执行",
                    modifier = Modifier.weight(1f),
                    loading = busy,
                    onClick = { runCmd(input) }
                )
                OutlineActionButton(
                    text = "清空输出",
                    modifier = Modifier.weight(1f),
                    color = iOSOrange,
                    onClick = { output = "" }
                )
            }
        }

        if (output.isNotEmpty()) {
            CommandOutput(output)
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// ttyd 终端（内嵌 WebView）
// ═══════════════════════════════════════════
@Composable
fun TtydScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var alive by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var url by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loadedTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val port = 7681
        feats?.ttydAlive(port)?.onSuccess { a ->
            alive = a
            if (a) url = feats.ttydUrl(port)
        }
        checking = false
    }

    FeaturePage(
        title = "ttyd 终端",
        subtitle = "内嵌 Web 终端（/api/hasTTYD）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "服务状态", subtitle = "探测 ttyd 端口 7681") {
            when {
                checking -> Text("检测中…", style = MaterialTheme.typography.bodyMedium, color = iOSSecondaryLabel)
                alive -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(url ?: "", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
                    Spacer(modifier = Modifier.weight(1f))
                    StatusPill("在线", iOSBlue)
                }
                else -> Column {
                    Text(
                        "设备后台未检测到 ttyd 服务（/api/hasTTYD 返回非 200）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = iOSSecondaryLabel
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "可先前往 root Shell 执行安装 / 启动命令，例如：\n" +
                            "  安装：opkg install ttyd\n  启动：ttyd -p 7681 busybox sh",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                }
            }
        }

        val target = url ?: return@FeaturePage
        if (alive) {
            Text(
                "内嵌终端（若空白请用浏览器打开 URL，加载需数秒）",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(iOSBackground)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    view.loadUrl(request.url.toString())
                                    return true
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { wv ->
                        if (loadedTag != target) {
                            loadedTag = target
                            wv.loadUrl(target)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "用浏览器打开",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
// SSH
// ═══════════════════════════════════════════
@Composable
fun SshScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var status by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(true) }
    var cmd by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshStatus() {
        checking = true
        scope.launch {
            feats?.sshStatus()
                ?.onSuccess { status = it; checking = false }
                ?.onFailure { e -> status = unsupportedOrMessage(e); checking = false }
        }
    }
    LaunchedEffect(Unit) { refreshStatus() }

    FeaturePage(
        title = "SSH",
        subtitle = "SSH 服务状态与远程命令（基于 root_shell 探测）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "服务状态", subtitle = "通过 ps / which 探测 sshd 或 dropbear") {
            if (checking) {
                Text("检测中…", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else {
                CommandOutput(status)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "重新检测",
                    modifier = Modifier.weight(1f),
                    onClick = { refreshStatus() },
                    loading = checking
                )
                OutlineActionButton(
                    text = "启动 dropbear",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.sshRun("nohup dropbear -p 22 >/dev/null 2>&1 & echo started")
                                ?.onSuccess { refreshStatus(); msg = it; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    }
                )
            }
        }

        FeatureCard(title = "远程命令", subtitle = "经 SSH 通道 / root shell 在设备上执行的命令") {
            FeatureTextField(cmd, { cmd = it }, "命令", "uname -a", singleLine = false)
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "执行",
                loading = busy,
                onClick = {
                    if (cmd.isBlank()) return@ActionButton
                    busy = true
                    scope.launch {
                        feats?.sshRun(cmd)
                            ?.onSuccess { result = it; msg = ""; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }
        if (result.isNotEmpty()) {
            CommandOutput(result)
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// 无线 ADB
// ═══════════════════════════════════════════
@Composable
fun AdbScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var enabled by remember { mutableStateOf(false) }
    var alive by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        feats?.getWirelessAdb()?.onSuccess { enabled = it }
        feats?.adbAlive()?.onSuccess { alive = it }
    }
    LaunchedEffect(Unit) { refresh() }

    FeaturePage(
        title = "无线 ADB",
        subtitle = "ADB over WiFi 自启开关（/api/adb_wifi_setting）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "开关", subtitle = "来自 adb_wifi_setting / adb_alive") {
            ToggleRow(
                label = "无线 ADB",
                desc = if (alive) "adb 进程当前在线" else "adb 进程当前离线/未探测",
                checked = enabled,
                enabled = !busy
            ) { value ->
                busy = true
                scope.launch {
                    feats?.setWirelessAdb(value, password)
                        ?.onSuccess { enabled = value; msg = "无线 ADB 已${if (value) "开启" else "关闭"}"; msgErr = false; refresh() }
                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    busy = false
                }
            }
            if (enabled) {
                Divider(color = iOSSeparator, thickness = 1.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("adb 状态：", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
                    Spacer(modifier = Modifier.weight(1f))
                    StatusPill(if (alive) "在线" else "离线", if (alive) iOSBlue else iOSOrange)
                }
            }
        }
        FeatureCard(title = "连接口令", subtitle = "开启时设备 ADB 所需密码（可选，部分固件支持）") {
            FeatureTextField(password, { password = it }, "ADB 口令", "")
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "保存口令并应用",
                loading = busy,
                onClick = {
                    busy = true
                    scope.launch {
                        feats?.setWirelessAdb(enabled, password)
                            ?.onSuccess { msg = "ADB 口令已保存"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
    }
}

// 本文件内的小型 Tab（避免与 NetworkPages 的 private 同名冲突）
@Composable
private fun androidx.compose.foundation.layout.RowScope.SelectTab2(active: Boolean, text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) iOSBlue else iOSFill)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) Color.White else iOSLabel, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
    }
}
