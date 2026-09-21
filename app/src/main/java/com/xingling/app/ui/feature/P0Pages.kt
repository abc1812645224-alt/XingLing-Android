/*
 * 星灵 (XingLing) · P0 组 5 项功能 UI 页面
 *
 *  ① ThemeCustomScreen     主题定制（get_theme / set_theme）
 *  ② FileManagerScreen     文件管理（upload_img / delete_img / delete_all_uploads_data / ls）
 *  ③ PowerForwardScreen    电量信息转发开关（power_status_forward_enabled）
 *  ④ PushTestScreen        主动推送测试（do_forward_msg，自定义 address/body）
 *  ⑤ CustomPluginScreen    自定义插件源（/api/proxy/--<url> 拉取第三方仓库）
 *
 * UI 统一走 FeaturePage / FeatureCard（iOS 玻璃卡片风格），
 * 经 backend?.features 异步调用，后台不支持时展示 UnsupportedCard。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.backend.FeatureUnsupported
import com.xingling.app.backend.PluginStore
import com.xingling.app.backend.ThemeConfig
import com.xingling.app.backend.UploadedFile
import com.xingling.app.backend.formatBytes
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSGreen
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSRed
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ═══════════════════════════════════════════
// ① 主题定制
// ═══════════════════════════════════════════
@Composable
fun ThemeCustomScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var config by remember { mutableStateOf(ThemeConfig()) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadTheme() {
        loading = true
        unsupported = false
        scope.launch {
            feats?.getTheme()
                ?.onSuccess { config = it; msg = ""; msgErr = false }
                ?.onFailure { e ->
                    unsupported = e is FeatureUnsupported
                    msg = if (unsupported) "" else unsupportedOrMessage(e)
                    msgErr = true
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadTheme() }

    FeaturePage(
        title = "主题定制",
        subtitle = "UFI-TOOLS Web 后台主题（/api/get_theme · set_theme）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        if (unsupported) {
            UnsupportedCard("当前设备后台不支持主题定制接口，请升级 UFI-TOOLS 后台。")
            return@FeaturePage
        }

        FeatureCard(
            title = "外观参数",
            subtitle = "色相 / 饱和度 / 亮度 / 不透明度均为百分比字符串",
            trailing = {
                OutlineActionButton(
                    text = "刷新",
                    modifier = Modifier.width(88.dp),
                    loading = loading,
                    onClick = { loadTheme() }
                )
            }
        ) {
            ToggleRow("启用自定义背景", "是否显示自定义背景图", config.backgroundEnabled, !saving) { v ->
                config = config.copy(backgroundEnabled = v)
            }
            Spacer(modifier = Modifier.height(8.dp))
            FeatureTextField(
                value = config.backgroundUrl,
                onValueChange = { config = config.copy(backgroundUrl = it) },
                label = "背景图地址",
                placeholder = "/api/uploads/xxx.png 或外链 URL"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureTextField(
                    value = config.themeColor,
                    onValueChange = { config = config.copy(themeColor = it) },
                    label = "主色色相",
                    placeholder = "201",
                    modifier = Modifier.weight(1f)
                )
                FeatureTextField(
                    value = config.colorPer,
                    onValueChange = { config = config.copy(colorPer = it) },
                    label = "主色百分比",
                    placeholder = "67",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureTextField(
                    value = config.saturationPer,
                    onValueChange = { config = config.copy(saturationPer = it) },
                    label = "饱和度 %",
                    placeholder = "100",
                    modifier = Modifier.weight(1f)
                )
                FeatureTextField(
                    value = config.brightPer,
                    onValueChange = { config = config.copy(brightPer = it) },
                    label = "亮度 %",
                    placeholder = "21",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureTextField(
                    value = config.opacityPer,
                    onValueChange = { config = config.copy(opacityPer = it) },
                    label = "背景不透明度 %",
                    placeholder = "21",
                    modifier = Modifier.weight(1f)
                )
                FeatureTextField(
                    value = config.textColorPer,
                    onValueChange = { config = config.copy(textColorPer = it) },
                    label = "文字颜色浓度 %",
                    placeholder = "100",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            FeatureTextField(
                value = config.textColor,
                onValueChange = { config = config.copy(textColor = it) },
                label = "文字颜色",
                placeholder = "rgba(255, 255, 255, 1)"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow("背景模糊", "开启背景毛玻璃模糊", config.blurSwitch, !saving) { v ->
                config = config.copy(blurSwitch = v)
            }
            ToggleRow("遮罩层", "开启内容遮罩层", config.overlaySwitch, !saving) { v ->
                config = config.copy(overlaySwitch = v)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                text = "保存主题",
                loading = saving,
                modifier = Modifier.weight(1f),
                onClick = {
                    saving = true
                    scope.launch {
                        feats?.setTheme(config)
                            ?.onSuccess { msg = "主题配置已保存，刷新 Web 后台查看效果"; msgErr = false }
                            ?.onFailure { e ->
                                unsupported = e is FeatureUnsupported
                                msg = if (unsupported) "" else unsupportedOrMessage(e)
                                msgErr = true
                            }
                        saving = false
                    }
                }
            )
            OutlineActionButton(
                text = "重置默认",
                modifier = Modifier.weight(1f),
                loading = saving,
                onClick = {
                    saving = true
                    scope.launch {
                        feats?.setTheme(ThemeConfig())
                            ?.onSuccess {
                                config = ThemeConfig()
                                msg = "已重置为默认主题"
                                msgErr = false
                            }
                            ?.onFailure { e ->
                                unsupported = e is FeatureUnsupported
                                msg = if (unsupported) "" else unsupportedOrMessage(e)
                                msgErr = true
                            }
                        saving = false
                    }
                }
            )
        }

        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// ② 文件管理
// ═══════════════════════════════════════════
@Composable
fun FileManagerScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }
    var listBusy by remember { mutableStateOf(false) }
    var uploadPath by remember { mutableStateOf("") }
    var uploadBusy by remember { mutableStateOf(false) }
    var downloadBusyName by remember { mutableStateOf("") }
    var clearBusy by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var savedPath by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadList() {
        listBusy = true
        unsupported = false
        scope.launch {
            feats?.listUploads()
                ?.onSuccess { files = it; msg = ""; msgErr = false }
                ?.onFailure { e ->
                    unsupported = e is FeatureUnsupported
                    msg = if (unsupported) "" else unsupportedOrMessage(e)
                    msgErr = true
                }
            listBusy = false
        }
    }

    LaunchedEffect(Unit) { loadList() }

    FeaturePage(
        title = "文件管理",
        subtitle = "设备 uploads 目录（/api/upload_img · delete_img）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        if (unsupported) {
            UnsupportedCard("当前设备后台不支持文件管理接口，请升级 UFI-TOOLS 后台。")
            return@FeaturePage
        }

        FeatureCard(
            title = "上传文件",
            subtitle = "输入本机文件路径，上传到设备 uploads 目录",
            trailing = {
                OutlineActionButton(
                    text = "刷新",
                    modifier = Modifier.width(88.dp),
                    loading = listBusy,
                    onClick = { loadList() }
                )
            }
        ) {
            FeatureTextField(
                value = uploadPath,
                onValueChange = { uploadPath = it },
                label = "本机文件路径",
                placeholder = "/sdcard/Download/test.png"
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActionButton(
                text = "上传文件",
                loading = uploadBusy,
                onClick = {
                    val path = uploadPath.trim()
                    if (path.isEmpty()) {
                        msg = "请输入本机文件路径"
                        msgErr = true
                    } else {
                        uploadBusy = true
                        scope.launch {
                            val bytes = withContext(Dispatchers.IO) {
                                runCatching { File(path).readBytes() }
                            }
                            bytes.fold(
                                    onSuccess = { data ->
                                        val name = path.substringAfterLast('/')
                                        feats?.uploadFile(name, data)
                                            ?.onSuccess { url ->
                                                msg = "上传成功：$url"
                                                msgErr = false
                                                loadList()
                                            }
                                            ?.onFailure { e ->
                                                unsupported = e is FeatureUnsupported
                                                msg = if (unsupported) "" else "上传失败：${unsupportedOrMessage(e)}"
                                                msgErr = true
                                            }
                                    },
                                    onFailure = { e ->
                                        msg = "读取本地文件失败：${e.message ?: e}"
                                        msgErr = true
                                    }
                                )
                            uploadBusy = false
                        }
                    }
                }
            )
        }

        FeatureCard(
            title = "已上传文件（${files.size}）",
            subtitle = "设备 uploads 目录，可下载到本机或删除"
        ) {
            if (files.isEmpty()) {
                Text(
                    "暂无上传文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = iOSSecondaryLabel
                )
            } else {
                files.forEachIndexed { i, f ->
                    if (i > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = iOSSeparator, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        f.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iOSLabel
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${formatBytes(f.size)}  ·  ${f.modified}",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineActionButton(
                            text = "下载到本机",
                            modifier = Modifier.weight(1f),
                            loading = downloadBusyName == f.name,
                            onClick = {
                                downloadBusyName = f.name
                                scope.launch {
                                    val dir = context.getExternalFilesDir(null) ?: context.filesDir
                                    val dest = File(dir, f.name)
                                    feats?.downloadUpload(f.name, dest)
                                        ?.onSuccess { bytes ->
                                            savedPath = dest.absolutePath
                                            msg = "已下载 $bytes 字节：$savedPath"
                                            msgErr = false
                                        }
                                        ?.onFailure { e ->
                                            unsupported = e is FeatureUnsupported
                                            msg = if (unsupported) "" else "下载失败：${unsupportedOrMessage(e)}"
                                            msgErr = true
                                        }
                                    downloadBusyName = ""
                                }
                            }
                        )
                        OutlineActionButton(
                            text = "删除",
                            color = iOSRed,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    feats?.deleteUploadedFile(f.name)
                                        ?.onSuccess { msg = "已删除 ${f.name}"; msgErr = false; loadList() }
                                        ?.onFailure { e ->
                                            unsupported = e is FeatureUnsupported
                                            msg = if (unsupported) "" else "删除失败：${unsupportedOrMessage(e)}"
                                            msgErr = true
                                        }
                                }
                            }
                        )
                    }
                }
            }
            if (files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ActionButton(
                    text = "清空全部上传文件",
                    color = iOSRed,
                    loading = clearBusy,
                    onClick = {
                        clearBusy = true
                        scope.launch {
                            feats?.clearAllUploads()
                                ?.onSuccess { map ->
                                    val ok = map.count { it.value }
                                    msg = "已清空 uploads 目录，成功删除 $ok 个文件"
                                    msgErr = false
                                    loadList()
                                }
                                ?.onFailure { e ->
                                    unsupported = e is FeatureUnsupported
                                    msg = if (unsupported) "" else unsupportedOrMessage(e)
                                    msgErr = true
                                }
                            clearBusy = false
                        }
                    }
                )
            }
        }

        if (savedPath.isNotBlank()) {
            Text(
                "已保存到：$savedPath",
                fontSize = 12.sp,
                color = iOSGreen
            )
        }

        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// ③ 电量信息转发开关
// ═══════════════════════════════════════════
@Composable
fun PowerForwardScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var enabled by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        busy = true
        unsupported = false
        scope.launch {
            feats?.getPowerForwardEnabled()
                ?.onSuccess { enabled = it; loaded = true; msg = ""; msgErr = false }
                ?.onFailure { e ->
                    unsupported = e is FeatureUnsupported
                    msg = if (unsupported) "" else unsupportedOrMessage(e)
                    msgErr = true
                }
            busy = false
        }
    }

    LaunchedEffect(Unit) { load() }

    FeaturePage(
        title = "电量信息转发",
        subtitle = "电量变化时按当前转发渠道推送（/api/power_status_forward_enabled）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        if (unsupported) {
            UnsupportedCard("当前设备后台不支持电量信息转发开关，请升级 UFI-TOOLS 后台。")
            return@FeaturePage
        }

        FeatureCard(
            title = "电量转发开关",
            subtitle = "GET / POST /api/power_status_forward_enabled"
        ) {
            ToggleRow(
                label = "启用电量信息转发",
                desc = "开启后设备电量/充电状态变化时，会通过已配置的转发渠道（邮件/钉钉/curl）推送通知",
                checked = enabled,
                enabled = !busy && loaded
            ) { on ->
                busy = true
                scope.launch {
                    feats?.setPowerForwardEnabled(on)
                        ?.onSuccess {
                            enabled = on
                            msg = "电量转发已${if (on) "启用" else "关闭"}"
                            msgErr = false
                        }
                        ?.onFailure { e ->
                            unsupported = e is FeatureUnsupported
                            msg = if (unsupported) "" else unsupportedOrMessage(e)
                            msgErr = true
                        }
                    busy = false
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "说明：该开关与短信转发总开关相互独立。需先在「短信转发」页配置好转发渠道（SMTP/钉钉/curl），" +
                    "电量状态才会实际推送出去。",
                style = MaterialTheme.typography.bodySmall,
                color = iOSSecondaryLabel,
                lineHeight = 18.sp
            )
        }

        ResultMessage(msg, msgErr)
    }
}

// ═══════════════════════════════════════════
// ④ 主动推送测试
// ═══════════════════════════════════════════
@Composable
fun PushTestScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var address by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var isSms by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var resultErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FeaturePage(
        title = "主动推送测试",
        subtitle = "按当前转发渠道主动推送一条测试消息（/api/do_forward_msg）",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        if (unsupported) {
            UnsupportedCard("当前设备后台不支持主动推送测试接口，请升级 UFI-TOOLS 后台。")
            return@FeaturePage
        }

        FeatureCard(
            title = "推送参数",
            subtitle = "address=来源号码，body=消息内容，is_sms=是否标记为短信"
        ) {
            FeatureTextField(
                value = address,
                onValueChange = { address = it },
                label = "来源号码（address）",
                placeholder = "10086"
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureTextField(
                value = body,
                onValueChange = { body = it },
                label = "消息内容（body）",
                placeholder = "这是一条星灵主动推送测试消息",
                singleLine = false
            )
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow(
                label = "标记为短信（is_sms）",
                desc = "关闭后按非短信消息推送",
                checked = isSms,
                enabled = !sending
            ) { isSms = it }
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "发送推送测试",
                loading = sending,
                onClick = {
                    sending = true
                    resultText = ""
                    scope.launch {
                        feats?.sendForwardTest(address.trim(), body.trim(), isSms)
                            ?.onSuccess {
                                resultText = "推送指令已下发，请到转发渠道（邮件/钉钉/curl）确认是否收到"
                                resultErr = false
                            }
                            ?.onFailure { e ->
                                unsupported = e is FeatureUnsupported
                                resultText = if (unsupported) "" else unsupportedOrMessage(e)
                                resultErr = true
                            }
                        sending = false
                    }
                }
            )
        }

        if (resultText.isNotBlank()) {
            FeatureCard(title = "发送结果") {
                ResultMessage(resultText, resultErr)
            }
        }
    }
}

// ═══════════════════════════════════════════
// ⑤ 自定义插件源
// ═══════════════════════════════════════════
@Composable
fun CustomPluginScreen(backend: DeviceBackend?, onBack: () -> Unit, onAddDevice: () -> Unit) {
    val feats = backend?.features
    var storeUrl by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var store by remember { mutableStateOf<PluginStore?>(null) }
    var unsupported by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FeaturePage(
        title = "自定义插件源",
        subtitle = "通过 /api/proxy/-- 拉取第三方插件仓库 JSON",
        backend = backend, onBack = onBack, onAddDevice = onAddDevice
    ) {
        if (unsupported) {
            UnsupportedCard("当前设备后台不支持自定义插件源接口，请升级 UFI-TOOLS 后台。")
            return@FeaturePage
        }

        FeatureCard(
            title = "插件仓库地址",
            subtitle = "需返回与 /api/plugins_store 同结构的 JSON（download_url + res.data.content）"
        ) {
            FeatureTextField(
                value = storeUrl,
                onValueChange = { storeUrl = it },
                label = "仓库 JSON URL",
                placeholder = "https://example.com/plugins.json"
            )
            Spacer(modifier = Modifier.height(12.dp))
            ActionButton(
                text = "拉取插件列表",
                loading = fetching,
                onClick = {
                    fetching = true
                    store = null
                    msg = ""
                    msgErr = false
                    scope.launch {
                        feats?.fetchCustomPluginStore(storeUrl.trim())
                            ?.onSuccess { s ->
                                store = s
                                if (s == null) {
                                    msg = "仓库返回为空或未解析到插件列表"
                                    msgErr = true
                                } else {
                                    msg = "成功拉取 ${s.plugins.size} 个插件"
                                    msgErr = false
                                }
                            }
                            ?.onFailure { e ->
                                unsupported = e is FeatureUnsupported
                                msg = if (unsupported) "" else unsupportedOrMessage(e)
                                msgErr = true
                            }
                        fetching = false
                    }
                }
            )
        }

        val s = store
        if (s != null) {
            FeatureCard(
                title = "插件列表（${s.plugins.size}）",
                subtitle = "download_url 为插件下载根地址"
            ) {
                if (s.downloadUrl.isNotBlank()) {
                    Text(
                        "下载地址：",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                    Text(
                        s.downloadUrl,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = iOSBlue,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (s.plugins.isEmpty()) {
                    Text(
                        "仓库中暂无插件",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSSecondaryLabel
                    )
                } else {
                    s.plugins.forEachIndexed { i, p ->
                        if (i > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = iOSSeparator, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            p.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = iOSLabel
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val sizeText = if (p.size > 0) formatBytes(p.size) else "--"
                        Text(
                            "$sizeText  ·  修改时间 ${p.modified.ifBlank { "--" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = iOSSecondaryLabel
                        )
                        if (p.md5.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "MD5：${p.md5}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = iOSSecondaryLabel
                            )
                        }
                        if (s.downloadUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "下载：${s.downloadUrl}/${p.name}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = iOSBlue,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        ResultMessage(msg, msgErr)
    }
}
