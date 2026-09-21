/*
 * 星灵 (XingLing) · 高级功能页共享基础组件
 *
 * 统一的 iOS 玻璃卡片风格 UI 构件：页面壳 / 卡片 / 开关行 / 按钮 /
 * 输入框 / 确认框 / 加载与结果态 / 后台不支持空态。
 * 所有样式均取自 ui/theme 的 iOS* 配色与 GlassCard（RoundedCornerShape(20.dp)）。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.DeviceFeatures
import com.xingling.app.backend.FeatureUnsupported
import com.xingling.app.ui.theme.GlassCard
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSButton
import com.xingling.app.ui.theme.iOSCardBackground
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSOutlineButton
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import com.xingling.app.ui.theme.iOSGroupHeader

// ═══════════════════════════════════════════
// 页面壳
// ═══════════════════════════════════════════
@Composable
fun FeaturePage(
    title: String,
    subtitle: String? = null,
    backend: DeviceBackend?,
    onBack: () -> Unit,
    onAddDevice: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (backend == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(iOSBackground)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("尚未配置设备", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = iOSLabel)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "设备功能需要先连接星灵后台才能使用。\n请先前往设备设置页录入设备地址与后台口令。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = iOSSecondaryLabel
            )
            Spacer(modifier = Modifier.height(20.dp))
            iOSButton(onClick = onAddDevice) {
                Text("去设备设置页", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
    ) {
        // 顶部导航栏
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
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = iOSLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
            content()
        }
    }
}

// ═══════════════════════════════════════════
// 卡片容器（iOS 玻璃卡片）
// ═══════════════════════════════════════════
@Composable
fun FeatureCard(
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = iOSLabel)
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = iOSSecondaryLabel
                            )
                        }
                    }
                    trailing?.invoke()
                }
                if (title != null && subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = iOSSeparator, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(14.dp))
            }
            content()
        }
    }
}

// ═══════════════════════════════════════════
// 开关行（iOS 风格右侧 Switch）
// ═══════════════════════════════════════════
@Composable
fun ToggleRow(
    label: String,
    desc: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = iOSLabel)
            if (desc != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iOSGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = iOSFill,
                uncheckedBorderColor = iOSSeparator
            )
        )
    }
}

// ═══════════════════════════════════════════
// 选择行（单选：单选组里使用）
// ═══════════════════════════════════════════
@Composable
fun SelectRow(
    label: String,
    desc: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) iOSLabel else iOSSecondaryLabel)
            if (desc != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (selected) {
            Text("✓", color = iOSBlue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════
// 按钮
// ═══════════════════════════════════════════
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    color: Color = iOSBlue,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    iOSButton(
        onClick = onClick,
        modifier = modifier,
        backgroundColor = color
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun OutlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    color: Color = iOSBlue,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    iOSOutlineButton(onClick = onClick, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = color, strokeWidth = 2.dp)
        } else {
            Text(
                text,
                color = if (enabled) color else iOSSecondaryLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

// ═══════════════════════════════════════════
// 输入框（iOS 圆角玻璃样式）
// ═══════════════════════════════════════════
@Composable
fun FeatureTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = iOSSecondaryLabel) },
        placeholder = {
            if (placeholder.isNotEmpty()) Text(placeholder, color = iOSSecondaryLabel.copy(alpha = 0.6f))
        },
        singleLine = singleLine,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = iOSBlue,
            unfocusedBorderColor = iOSSeparator,
            focusedLabelColor = iOSBlue,
            unfocusedLabelColor = iOSSecondaryLabel,
            focusedTextColor = iOSLabel,
            unfocusedTextColor = iOSLabel,
            cursorColor = iOSBlue,
            focusedContainerColor = iOSFill.copy(alpha = 0.6f),
            unfocusedContainerColor = iOSFill.copy(alpha = 0.6f)
        )
    )
}

// ═══════════════════════════════════════════
// 状态 / 结果展示
// ═══════════════════════════════════════════
@Composable
fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ResultMessage(text: String?, isError: Boolean = false) {
    if (text.isNullOrBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) iOSRed else iOSGreen,
        lineHeight = 18.sp
    )
}

// ═══════════════════════════════════════════
// 后台不支持空态（后台不支持此功能时展示）
// ═══════════════════════════════════════════
@Composable
fun UnsupportedCard(message: String = "当前设备后台（UFI-TOOLS）不支持此功能，请升级后台或改用支持该能力的固件。") {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("功能不可用", style = MaterialTheme.typography.titleMedium, color = iOSOrange)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel, lineHeight = 19.sp)
        }
    }
}

// ═══════════════════════════════════════════
// 命令输出框（shell 等）
// ═══════════════════════════════════════════
@Composable
fun CommandOutput(text: String, height: androidx.compose.ui.unit.Dp = 220.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101014))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text.ifBlank { "(无输出)" },
            color = Color(0xFF8AF28A),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

// ═══════════════════════════════════════════
// 通用异步结果推导
// ═══════════════════════════════════════════
/** 判断异常是否为“后台不支持” */
fun Throwable.isUnsupported(): Boolean = this is FeatureUnsupported

fun unsupportedOrMessage(e: Throwable): String =
    if (e is FeatureUnsupported) "当前设备后台不支持此功能。" else (e.message ?: "操作失败，请重试")

// ═══════════════════════════════════════════
// 分组标题（灰色小字）
// ═══════════════════════════════════════════
@Composable
fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = iOSGroupHeader,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
    )
}

// ═══════════════════════════════════════════
// 带彩色方形图标的列表项（参考图控制页风格）
// ═══════════════════════════════════════════
@Composable
fun IconRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 彩色方形图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = iOSLabel, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }
        trailing?.invoke()
    }
}

// ═══════════════════════════════════════════
// 带彩色方形图标的开关行（参考图控制页风格）
// ═══════════════════════════════════════════
@Composable
fun IconToggleRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = iOSLabel, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iOSGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = iOSFill,
                uncheckedBorderColor = iOSSeparator
            )
        )
    }
}
// ═══════════════════════════════════════════
// 右侧箭头
// ═══════════════════════════════════════════
@Composable
fun ChevronRight() {
    Icon(
            Icons.Filled.ArrowBack,
            contentDescription = null,
            tint = iOSSecondaryLabel.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp).rotate(180f)
        )
}