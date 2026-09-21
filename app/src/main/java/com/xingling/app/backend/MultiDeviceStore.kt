/*
 * 星灵 (XingLing) · 多设备管理存储
 *
 * 独立于单设备 DeviceStore 的多设备列表存储，使用 SharedPreferences 保存
 * 设备列表（JSON 数组）与当前选中设备 ID。序列化零第三方依赖，仅用 org.json。
 * 通过"同步到当前设备"按钮把选中设备写入 DeviceStore，复用现有连接逻辑，
 * 不对 MainActivity / DeviceStore 做任何重构。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一条已保存的设备连接配置 */
data class SavedDevice(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val port: Int = 2333,
    val token: String = "",
    val zteToken: String = ""
)

class MultiDeviceStore(context: Context) {

    private val ctx = context.applicationContext

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 读取全部已保存设备；数据损坏时返回空列表而不崩溃 */
    fun listDevices(): List<SavedDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedDevice(
                    id = o.optString("id", ""),
                    name = o.optString("name", ""),
                    host = o.optString("host", ""),
                    port = if (o.has("port")) o.optInt("port", 2333) else 2333,
                    token = o.optString("token", ""),
                    zteToken = o.optString("zteToken", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 新增设备；id 为空时自动生成 UUID */
    fun addDevice(device: SavedDevice) {
        val list = listDevices().toMutableList()
        val finalDevice = if (device.id.isBlank()) device.copy(id = UUID.randomUUID().toString()) else device
        list.add(finalDevice)
        persist(list)
        // 首个设备自动设为当前
        if (getCurrentDeviceId().isBlank()) setCurrentDeviceId(finalDevice.id)
    }

    /** 按 id 删除设备；若删除的是当前设备则清空当前 ID */
    fun removeDevice(id: String) {
        val list = listDevices().filterNot { it.id == id }
        persist(list)
        if (getCurrentDeviceId() == id) setCurrentDeviceId("")
    }

    /** 按 id 覆盖更新设备 */
    fun updateDevice(device: SavedDevice) {
        val list = listDevices().map { if (it.id == device.id) device else it }
        persist(list)
    }

    fun getCurrentDeviceId(): String = prefs.getString(KEY_CURRENT_ID, "") ?: ""

    fun setCurrentDeviceId(id: String) {
        prefs.edit().putString(KEY_CURRENT_ID, id).apply()
    }

    /** 当前选中设备；无或未找到返回 null */
    fun getCurrentDevice(): SavedDevice? {
        val id = getCurrentDeviceId()
        if (id.isBlank()) return null
        return listDevices().firstOrNull { it.id == id }
    }

    // ── 内部：序列化落盘 ──
    private fun persist(list: List<SavedDevice>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("host", d.host)
                put("port", d.port)
                put("token", d.token)
                put("zteToken", d.zteToken)
            })
        }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    companion object {
        private const val FILE_NAME = "xingling_multi_devices"
        private const val KEY_DEVICES = "devices"
        private const val KEY_CURRENT_ID = "current_id"
    }
}
