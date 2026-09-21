/*
 * 星灵 (XingLing) · 高级后台兼容批页面（P0/P1）
 *
 *  ① SysUpdateScreen    一键禁用系统更新（rootShell 逐条 disable + uninstall ZTE 组件）
 *  ② PartitionScreen    AB 分区展示（getprop ro.boot.slot_suffix）+ 当前分区镜像提取下载
 *  ③ DiagScreen         展锐 DIAG 通道工具（写 /dev/sdiag_nr 查询 IMEI）
 *
 * 全部经 UfiToolsFeatureApi → 设备后台 rootShell / /api/uploads 真实调用，
 * UI 统一走 FeaturePage / FeatureCard（iOS 玻璃卡片风格）。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.BootImageInfo
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.UpdateComponentResult
import com.xingling.app.backend.ZTE_UPDATE_COMPONENTS
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.launch
import java.io.File

// ═══════════════════════════════════════════
// ① 系统更新治理：一键禁用 ZTE 更新组件
// ═══════════════════════════════════════════
@Composable
fun SysUpdateScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var statusText by remember { mutableStateOf("") }
    var statusBusy by remember { mutableStateOf(false) }
    var disableBusy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<UpdateComponentResult>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadStatus() {
        statusBusy = true
        scope.launch {
            feats?.zteComponentStatus()
                ?.onSuccess { statusText = it; msg = ""; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            statusBusy = false
        }
    }

    fun runDisable() {
        disableBusy = true
        scope.launch {
            feats?.disableSystemUpdateComponents()
                ?.onSuccess { list ->
                    results = list
                    val okCount = list.count { r ->
                        r.uninstallOutput.contains("Success", true) ||
                            r.disableOutput.contains("new state", true)
                    }
                    msg = "已对 ${list.size} 个组件下发禁用与卸载指令，$okCount 个返回成功回显（逐条原始输出见下方）"
                    msgErr = false
                }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            disableBusy = false
        }
    }

    FeaturePage(
        title = "系统更新治理",
        subtitle = "一键禁用 ZTE 设备管理与推送组件（rootShell 顺序执行）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "ZTE 更新组件清单",
            subtitle = "逐条执行 pm disable-user 与 pm uninstall -k --user 0"
        ) {
            ZTE_UPDATE_COMPONENTS.forEachIndexed { i, pkg ->
                if (i > 0) Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(iOSRed.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("${i + 1}", color = iOSRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(pkg, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = iOSLabel)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "说明：禁用系统更新的软件路径 = 卸载设备上的 ZTE 设备管理 / 售后 / 推送组件（-k 保留应用数据）。" +
                    "该操作不可逆，恢复需重新安装官方组件，并可能导致设备失去 OTA 升级与保修支持。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlineActionButton(
                text = "查询设备现存 ZTE 相关包",
                loading = statusBusy,
                onClick = { loadStatus() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(
                text = "一键禁用系统更新",
                color = iOSRed,
                loading = disableBusy,
                onClick = { confirming = true }
            )
        }

        if (statusText.isNotBlank()) {
            FeatureCard(title = "设备现存 ZTE 相关包", subtitle = "pm list packages 过滤结果") {
                CommandOutput(statusText, 170.dp)
            }
        }

        if (results.isNotEmpty()) {
            FeatureCard(title = "执行结果（逐条回显）", subtitle = "共 ${results.size} 个组件，原始输出截断展示") {
                results.forEachIndexed { i, r ->
                    if (i > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = iOSSeparator, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(r.pkg, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = iOSLabel)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "disable-user：${r.disableOutput}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = iOSSecondaryLabel
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "uninstall -k：${r.uninstallOutput}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = iOSSecondaryLabel
                    )
                }
            }
        }

        ResultMessage(msg, msgErr)
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("确认禁用系统更新？") },
            text = {
                Text(
                    "将对 ${ZTE_UPDATE_COMPONENTS.size} 个 ZTE 组件执行 disable-user 与 uninstall -k --user 0。\n" +
                        "该操作不可逆，可能导致设备失去 OTA 升级能力与保修支持，请确认已了解风险。"
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; runDisable() }) {
                    Text("确认执行", color = iOSRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("取消", color = iOSBlue) }
            }
        )
    }
}

// ═══════════════════════════════════════════
// ② 设备分区 · BOOT（AB 分区展示 + 镜像提取下载）
// ═══════════════════════════════════════════
private fun slotDisplay(slot: String): String = when (slot.trim()) {
    "_a" -> "A 槽（_a）"
    "_b" -> "B 槽（_b）"
    "" -> "未识别（可能非 A/B 分区机型）"
    else -> slot
}

@Composable
fun PartitionScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    val context = LocalContext.current
    var slot by remember { mutableStateOf("") }
    var slotBusy by remember { mutableStateOf(false) }
    var boot by remember { mutableStateOf<BootImageInfo?>(null) }
    var extractBusy by remember { mutableStateOf(false) }
    var downloadBusy by remember { mutableStateOf(false) }
    var savedPath by remember { mutableStateOf("") }
    var savedBytes by remember { mutableStateOf(0L) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadSlot() {
        slotBusy = true
        scope.launch {
            feats?.currentSlotSuffix()
                ?.onSuccess { s -> slot = s; msg = ""; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            slotBusy = false
        }
    }

    LaunchedEffect(Unit) { loadSlot() }

    FeaturePage(
        title = "设备分区 · BOOT",
        subtitle = "AB 分区查询与当前分区镜像提取下载",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "当前系统分区（A/B）",
            subtitle = "getprop ro.boot.slot_suffix",
            trailing = {
                OutlineActionButton(
                    text = "刷新",
                    modifier = Modifier.width(88.dp),
                    loading = slotBusy,
                    onClick = { loadSlot() }
                )
            }
        ) {
            Text("当前槽位：${slotDisplay(slot)}", style = MaterialTheme.typography.bodyLarge, color = iOSLabel)
            val s = slot.trim()
            if (s == "_a" || s == "_b") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "对应当前分区节点：/dev/block/by-name/boot$s",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = iOSSecondaryLabel
                )
            }
        }

        FeatureCard(
            title = "当前分区镜像提取",
            subtitle = "dd 提取 boot 镜像到后台 uploads 目录，再经 /api/uploads 下载到本机"
        ) {
            ActionButton(
                text = "提取当前分区 BOOT 镜像",
                loading = extractBusy,
                onClick = {
                    extractBusy = true
                    boot = null
                    savedPath = ""
                    scope.launch {
                        feats?.extractBootImage()
                            ?.onSuccess { info ->
                                boot = info
                                msg = "镜像已在设备端生成：${info.devicePath}"
                                msgErr = false
                            }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        extractBusy = false
                    }
                }
            )
            val info = boot
            if (info != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = iOSSeparator, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("槽位：${info.slotSuffix}", style = MaterialTheme.typography.bodySmall, color = iOSLabel)
                Spacer(modifier = Modifier.height(2.dp))
                Text("文件名：${info.fileName}", style = MaterialTheme.typography.bodySmall, color = iOSLabel)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "设备路径：${info.devicePath}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = iOSSecondaryLabel
                )
                Spacer(modifier = Modifier.height(10.dp))
                CommandOutput(info.rawOutput, 130.dp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlineActionButton(
                    text = "下载 ${info.fileName} 到本机",
                    loading = downloadBusy,
                    onClick = {
                        downloadBusy = true
                        scope.launch {
                            val dir = context.getExternalFilesDir(null) ?: context.filesDir
                            val dest = File(dir, info.fileName)
                            feats?.downloadUpload(info.fileName, dest)
                                ?.onSuccess { bytes ->
                                    savedPath = dest.absolutePath
                                    savedBytes = bytes
                                    msg = "已下载并保存（$bytes 字节）"
                                    msgErr = false
                                }
                                ?.onFailure { e -> msg = "下载失败：${unsupportedOrMessage(e)}"; msgErr = true }
                            downloadBusy = false
                        }
                    }
                )
                if (savedPath.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("已保存到：$savedPath（$savedBytes 字节）", fontSize = 12.sp, color = iOSGreen)
                }
            }
        }

        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// ③ DIAG 工具：展锐 DIAG 通道查 IMEI
// ═══════════════════════════════════════════
/** 高级后台插件记录的 DIAG 命令码（IMEI 相关查询），完整帧需按机型补长度与 CRC */
private const val DIAG_CMD_IMEI_1 = "5E81"
private const val DIAG_CMD_IMEI_2 = "5E82"
private const val DIAG_CMD_IMEI_3 = "5E90"

