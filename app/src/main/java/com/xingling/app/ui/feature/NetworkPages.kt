/*
 * 星灵 (XingLing) · 网络控制批页面（一）
 *  VoLTE / VoNR 开关 · 5G 频段与锁频 · 锁基站(PCI/EARFCN)
 * 全部通过 UfiToolsFeatureApi → UFI-TOOLS /api 与 goform 反代真实调用。
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.BackendSignalInfo
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.CellInfo
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSButton
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════
// VoLTE / VoNR
// ═══════════════════════════════════════════
@Composable
fun VolteScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var volte by remember { mutableStateOf(false) }
    var vonr by remember { mutableStateOf(false) }
    var volteBusy by remember { mutableStateOf(false) }
    var vonrBusy by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.volteEnabled()?.onSuccess { volte = it }
            ?.onFailure { e -> if (e.isUnsupported()) unsupported = "VoLTE" }
        feats?.vonrEnabled()?.onSuccess { vonr = it }
            ?.onFailure { e -> if (e.isUnsupported()) unsupported = "VoNR" }
    }

    FeaturePage(
        title = "VoLTE / VoNR",
        subtitle = "高清语音与 5G 语音开关",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "语音承载",
            subtitle = "实时状态来自 /api/volte_status 与 /api/vonr_status"
        ) {
            ToggleRow(
                label = "VoLTE 高清语音",
                desc = "4G 网络下启用 IMS 语音通话",
                checked = volte,
                enabled = unsupported != "VoLTE" && !volteBusy && !vonrBusy
            ) { on ->
                volteBusy = true
                scope.launch {
                    feats?.setVolte(on, 0)
                        ?.onSuccess {
                            volte = on
                            msg = "VoLTE 已${if (on) "开启" else "关闭"}"
                            msgErr = false
                        }
                        ?.onFailure { e ->
                            msg = unsupportedOrMessage(e)
                            msgErr = true
                        }
                    volteBusy = false
                }
            }
            Divider(color = iOSSeparator, thickness = 1.dp)
            ToggleRow(
                label = "VoNR 5G 语音",
                desc = "5G 网络下启用 IMS 语音通话",
                checked = vonr,
                enabled = unsupported != "VoNR" && !volteBusy && !vonrBusy
            ) { on ->
                vonrBusy = true
                scope.launch {
                    feats?.setVonr(on, 0)
                        ?.onSuccess {
                            vonr = on
                            msg = "VoNR 已${if (on) "开启" else "关闭"}"
                            msgErr = false
                        }
                        ?.onFailure { e ->
                            msg = unsupportedOrMessage(e)
                            msgErr = true
                        }
                    vonrBusy = false
                }
            }
        }
        ResultMessage(msg, msgErr)
        if (unsupported != null) {
            UnsupportedCard("当前设备后台未提供「$unsupported」语音状态接口，或版本不支持，已禁用对应开关。")
        }
    }
}

// ═══════════════════════════════════════════
// 5G 频段与锁频（NR 锁频 + LTE 锁频）
// ═══════════════════════════════════════════
private val DEFAULT_NR_BANDS = listOf(1, 2, 3, 5, 7, 8, 20, 25, 28, 38, 40, 41, 66, 71, 77, 78, 79, 80)

/** 常用 LTE（4G）频段参考列表，设备未上报支持列表时用于展示与锁频 */
private val DEFAULT_LTE_BANDS = listOf(1, 2, 3, 4, 5, 7, 8, 20, 28, 34, 38, 39, 40, 41, 42, 66)

@Composable
fun BandChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) iOSBlue else iOSFill)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else iOSLabel,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun nrBandName(b: Int): String =
    mapOf(
        1 to "n1 2100", 2 to "n2 1900", 3 to "n3 1800", 5 to "n5 850", 7 to "n7 2600",
        8 to "n8 900", 20 to "n20 800", 25 to "n25 1900", 28 to "n28 700", 38 to "n38 2600",
        40 to "n40 2300", 41 to "n41 2500", 66 to "n66 1700", 71 to "n71 600",
        75 to "n75 1500", 76 to "n76 1500", 77 to "n77 3300", 78 to "n78 3500", 79 to "n79 4800",
        80 to "n80 1800", 81 to "n81 900", 82 to "n82 800", 83 to "n83 700", 84 to "n84 2100",
        85 to "n85 700", 86 to "n86 1700"
    )[b] ?: "n$b"

