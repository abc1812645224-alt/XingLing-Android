/*
 * 星灵 (XingLing) · 中兴官方 WEB 后台 goform 代理客户端
 *
 * 通过 UFI-TOOLS 的 /api/goform 反向代理访问官方后台（中兴 Web API）：
 *   - 登录（参考 UfiPeek v3.0 降级链路，纯算法、无外部二进制）：
 *       ① GET goform(loginfo,LD) 探测登录态并取 LD 随机数；
 *       ② loginfo != ok → ZTE 原生 POST 登录：
 *            passwordHash = SHA256(ZTE 口令).toUpperCase()
 *            loginHash    = SHA256(passwordHash + LD).toUpperCase()
 *       ③ 登录后重取 loginfo 校验；
 *       ④ 上述链路整体失败 → 回落原有 LOGIN_MULTI_USER 登录（既有行为）。
 *       口令区分：ZTE 专用口令留空则复用后台口令。
 *   - 会话：从响应头 kano-cookie 取 → 通过 /api/set_cookie 持久化
 *   - 读：goform_get_cmd_process?cmd=...&multi_data=1（携带 Kano-Cookie）
 *   - 写：goform_set_cmd_process（携带 AD=SHA256(SHA256(wa_inner_version+cr_version)+RD))
 *   - SEND_SMS：MessageBody 需 UTF-16BE hex 编码
 *
 * 协议参照 UFI-TOOLS API_Doc.md v4.1.3 §3.13。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import org.json.JSONObject

class UfiToolsGoform(
    private val api: UfiToolsApi,
    private val token: String,
    /** ZTE（官方后台）专用口令：连接页可单独填写，留空则复用后台口令 */
    private val zteToken: String = ""
) {

    /** ZTE 登录口令：单独填写为空时复用 UFI-TOOLS 后台口令 */
    private val ztePassword: String get() = zteToken.ifBlank { token }

    private var session: String? = null

    private suspend fun currentSession(): String {
        session?.let { return it }
        // 1) 尝试读取 UFI-TOOLS 持久化的官方后台会话
        val saved = readSavedCookie()
        if (saved.isNotBlank()) {
            if (isSessionAlive(saved)) {
                session = saved
                return saved
            }
            clearSavedCookie()
        }
        // 2) 无有效会话 → 重新登录
        val fresh = login()
        session = fresh
        return fresh
    }

    private suspend fun readSavedCookie(): String = runCatching {
        val text = api.get("/api/get_cookie")
        JSONObject(text).optString("cookie", "").trim()
    }.getOrDefault("")

    private suspend fun clearSavedCookie() = runCatching {
        api.post("/api/set_cookie", """{"cookie":""}""")
    }

    private suspend fun isSessionAlive(cookie: String): Boolean = runCatching {
        if (cookie.isBlank()) false
        else JSONObject(goformGetRaw("loginfo", cookie)).optString("loginfo", "") == "ok"
    }.getOrDefault(false)

    /** Kano-Cookie 请求头（会话为空时不发送该头） */
    private fun cookieHeader(cookie: String?): Map<String, String> =
        if (cookie.isNullOrBlank()) emptyMap() else mapOf("Kano-Cookie" to cookie)

    /** 裸读 goform：cookie 为 null 时不携带会话（用于登录前的登录态探测） */
    private suspend fun goformGetRaw(cmd: String, cookie: String?): String {
        val path = "/api/goform/goform_get_cmd_process?isTest=false&cmd=${urlEncode(cmd)}" +
            "&multi_data=1&_=${System.currentTimeMillis()}"
        return api.get(path, extraHeaders = cookieHeader(cookie), timeout = 20000)
    }

    private suspend fun persistCookie(cookie: String) {
        runCatching { api.post("/api/set_cookie", JSONObject().put("cookie", cookie).toString()) }
    }

    /**
     * 登录官方后台（参考 UfiPeek v3.0 的降级链路）：
     *   ① GET goform(loginfo,LD)：探测登录态并取 LD 随机数；
     *   ② loginfo != ok → ZTE 原生 POST 登录（纯算法）；
     *   ③ 登录后重取 loginfo 校验；
     *   ④ 上述链路整体失败 → 回落原有 LOGIN_MULTI_USER 登录（既有行为）。
     * 失败时抛出后台返回的原始错误信息，不做静默吞掉。
     */
    private suspend fun login(): String {
        val failures = mutableListOf<String>()

        // ① 登录态探测 + 取 LD 随机数（不带会话）
        var ld = ""
        try {
            val probe = JSONObject(goformGetRaw("loginfo,LD", null))
            ld = probe.optString("LD", "").trim()
        } catch (e: Throwable) {
            failures += "登录态探测失败：${e.message ?: e.toString()}"
        }

        // ②③ ZTE 原生登录 + 登录后重取校验
        try {
            return zteNativeLogin(ld, failures)
        } catch (e: Throwable) {
            failures += "ZTE 原生登录失败：${e.message ?: e.toString()}"
        }

        // ④ 回落既有 LOGIN_MULTI_USER 链路
        try {
            return legacyLogin()
        } catch (e: Throwable) {
            failures += "后台口令登录失败：${e.message ?: e.toString()}"
        }

        throw IllegalStateException(failures.joinToString("；"))
    }

    /** ZTE 原生 POST 登录：passwordHash=SHA256(ZTE口令).upper，loginHash=SHA256(passwordHash+LD).upper */
    private suspend fun zteNativeLogin(ldHint: String, failures: MutableList<String>): String {
        var ld = ldHint
        if (ld.isBlank()) {
            ld = runCatching { JSONObject(goformGetRaw("LD", null)).optString("LD", "").trim() }
                .getOrElse {
                    failures += "LD 随机数获取失败：${it.message ?: it.toString()}"
                    ""
                }
        }
        if (ld.isBlank()) throw IllegalStateException("未取得 LD 随机数")

        val passwordHash = sha256Hex(ztePassword.toByteArray(Charsets.UTF_8)).uppercase()
        val loginHash = sha256Hex((passwordHash + ld).toByteArray(Charsets.UTF_8)).uppercase()

        val resp = api.requestEx(
            method = "POST",
            path = "/api/goform/goform_set_cmd_process",
            extraHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"),
            body = "goformId=LOGIN&isTest=false&user=admin&password=$loginHash",
            contentType = "application/x-www-form-urlencoded; charset=UTF-8"
        )
        val body = resp.body.trim()
        val cookie = resp.header("kano-cookie")?.substringBefore(';')?.trim().orEmpty()

        // 重取 loginfo 校验登录结果（result=0 或 loginfo=ok 视为成功）
        val recheck = runCatching {
            JSONObject(goformGetRaw("loginfo", cookie.ifBlank { null })).optString("loginfo", "")
        }.getOrDefault("")
        val accepted = body.contains("\"result\":0") || recheck == "ok"

        if (!accepted) {
            val detail = buildString {
                append("后台拒绝登录（HTTP ${resp.statusCode}")
                if (recheck.isNotBlank()) append("，loginfo=$recheck")
                append("）")
                if (body.isNotEmpty()) append("，后台响应：").append(body.take(200))
            }
            throw IllegalStateException(detail)
        }
        if (cookie.isNotBlank()) persistCookie(cookie)
        return cookie
    }

    /** 原有登录链路（LD 随机数 + SHA256(SHA256(口令)+LD) → LOGIN_MULTI_USER） */
    private suspend fun legacyLogin(): String {
        // 1) 取 LD 随机数
        val ldJson = goformGetRaw("LD", null)
        val ld = JSONObject(ldJson).optString("LD", "")
        // 2) password = SHA256(SHA256(口令) + LD)
        val pwdHex = sha256Hex(token.toByteArray(Charsets.UTF_8))
        val password = sha256Hex((pwdHex + ld).toByteArray(Charsets.UTF_8))
        // 3) POST LOGIN_MULTI_USER（新式登录）
        val form = buildString {
            append("goformId=LOGIN_MULTI_USER")
            append("&isTest=false")
            append("&user=admin")
            append("&IP=localhost")
            append("&password=").append(urlEncode(password))
        }
        val resp = api.requestEx(
            method = "POST",
            path = "/api/goform/goform_set_cmd_process",
            extraHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"),
            body = form,
            contentType = "application/x-www-form-urlencoded; charset=UTF-8"
        )
        val sessionCookie = resp.header("kano-cookie")?.substringBefore(';')?.trim().orEmpty()
        if (sessionCookie.isBlank()) {
            throw IllegalStateException(
                "登录官方后台未取得会话 Cookie（HTTP ${resp.statusCode}）：${resp.body.trim().take(200)}"
            )
        }
        // 4) 持久化会话
        persistCookie(sessionCookie)
        return sessionCookie
    }

    /** 读操作：cmd 逗号拼接，返回原始 JSON 文本 */
    suspend fun goformGet(cmd: String, cookieOverride: String? = null): String {
        val c = cookieOverride ?: currentSession()
        val path = "/api/goform/goform_get_cmd_process?isTest=false&cmd=${urlEncode(cmd)}&multi_data=1&_=${System.currentTimeMillis()}"
        return api.get(path, extraHeaders = cookieHeader(c), timeout = 20000)
    }

    /**
     * 读操作（自定义完整路径 + 携带会话），如短信分页列表：
     * getPath("goform_get_cmd_process?cmd=sms_data_total&page=0&data_per_page=100&...")
     */
    suspend fun getPath(pathAndQuery: String): String {
        val c = currentSession()
        return api.get("/api/goform/$pathAndQuery", extraHeaders = cookieHeader(c), timeout = 20000)
    }

    /** 便捷读：返回 JSONObject（多字段时键名与 cmd 一一对应） */
    suspend fun read(vararg cmd: String): JSONObject = JSONObject(goformGet(cmd.joinToString(",")))

    /**
     * 写操作：goform_set_cmd_process，自动计算 AD 防篡改签名。
     * @return 响应文本（通常含 "result":"success"）
     */
    suspend fun write(goformId: String, params: Map<String, String>): String {
        val c = currentSession()
        val ad = computeAd(c)
        val form = buildString {
            append("goformId=").append(goformId)
            params.forEach { (k, v) ->
                append('&').append(k).append('=').append(urlEncode(v))
            }
            append("&isTest=false")
            append("&AD=").append(ad)
        }
        val headers = cookieHeader(c).toMutableMap()
        headers["authorization"] = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        return api.postForm(
            "/api/goform/goform_set_cmd_process",
            form,
            extraHeaders = headers,
            timeout = 20000
        )
    }

    /** 写操作便捷方法：校验 result == "success" */
    suspend fun writeChecked(goformId: String, params: Map<String, String>) {
        val text = write(goformId, params)
        val body = text.trim()
        if (body.startsWith("{")) {
            val result = JSONObject(body).optString("result", "")
            if (result == "3") throw IllegalStateException("官方后台密码错误（result=3）")
            if (result.isNotBlank() && result != "success") {
                throw IllegalStateException("后台操作未成功：result=$result")
            }
        }
    }

    /** 计算 AD：SHA256( SHA256(wa_inner_version + cr_version) + RD ) */
    private suspend fun computeAd(cookie: String): String {
        val verJson = goformGet("wa_inner_version,cr_version,Language", cookie)
        val ver = JSONObject(verJson)
        val wa = ver.optString("wa_inner_version").ifBlank { "V1.0.0B02" }
        val cr = ver.optString("cr_version").ifBlank { "V1.0.0B02" }
        val rdJson = goformGet("RD", cookie)
        val rd = JSONObject(rdJson).optString("RD", "")
        return sha256Hex((sha256Hex((wa + cr).toByteArray(Charsets.UTF_8)) + rd).toByteArray(Charsets.UTF_8))
    }

    /** 发短信：MessageBody 用 UTF-16BE hex（gsmEncode），携带官方会话 */
    suspend fun sendSms(number: String, body: String): String {
        writeChecked(
            "SEND_SMS",
            mapOf(
                "Number" to number,
                "MessageBody" to utf16Hex(body)
            )
        )
        return ""
    }

    companion object {
        private fun sha256(data: ByteArray): ByteArray =
            java.security.MessageDigest.getInstance("SHA-256").digest(data)

        private fun sha256Hex(data: ByteArray): String =
            sha256(data).joinToString("") { "%02x".format(it) }

        private fun urlEncode(v: String): String =
            java.net.URLEncoder.encode(v, "UTF-8")

        /** 短信内容编码为 UTF-16BE 十六进制（官方 gsmEncode 规则） */
        fun utf16Hex(text: String): String =
            text.toByteArray(java.nio.charset.StandardCharsets.UTF_16BE)
                .joinToString("") { "%02x".format(it) }
    }
}
