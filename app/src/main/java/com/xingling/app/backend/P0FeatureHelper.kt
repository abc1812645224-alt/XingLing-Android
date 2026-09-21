/*
 * 星灵 (XingLing) · P0 组 5 项功能后端逻辑
 *
 *  P0FeatureHelper 封装主题定制 / 文件管理 / 电量转发开关 / 主动推送测试 /
 *  自定义插件源 五项 UFI-TOOLS 后台调用，全部经 api（HttpURLConnection）
 *  与 org.json 完成，零第三方依赖。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import org.json.JSONObject

/**
 * P0 组 5 项功能后端实现。
 *
 * @param api      已鉴权的 UFI-TOOLS JSON API 客户端
 * @param goform   官方后台 goform 反代（本批功能未直接使用，保留以与 UfiToolsFeatureApi 构造一致）
 * @param host     设备主机地址
 */
class P0FeatureHelper(
    private val api: UfiToolsApi,
    private val goform: UfiToolsGoform,
    private val host: String
) {

    /** 设备 uploads 目录 */
    private val uploadsDir: String = "/data/data/com.minikano.f50_sms/files/uploads"

    // ═══════════════════ 1. 主题定制 ═══════════════════

    /** 读取 UFI-TOOLS Web 后台主题配置（GET /api/get_theme，免认证） */
    suspend fun getTheme(): Result<ThemeConfig> = runCatching {
        val text = api.getNoAuth("/api/get_theme")
        val j = JSONObject(text)
        ThemeConfig(
            backgroundEnabled = j.optString("backgroundEnabled", "false") == "true",
            backgroundUrl = j.optString("backgroundUrl", ""),
            textColor = j.optString("textColor", "rgba(255, 255, 255, 1)"),
            textColorPer = j.optString("textColorPer", "100"),
            themeColor = j.optString("themeColor", "201"),
            colorPer = j.optString("colorPer", "67"),
            saturationPer = j.optString("saturationPer", "100"),
            brightPer = j.optString("brightPer", "21"),
            opacityPer = j.optString("opacityPer", "21"),
            blurSwitch = j.optString("blurSwitch", "true") == "true",
            overlaySwitch = j.optString("overlaySwitch", "true") == "true"
        )
    }

    /** 保存主题配置（POST /api/set_theme，布尔字段转 "true"/"false" 字符串） */
    suspend fun setTheme(config: ThemeConfig): Result<Unit> = runCatching {
        val payload = JSONObject()
            .put("backgroundEnabled", if (config.backgroundEnabled) "true" else "false")
            .put("backgroundUrl", config.backgroundUrl)
            .put("textColor", config.textColor)
            .put("textColorPer", config.textColorPer)
            .put("themeColor", config.themeColor)
            .put("colorPer", config.colorPer)
            .put("saturationPer", config.saturationPer)
            .put("brightPer", config.brightPer)
            .put("opacityPer", config.opacityPer)
            .put("blurSwitch", if (config.blurSwitch) "true" else "false")
            .put("overlaySwitch", if (config.overlaySwitch) "true" else "false")
        api.post("/api/set_theme", payload.toString())
        Unit
    }

    // ═══════════════════ 2. 文件管理 ═══════════════════

    /** 上传文件到设备 uploads 目录（multipart/form-data，POST /api/upload_img），返回响应 url */
    suspend fun uploadFile(fileName: String, fileBytes: ByteArray): Result<String> = runCatching {
        val name = fileName.trim().substringAfterLast('/')
        require(name.isNotBlank()) { "文件名不能为空" }
        val text = api.uploadMultipart("/api/upload_img", name, fileBytes, "file", null, 60000)
        val url = try {
            JSONObject(text).optString("url", "")
        } catch (_: Exception) {
            ""
        }
        url.ifBlank { text }
    }

    /** 列出 uploads 目录文件（root_shell ls -l --time-style=long-iso 解析） */
    suspend fun listUploads(): Result<List<UploadedFile>> = runCatching {
        val out = rootShell("ls -l --time-style=long-iso $uploadsDir 2>/dev/null || echo 'EMPTY'")
            .trim()
        if (out.isBlank() || out == "EMPTY" || out.startsWith("ls:")) return@runCatching emptyList()
        out.lines().mapNotNull { line ->
            // -rw-rw---- 1 u0_aXXX u0_aXXX 12345 2026-01-01 12:00 filename.ext
            val parts = line.split(Regex("\\s+"), limit = 7)
            if (parts.size < 7) return@mapNotNull null
            val size = parts.getOrNull(4)?.toLongOrNull() ?: 0
            val date = parts.getOrNull(5) ?: ""
            val rest = parts.getOrNull(6) ?: ""
            val time = rest.substringBeforeLast(' ', "")
            val name = rest.substringAfterLast(' ', "")
            if (name.isBlank() || name == "." || name == "..") return@mapNotNull null
            UploadedFile(name = name, size = size, modified = listOf(date, time).filter { it.isNotBlank() }.joinToString(" "))
        }
    }

    /** 删除单个上传文件（POST /api/delete_img） */
    suspend fun deleteUploadedFile(fileName: String): Result<Unit> = runCatching {
        val name = fileName.trim().substringAfterLast('/')
        require(name.isNotBlank()) { "文件名不能为空" }
        api.post("/api/delete_img", JSONObject().put("file_name", name).toString())
        Unit
    }

    /** 清空 uploads 目录（POST /api/delete_all_uploads_data），返回每个文件的删除结果 */
    suspend fun clearAllUploads(): Result<Map<String, Boolean>> = runCatching {
        val text = api.post("/api/delete_all_uploads_data", "{}")
        val j = JSONObject(text)
        val deleted = j.optJSONObject("deleted_list")
        val result = mutableMapOf<String, Boolean>()
        if (deleted != null) {
            for (k in deleted.keys()) {
                result[k] = deleted.optBoolean(k, false)
            }
        }
        result
    }

    // ═══════════════════ 3. 电量信息转发 ═══════════════════

    /** 读取电量信息转发开关（GET /api/power_status_forward_enabled，解析 {"enabled":"0"/"1"}） */
    suspend fun getPowerForwardEnabled(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/power_status_forward_enabled")).optString("enabled", "0") == "1"
    }

    /** 设置电量信息转发开关（GET /api/power_status_forward_enabled?enable=0/1） */
    suspend fun setPowerForwardEnabled(enabled: Boolean): Result<Unit> = runCatching {
        api.get("/api/power_status_forward_enabled?enable=${if (enabled) 1 else 0}")
        Unit
    }

    // ═══════════════════ 4. 主动推送测试 ═══════════════════

    /** 主动推送一条消息（POST /api/do_forward_msg，自定义 address/body/is_sms） */
    suspend fun sendForwardTest(address: String, body: String, isSms: Boolean): Result<Unit> = runCatching {
        require(address.isNotBlank()) { "请输入来源号码" }
        require(body.isNotBlank()) { "请输入消息内容" }
        val payload = JSONObject()
            .put("address", address)
            .put("body", body)
            .put("is_sms", isSms)
            .put("timestamp", System.currentTimeMillis())
        api.post("/api/do_forward_msg", payload.toString())
        Unit
    }

    // ═══════════════════ 5. 自定义插件源 ═══════════════════

    /** 拉取自定义插件源（/api/proxy/--<url>，签名用原始完整路径），解析为 PluginStore */
    suspend fun fetchCustomPluginStore(storeUrl: String): Result<PluginStore?> = runCatching {
        val target = storeUrl.trim()
        require(target.startsWith("http://") || target.startsWith("https://")) {
            "插件源地址必须以 http:// 或 https:// 开头"
        }
        val path = "/api/proxy/--$target"
        val text = api.requestEx("GET", path, signPath = path, timeout = 30000).body
        val j = JSONObject(text)
        val url = j.optString("download_url", "")
        val content = j.optJSONObject("res")?.optJSONObject("data")?.optJSONArray("content")
        val list = mutableListOf<PluginItem>()
        if (content != null) {
            for (i in 0 until content.length()) {
                val o = content.getJSONObject(i)
                list.add(
                    PluginItem(
                        name = o.optString("name", ""),
                        modified = o.optString("modified", ""),
                        size = o.optLong("size", 0),
                        md5 = o.optJSONObject("hash_info")?.optString("md5") ?: ""
                    )
                )
            }
        }
        if (url.isBlank() && list.isEmpty()) null else PluginStore(url, list)
    }

    // ── 内部 rootShell 辅助 ──

    /**
     * 经 POST /api/root_shell 执行 shell 命令并返回输出文本。
     * result 可能是字符串直接返回，也可能是 {content: "..."} 对象。
     */
    private suspend fun rootShell(cmd: String): String {
        val body = JSONObject().put("command", cmd).put("timeout", 100000)
        val j = JSONObject(api.post("/api/root_shell", body.toString()))
        val result = j.opt("result")
        return when (result) {
            is String -> result
            is JSONObject -> result.optString("content", "")
            else -> throw IllegalStateException("root_shell 执行失败：${j.optString("error")}")
        }
    }
}
