/*
 * 星灵 (XingLing) · UFI-TOOLS JSON API 底层客户端（扩展版）
 *
 * 使用 Android 内置 HttpURLConnection（免第三方网络依赖）+ org.json 解析。
 * 所有 /api/ 认证请求自动附加 kano-t / kano-sign / authorization 三个请求头；
 * 免认证端点（need_token / version_info / get_custom_head 等）走 getNoAuth。
 *
 * 扩展能力（供 goform / root_shell / 测速等高级功能复用）：
 *   - requestEx：返回状态码 + 全部响应头 + 正文（可用于取 Kano-Cookie 登录会话）
 *   - extraHeaders：附加自定义请求头（如 Kano-Cookie 携带官方后台会话）
 *   - postForm：application/x-www-form-urlencoded 表单提交
 *   - speedtestDownload：流式读取 /api/speedtest 并统计速率
 *   - uploadFile：multipart/form-data 文件上传
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 底层 HTTP 原始响应 */
class RawResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>, // key 已小写
    val body: String
) {
    fun header(name: String): String? =
        headers[name.lowercase()]?.firstOrNull { it.isNotBlank() }
}

class UfiToolsApi(
    private val baseUrl: String,   // 形如 http://192.168.0.1:2333
    private val token: String,     // 明文口令（用于生成 authorization）
    private val timeoutMs: Int = 8000
) {

    /** 免认证 GET（如 /api/need_token、/api/version_info、/api/get_custom_head） */
    suspend fun getNoAuth(path: String): String = requestEx("GET", path, auth = false).body

    /** 认证 GET（path 可含 query，签名自动剥离 query） */
    suspend fun get(
        path: String,
        extraHeaders: Map<String, String>? = null,
        timeout: Int = timeoutMs
    ): String = requestEx("GET", path, extraHeaders = extraHeaders, timeout = timeout).body

    /** 认证 POST（body 为 JSON 字符串） */
    suspend fun post(
        path: String,
        jsonBody: String,
        extraHeaders: Map<String, String>? = null,
        timeout: Int = timeoutMs
    ): String = requestEx(
        "POST", path, auth = true, extraHeaders = extraHeaders,
        body = jsonBody, contentType = "application/json; charset=utf-8", timeout = timeout
    ).body

    /** 认证 POST（application/x-www-form-urlencoded 表单） */
    suspend fun postForm(
        path: String,
        formBody: String,
        extraHeaders: Map<String, String>? = null,
        timeout: Int = timeoutMs
    ): String = requestEx(
        "POST", path, auth = true, extraHeaders = extraHeaders,
        body = formBody, contentType = "application/x-www-form-urlencoded; charset=UTF-8", timeout = timeout
    ).body

    /** 完整请求，返回状态码 + 响应头 + 正文（auth=false 用于免认证端点）
     *  @param signPath 签名使用的规范化路径；默认取 path 不含 query 部分。
     *                  /api/proxy/... 例外需传原始请求路径（含 --目标URL 整体）。
     */
    suspend fun requestEx(
        method: String,
        path: String,
        auth: Boolean = true,
        extraHeaders: Map<String, String>? = null,
        body: String? = null,
        contentType: String? = null,
        timeout: Int = timeoutMs,
        signPath: String? = null
    ): RawResponse = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeout
            readTimeout = timeout
            useCaches = false
            if (auth) {
                // kano-t / kano-sign / authorization 三头齐全，设备端开启登录口令时才不会 401
                val signPathValue = signPath ?: path.substringBefore("?")
                setRequestProperty("kano-t", ts.toString())
                setRequestProperty("kano-sign", UfiToolsAuth.kanoSign(method, signPathValue, ts))
                setRequestProperty("authorization", UfiToolsAuth.authorization(token))
            }
            extraHeaders?.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val headers = conn.headerFields
                .mapKeys { it.key?.lowercase() ?: "" }
                .mapValues { it.value ?: emptyList() }
            val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText) ?: ""
            RawResponse(code, headers, text)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 流式下载 /api/speedtest 测速数据，返回 (接收字节数, 耗时毫秒)。
     */
    suspend fun speedtestDownload(ckSize: Int): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val conn = (URL(baseUrl + "/api/speedtest?ckSize=$ckSize").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 20000
            useCaches = false
            setRequestProperty("kano-t", ts.toString())
            setRequestProperty("kano-sign", UfiToolsAuth.kanoSign("GET", "/api/speedtest", ts))
            setRequestProperty("authorization", UfiToolsAuth.authorization(token))
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                throw IllegalStateException("测速失败（HTTP $code）：$err")
            }
            val started = System.currentTimeMillis()
            var total: Long = 0
            val buf = ByteArray(64 * 1024)
            conn.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                }
            }
            val elapsed = System.currentTimeMillis() - started
            total to elapsed
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 流式下载后台文件（如 /api/uploads/<file> 提供的分区镜像），
     * 直接写入 destFile，返回落盘字节数。
     */
    suspend fun downloadFile(path: String, destFile: java.io.File): Long = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 120000
            useCaches = false
            setRequestProperty("kano-t", ts.toString())
            setRequestProperty("kano-sign", UfiToolsAuth.kanoSign("GET", path.substringBefore("?"), ts))
            setRequestProperty("authorization", UfiToolsAuth.authorization(token))
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                throw IllegalStateException("下载失败（HTTP $code）：$err")
            }
            destFile.parentFile?.mkdirs()
            var total: Long = 0
            val buf = ByteArray(64 * 1024)
            conn.inputStream.use { ins ->
                destFile.outputStream().use { out ->
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                    }
                    out.flush()
                }
            }
            total
        } finally {
            conn.disconnect()
        }
    }

    /**
     * multipart/form-data 文件上传（/api/upload_img）。
     * 返回响应正文（成功时为 {"url":"/uploads/<uuid>.<ext>"}）。
     */
    suspend fun uploadFile(fileName: String, fileBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val boundary = "----XingLingBoundary${System.currentTimeMillis()}"
        val ts = System.currentTimeMillis()
        val conn = (URL(baseUrl + "/api/upload_img").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 60000
            useCaches = false
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("kano-t", ts.toString())
            setRequestProperty("kano-sign", UfiToolsAuth.kanoSign("POST", "/api/upload_img", ts))
            setRequestProperty("authorization", UfiToolsAuth.authorization(token))
        }
        try {
            conn.outputStream.use { out ->
                val header = buildString {
                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                    append("Content-Type: application/octet-stream\r\n\r\n")
                }
                out.write(header.toByteArray(Charsets.UTF_8))
                out.write(fileBytes)
                out.write("\r\n".toByteArray(Charsets.UTF_8))
                out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val code = conn.responseCode
            val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText) ?: ""
        } finally {
            conn.disconnect()
        }
    }

    /**
     * multipart/form-data 文件上传（/api/upload_img）。
     * 手动拼接 multipart 边界，零第三方依赖。返回响应正文（含 url 字段）。
     */
    suspend fun uploadMultipart(
        path: String,
        fileName: String,
        fileBytes: ByteArray,
        fieldName: String = "file",
        extraHeaders: Map<String, String>? = null,
        timeout: Int = 60000
    ): String = withContext(Dispatchers.IO) {
        val boundary = "----XingLing${System.currentTimeMillis()}"
        val ts = System.currentTimeMillis()
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeout
            readTimeout = timeout
            useCaches = false
            doOutput = true
            setRequestProperty("kano-t", ts.toString())
            setRequestProperty("kano-sign", UfiToolsAuth.kanoSign("POST", path.substringBefore("?"), ts))
            setRequestProperty("authorization", UfiToolsAuth.authorization(token))
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            extraHeaders?.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { out ->
                val header = "--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n"
                out.write(header.toByteArray(Charsets.UTF_8))
                out.write(fileBytes)
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText) ?: ""
        } finally {
            conn.disconnect()
        }
    }

    /** 是否需要口令认证（免认证） */
    suspend fun needToken(): Boolean = runCatching {
        val text = getNoAuth("/api/need_token")
        val upper = text.lowercase()
        upper.contains("true") || upper.contains("1\"") || text.trim() == "1" || text.trim() == "true"
    }.getOrDefault(true)

    companion object {
        /** 对 query 值做 URL 编码（供 AT 命令等场景复用） */
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
