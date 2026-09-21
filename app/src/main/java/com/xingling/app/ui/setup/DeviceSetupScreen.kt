/*
 * 星灵 (XingLing) · 设备接入设置页
 *
 * 面向普通用户的一键接入：连上设备 WiFi 后仅需录入后台口令即可直接进入
 * 主界面，无需关心 IP 与端口。进入页面自动扫描同网段 UFI 服务（DeviceDiscoverer），
 * 命中即填充地址；发现失败才回退手动输入 IP。后台服务端口默认 2333，
 * 折叠在「高级设置」中，仅高级用户需要时修改。口令经 EncryptedSharedPreferences
 * 加密存储。
 *
 * 新手引导三步走：① 连接设备 WiFi → ② 自动扫描设备 → ③ 输入口令连接
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.setup

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.DeviceDiscoverer
import com.xingling.app.backend.DeviceStore
import com.xingling.app.backend.UfiToolsBackend
import com.xingling.app.ui.theme.iOSBackground
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSCardBackground
import com.xingling.app.ui.theme.iOSFill
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

@Composable
fun DeviceSetupScreen(
    store: DeviceStore,
    onConfigured: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(if (store.configured) store.host else "") }
    var portText by remember { mutableStateOf(store.port.toString()) }
    var token by remember { mutableStateOf("") }
    var zteToken by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(true) }
    var foundDuringScan by remember { mutableStateOf<String?>(null) }
    var manualMode by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    // 从设置页进入时，返回键回到上一级而非退出 App
    if (onBack != null) {
        BackHandler { onBack() }
    }

    // 统一扫描动作（初始进入、手动重扫、WiFi 网络变化共用）
    val scanAction = remember { AtomicReference<() -> Unit> {} }
    scanAction.set {
        scanning = true
        manualMode = false
        foundDuringScan = null
        message = null
        scope.launch {
            val found = runCatching {
                DeviceDiscoverer.discover(context, port = portText.toIntOrNull() ?: 2333)
            }.getOrNull()
            scanning = false
            if (found != null) {
                foundDuringScan = found
                host = found
                message = "已自动发现设备：$found\n请确认后输入口令点击连接。"
                messageIsError = false
            } else {
                manualMode = true
                message = "未自动发现设备，请确认已连接设备 WiFi（设备端需运行 UFI-TOOLS 后台），或手动输入设备 IP。"
                messageIsError = true
            }
        }
    }

    // 进入页面即自动扫描一次同网段 UFI 服务；命中即填充地址
    LaunchedEffect(Unit) {
        scanAction.get().invoke()
    }

    // 监听网络变化：连上设备 WiFi（热点常无互联网，DHCP 完成略慢）后自动补扫一次
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var lastAutoScan = 0L
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm?.getNetworkCapabilities(network) ?: return
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
                val now = System.currentTimeMillis()
                if (now - lastAutoScan < 3000) return
                lastAutoScan = now
                scope.launch {
                    delay(800) // 等待 DHCP / 路由就绪
                    if (!scanning && !busy && foundDuringScan == null) {
                        scanAction.get().invoke()
                    }
                }
            }
        }
        cm?.registerDefaultNetworkCallback(callback)
        onDispose { cm?.unregisterNetworkCallback(callback) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBackground)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ── 顶部导航栏（从设置页进入时显示返回按钮）──
        if (onBack != null) {
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
                Text(
                    "添加设备",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = iOSLabel
                )
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            if (onBack == null) {
                Text("添加设备", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = iOSLabel)
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                "三步接入你的随身 WiFi 设备",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = iOSLabel
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ════════ 新手引导三步走 ════════
            GuideStepCard(
                stepNumber = 1,
                icon = Icons.Filled.Settings,
                iconColor = iOSBlue,
                title = "连接设备 WiFi",
                desc = "打开手机 WiFi 设置，连接到随身 WiFi 设备发出的热点（通常以设备型号命名，如 U30_Air_XXXX）",
                actionLabel = "打开 WiFi 设置",
                onAction = {
                    context.startActivity(Intent(AndroidSettings.ACTION_WIFI_SETTINGS))
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            GuideStepCard(
                stepNumber = 2,
                icon = Icons.Filled.Refresh,
                iconColor = iOSGreen,
                title = "自动扫描设备",
                desc = "连接 WiFi 后，App 会自动扫描局域网内的设备并填入地址，无需手动输入 IP",
                actionLabel = null,
                onAction = null
            )
            Spacer(modifier = Modifier.height(10.dp))
            GuideStepCard(
                stepNumber = 3,
                icon = Icons.Filled.Lock,
                iconColor = iOSOrange,
                title = "输入后台口令",
                desc = "输入设备后台管理口令（默认 admin，未修改过可直接留空），点击连接即可",
                actionLabel = null,
                onAction = null
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ════════ 连接表单 ════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // ── 自动发现状态 ──
                    if (scanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = iOSBlue, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("正在自动扫描局域网内的星灵设备…", fontSize = 13.sp, color = iOSSecondaryLabel)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (foundDuringScan != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = iOSGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("已发现：", fontSize = 13.sp, color = iOSSecondaryLabel)
                            Text(foundDuringScan.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = iOSBlue)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ── 手动 IP（仅自动发现失败时回退显示）──
                    if (manualMode) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("设备 IP") },
                            placeholder = { Text("192.168.0.1") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Text),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = iOSBlue,
                                cursorColor = iOSBlue
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ── 后台口令 ──
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("后台口令") },
                        placeholder = { Text("默认口令 admin，未修改时留空亦可") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = iOSBlue,
                            cursorColor = iOSBlue
                        )
                    )

                    // ── 高级设置（端口对普通用户透明，折叠收纳）──
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                    ) {
                        Text(
                            if (showAdvanced) "收起高级设置" else "高级设置（端口等）",
                            fontSize = 13.sp,
                            color = iOSSecondaryLabel
                        )
                    }
                    if (showAdvanced) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("后台服务端口") },
                            placeholder = { Text("2333") },
                            supportingText = { Text("默认 2333，一般无需修改", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = iOSBlue,
                                cursorColor = iOSBlue,
                                focusedSupportingTextColor = iOSSecondaryLabel,
                                unfocusedSupportingTextColor = iOSSecondaryLabel
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // ── ZTE 专用口令（与后台口令不同时才需单独填写）──
                        OutlinedTextField(
                            value = zteToken,
                            onValueChange = { zteToken = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ZTE 专用口令（选填）") },
                            placeholder = { Text("留空则复用后台口令") },
                            supportingText = { Text("用于中兴官方后台（ZTE）登录；与后台口令不同时才需填写", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = iOSBlue,
                                cursorColor = iOSBlue,
                                focusedSupportingTextColor = iOSSecondaryLabel,
                                unfocusedSupportingTextColor = iOSSecondaryLabel
                            )
                        )
                    }

                    if (message != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            message.orEmpty(),
                            fontSize = 13.sp,
                            color = if (messageIsError) iOSRed else iOSBlue,
                            textAlign = TextAlign.Start
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val ip = host.trim()
                            if (ip.isEmpty()) {
                                message = "尚未获取到设备 IP：请点击「重新扫描」，或输入设备 IP 后连接。"
                                messageIsError = true
                                return@Button
                            }
                            val port = portText.toIntOrNull() ?: 2333
                            busy = true
                            message = null
                            val pass = token.ifBlank { "admin" }
                            val ztePass = zteToken.trim()
                            scope.launch {
                                val result = runCatching {
                                    UfiToolsBackend(ip, port, pass, ztePass).connect()
                                }
                                busy = false
                                result.onSuccess {
                                    // 保存配置并进入主界面
                                    store.host = ip
                                    store.port = port
                                    store.token = token
                                    store.zteToken = ztePass
                                    store.configured = true
                                    onConfigured()
                                }.onFailure { e ->
                                    message = "连接失败：${e.message ?: "未知错误"}\n请确认已连接设备 WiFi、口令正确。"
                                    messageIsError = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = iOSBlue),
                        enabled = !busy
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Text("连接中…", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                        } else {
                            Text("连接设备", fontSize = 16.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { scanAction.get().invoke() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !busy
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = iOSBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (scanning) "扫描中…（约数秒）" else "重新扫描局域网设备",
                            fontSize = 14.sp,
                            color = iOSBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "提示：设备默认后台端口 2333，普通用户无需修改；端口在「高级设置」中可调。",
                        fontSize = 12.sp,
                        color = iOSSecondaryLabel
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 新手引导步骤卡片
 */
@Composable
private fun GuideStepCard(
    stepNumber: Int,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    desc: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = iOSCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 步骤序号圆圈
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "步骤 $stepNumber",
                        fontSize = 11.sp,
                        color = iconColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iOSLabel
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    desc,
                    fontSize = 12.sp,
                    color = iOSSecondaryLabel,
                    lineHeight = 17.sp
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(iconColor.copy(alpha = 0.1f))
                            .clickable { onAction() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            actionLabel,
                            fontSize = 12.sp,
                            color = iconColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