private fun lteBandName(b: Int): String =
    mapOf(
        1 to "B1 2100", 2 to "B2 1900", 3 to "B3 1800", 4 to "B4 1700", 5 to "B5 850",
        7 to "B7 2600", 8 to "B8 900", 12 to "B12 700", 13 to "B13 700", 17 to "B17 700",
        18 to "B18 850", 19 to "B19 850", 20 to "B20 800", 25 to "B25 1900", 26 to "B26 850",
        28 to "B28 700", 30 to "B30 2300", 34 to "B34 2000", 38 to "B38 2600", 39 to "B39 1900",
        40 to "B40 2300", 41 to "B41 2500", 42 to "B42 3500", 43 to "B43 3700", 66 to "B66 1700",
        71 to "B71 600"
    )[b] ?: "B$b"

/** 把后台回显的频段字符串（"3" / "B3" / "n78"）规范化为带频段名的展示文本 */
private fun formatBandLabel(raw: String, preferNr: Boolean): String {
    val t = raw.trim()
    if (t.isEmpty()) return "--"
    if (t.startsWith("n", true)) {
        val n = t.drop(1).trim().toIntOrNull() ?: return t
        return nrBandName(n)
    }
    if (t.startsWith("B", true)) {
        val n = t.drop(1).trim().toIntOrNull() ?: return t
        return lteBandName(n)
    }
    val n = t.toIntOrNull() ?: return t
    return if (preferNr) nrBandName(n) else lteBandName(n)
}

/** 当前服务小区实时频段（来自后台信号快照解析） */
private data class ServingBandInfo(
    val networkType: String,
    val bandText: String,
    val pci: String,
    val rsrp: String,
    val rssi: String,
    val sinr: String
)

private fun BackendSignalInfo.toServingBandInfo(): ServingBandInfo {
    val isNr = networkType.contains("NR", true) || networkType.contains("5G", true)
    return ServingBandInfo(
        networkType = networkType.ifBlank { "--" },
        bandText = formatBandLabel(band, isNr),
        pci = if (pci >= 0) pci.toString() else "--",
        rsrp = if (rsrp > -200) "$rsrp dBm" else "--",
        rssi = if (rssi > -200) "$rssi dBm" else "--",
        sinr = if (sinr > -100) "$sinr dB" else "--"
    )
}

@Composable
private fun ServingCellItem(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = iOSSecondaryLabel)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = iOSLabel, maxLines = 1)
    }
}

