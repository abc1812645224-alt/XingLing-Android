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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class UfiToolsGoform(
    private val api: UfiToolsApi,
    private val token: String,
    /** ZTE（官方后台）专用口令：连接页可单独填写，留空则复用后台口令 */
    private val zteToken: String = "",
    /** 会话隔离键：同一台设备（host:port）即使 new 出多个客户端，也共享一把登录锁与会话 */
    private val sessionKey: String = "default"
) {

    /** ZTE 登录口令：单独填写为空时复用 UFI-TOOLS 后台口令 */
    private val ztePassword: String get() = zteToken.ifBlank { token }

    private val state get() = sessionState(sessionKey)

    /** 登录失败冷却（指数退避，避免错误口令被反复提交触发官方后台锁定） */
    private fun loginCooldownMs(failCount: Int): Long = when {
        failCount <= 0 -> 0L
        failCount == 1 -> 30_000L
        failCount == 2 -> 60_000L
        failCount == 3 -> 120_000L
        failCount == 4 -> 180_000L
        else -> 300_000L
    }

    private suspend fun currentSession(): String {
        state.cookie?.let { return it }
        return state.mutex.withLock {
            // 双检：等待锁期间可能已由其他协程/其他客户端实例完成登录
            state.cookie?.let { return@withLock it }

            // 登录失败冷却期内直接拒绝，不再向官方后台提交口令
            if (state.failAtMs > 0) {
                val remain = loginCooldownMs(state.failCount) -
                    (System.currentTimeMillis() - state.failAtMs)
                if (remain > 0) {
                    throw IllegalStateException(
                        "官方后台口令疑似错误，已暂停登录 ${remain / 1000 + 1} 秒（防止触发“密码错误多次”锁定）。" +
                            "请在设备连接配置中核对 ZTE 官方后台口令（默认常为 admin）。"
                    )
                }
            }

            // 1) 尝试读取 UFI-TOOLS 持久化的官方后台会话（网页端登录后可直接复用，不抢占）
            val saved = readSavedCookie()
            if (saved.isNotBlank() && isSessionAlive(saved)) {
                state.cookie = saved
                state.failAtMs = 0
                return@withLock saved
            }
            if (saved.isNotBlank()) clearSavedCookie()

            // 2) 无有效会话 → 串行登录（同设备全局唯一一处提交口令）
            try {
                val fresh = login()
                state.cookie = fresh
                state.failAtMs = 0
                state.failCount = 0
                fresh
            } catch (e: Throwable) {
                state.failCount++
                state.failAtMs = System.currentTimeMillis()
                throw e
            }
        }
    }

    /** 会话失效（被踢/重启）后清空缓存，下次请求重新走串行登录 */
    private fun invalidateSession() {
        state.cookie = null
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
        // 2) password = SHA256(SHA256(ZTE官方后台口令) + LD)
        //    注意：必须用 ztePassword（ZTE 专用口令），不能用 token（UFI-TOOLS 口令）
        val pwdHex = sha256Hex(ztePassword.toByteArray(Charsets.UTF_8))
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

    /** 读操作：cmd 逗号拼接，返回原始 JSON 文本。
     *  会话失效（被网页端挤掉/设备重启）时清空会话并串行重登一次，仅一次；
     *  网络层异常不触发重登，避免不可达时反复提交口令。 */
    suspend fun goformGet(cmd: String, cookieOverride: String? = null): String {
        if (cookieOverride != null) return rawGoformGet(cmd, cookieOverride)
        val text = rawGoformGet(cmd, currentSession())
        if (looksUnauthenticated(text)) {
            invalidateSession()
            return rawGoformGet(cmd, currentSession())
        }
        return text
    }

    private suspend fun rawGoformGet(cmd: String, cookie: String): String {
        val path = "/api/goform/goform_get_cmd_process?isTest=false&cmd=${urlEncode(cmd)}&multi_data=1&_=${System.currentTimeMillis()}"
        return api.get(path, extraHeaders = cookieHeader(cookie), timeout = 20000)
    }

    /** 响应是否像“未登录”：空体、非 JSON（登录页 HTML）、loginfo=timeout */
    private fun looksUnauthenticated(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return true
        if (!t.startsWith("{")) return true
        return t.contains("\"loginfo\":\"timeout\"") || t.equals("{\"loginfo\":\"\"}")
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
        return api.postForm(
            "/api/goform/goform_set_cmd_process",
            form,
            extraHeaders = headers,
            timeout = 20000
        )
    }

    /** 重启 / 关机类 goformId：设备常在回包前就断网断电，需对连接中断做乐观判定 */
    private val powerGoformIds = setOf("REBOOT_DEVICE", "SHUTDOWN_DEVICE")

    /**
     * 写操作便捷方法：校验 result == "success"。
     * 与读路径 goformGet 对齐：会话失效（cookie 过期 / 被网页端挤掉 / 设备重启过）时清空会话、
     * 重新登录并重试一次。此前写路径没有这层重试，一旦缓存会话失效，所有 set 指令都会静默失败，
     * 表现为“重启 / 关机 / 性能模式点击无反应”。
     * 重启 / 关机在设备断连（空响应 / 代理错误）时视为指令已下发，不当作失败。
     */
    suspend fun writeChecked(goformId: String, params: Map<String, String>) {
        val power = goformId in powerGoformIds
        var text = firstAttempt(goformId, params, power)
        // 首次尝试判定为会话失效（set 返回登录页 / result=3 / timeout，或取 AD 的 RD 时拿到非 JSON）
        // → 清空会话重新登录后再试一次。
        if (text == null || needsReauth(text)) {
            invalidateSession()
            text = secondAttempt(goformId, params, power)
        }
        evaluateWrite(text, power)
    }

    /** 首次写：电源命令遇到网络断连返回空串（设备可能已开始重启/关机）；
     *  取版本号 / RD 时拿到登录页等非 JSON（JSONException）返回 null，作为“需要重登”信号。 */
    private suspend fun firstAttempt(goformId: String, params: Map<String, String>, power: Boolean): String? =
        try {
            write(goformId, params)
        } catch (e: java.io.IOException) {
            if (power) "" else throw e
        } catch (e: org.json.JSONException) {
            null
        }

    /** 重登后的第二次写：currentSession() 会重新登录，登录失败直接抛出；电源命令仍容忍断连 */
    private suspend fun secondAttempt(goformId: String, params: Map<String, String>, power: Boolean): String =
        try {
            write(goformId, params)
        } catch (e: java.io.IOException) {
            if (power) "" else throw e
        }

    /** 统一判定写结果 */
    private fun evaluateWrite(text: String, power: Boolean) {
        val body = text.trim()
        if (body.startsWith("{")) {
            val j = JSONObject(body)
            if (j.has("error")) {
                throw IllegalStateException("后台报错：${j.optString("error")}")
            }
            val result = j.optString("result", "")
            if (result == "3") throw IllegalStateException("官方后台密码错误（result=3），请核对 ZTE 官方后台口令")
            if (result.isNotBlank() && result != "success") {
                throw IllegalStateException("后台操作未成功：result=$result")
            }
            return
        }
        // 非 JSON 响应
        if (power) return // 重启/关机：设备通常在返回 success 前就断开连接（空体/代理错误），按已下发处理
        if (body.isBlank()) throw IllegalStateException("后台无响应，请确认设备在线后重试")
        throw IllegalStateException("后台未返回有效结果（可能未登录官方后台）：${body.take(120)}")
    }

    /** 响应是否明确指向“会话失效需重登”：result=3/timeout，或登录页 HTML / loginfo=timeout。
     *  空体与 "Proxy error: ..." 不算登录页（对电源命令那是设备断连），不触发重登，避免重复下发重启。 */
    private fun needsReauth(text: String): Boolean {
        val r = jsonResult(text)
        if (r == "3" || r == "timeout") return true
        val b = text.trim()
        if (b.startsWith("{")) return false
        return b.contains("login", ignoreCase = true) || b.contains("\"loginfo\":\"timeout\"")
    }

    /** 取 JSON 响应里的 result 字段；非 JSON / 无 result 返回 null */
    private fun jsonResult(text: String): String? {
        val b = text.trim()
        if (!b.startsWith("{")) return null
        val j = runCatching { JSONObject(b) }.getOrNull() ?: return null
        return j.optString("result", "").ifBlank { null }
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
        /** 同一台设备共享的登录状态：互斥锁 + 会话 cookie + 失败冷却（跨多个客户端实例） */
        class SessionState {
            val mutex = Mutex()
            @Volatile var cookie: String? = null
            @Volatile var failAtMs: Long = 0
            @Volatile var failCount: Int = 0
        }
        private val sessionStates = java.util.concurrent.ConcurrentHashMap<String, SessionState>()
        private fun sessionState(key: String): SessionState =
            sessionStates.computeIfAbsent(key) { SessionState() }

        private fun sha256(data: ByteArray): ByteArray =
            java.security.MessageDigest.getInstance("SHA-256").digest(data)

        private fun sha256Hex(data: ByteArray): String =
            sha256(data).joinToString("") { "%02x".format(it) }.uppercase()

        private fun urlEncode(v: String): String =
            java.net.URLEncoder.encode(v, "UTF-8")

        /** 短信内容编码为 UTF-16BE 十六进制（官方 gsmEncode 规则） */
        fun utf16Hex(text: String): String =
            text.toByteArray(java.nio.charset.StandardCharsets.UTF_16BE)
                .joinToString("") { "%02x".format(it) }
    }
}
