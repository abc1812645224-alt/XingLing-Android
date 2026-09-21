/*
 * 星灵 (XingLing) · 设备接入配置加密存储
 *
 * 使用 androidx.security EncryptedSharedPreferences 保存设备地址与口令；
 * 个别 ROM 上 Tink 初始化异常时降级为普通 SharedPreferences 以保证可用性。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DeviceStore(context: Context) {

    private val ctx = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            // 降级：普通明文首选项（避免某些 ROM 上 Tink 崩溃导致 App 无法启动）
            ctx.getSharedPreferences(FILE_NAME_PLAIN, Context.MODE_PRIVATE)
        }
    }

    var host: String
        get() = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** ZTE（官方后台）专用口令：连接页可单独填写，留空则复用 token */
    var zteToken: String
        get() = prefs.getString(KEY_ZTE_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ZTE_TOKEN, value).apply()

    /** 是否已完成首次配置（连接成功并保存过口令） */
    var configured: Boolean
        get() = prefs.getBoolean(KEY_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONFIGURED, value).apply()

    /** 是否已展示首启致谢页 */
    var thanksShown: Boolean
        get() = prefs.getBoolean(KEY_THANKS, false)
        set(value) = prefs.edit().putBoolean(KEY_THANKS, value).apply()

    fun toBaseUrl(): String = "http://$host:$port"

    companion object {
        private const val FILE_NAME = "xingling_device_store"
        private const val FILE_NAME_PLAIN = "xingling_device_plain"
        private const val KEY_HOST = "device_host"
        private const val KEY_PORT = "device_port"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_ZTE_TOKEN = "device_zte_token"
        private const val KEY_CONFIGURED = "configured"
        private const val KEY_THANKS = "thanks_shown"
        private const val DEFAULT_HOST = "192.168.0.1"
        private const val DEFAULT_PORT = 2333
    }
}