@Composable
fun BandsScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var support by remember { mutableStateOf<List<Int>?>(null) }
    var nrLock by remember { mutableStateOf<List<Int>>(emptyList()) }
    var lteLockText by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var lteSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var lteInput by remember { mutableStateOf("") }
    var panel by remember { mutableStateOf(0) } // 0 = NR, 1 = LTE
    var busy by remember { mutableStateOf(false) }
    var endc by remember { mutableStateOf(false) }
    var endcBusy by remember { mutableStateOf(false) }
    var unlocBusy by remember { mutableStateOf(false) }
    var lockEarfcnBusy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    // 当前服务小区实时频段
    var serving by remember { mutableStateOf<ServingBandInfo?>(null) }
    var servingBusy by remember { mutableStateOf(false) }
    var servingErr by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refreshServing() {
        servingBusy = true
        backend?.fetchSignalInfo()
            ?.onSuccess { sig ->
                if (sig.isKnown) {
                    serving = sig.toServingBandInfo()
                    servingErr = null
                } else {
                    serving = null
                    servingErr = "当前无驻网小区（设备可能未注册网络或未插卡）"
                }
            }
            ?.onFailure { e -> serving = null; servingErr = unsupportedOrMessage(e) }
        servingBusy = false
    }

    LaunchedEffect(Unit) {
        feats?.supportNrBands()
            ?.onSuccess { list ->
                support = list
                selected = list.toSet()
            }
        feats?.getNrBandLock()
            ?.onSuccess { locked ->
                nrLock = locked
                if (locked.isNotEmpty()) selected = locked.toSet()
            }
        feats?.getLteBandLock()?.onSuccess { locked ->
            lteLockText = locked.joinToString(",")
            lteInput = locked.joinToString(",")
            lteSelected = locked.toSet()
        }
        feats?.endcState()?.onSuccess { endc = it }
        refreshServing()
    }

    val bands = support ?: DEFAULT_NR_BANDS

    FeaturePage(
        title = "5G 频段与锁频",
        subtitle = "锁定 4G / 5G 频段（LTE_BAND_LOCK / NR_BAND_LOCK）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        // 当前服务小区实时频段（后台信号快照）
        FeatureCard(
            title = "当前服务小区（实时频段）",
            subtitle = "实时服务小区制式 / 频段 / PCI / 电平，来源于后台信号快照",
            trailing = {
                OutlineActionButton(
                    text = "刷新",
                    modifier = Modifier.width(88.dp),
                    loading = servingBusy,
                    onClick = { scope.launch { refreshServing() } }
                )
            }
        ) {
            val sig = serving
            if (sig == null) {
                Text(
                    servingErr ?: "尚未读取到服务小区信息。",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel,
                    lineHeight = 18.sp
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ServingCellItem("网络制式", sig.networkType, Modifier.weight(1f))
                    ServingCellItem("实时频段", sig.bandText, Modifier.weight(1f))
                    ServingCellItem("PCI", sig.pci, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ServingCellItem("RSRP", sig.rsrp, Modifier.weight(1f))
                    ServingCellItem("RSSI", sig.rssi, Modifier.weight(1f))
                    ServingCellItem("SINR", sig.sinr, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "锁频前建议参考此处实时频段；数据随设备信号快照刷新。",
                    style = MaterialTheme.typography.labelSmall,
                    color = iOSSecondaryLabel
                )
            }
        }

        FeatureCard(
            title = "ENDC / 载波聚合",
            subtitle = "ENDC（NR-LTE 双连接）状态，来自官方 /api/getEndcState"
        ) {
            ToggleRow(
                label = "启用 ENDC",
                desc = "开启后允许 LTE+NR 载波聚合，需随身 WiFi 卡支持。改动经 /api/setEndcState 下发",
                checked = endc,
                enabled = !endcBusy
            ) { on ->
                endcBusy = true
                scope.launch {
                    feats?.setEndcState(on)
                        ?.onSuccess { endc = on; msg = "ENDC 已${if (on) "开启" else "关闭"}"; msgErr = false }
                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    endcBusy = false
                }
            }
            Divider(color = iOSSeparator, thickness = 1.dp)
            OutlineActionButton(
                text = "解锁全部频段（LTE + NR）",
                onClick = {
                    unlocBusy = true
                    scope.launch {
                        feats?.unlockAllBands()
                            ?.onSuccess {
                                nrLock = emptyList(); selected = emptySet()
                                lteLockText = ""; lteInput = ""; lteSelected = emptySet()
                                msg = "已一键解锁全部频段（/api/unlockAllBand）"
                                msgErr = false
                            }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        unlocBusy = false
                    }
                },
                loading = unlocBusy
            )
            Divider(color = iOSSeparator, thickness = 1.dp)
            OutlineActionButton(
                text = "锁定当前服务小区频点",
                color = iOSBlue,
                onClick = {
                    lockEarfcnBusy = true
                    scope.launch {
                        val sig = backend?.fetchSignalInfo()?.getOrNull()
                        val earfcn = sig?.band?.toIntOrNull() ?: 0
                        if (earfcn <= 0) {
                            msg = "未获取到当前服务小区频点（band=${sig?.band ?: "--"}）"
                            msgErr = true
                        } else {
                            val rat = if (sig != null && (sig.networkType.contains("NR", true) || sig.networkType.contains("5G", true))) "NR" else "LTE"
                            feats?.lockCurrentEarfcn(earfcn, rat)
                                ?.onSuccess { msg = "已锁定当前服务小区频点 EARFCN=$earfcn（$rat）"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        }
                        lockEarfcnBusy = false
                    }
                },
                loading = lockEarfcnBusy
            )
        }

        FeatureCard(
            title = "支持频段（NR）",
            subtitle = if (support == null)
                "设备未上报支持列表（/api/getSupportNrBandList），以下为常见 NR 频段，可直接锁频"
            else "来自 /api/getSupportNrBandList"
        ) {
            bands.sorted().chunked(4).forEach { rowBands ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowBands.forEach { b ->
                        BandChip(
                            text = nrBandName(b),
                            selected = selected.contains(b),
                            enabled = !busy
                        ) {
                            selected = if (selected.contains(b)) selected - b else selected + b
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                "已锁定 NR：${nrLock.ifEmpty { "无" }}",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
        }

        if (panel == 0) {
            FeatureCard(title = "NR 锁频", subtitle = "勾选上方频段后点击应用") {
                ActionButton(
                    text = "应用 NR 锁频（${selected.size} 个频段）",
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.setNrBandLock(selected.toList())
                                ?.onSuccess {
                                    nrLock = selected.toList()
                                    msg = "NR 频段已更新：${nrLock}"
                                    msgErr = false
                                }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    },
                    loading = busy
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlineActionButton(
                    text = "解锁全部 NR 频段",
                    color = iOSRed,
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.setNrBandLock(emptyList())
                                ?.onSuccess { nrLock = emptyList(); selected = emptySet(); msg = "已解锁全部 NR 频段"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    },
                    loading = busy
                )
            }
        } else {
            FeatureCard(
                title = "支持频段（LTE）",
                subtitle = "常用 LTE 频段（设备未上报 LTE 支持列表时为本机参考列表），点击选择后应用锁频"
            ) {
                DEFAULT_LTE_BANDS.sorted().chunked(4).forEach { rowBands ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowBands.forEach { b ->
                            BandChip(
                                text = lteBandName(b),
                                selected = lteSelected.contains(b),
                                enabled = !busy
                            ) {
                                lteSelected = if (lteSelected.contains(b)) lteSelected - b else lteSelected + b
                                lteInput = lteSelected.sorted().joinToString(",")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    "已选 LTE：${if (lteSelected.isEmpty()) "无" else lteSelected.sorted().joinToString(",")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            }

            FeatureCard(
                title = "LTE 锁频",
                subtitle = "输入以逗号分隔的频段号，如 1,3,7,38,41；也可在上方频段列表中直接选择"
            ) {
                FeatureTextField(
                    value = lteInput,
                    onValueChange = { text ->
                        lteInput = text
                        lteSelected = text.split(',', ' ', '，')
                            .map { s -> s.trim() }
                            .filter { s -> s.isNotEmpty() }
                            .mapNotNull { s -> s.toIntOrNull() }
                            .toSet()
                    },
                    label = "LTE 频段",
                    placeholder = "1,3,7,38,41"
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "当前锁定：${lteLockText.ifBlank { "无" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
                Spacer(modifier = Modifier.height(12.dp))
                ActionButton(
                    text = "应用 LTE 锁频",
                    onClick = {
                        val list = lteInput.split(',', ' ', '，')
                            .map { it.trim() }.filter { it.isNotEmpty() }
                            .mapNotNull { it.toIntOrNull() }
                        busy = true
                        scope.launch {
                            feats?.setLteBandLock(list)
                                ?.onSuccess { lteLockText = list.joinToString(","); lteSelected = list.toSet(); msg = "LTE 锁频已更新：${list}"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    },
                    loading = busy
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlineActionButton(
                    text = "解锁全部 LTE 频段",
                    color = iOSRed,
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.setLteBandLock(emptyList())
                                ?.onSuccess { lteLockText = ""; lteInput = ""; lteSelected = emptySet(); msg = "已解锁全部 LTE 频段"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    },
                    loading = busy
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedSelectTab(panel == 0, "NR 锁频") { panel = 0 }
            OutlinedSelectTab(panel == 1, "LTE 锁频") { panel = 1 }
        }
        ResultMessage(msg, msgErr)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.OutlinedSelectTab(active: Boolean, text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) iOSBlue else iOSFill)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text,
            color = if (active) Color.White else iOSLabel,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

// ═══════════════════════════════════════════
// 锁基站（PCI / EARFCN）
// ═══════════════════════════════════════════
@Composable
fun CellLockScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var neighbors by remember { mutableStateOf<List<CellInfo>>(emptyList()) }
    var locked by remember { mutableStateOf<List<CellInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var simLockBusy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        feats?.neighborCells()?.onSuccess { neighbors = it }
        feats?.lockedCells()?.onSuccess { locked = it }
        loaded = true
    }
    LaunchedEffect(Unit) { refresh() }

    FeaturePage(
        title = "锁基站",
        subtitle = "锁定小区 PCI / EARFCN（CELL_LOCK）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(
            title = "已锁定小区",
            subtitle = if (loaded) "来自 locked_cell_info" else "读取中…",
            trailing = {
                OutlineActionButton(
                    text = "解锁全部",
                    color = iOSRed,
                    modifier = Modifier.width(110.dp),
                    enabled = locked.isNotEmpty(),
                    onClick = {
                        busy = true
                        scope.launch {
                            feats?.unlockCell()
                                ?.onSuccess { locked = emptyList(); msg = "已解锁全部小区"; msgErr = false }
                                ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            busy = false
                        }
                    }
                )
            }
        ) {
            if (locked.isEmpty()) {
                Text("当前未锁定任何小区。", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else {
                locked.forEach { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${c.rat.ifBlank { "CELL" }} · PCI ${c.pci} · EARFCN ${c.earfcn}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = iOSLabel
                        )
                        StatusPill("已锁", iOSOrange)
                    }
                }
            }
        }

        FeatureCard(
            title = "邻区列表",
            subtitle = if (loaded) "来自 neighbor_cell_info，点击一行锁定该小区" else "读取中…",
            trailing = {
                OutlineActionButton(
                    text = "刷新",
                    modifier = Modifier.width(80.dp),
                    onClick = {
                        busy = true
                        scope.launch { refresh(); busy = false }
                    }
                )
            }
        ) {
            if (neighbors.isEmpty()) {
                Text("未扫描到邻区，或后台无邻区数据。", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else {
                neighbors.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(iOSFill.copy(alpha = 0.6f))
                            .clickable(enabled = !busy) {
                                busy = true
                                scope.launch {
                                    feats?.lockCell(c.pci, c.earfcn, c.rat)
                                        ?.onSuccess { msg = "已锁定 ${c.rat.ifBlank { "CELL" }} PCI=${c.pci} EARFCN=${c.earfcn}"; msgErr = false; refresh() }
                                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                                    busy = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${c.rat.ifBlank { "CELL" }} · PCI ${c.pci} · EARFCN ${c.earfcn}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = iOSLabel,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (c.rsrp != Int.MIN_VALUE) {
                                Text("RSRP ${c.rsrp} dBm", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                            }
                        }
                        Text("锁定 ⇢", color = iOSBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        FeatureCard(
            title = "SIM 锁定",
            subtitle = "清除 SIM 相关锁定（UNLOCK_ALL_CELL，官方同源操作）"
        ) {
            Text(
                "当设备提示 SIM 被锁定（如运营商策略限制、锁定指定运营商）时，可尝试清除 SIM 锁定以恢复自动选网。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlineActionButton(
                text = "清除 SIM 锁定",
                color = iOSRed,
                onClick = {
                    simLockBusy = true
                    scope.launch {
                        feats?.clearSimLock()
                            ?.onSuccess { msg = "已清除 SIM 锁定（UNLOCK_ALL_CELL）"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        simLockBusy = false
                    }
                },
                loading = simLockBusy
            )
        }
        ResultMessage(msg, msgErr)
    }
}