/** 十六进制回显转可打印 ASCII（不可打印字符以 . 代替） */
private fun hexToAscii(hex: String): String {
    val clean = hex.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    val sb = StringBuilder()
    var i = 0
    while (i + 1 < clean.length) {
        val code = clean.substring(i, i + 2).toInt(16)
        sb.append(if (code in 32..126) code.toChar() else '.')
        i += 2
    }
    return sb.toString()
}

/** 从 ASCII 回显中提取疑似 IMEI（14~16 位连续数字） */
private fun extractImeiCandidates(ascii: String): List<String> =
    Regex("\\d{14,16}").findAll(ascii).map { it.value }.distinct().toList()

@Composable
fun DiagScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var channels by remember { mutableStateOf("") }
    var channelBusy by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf("7E $DIAG_CMD_IMEI_1") }
    var slotIndex by remember { mutableStateOf(0) }
    var readBytes by remember { mutableStateOf("512") }
    var respHex by remember { mutableStateOf("") }
    var sendBusy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadChannels() {
        channelBusy = true
        scope.launch {
            feats?.diagChannels()
                ?.onSuccess { channels = it; msg = ""; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            channelBusy = false
        }
    }

    fun sendFrame() {
        sendBusy = true
        scope.launch {
            val count = readBytes.toIntOrNull() ?: 512
            feats?.diagSend(frame, slotIndex, count)
                ?.onSuccess { respHex = it; msg = "DIAG 帧已发送"; msgErr = false }
                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
            sendBusy = false
        }
    }

    FeaturePage(
        title = "DIAG 工具",
        subtitle = "展锐 DIAG 通道 · IMEI 查询（/dev/sdiag_nr）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "DIAG 通道检测", subtitle = "ls -l /dev/sdiag*（确认设备开放 DIAG 节点）") {
            ActionButton(
                text = "检测 DIAG 设备节点",
                loading = channelBusy,
                onClick = { loadChannels() }
            )
            if (channels.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                CommandOutput(channels, 130.dp)
            }
        }

        FeatureCard(
            title = "DIAG 帧发送",
            subtitle = "写入 /dev/sdiag_nr（slot 1 为 /dev/sdiag_nr2）后回读响应"
        ) {
            Text("通道", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            SelectRow(
                label = "slot 0（/dev/sdiag_nr）",
                selected = slotIndex == 0,
                onClick = { slotIndex = 0 }
            )
            SelectRow(
                label = "slot 1（/dev/sdiag_nr2）",
                selected = slotIndex == 1,
                onClick = { slotIndex = 1 }
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(
                value = frame,
                onValueChange = { frame = it },
                label = "DIAG 帧（十六进制）",
                placeholder = "7E 5E 81",
                singleLine = false
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlineActionButton(
                    text = DIAG_CMD_IMEI_1,
                    modifier = Modifier.weight(1f),
                    onClick = { frame = "7E $DIAG_CMD_IMEI_1" }
                )
                OutlineActionButton(
                    text = DIAG_CMD_IMEI_2,
                    modifier = Modifier.weight(1f),
                    onClick = { frame = "7E $DIAG_CMD_IMEI_2" }
                )
                OutlineActionButton(
                    text = DIAG_CMD_IMEI_3,
                    modifier = Modifier.weight(1f),
                    onClick = { frame = "7E $DIAG_CMD_IMEI_3" }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "帧模板仅含起始标志与命令码（5E81 / 5E82 / 5E90 为高级后台插件记录的 IMEI 相关命令码）；" +
                    "完整帧的长度字节与 CRC 随平台/机型不同，请按设备实际情况补全后再发送。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureTextField(
                value = readBytes,
                onValueChange = { t -> readBytes = t.filter { c -> c.isDigit() }.take(4) },
                label = "读取字节数",
                placeholder = "512",
                keyboard = KeyboardType.Number
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(
                text = "发送 DIAG 帧并读取响应",
                loading = sendBusy,
                onClick = { sendFrame() }
            )
        }

        if (respHex.isNotBlank()) {
            FeatureCard(title = "响应回显", subtitle = "十六进制原文与 ASCII 解析") {
                CommandOutput(respHex, 160.dp)
                Spacer(modifier = Modifier.height(10.dp))
                val ascii = hexToAscii(respHex)
                Text(
                    "ASCII：${ascii.take(400)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = iOSSecondaryLabel
                )
                Spacer(modifier = Modifier.height(8.dp))
                val imeis = extractImeiCandidates(ascii)
                if (imeis.isNotEmpty()) {
                    Text(
                        "疑似 IMEI：${imeis.joinToString(" / ")}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iOSGreen
                    )
                } else {
                    Text(
                        "未在响应中解析到 14~16 位数字串（IMEI 通常为 15 位）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                }
            }
        }

        ResultMessage(msg, msgErr)
    }
}
