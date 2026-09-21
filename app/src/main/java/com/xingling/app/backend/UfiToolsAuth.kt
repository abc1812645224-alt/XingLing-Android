/*
 * 星灵 (XingLing) · UFI-TOOLS 鉴权 / 签名算法
 *
 * 参照 https://github.com/kanoqwq/UFI-TOOLS (MIT License) API_Doc.md v4.1.3：
 *   authorization = SHA256(口令).hex 小写
 *   kano-t        = 当前毫秒时间戳
 *   kano-sign     = SHA256( SHA256(part1) + SHA256(part2) )
 *     rawData = "minikano" + METHOD + URL_PATH(不含query) + 时间戳
 *     hmac    = HMAC-MD5(rawData, secretKey)
 *     part1   = hmac[0..8)，part2 = hmac[8..16)
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object UfiToolsAuth {

    /** 固定签名密钥（源自 UFI-TOOLS 协议） */
    private const val HMAC_KEY = "minikano_kOyXz0Ciz4V7wR0IeKmJFYFQ20jd"

    /** authorization = SHA256(明文口令) 的小写 hex */
    fun authorization(token: String): String = sha256Hex(token.toByteArray(Charsets.UTF_8))

    /** 计算 kano-sign：method 全大写；path 为不含 query 的请求路径（如 /api/baseDeviceInfo） */
    fun kanoSign(method: String, path: String, timestampMs: Long): String {
        val rawData = "minikano" + method.uppercase() + path + timestampMs
        val hmac = hmacMd5(rawData.toByteArray(Charsets.UTF_8), HMAC_KEY.toByteArray(Charsets.UTF_8))
        val part1 = hmac.copyOfRange(0, 8)
        val part2 = hmac.copyOfRange(8, 16)
        val sha1 = sha256(part1)
        val sha2 = sha256(part2)
        val joined = ByteArray(sha1.size + sha2.size)
        sha1.copyInto(joined, 0, 0, sha1.size)
        sha2.copyInto(joined, sha1.size, 0, sha2.size)
        return sha256Hex(joined)
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun sha256Hex(data: ByteArray): String =
        sha256(data).joinToString("") { "%02x".format(it) }

    private fun hmacMd5(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(key, "HmacMD5"))
        return mac.doFinal(data)
    }
}
