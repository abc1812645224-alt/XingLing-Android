/*
 * 星灵 (XingLing) · P1 组功能页面（root_shell 系统级控制）
 *
 *  ① CpuControlScreen     CPU 核心控制（在线开关 / 调度策略 / 最大频率）
 *  ② BatteryChargeScreen  电池定量停充（充电上限 / 充电开关 / 节点探测）
 *  ③ BootScriptsScreen    开机自启脚本（/data/local/tmp/init.d/ 增删改查与运行）
 *  ④ CrontabScreen        系统 crontab 表达式编辑
 *
 * 全部经 backend.features → 设备后台 rootShell 真实调用，
 * UI 统一走 FeaturePage / FeatureCard（iOS 玻璃卡片风格）。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.BatteryChargeInfo
import com.xingling.app.backend.BootScript
import com.xingling.app.backend.CpuCoreInfo
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════
// ① CPU 核心控制
// ═══════════════════════════════════════════
@Composable
fun CpuControlScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var cores by remember { mutableStateOf<List<CpuCoreInfo>>(emptyList()) }
    var governors by remember { mutableStateOf<List<String>>(emptyList()) }
    var freqMap by remember { mutableStateOf<Map<Int, List<Int>>>(emptyMap()) }
    var expandedCore by remember { mutableStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        busy = true
        scope.launch {
            feats?.getCpuCoreInfo()
                ?.onSuccess { cores = it; msg = ""; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        feats?.getCpuAvailableGovernors()?.onSuccess { governors = it }
    }

    fun toggleOnline(core: Int, online: Boolean) {
        busy = true
        scope.launch {
            feats?.setCpuCoreOnline(core, online)
                ?.onSuccess { msg = "CPU$core 已${if (online) "上线" else "下线"}"; msgErr = false; refresh() }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    fun applyGovernor(core: Int, g: String) {
        busy = true
        scope.launch {
            feats?.setCpuGovernor(core, g)
                ?.onSuccess { msg = "CPU$core 调度策略已设为 $g"; msgErr = false; refresh() }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    fun loadFreqs(core: Int) {
        busy = true
        scope.launch {
            feats?.getCpuAvailableFreqs(core)
                ?.onSuccess { list ->
                    freqMap = freqMap + (core to list)
                    expandedCore = if (expandedCore == core) -1 else core
                    msg = ""; msgErr = false
                }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    fun applyMaxFreq(core: Int, freq: Int) {
        busy = true
        scope.launch {
            feats?.setCpuMaxFreq(core, freq)
                ?.onSuccess { msg = "CPU$core 最大频率已设为 ${freq / 1000} MHz"; msgErr = false; refresh() }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    FeaturePage(
        title = "CPU 核心控制",
        subtitle = "sysfs 核心开关 / 调度策略 / 最大频率（root_shell）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "核心状态",
            subtitle = "共 ${cores.size} 核",
            trailing = {
                OutlineActionButton(
                    text = "刷新",
                    modifier = Modifier.width(88.dp),
                    loading = busy,
                    onClick = { refresh() }
                )
            }
        ) {
            if (cores.isEmpty()) {
                Text(
                    if (busy) "加载中…" else "未检测到 CPU 核心信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = iOSSecondaryLabel
                )
            }
        }

        cores.forEach { c ->
            FeatureCard(
                title = "CPU${c.core}",
                subtitle = buildString {
                    append(if (c.online) "在线" else "离线")
                    append(" · 实时 ${c.curFreqKhz / 1000} MHz / 上限 ${c.maxFreqKhz / 1000} MHz")
                    append(" · 策略 ${c.governor.ifBlank { "-" }}")
                }
            ) {
                ToggleRow(
                    label = "核心在线",
                    desc = if (c.core == 0) "CPU0 为引导核心，通常不可关闭" else null,
                    checked = c.online,
                    enabled = !busy && c.core > 0
                ) { toggleOnline(c.core, it) }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = iOSSeparator, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("调度策略", style = MaterialTheme.typography.titleSmall, color = iOSLabel)
                Spacer(modifier = Modifier.height(4.dp))
                if (governors.isEmpty()) {
                    Text("未读取到可用策略", fontSize = 12.sp, color = iOSSecondaryLabel)
                } else {
                    governors.forEach { g ->
                        SelectRow(
                            label = g,
                            selected = c.governor == g,
                            enabled = !busy && c.online,
                            onClick = { applyGovernor(c.core, g) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = iOSSeparator, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("最大频率限制", style = MaterialTheme.typography.titleSmall, color = iOSLabel)
                Spacer(modifier = Modifier.height(4.dp))
                val freqs = freqMap[c.core]
                OutlineActionButton(
                    text = if (freqs == null) "加载可选频率"
                    else (if (expandedCore == c.core) "收起频率列表" else "可选频率（${freqs.size} 档）"),
                    enabled = !busy && c.online,
                    onClick = { if (freqs == null) loadFreqs(c.core) else expandedCore = if (expandedCore == c.core) -1 else c.core }
                )
                if (expandedCore == c.core && freqs != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    freqs.forEach { f ->
                        SelectRow(
                            label = "${f / 1000} MHz",
                            selected = c.maxFreqKhz == f,
                            enabled = !busy && c.online,
                            onClick = { applyMaxFreq(c.core, f) }
                        )
                    }
                }
            }
        }

        ResultMessage(msg, msgErr)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "提示：CPU0 通常不可关闭；锁定最大频率可降低发热但会损失性能。修改即时生效，重启后由内核策略恢复。",
            style = MaterialTheme.typography.bodySmall,
            color = iOSSecondaryLabel,
            lineHeight = 18.sp
        )
    }
}

// ═══════════════════════════════════════════
// ② 电池定量停充
// ═══════════════════════════════════════════


// ═══════════════════════════════════════════
// ③ 开机自启脚本
// ═══════════════════════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BootScriptsScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var scripts by remember { mutableStateOf<List<BootScript>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var runOutput by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf("") }
    var editingContent by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        busy = true
        scope.launch {
            feats?.listBootScripts()
                ?.onSuccess { scripts = it; msg = ""; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun saveCurrent() {
        val n = editingName.trim()
        if (n.isEmpty()) { msg = "请填写脚本名"; msgErr = true; return }
        busy = true
        scope.launch {
            feats?.saveBootScript(n, editingContent)
                ?.onSuccess { msg = "脚本 $n 已保存"; msgErr = false; showEditor = false; refresh() }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    fun toggleEnabled(s: BootScript, enabled: Boolean) {
        busy = true
        scope.launch {
            feats?.setBootScriptEnabled(s.name, enabled)
                ?.onSuccess { msg = "脚本 ${s.name} 已${if (enabled) "启用" else "禁用"}"; msgErr = false; refresh() }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    fun delete(s: BootScript) {
        busy = true
        scope.launch {
            feats?.deleteBootScript(s.name)
                ?.onSuccess { msg = "脚本 ${s.name} 已删除"; msgErr = false; refresh() }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    fun run(s: BootScript) {
        busy = true
        scope.launch {
            feats?.runBootScript(s.name)
                ?.onSuccess { out -> runOutput = "[${s.name}]\n$out"; msg = "脚本 ${s.name} 已运行"; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    FeaturePage(
        title = "开机自启脚本",
        subtitle = "/data/local/tmp/init.d/ 脚本管理（root_shell）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "脚本目录", subtitle = "已 ${scripts.size} 个脚本（可执行文件将随自启运行）") {
            ActionButton(
                text = "+ 新建 / 编辑脚本",
                loading = busy,
                onClick = {
                    editingName = ""; editingContent = ""; showEditor = true
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlineActionButton(text = "刷新列表", loading = busy, onClick = { refresh() })
        }

        if (scripts.isEmpty()) {
            FeatureCard(title = "暂无脚本") {
                Text(
                    if (busy) "加载中…" else "尚未添加自启脚本，点击上方「新建脚本」创建。",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            }
        }

        scripts.forEach { s ->
            FeatureCard(
                title = s.name,
                subtitle = if (s.enabled) "已启用（可执行）" else "已禁用（不可执行）"
            ) {
                ToggleRow(
                    label = "开机自启",
                    desc = "chmod +x / -x",
                    checked = s.enabled,
                    enabled = !busy
                ) { toggleEnabled(s, it) }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineActionButton(
                        text = "运行",
                        modifier = Modifier.width(96.dp),
                        loading = busy,
                        onClick = { run(s) }
                    )
                    OutlineActionButton(
                        text = "编辑",
                        modifier = Modifier.width(96.dp),
                        onClick = {
                            editingName = s.name; editingContent = s.content; showEditor = true
                        }
                    )
                    OutlineActionButton(
                        text = "删除",
                        modifier = Modifier.width(96.dp),
                        color = iOSRed,
                        loading = busy,
                        onClick = { delete(s) }
                    )
                }
            }
        }

        if (runOutput.isNotBlank()) {
            FeatureCard(title = "运行输出") {
                CommandOutput(runOutput, height = 160.dp)
            }
        }

        ResultMessage(msg, msgErr)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "提示：脚本需为 shell 脚本并具备可执行权限才会被自启。目录为 /data/local/tmp/init.d/，" +
                "设备重启后由开机脚本统一执行该目录下所有可执行文件。",
            style = MaterialTheme.typography.bodySmall,
            color = iOSSecondaryLabel,
            lineHeight = 18.sp
        )
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingName.isBlank()) "新建脚本" else "编辑脚本：$editingName") },
            text = {
                Column {
                    FeatureTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = "脚本名（仅文件名，不含路径）",
                        placeholder = "e.g. 99-myinit"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FeatureTextField(
                        value = editingContent,
                        onValueChange = { editingContent = it },
                        label = "脚本内容（shell）",
                        placeholder = "#!/system/bin/sh\necho hello",
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { saveCurrent() }) {
                    Text("保存", color = iOSBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("取消", color = iOSSecondaryLabel) }
            }
        )
    }
}

// ═══════════════════════════════════════════
// ④ Crontab 定时任务
// ═══════════════════════════════════════════
@Composable
fun CrontabScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var content by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        busy = true
        scope.launch {
            feats?.getCrontab()
                ?.onSuccess { content = it; msg = ""; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    LaunchedEffect(Unit) { load() }

    fun save() {
        busy = true
        scope.launch {
            feats?.setCrontab(content)
                ?.onSuccess { msg = "crontab 已保存并生效"; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            busy = false
        }
    }

    FeaturePage(
        title = "Crontab 定时",
        subtitle = "系统 crontab 表达式编辑（crontab -l / crontab <file>）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "当前 crontab",
            subtitle = "完整覆盖写入；空内容将清空任务"
        ) {
            FeatureTextField(
                value = content,
                onValueChange = { content = it },
                label = "crontab 内容",
                placeholder = "# 分 时 日 月 周  命令\n0 3 * * * /system/bin/echo wake",
                singleLine = false,
                modifier = Modifier.fillMaxWidth().height(240.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineActionButton(
                    text = "读取当前",
                    modifier = Modifier.weight(1f),
                    loading = busy,
                    onClick = { load() }
                )
                ActionButton(
                    text = "保存",
                    modifier = Modifier.weight(1f),
                    loading = busy,
                    onClick = { save() }
                )
            }
        }

        FeatureCard(title = "表达式格式说明", subtitle = "分 时 日 月 周 · 命令") {
            Text(
                "*  *  *  *  *  要执行的命令\n" +
                    "│  │  │  │  │\n" +
                    "│  │  │  │  └─ 星期 (0-7，0/7 都是周日)\n" +
                    "│  │  │  └──── 月份 (1-12)\n" +
                    "│  │  └────── 日期 (1-31)\n" +
                    "│  └───────── 小时 (0-23)\n" +
                    "└──────────── 分钟 (0-59)\n\n" +
                    "示例：\n" +
                    "*/5 * * * * sh /data/local/tmp/init.d/99-myinit   # 每 5 分钟\n" +
                    "0 4 * * * reboot                                   # 每天 4 点重启",
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = iOSSecondaryLabel
            )
        }

        ResultMessage(msg, msgErr)
    }
}
