/*
 * 星灵 (XingLing) · 网络控制批页面（二）
 *  网络模式切换 · SIM 卡槽切换 · APN 配置
 * 全部通过 UfiToolsFeatureApi → goform 反代（SET_BEARER_PREFERENCE / SET_SIM_SLOT / APN_PROC_EX）真实调用。
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.ApnProfile
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════
// 网络模式切换
// ═══════════════════════════════════════════
private val BEARER_PRESETS = listOf(
    "AUTO" to "自动（4G + 5G）",
    "LTE_ONLY" to "仅 4G LTE",
    "NR_ONLY" to "仅 5G NR",
    "LTE_PREF" to "4G 优先",
    "NR_PREF" to "5G 优先"
)

@Composable
fun NetworkModeScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var current by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var customValue by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var endc by remember { mutableStateOf(false) }
    var endcBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.getBearerPreference()?.onSuccess { current = it }
        feats?.endcState()?.onSuccess { endc = it }
    }

    FeaturePage(
        title = "网络模式",
        subtitle = "4G / 5G 制式偏好（SET_BEARER_PREFERENCE）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "网络模式",
            subtitle = "当前设备值：" + current.ifBlank { "未读取" }
        ) {
            if (!custom) {
                BEARER_PRESETS.forEach { (value, label) ->
                    SelectRow(
                        label = label,
                        desc = "透传值：$value",
                        selected = current == value,
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                feats?.setBearerPreference(value)
                                    ?.onSuccess { current = value; msg = "已切换为「$label」"; msgErr = false }
                                    ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                                busy = false
                            }
                        }
                    )
                    Divider(color = iOSSeparator, thickness = 1.dp)
                }
                OutlineActionButton(
                    text = "自定义值…",
                    color = iOSBlue,
                    onClick = {
                        custom = true
                        customValue = current
                    }
                )
            } else {
                FeatureTextField(
                    value = customValue,
                    onValueChange = { customValue = it },
                    label = "自定义 BearerPreference",
                    placeholder = "如 LTENR / AUTOMODE"
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineActionButton(
                        text = "取消",
                        modifier = Modifier.weight(1f),
                        onClick = { custom = false }
                    )
                    ActionButton(
                        text = "应用",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val v = customValue.trim()
                            busy = true
                            scope.launch {
                                feats?.setBearerPreference(v)
                                    ?.onSuccess { current = v; custom = false; msg = "已应用网络模式：$v"; msgErr = false }
                                    ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                                busy = false
                            }
                        },
                        loading = busy
                    )
                }
            }
        }
        FeatureCard(
            title = "EN-DC 驻网策略",
            subtitle = "NSA 锚点（ENDC 双连接）策略，改动经 /api/setEndcState 下发"
        ) {
            ToggleRow(
                label = "启用 EN-DC",
                desc = "允许 LTE + NR 双连接（ENDC/NSA 锚点）。开启后 5G 载波聚合可获得更高速率，但部分机型会增加耗电。",
                checked = endc,
                enabled = !endcBusy
            ) { on ->
                endcBusy = true
                scope.launch {
                    feats?.setEndcState(on)
                        ?.onSuccess { endc = on; msg = "EN-DC 已${if (on) "开启" else "关闭"}"; msgErr = false }
                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    endcBusy = false
                }
            }
            Divider(color = iOSSeparator, thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "驻网策略说明：AUTO 自动选择最优制式；NR_ONLY 强制 5G 驻网（无 5G 覆盖时可能脱网）；" +
                    "LTE_ONLY 关闭 5G 仅用 4G；ENDC 开启时设备优先驻留 LTE 锚点并叠加 NR 载波。" +
                    "切换网络模式或 ENDC 后设备可能短暂断网重驻网，属正常现象。",
                style = MaterialTheme.typography.labelSmall,
                color = iOSSecondaryLabel,
                lineHeight = 17.sp
            )
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// SIM 卡槽切换
// ═══════════════════════════════════════════
@Composable
fun SimSlotScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var current by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dataBusy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.getSimSlot()?.onSuccess { current = it }
    }

    val options = listOf("0" to "SIM 1", "1" to "SIM 2", "11" to "双卡待机")

    FeaturePage(
        title = "SIM 卡槽",
        subtitle = "切换 SIM1 / SIM2（SET_SIM_SLOT）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "当前卡槽",
            subtitle = "设备上报 sim_slot = " + current.ifBlank { "未读取" }
        ) {
            options.forEach { (value, label) ->
                SelectRow(
                    label = label,
                    desc = "sim_slot=$value",
                    selected = current == value,
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.setSimSlot(value)
                                ?.onSuccess { current = value; msg = "已切换至「$label」"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    }
                )
                Divider(color = iOSSeparator, thickness = 1.dp)
            }
            Text(
                "提示：仅双卡机型支持切换；切换后设备可能短暂断网。若设备不接受预设值，可留待后台支持。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
        }

        FeatureCard(
            title = "默认数据卡",
            subtitle = "一键切换为默认数据卡（SET_SIM_SLOT，官方同源操作）"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "SIM 1 为默认数据卡",
                    modifier = Modifier.weight(1f),
                    enabled = !dataBusy,
                    onClick = {
                        dataBusy = true
                        scope.launch {
                            feats?.switchDataCard("0")
                                ?.onSuccess { msg = "已切换默认数据卡为 SIM 1"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            dataBusy = false
                        }
                    },
                    loading = dataBusy
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "SIM 2 为默认数据卡",
                    modifier = Modifier.weight(1f),
                    enabled = !dataBusy,
                    onClick = {
                        dataBusy = true
                        scope.launch {
                            feats?.switchDataCard("1")
                                ?.onSuccess { msg = "已切换默认数据卡为 SIM 2"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            dataBusy = false
                        }
                    },
                    loading = dataBusy
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "与官方「切换为默认数据卡」一致：仅设置数据承载，不改变语音卡槽。",
                style = MaterialTheme.typography.labelSmall,
                color = iOSSecondaryLabel
            )
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// APN 配置
// ═══════════════════════════════════════════
@Composable
fun ApnScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var mode by remember { mutableStateOf("") }
    var currentIndex by remember { mutableStateOf(-1) }
    var profileName by remember { mutableStateOf("") }
    var apn by remember { mutableStateOf("") }
    var pdpType by remember { mutableStateOf("IPV4") }
    var dial by remember { mutableStateOf("") }
    // 编辑表单
    var fName by remember { mutableStateOf("") }
    var fApn by remember { mutableStateOf("") }
    var fUser by remember { mutableStateOf("") }
    var fPass by remember { mutableStateOf("") }
    var fType by remember { mutableStateOf("IPV4") }
    var fAuth by remember { mutableStateOf("PAP") }
    var fAddr by remember { mutableStateOf("") }
    var fIndex by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.getApn()?.onSuccess { info ->
            mode = info.mode
            currentIndex = info.currentIndex
            profileName = info.profileName
            apn = info.apn
            pdpType = info.pdpType
            dial = info.dialNumber
            fIndex = info.currentIndex.coerceAtLeast(0)
        }
    }

    fun fillForm() {
        fName = profileName
        fApn = apn
        fUser = ""
        fPass = ""
        fType = pdpType.ifBlank { "IPV4" }
        fAuth = "PAP"
        fAddr = ""
    }

    FeaturePage(
        title = "APN 配置",
        subtitle = "接入点新增 / 编辑 / 删除（APN_PROC_EX）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "当前 APN",
            subtitle = "来自 apn_mode / apn_wan_apn 等设置项"
        ) {
            Text("模式：${mode.ifBlank { "--" }}", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
            Spacer(modifier = Modifier.height(4.dp))
            Text("名称：${profileName.ifBlank { "--" }}", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
            Text("APN：${apn.ifBlank { "--" }}", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
            Text("PDP 类型：${pdpType.ifBlank { "--" }}", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
            Text("拨号：${dial.ifBlank { "--" }}", style = MaterialTheme.typography.bodyMedium, color = iOSLabel)
            if (currentIndex >= 0) {
                Text("当前配置索引：$currentIndex", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }

        FeatureCard(title = "APN 模式", subtitle = "auto = 由 SIM 自动配置；manual = 手动管理") {
            SelectRow("自动（auto）", selected = mode == "auto", enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    feats?.setApnMode("auto")
                        ?.onSuccess { mode = "auto"; msg = "已切换自动 APN 模式"; msgErr = false }
                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    busy = false
                }
            })
            Divider(color = iOSSeparator, thickness = 1.dp)
            SelectRow("手动（manual）", selected = mode == "manual", enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    feats?.setApnMode("manual")
                        ?.onSuccess { mode = "manual"; msg = "已切换手动 APN 模式"; msgErr = false }
                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    busy = false
                }
            })
        }

        FeatureCard(title = "编辑 APN 配置", subtitle = "保存后写入手动配置并设为当前默认") {
            FeatureTextField(fName, { fName = it }, "配置名称", "China Mobile/CMNET")
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(fApn, { fApn = it }, "APN 接入点", "cmnet")
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    FeatureTextField(fUser, { fUser = it }, "用户名", "")
                }
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    FeatureTextField(fPass, { fPass = it }, "密码", "")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    FeatureTextField(fType, { fType = it }, "PDP 类型", "IPV4 / IPV4V6")
                }
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    FeatureTextField(fAuth, { fAuth = it }, "认证", "PAP / CHAP")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(fAddr, { fAddr = it }, "PDP 地址（可空）", "")
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineActionButton(text = "载入当前", modifier = Modifier.weight(1f), onClick = { fillForm() })
                ActionButton(
                    text = "保存并设置",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.saveApn(
                                ApnProfile(
                                    index = fIndex,
                                    profileName = fName.trim(),
                                    apn = fApn.trim(),
                                    pdpType = fType.trim().ifBlank { "IPV4" },
                                    username = fUser.trim(),
                                    password = fPass.trim(),
                                    authMode = fAuth.trim().ifBlank { "PAP" },
                                    pdpAddr = fAddr.trim()
                                )
                            )?.onSuccess {
                                msg = "APN 已保存并设为默认"; msgErr = false
                                feats?.getApn()?.onSuccess { info ->
                                    mode = info.mode; profileName = info.profileName; apn = info.apn
                                }
                            }?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    },
                    loading = busy
                )
            }
            if (currentIndex > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlineActionButton(
                    text = "删除当前配置（索引 $currentIndex）",
                    color = iOSRed,
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.deleteApn(currentIndex)
                                ?.onSuccess { msg = "已删除 APN（索引 $currentIndex）"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    },
                    loading = busy
                )
            }
        }

        ResultMessage(msg, msgErr)
    }
}
