/*
 * 星灵 (XingLing) · P3 组功能页
 *
 *  ① DeviceManagerScreen  多设备管理（MultiDeviceStore 增删改查 / 切换 / 同步到当前设备）
 *
 * 全部使用 FeaturePage / FeatureCard（iOS 玻璃卡片风格），零第三方依赖。
 * 多设备方案为最小侵入式：MultiDeviceStore 独立存储，
 * 通过"同步到当前设备"写入 DeviceStore，不重构现有连接逻辑。
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.DeviceStore
import com.xingling.app.backend.MultiDeviceStore
import com.xingling.app.backend.SavedDevice
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel

// ═══════════════════════════════════════════
// ① 多设备管理
// ═══════════════════════════════════════════

@Composable
fun DeviceManagerScreen(
    backend: DeviceBackend?,
    onBack: () -> Unit,
    onAddDevice: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { MultiDeviceStore(context) }
    var devices by remember { mutableStateOf(store.listDevices()) }
    var currentId by remember { mutableStateOf(store.getCurrentDeviceId()) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }

    // 表单 / 弹窗状态
    var showForm by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf("") }
    var fName by remember { mutableStateOf("") }
    var fHost by remember { mutableStateOf("") }
    var fPort by remember { mutableStateOf("2333") }
    var fToken by remember { mutableStateOf("") }
    var fZteToken by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SavedDevice?>(null) }

    fun reload() {
        devices = store.listDevices()
        currentId = store.getCurrentDeviceId()
    }

    fun openAdd() {
        editingId = ""
        fName = ""; fHost = ""; fPort = "2333"; fToken = ""; fZteToken = ""
        showForm = true
    }

    fun openEdit(d: SavedDevice) {
        editingId = d.id
        fName = d.name; fHost = d.host; fPort = d.port.toString(); fToken = d.token; fZteToken = d.zteToken
        showForm = true
    }

    fun saveForm() {
        val port = fPort.toIntOrNull() ?: 2333
        if (fHost.isBlank()) {
            msg = "请填写设备主机地址"; msgErr = true; return
        }
        val dev = SavedDevice(
            id = editingId.ifBlank { java.util.UUID.randomUUID().toString() },
            name = fName.ifBlank { fHost },
            host = fHost.trim(),
            port = port,
            token = fToken,
            zteToken = fZteToken
        )
        if (editingId.isBlank()) store.addDevice(dev) else store.updateDevice(dev)
        showForm = false
        msg = if (editingId.isBlank()) "已添加设备" else "已更新设备"
        msgErr = false
        reload()
    }

    fun selectDevice(d: SavedDevice) {
        store.setCurrentDeviceId(d.id)
        reload()
        msg = "已切换为「${d.name}」为当前设备，点击「同步到当前设备」后重新连接生效。"
        msgErr = false
    }

    /** 把当前选中设备写入 DeviceStore，复用现有连接逻辑 */
    fun syncToActive() {
        val cur = store.getCurrentDevice()
        if (cur == null) {
            msg = "请先选择一个设备"; msgErr = true; return
        }
        runCatching {
            DeviceStore(context).apply {
                host = cur.host
                port = cur.port
                token = cur.token
                zteToken = cur.zteToken
                configured = true
            }
        }.onSuccess {
            msg = "已将「${cur.name}」写入当前设备配置，请返回连接页重新连接。"
            msgErr = false
        }.onFailure {
            msg = "写入当前设备失败：${it.message ?: "未知错误"}"; msgErr = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
    ) {
        // 顶部导航栏（多设备管理不依赖后端连接状态，常驻显示）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iOSFill)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = iOSBlue)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("多设备管理", style = MaterialTheme.typography.headlineSmall, color = iOSLabel)
                Text("保存多个设备地址，一键切换并同步到当前连接", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 设备列表
            FeatureCard(title = "已保存设备", subtitle = "共 ${devices.size} 台 · 点击设为当前") {
                if (devices.isEmpty()) {
                    Text("暂无设备，点击下方「添加设备」录入第一台。", color = iOSSecondaryLabel, fontSize = 13.sp)
                }
                devices.forEachIndexed { i, d ->
                    if (i > 0) Spacer(modifier = Modifier.height(8.dp))
                    val isCurrent = d.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) iOSBlue.copy(alpha = 0.08f) else iOSFill.copy(alpha = 0.5f))
                            .clickable { selectDevice(d) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.name.ifBlank { d.host }, color = iOSLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusPill("当前", iOSGreen)
                                }
                            }
                            Text("${d.host}:${d.port}", color = iOSSecondaryLabel, fontSize = 12.sp)
                        }
                        TextButton(onClick = { openEdit(d) }) { Text("编辑", color = iOSBlue, fontSize = 13.sp) }
                        TextButton(onClick = { pendingDelete = d }) { Text("删除", color = iOSRed, fontSize = 13.sp) }
                    }
                }
            }

            // 添加 / 同步操作
            FeatureCard(title = "操作") {
                ActionButton(text = "＋ 添加设备", onClick = { openAdd() }, color = iOSBlue)
                Spacer(modifier = Modifier.height(10.dp))
                OutlineActionButton(text = "同步到当前设备", onClick = { syncToActive() }, color = iOSGreen)
                if (msg.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ResultMessage(text = msg, isError = msgErr)
                }
            }
        }
    }

    // 添加 / 编辑表单弹窗
    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (editingId.isBlank()) "添加设备" else "编辑设备") },
            text = {
                Column {
                    FeatureTextField(value = fName, onValueChange = { fName = it }, label = "设备名称", placeholder = "例如：客厅 CPE")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureTextField(value = fHost, onValueChange = { fHost = it }, label = "主机地址", placeholder = "192.168.0.1")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureTextField(value = fPort, onValueChange = { fPort = it }, label = "端口", placeholder = "2333", keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureTextField(value = fToken, onValueChange = { fToken = it }, label = "后台口令", placeholder = "UFI-TOOLS token")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureTextField(value = fZteToken, onValueChange = { fZteToken = it }, label = "ZTE 口令（可选）", placeholder = "留空则复用后台口令")
                }
            },
            confirmButton = { TextButton(onClick = { saveForm() }) { Text("保存", color = iOSBlue) } },
            dismissButton = { TextButton(onClick = { showForm = false }) { Text("取消") } }
        )
    }

    // 删除确认
    pendingDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除设备") },
            text = { Text("确定删除「${d.name.ifBlank { d.host }}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    store.removeDevice(d.id); pendingDelete = null; reload()
                    msg = "已删除设备"; msgErr = false
                }) { Text("删除", color = iOSRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}
