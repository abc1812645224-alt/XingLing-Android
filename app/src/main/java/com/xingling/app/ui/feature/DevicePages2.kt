/*
 * 星灵 (XingLing) · 设备控制批页面（二）
 *  重启 / 关机 · 性能模式 · 定时重启与定时任务
 * 通过 goform 反代（REBOOT_DEVICE / SHUTDOWN_DEVICE / PERFORMANCE_MODE_SETTING）与 /api/list_tasks 等真实调用。
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import com.xingling.app.ui.theme.GlassCard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.ScheduledTask
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════
// 重启 / 关机
// ═══════════════════════════════════════════
@Composable
fun RebootScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FeaturePage(
        title = "重启 / 关机",
        subtitle = "远程电源控制",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        // 电源控制
        SectionTitle("电源控制")
        FeatureCard {
            SettingRow(
                icon = Icons.Filled.Refresh,
                iconColor = iOSBlue,
                title = "立即重启",
                desc = "设备将短暂离线，约 1~3 分钟恢复",
                onClick = {
                    busy = true
                    scope.launch {
                        feats?.rebootDevice()
                            ?.onSuccess { msg = "重启指令已下发，设备约 1~3 分钟恢复联网"; msgErr = false }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
            Divider(color = iOSSeparator, thickness = 1.dp)
            SettingRow(
                icon = Icons.Filled.Close,
                iconColor = iOSRed,
                title = "立即关机",
                desc = "设备将断电停止运行",
                tint = iOSRed,
                onClick = {
                    busy = true
                    scope.launch {
                        feats?.shutdownDevice()
                            ?.onSuccess { msg = "关机指令已下发，设备即将断电"; msgErr = false }
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
// 性能模式
// ═══════════════════════════════════════════
@Composable
fun PerformanceScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var on by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feats?.getPerformanceMode()?.onSuccess { on = it }
    }

    FeaturePage(
        title = "性能模式",
        subtitle = "性能与功耗偏好（PERFORMANCE_MODE_SETTING）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        FeatureCard(title = "性能模式", subtitle = "来自 performance_mode 设置项") {
            ToggleRow(
                label = "开启性能模式",
                desc = "优先保证网络吞吐与延迟，功耗略有上升",
                checked = on,
                enabled = !busy
            ) { value ->
                busy = true
                scope.launch {
                    feats?.setPerformanceMode(value)
                        ?.onSuccess { on = value; msg = "性能模式已${if (value) "开启" else "关闭"}"; msgErr = false }
                        ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                    busy = false
                }
            }
            Divider(color = iOSSeparator, thickness = 1.dp)
            Text(
                "提示：不同固件的性能取向不同，部分机型此开关控制频点/功率策略。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel
            )
        }
        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// 定时重启 / 定时任务
// ═══════════════════════════════════════════
private val TASK_ACTIONS = listOf(
    "REBOOT_DEVICE" to "定时重启",
    "SHUTDOWN_DEVICE" to "定时关机",
    "PERFORMANCE_MODE_SETTING" to "切换性能模式"
)

@Composable
fun TasksScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var tasks by remember { mutableStateOf<List<ScheduledTask>?>(null) }
    var hour by remember { mutableStateOf("02") }
    var minute by remember { mutableStateOf("00") }
    var repeat by remember { mutableStateOf(true) }
    var action by remember { mutableStateOf("REBOOT_DEVICE") }
    var perfFlag by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        feats?.listTasks()?.onSuccess { tasks = it }
    }
    LaunchedEffect(Unit) { refresh() }

    fun buildActionMap(): Map<String, String> = when (action) {
        "PERFORMANCE_MODE_SETTING" -> mapOf("goformId" to action, "performance_mode" to if (perfFlag) "1" else "0")
        else -> mapOf("goformId" to action)
    }

    FeaturePage(
        title = "定时任务",
        subtitle = "定时重启 / 开关动作",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        // 当前任务
        SectionTitle("当前任务")
        FeatureCard {
            if (tasks == null) {
                Text("读取中…", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            } else if (tasks!!.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("暂无定时任务", style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                    }
                }
            } else {
                tasks!!.forEachIndexed { idx, t ->
                    SettingRow(
                        icon = Icons.Filled.Refresh,
                        iconColor = iOSBlue,
                        title = "${t.time}",
                        desc = "${if (t.repeatDaily) "每天重复" else "单次执行"} · ${t.actionMap["goformId"] ?: ""}",
                        value = "删除",
                        tint = iOSRed,
                        onClick = {
                            scope.launch {
                                feats?.removeTask(t.id)
                                    ?.onSuccess { msg = "已删除任务 ${t.id}"; msgErr = false; refresh() }
                                    ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                            }
                        }
                    )
                    if (idx < tasks!!.size - 1) {
                        Divider(color = iOSSeparator, thickness = 1.dp)
                    }
                }
            }
        }

        // 新建任务
        SectionTitle("新建任务")
        FeatureCard {
            SettingRow(
                icon = Icons.Filled.Refresh,
                iconColor = iOSBlue,
                title = "执行时间",
                desc = "小时 : 分钟",
                value = "${hour}:${minute}",
                onClick = { /* TODO: 时间选择器 */ }
            )
            Divider(color = iOSSeparator, thickness = 1.dp)
            TASK_ACTIONS.forEach { (v, label) ->
                SelectRow(
                    label = label,
                    desc = v,
                    selected = action == v,
                    enabled = !busy,
                    onClick = { action = v }
                )
            }
            if (action == "PERFORMANCE_MODE_SETTING") {
                ToggleRow("执行后开启性能模式", "", perfFlag, !busy) { perfFlag = it }
            }
            Divider(color = iOSSeparator, thickness = 1.dp)
            ToggleRow("每天重复", "取消后仅执行一次", repeat, !busy) { repeat = it }
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "添加任务",
                loading = busy,
                onClick = {
                    val h = hour.trim().padStart(2, '0').takeLast(2).ifBlank { "02" }
                    val m = minute.trim().padStart(2, '0').takeLast(2).ifBlank { "00" }
                    busy = true
                    scope.launch {
                        feats?.addTask("$action-$h$m", "$h:$m", repeat, buildActionMap())
                            ?.onSuccess { msg = "任务已添加（每天 $h:$m）"; msgErr = false; refresh() }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }
        FeatureCard(title = "批量操作", subtitle = "") {
            OutlineActionButton(
                text = "清空全部任务",
                color = iOSRed,
                onClick = {
                    busy = true
                    scope.launch {
                        feats?.clearTasks()
                            ?.onSuccess { msg = "已清空全部定时任务"; msgErr = false; refresh() }
                            ?.onFailure { e -> msg = unsupportedOrMessage(e); msgErr = true }
                        busy = false
                    }
                }
            )
        }
        ResultMessage(msg, msgErr)
    }
}

// 分组标题
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = iOSSecondaryLabel,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

// 设置行（左彩色圆角图标+标题+描述，右值+箭头）
@Composable
internal fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color = iOSBlue,
    title: String,
    desc: String,
    value: String? = null,
    tint: Color = iOSLabel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 彩色圆角图标
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            Spacer(modifier = Modifier.height(1.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = iOSSecondaryLabel)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text("›", color = iOSSecondaryLabel.copy(alpha = 0.5f), fontSize = 20.sp)
    }
}