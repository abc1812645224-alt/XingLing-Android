/*
 * 星灵 (XingLing) · 设备控制批页面（一）
 *  短信收发 · SMS 转发 · WiFi 热点 · 流量校准
 * 通过 goform 反代（SEND_SMS / setAccessPointInfo / FLOW_CALIBRATION_MANUAL）与 /api/sms_forward_* 真实调用。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.BlacklistConfig
import com.xingling.app.backend.CurlForwardConfig
import com.xingling.app.backend.DataLimit
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.MailConfig
import com.xingling.app.backend.SmsMessage
import com.xingling.app.backend.WifiApInfo
import com.xingling.app.backend.formatBytes
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.GlassCard
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════
// 短信收发
// ═══════════════════════════════════════════
@Composable
fun SmsScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var inbox by remember { mutableStateOf<List<SmsMessage>?>(null) }
    var phone by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var recvMode by remember { mutableStateOf("") }
    var recvBusy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        feats?.inboxSms()?.onSuccess { inbox = it }
        feats?.smsReceiveMode()?.onSuccess { recvMode = it }
    }
    LaunchedEffect(Unit) { refresh() }

    FeaturePage(
        title = "短信收发",
        subtitle = "发送短信（goform SEND_SMS）与收件箱（/api/sms_list）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "发送短信", subtitle = "UTF-8 编码发送，号码为国际格式") {
            FeatureTextField(phone, { phone = it }, "接收号码", "+8613800138000")
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(body, { body = it }, "短信内容", "你好，星灵", singleLine = false)
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "发送",
                loading = busy,
                onClick = {
                    if (phone.isBlank() || body.isBlank()) return@ActionButton
                    busy = true
                    scope.launch {
                        feats?.sendSms(phone.trim(), body)
                            ?.onSuccess { msg = "短信已发送至 $phone"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }

        FeatureCard(
            title = "收件箱",
            subtitle = "最近 50 条短信（inbox()）",
            trailing = {
                OutlineActionButton(text = "刷新", modifier = Modifier.width(80.dp), onClick = {
                    scope.launch { refresh() }
                })
            }
        ) {
            if (inbox == null) {
                Text("读取中…", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else if (inbox!!.isEmpty()) {
                Text("收件箱为空。", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else {
                inbox!!.forEach { s ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                s.number,
                                style = MaterialTheme.typography.bodyMedium,
                                color = iOSLabel,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            StatusPill(if (s.unread) "新" else "已读", if (s.unread) iOSBlue else iOSOrange)
                        }
                        Text(s.body, style = MaterialTheme.typography.bodyMedium, color = iOSSecondaryLabel)
                        if (s.date.isNotBlank()) {
                            Text(
                                s.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = iOSSecondaryLabel
                            )
                        }
                    }
                    Divider(color = iOSSeparator, thickness = 1.dp)
                }
            }
        }

        FeatureCard(
            title = "短信接收模式",
            subtitle = "官方 /api/sms_receive_mode（get/set）。设备收到短信后的处理模式，值取决于后台版本"
        ) {
            FeatureTextField(recvMode, { recvMode = it }, "接收模式值", "auto")
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "保存接收模式",
                loading = recvBusy,
                onClick = {
                    if (recvMode.isBlank()) return@ActionButton
                    recvBusy = true
                    scope.launch {
                        feats?.setSmsReceiveMode(recvMode.trim())
                            ?.onSuccess { msg = "短信接收模式已保存：$recvMode"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        recvBusy = false
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
    }
}

private fun formatTimestamp(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(
        java.util.Locale.CHINA,
        "%04d-%02d-%02d %02d:%02d",
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH),
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
}

// ═══════════════════════════════════════════
// SMS 转发
// ═══════════════════════════════════════════
@Composable
fun ForwardScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var channel by remember { mutableStateOf(0) } // 0 mail 1 curl
    var enabled by remember { mutableStateOf(false) }
    var powerEnabled by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("465") }
    var to by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var devInfo by remember { mutableStateOf(true) }
    var curl by remember { mutableStateOf("") }
    var blackPhone by remember { mutableStateOf("") }
    var blackKw by remember { mutableStateOf("") }
    var pushAddr by remember { mutableStateOf("") }
    var pushBody by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.getForwardEnabled()?.onSuccess { enabled = it }
        feats?.getPowerForwardEnabled()?.onSuccess { powerEnabled = it }
        feats?.getForwardMail()?.onSuccess { m ->
            if (m != null) {
                host = m.smtpHost; port = m.smtpPort.ifBlank { "465" }
                to = m.smtpTo; user = m.smtpUsername
                pass = m.smtpPassword; devInfo = m.forwardDevInfo
            }
        }
        feats?.getForwardCurl()?.onSuccess { curl = it ?: "" }
        feats?.getForwardBlacklist()?.onSuccess { b ->
            if (b != null) { blackPhone = b.phone; blackKw = b.keywords }
        }
    }

    FeaturePage(
        title = "短信转发",
        subtitle = "邮件 / 钉钉 / curl 通道（sms_forward_*）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "转发总开关", subtitle = "/api/sms_forward_enabled") {
            ToggleRow("启用转发", "开启后新收到的短信会按通道转发", enabled, !busy) { on ->
                busy = true
                scope.launch {
                    feats?.setForwardEnabled(on)
                        ?.onSuccess { enabled = on; msg = "转发已${if (on) "启用" else "关闭"}"; msgErr = false }
                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    busy = false
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectTab(active = channel == 0, text = "SMTP 邮件") { channel = 0 }
            SelectTab(active = channel == 1, text = "curl 通道") { channel = 1 }
        }

        if (channel == 0) {
            FeatureCard(title = "SMTP 邮件", subtitle = "/api/sms_forward_mail") {
                FeatureTextField(host, { host = it }, "SMTP 服务器", "smtp.qq.com")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FeatureTextField(port, { port = it }, "端口", "465")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FeatureTextField(user, { user = it }, "发件账号", "xxx@qq.com")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FeatureTextField(pass, { pass = it }, "授权码", "")
                Spacer(modifier = Modifier.height(8.dp))
                FeatureTextField(to, { to = it }, "接收邮箱", "yyy@qq.com")
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow("附带设备信息", "转发内容中包含 dev_info", devInfo, true) { devInfo = it }
                Spacer(modifier = Modifier.height(12.dp))
                ActionButton(
                    text = "保存邮件配置",
                    loading = busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.setForwardMail(
                                MailConfig(host.trim(), port.trim(), to.trim(), user.trim(), pass.trim(), devInfo)
                            )?.onSuccess { msg = "邮件配置已保存"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    }
                )
            }
        } else {
            FeatureCard(title = "curl 通道", subtitle = "/api/sms_forward_curl，支持钉钉 webhook 等") {
                FeatureTextField(curl, { curl = it }, "curl 模板", "curl -X POST …", singleLine = false)
                Spacer(modifier = Modifier.height(12.dp))
                ActionButton(
                    text = "保存 curl 配置",
                    loading = busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.setForwardCurl(curl)
                                ?.onSuccess { msg = "curl 配置已保存"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    }
                )
            }
        }

        FeatureCard(title = "转发黑名单", subtitle = "/api/sms_forward_blacklist，号码与关键词") {
            FeatureTextField(blackPhone, { blackPhone = it }, "屏蔽号码（逗号分隔）", "10086,10010")
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(blackKw, { blackKw = it }, "屏蔽关键词（逗号分隔）", "广告,推广")
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "保存黑名单",
                loading = busy,
                onClick = {
                    busy = true
                    scope.launch {
                        feats?.setForwardBlacklist(BlacklistConfig(blackPhone.trim(), blackKw.trim()))
                            ?.onSuccess { msg = "黑名单已保存"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineActionButton(
                text = "发送测试短信",
                modifier = Modifier.weight(1f),
                onClick = {
                    busy = true
                    scope.launch {
                        feats?.testForward()
                            ?.onSuccess { msg = "测试消息已发出，请检查转发通道"; msgErr = false }
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
// WiFi 热点
// ═══════════════════════════════════════════
// WiFi 热点
// ═══════════════════════════════════════════
// WiFi 热点
// ═══════════════════════════════════════════
// WiFi 热点
// ═══════════════════════════════════════════
@Composable
fun HotspotScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var powerEnabled by remember { mutableStateOf(false) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf("WPA2PSK") }
    var encrypType by remember { mutableStateOf("") }
    var maxStation by remember { mutableStateOf("10") }
    var broadcast by remember { mutableStateOf(true) }
    var isolate by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf("0") }
    var apIdx by remember { mutableStateOf("0") }
    var bootAuto by remember { mutableStateOf(false) }
    var bootDelay by remember { mutableStateOf("0") }
    var bootBusy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf<EditField?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        feats?.getWifiAp()?.onSuccess { w ->
            enabled = w.enabled
            ssid = w.ssid
            password = w.password
            authMode = w.authMode.ifBlank { "WPA2PSK" }
            encrypType = w.encrypType.ifBlank { "AES" }
            maxStation = w.maxStation.toString()
            broadcast = !w.broadcastDisabled
            isolate = w.isolate
            chip = w.chipIndex.toString()
            apIdx = w.accessPointIndex.toString()
            loaded = true
        }
        feats?.hotspotBootAutostart()?.onSuccess { bootAuto = it }
        feats?.hotspotBootAutostartDelay()?.onSuccess { bootDelay = it.toString() }
    }
    LaunchedEffect(Unit) { refresh() }

    FeaturePage(
        title = "WiFi 热点",
        subtitle = "SSID · 密码 · 认证设置",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        // ===== 热点信息 =====
        SectionTitle("热点信息")
        FeatureCard {
            SettingRow(
                icon = Icons.Filled.Star, iconColor = Color(0xFF007AFF),
                title = "热点名称",
                desc = "当前 WiFi SSID",
                value = ssid.ifBlank { "未设置" },
                onClick = { editDialog = EditField.SSID }
            )
            Divider(color = iOSSeparator, thickness = 1.dp)
            SettingRow(
                icon = Icons.Filled.Lock, iconColor = Color(0xFF34C759),
                title = "密码",
                desc = "8~63 位字符",
                value = "••••••••",
                onClick = { editDialog = EditField.Password }
            )
            Divider(color = iOSSeparator, thickness = 1.dp)
            SettingRow(
                icon = Icons.Filled.Lock, iconColor = Color(0xFF5856D6),
                title = "认证模式",
                desc = "安全加密方式",
                value = authMode,
                onClick = { editDialog = EditField.AuthMode }
            )
            Divider(color = iOSSeparator, thickness = 1.dp)
            SettingRow(
                icon = Icons.Filled.Lock, iconColor = Color(0xFFFF9500),
                title = "加密方式",
                desc = "数据加密算法",
                value = encrypType,
                onClick = { editDialog = EditField.EncrypType }
            )
        }

        // ===== 连接设置 =====
        SectionTitle("连接设置")
        FeatureCard {
            SettingRow(
                icon = Icons.Filled.Person, iconColor = Color(0xFF007AFF),
                title = "最大连接数",
                desc = "最多同时连接的设备数",
                value = "${maxStation} 台",
                onClick = { editDialog = EditField.MaxStation }
            )
            Divider(color = iOSSeparator, thickness = 1.dp)
            ToggleRow(
                label = "广播 SSID",
                desc = "关闭后隐藏热点名称",
                checked = broadcast,
                enabled = true
            ) { broadcast = it }
            Divider(color = iOSSeparator, thickness = 1.dp)
            ToggleRow(
                label = "AP 隔离",
                desc = "禁止客户端互相访问",
                checked = isolate,
                enabled = true
            ) { isolate = it }
        }

        // ===== 保存按钮 =====
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineActionButton(
                text = "刷新",
                modifier = Modifier.weight(1f),
                onClick = { scope.launch { refresh() } }
            )
            ActionButton(
                text = "保存配置",
                modifier = Modifier.weight(1f),
                loading = busy,
                onClick = {
                    busy = true
                    scope.launch {
                        feats?.setWifiAp(
                            WifiApInfo(
                                enabled = enabled,
                                ssid = ssid.trim(),
                                password = password,
                                authMode = authMode.trim().ifBlank { "WPA2PSK" },
                                encrypType = encrypType.trim().ifBlank { "AES" },
                                maxStation = maxStation.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                                broadcastDisabled = !broadcast,
                                isolate = isolate,
                                chipIndex = chip.toIntOrNull() ?: 0,
                                accessPointIndex = apIdx.toIntOrNull() ?: 0
                            )
                        )?.onSuccess {
                            msg = "热点配置已保存，设备将重新应用"; msgErr = false
                        }?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)

    }

    // ===== 编辑对话框 =====
    val currentDialog = editDialog
    if (currentDialog != null) {
        EditDialog(currentDialog, ssid, password, authMode, encrypType, maxStation, bootDelay,
            onDismiss = { editDialog = null },
            onConfirm = { field, value ->
                when (field) {
                    EditField.SSID -> ssid = value
                    EditField.Password -> password = value
                    EditField.AuthMode -> authMode = value
                    EditField.EncrypType -> encrypType = value
                    EditField.MaxStation -> maxStation = value.filter { it.isDigit() }
                    EditField.BootDelay -> {
                        val sec = value.filter { it.isDigit() }.toLongOrNull() ?: 0
                        bootBusy = true
                        scope.launch {
                            feats?.setHotspotBootAutostartDelay(sec)
                                ?.onSuccess { bootDelay = sec.toString(); msg = "启动延迟已保存：$sec 秒"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            bootBusy = false
                        }
                    }
                }
                editDialog = null
            }
        )
    }
}


// 可编辑字段枚举
private enum class EditField { SSID, Password, AuthMode, EncrypType, MaxStation, BootDelay }


// 编辑对话框
@Composable
private fun EditDialog(
    field: EditField,
    ssid: String,
    password: String,
    authMode: String,
    encrypType: String,
    maxStation: String,
    bootDelay: String,
    onDismiss: () -> Unit,
    onConfirm: (EditField, String) -> Unit
) {
    var text by remember {
        mutableStateOf(
            when (field) {
                EditField.SSID -> ssid
                EditField.Password -> password
                EditField.AuthMode -> authMode
                EditField.EncrypType -> encrypType
                EditField.MaxStation -> maxStation
                EditField.BootDelay -> bootDelay
            }
        )
    }
    val title = when (field) {
        EditField.SSID -> "修改热点名称"
        EditField.Password -> "修改密码"
        EditField.AuthMode -> "修改认证模式"
        EditField.EncrypType -> "修改加密方式"
        EditField.MaxStation -> "修改最大连接数"
        EditField.BootDelay -> "修改启动延迟"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = iOSLabel, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = iOSBlue,
                    unfocusedBorderColor = iOSSeparator,
                    focusedTextColor = iOSLabel,
                    unfocusedTextColor = iOSLabel
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(field, text) }) {
                Text("确定", color = iOSBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = iOSSecondaryLabel)
            }
        }
    )
}

// ═══════════════════════════════════════════
// 流量校准
// ═══════════════════════════════════════════
@Composable
fun CalibrateScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var usageText by remember { mutableStateOf("0") }
    var busy by remember { mutableStateOf(false) }
    var limitEnabled by remember { mutableStateOf(false) }
    var limitGb by remember { mutableStateOf("0") }
    var limitPeriod by remember { mutableStateOf("monthly") }
    var limitForward by remember { mutableStateOf(false) }
    var limitBusy by remember { mutableStateOf(false) }
    var limitLoaded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.getDataLimit()?.onSuccess { d ->
            limitEnabled = d.enabled
            limitGb = d.maxLimit
            limitPeriod = d.period
            limitForward = d.statusForwardEnabled
            limitLoaded = true
        }
    }

    FeaturePage(
        title = "流量校准",
        subtitle = "手动校准已用流量（FLOW_CALIBRATION_MANUAL）与限额（/api/get_data_limit）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "校准", subtitle = "输入本月已使用的流量总量") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FeatureTextField(
                    usageText, { usageText = it }, "已用流量（MB）", "0",
                    modifier = Modifier.weight(1f),
                    keyboard = androidx.compose.ui.text.input.KeyboardType.Number
                )
                Text("MB", color = iOSSecondaryLabel, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "校准为指定值",
                loading = busy,
                onClick = {
                    val mb = usageText.toLongOrNull() ?: return@ActionButton
                    busy = true
                    scope.launch {
                        feats?.calibrateFlow(mb * 1024L * 1024L)
                            ?.onSuccess { msg = "已校准为 $mb MB（${formatBytes(mb * 1024L * 1024L)}）"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlineActionButton(
                text = "清零流量",
                color = iOSRed,
                onClick = {
                    busy = true
                    scope.launch {
                        feats?.calibrateFlow(0)
                            ?.onSuccess { msg = "已清零流量"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }

        FeatureCard(
            title = "流量限额",
            subtitle = if (limitLoaded)
                "来自官方 /api/get_data_limit（超限告警与断网由后台控制）"
            else "读取中…（/api/get_data_limit）"
        ) {
            ToggleRow(
                label = "启用流量限额",
                desc = "开启后达到上限将触发后台限额策略",
                checked = limitEnabled,
                enabled = !limitBusy
            ) { on -> limitEnabled = on }
            Divider(color = iOSSeparator, thickness = 1.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                FeatureTextField(
                    limitGb, { limitGb = it.filter { c -> c.isDigit() || c == '.' } },
                    "每月限额（GB）", "0",
                    modifier = Modifier.weight(1f),
                    keyboard = androidx.compose.ui.text.input.KeyboardType.Number
                )
                Text("GB", color = iOSSecondaryLabel, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            SelectRow(
                label = "月度统计",
                desc = "按自然月统计流量",
                selected = limitPeriod == "monthly"
            ) { limitPeriod = "monthly" }
            SelectRow(
                label = "每日统计",
                desc = "按自然日统计流量",
                selected = limitPeriod == "daily"
            ) { limitPeriod = "daily" }
            Divider(color = iOSSeparator, thickness = 1.dp)
            ToggleRow(
                label = "超限状态上报短信",
                desc = "data_limit_status_forward_enabled",
                checked = limitForward,
                enabled = !limitBusy
            ) { on -> limitForward = on }
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "保存限额",
                loading = limitBusy,
                onClick = {
                    limitBusy = true
                    scope.launch {
                        feats?.setDataLimit(
                            DataLimit(
                                enabled = limitEnabled,
                                maxLimit = limitGb.ifBlank { "0" },
                                period = limitPeriod,
                                checkReference = "system",
                                statusForwardEnabled = limitForward
                            )
                        )?.onSuccess {
                            msg = "流量限额已保存（上限 ${limitGb.ifBlank { "0" }} GB / $limitPeriod）"
                            msgErr = false
                        }?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        limitBusy = false
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
    }
}

// 面板选择 Tab
@Composable
private fun androidx.compose.foundation.layout.RowScope.SelectTab(active: Boolean, text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) iOSBlue else iOSFill)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(text, color = if (active) Color.White else iOSLabel, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
    }
}
