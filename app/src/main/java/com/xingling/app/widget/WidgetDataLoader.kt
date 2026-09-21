/*
 * 星灵 (XingLing) · 小组件取数
 *
 * 复用既有数据链路，不新增任何后台端点：
 *   1) UfiToolsBackend.fetchOverview() → /api/baseDeviceInfo（型号/电量/CPU/温度/内存/当日当月流量）
 *   2) UfiToolsGoform.goformGet()      → 官方后台 goform（运营商/网络类型/信号格/RSRP/未读短信/频段/已连接设备数）
 *   3) DeviceFeatures.rootShell()      → dumpsys wifi 取 WiFi 频点（尽力而为，失败不影响其它字段）
 * 全部走本地已存配置（DeviceStore 的 host/port/token），用户无需重新输入口令。
 * 任一子任务失败均降级为「字段缺省 + 缓存回显」，不抛出异常。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.widget

import android.content.Context
import android.os.SystemClock
import com.xingling.app.backend.DeviceStore
import com.xingling.app.backend.LanClient
import com.xingling.app.backend.LanClientParser
import com.xingling.app.backend.UfiToolsApi
import com.xingling.app.backend.UfiToolsApiFactory
import com.xingling.app.backend.UfiToolsBackend
import com.xingling.app.backend.UfiToolsGoform
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object WidgetDataLoader {

    /** 取数互斥：点击 / 亮屏窗口 / App 内预览页可能同时触发，串行化避免重复请求设备 */
    private val mutex = Mutex()

    /** 最近一次取数结果与时刻（同进程短窗口内复用，进一步压低设备请求量） */
    @Volatile
    private var lastSnapshot: WidgetSnapshot? = null

    @Volatile
    private var lastLoadAt: Long = 0L

    /** 一次 goform 批量读取全部所需字段，避免多次往返。
     *  station_list / lan_station_list 必须随这批字段同一次请求取回：
     *  另起 goform 客户端会触发第二次官方后台登录，把本次会话挤掉，
     *  导致受登录态保护的站点列表回空（小组件设备数恒显示 0）。 */
    private const val GOFORM_FIELDS =
        "model_name,network_provider,network_type,network_signalbar,battery_value,battery_charging," +
            "Z5g_rsrp,snr,RSRQ,sms_unread_num,Nr_bands,Lte_bands,station_list,lan_station_list"

    /** WiFi 当前频点（root 权限，失败即 "未知"） */
    private const val WIFI_FREQ_CMD =
        "dumpsys wifi 2>/dev/null | grep -o 'frequency= [0-9]*' | head -1"

    /**
     * 拉取一次快照（分层刷新策略统一入口）。
     *
     * @param commit 是否把结果写入快照缓存；高频轮询先比对再落盘，传 false
     * @param force  是否强制真实取数（跳过短窗口复用，点击刷新用）
     */
    suspend fun load(
        context: Context,
        commit: Boolean = true,
        force: Boolean = false
    ): WidgetSnapshot {
        val memo = lastSnapshot
        if (!force && memo != null &&
            SystemClock.elapsedRealtime() - lastLoadAt < WidgetRefreshPolicy.CACHE_REUSE_MS
        ) {
            return memo
        }
        return mutex.withLock { loadLocked(context, commit) }
    }

    /** 实际取数（调用方已持有互斥锁） */
    private suspend fun loadLocked(context: Context, commit: Boolean): WidgetSnapshot {
        val now = System.currentTimeMillis()

        val store = runCatching { DeviceStore(context) }.getOrNull()
            ?: return WidgetSnapshot(configured = false, updateAt = now)
        if (!store.configured) return WidgetSnapshot(configured = false, updateAt = now)

        val host = store.host
        val port = store.port
        val token = store.token.ifBlank { "admin" }
        val zteToken = store.zteToken

        val api = UfiToolsApi(UfiToolsApiFactory.baseUrl(host, port), token)
        val goform = UfiToolsGoform(api, token, zteToken)
        val backend = UfiToolsBackend(host, port, token, zteToken)

        // 三路并行取数，各自超时，互不阻塞。
        // 已连接设备数随 goform 批量请求一并取回，不再单独调用 getLanClients()：
        // 那条链路会用另一个 goform 客户端并发登录官方后台，ZTE 后台同一账号只保留
        // 一个 Web 会话，后登录的会话会把先登录的挤掉，被挤掉的请求对 station_list
        // 这类受保护字段只返回空列表，小组件因此恒显示 0 台。
        val parts = coroutineScope {
            val overviewDeferred = async {
                withTimeoutOrNull(12_000L) {
                    runCatching { backend.fetchOverview().getOrNull() }.getOrNull()
                }
            }
            val goformDeferred = async {
                withTimeoutOrNull(14_000L) {
                    runCatching { JSONObject(goform.goformGet(GOFORM_FIELDS)) }.getOrNull()
                }
            }
            val shellDeferred = async {
                withTimeoutOrNull(8_000L) {
                    runCatching { backend.features.rootShell(WIFI_FREQ_CMD).getOrNull() }.getOrNull()
                }
            }
            Triple(overviewDeferred.await(), goformDeferred.await(), shellDeferred.await())
        }

        val overview = parts.first
        val goformJson = parts.second
        val shellText = parts.third.orEmpty()

        // 两条链路全失败：用缓存回显
        if (overview == null && goformJson == null) {
            val cached = WidgetSnapshotStore.read(context)
            return cached?.copy(stale = true)
                ?: WidgetSnapshot(configured = true, updateAt = now, stale = true)
        }

        // 已连接设备数（无线 station_list + 有线 lan_station_list，按 MAC 去重）
        val clients = clientsCount(goformJson)

        var rsrp = intOf(goformJson, "Z5g_rsrp") ?: Int.MIN_VALUE
        var snr = intOf(goformJson, "snr") ?: Int.MIN_VALUE
        var atBand = ""
        var atNetType = ""

        // goform 拿不到 RSRP 或频段时，用既有 AT 通道兜底（尽力而为）
        val bandMissing = textOf(goformJson, "Nr_bands") == null &&
            textOf(goformJson, "Lte_bands") == null
        if (rsrp == Int.MIN_VALUE || bandMissing) {
            val signal = withTimeoutOrNull(9_000L) {
                runCatching { backend.fetchSignalInfo().getOrNull() }.getOrNull()
            }
            if (signal != null) {
                rsrp = signal.rsrp
                if (snr == Int.MIN_VALUE) snr = signal.sinr
                atBand = signal.band
                atNetType = signal.networkType
            }
        }

        val deviceName = overview?.model?.takeIf { it.isNotBlank() && it != "未知设备" }
            ?: textOf(goformJson, "model_name")
            ?: "星灵设备"

        val carrier = textOf(goformJson, "network_provider") ?: "--"

        val signalBarsRaw = intOf(goformJson, "network_signalbar")
        val signalBars = when {
            signalBarsRaw == null -> -1
            signalBarsRaw < 0 -> -1
            else -> signalBarsRaw.coerceAtMost(5)
        }

        val netType = normNetType(textOf(goformJson, "network_type") ?: atNetType)

        val band = bandText(
            textOf(goformJson, "Nr_bands"),
            textOf(goformJson, "Lte_bands"),
            atBand
        )

        val battery = when {
            overview != null && overview.battery >= 0 -> overview.battery
            else -> intOf(goformJson, "battery_value") ?: -1
        }

        val charging = (overview?.isCharging ?: false) || textOf(goformJson, "battery_charging") == "1"

        val snapshot = WidgetSnapshot(
            configured = true,
            deviceName = deviceName,
            version = overview?.appVer?.takeIf { it.isNotBlank() && it != "--" } ?: "",
            signalBars = signalBars,
            battery = battery,
            charging = charging,
            dailyBytes = overview?.dailyBytes ?: -1L,
            monthlyBytes = overview?.monthlyBytes ?: -1L,
            carrier = carrier,
            netType = netType,
            band = band,
            cpuUsage = overview?.cpuUsage ?: -1f,
            cpuTemp = overview?.cpuTemp ?: -1f,
            wifiBand = wifiBand(shellText),
            memUsage = overview?.memUsage ?: -1f,
            rsrp = rsrp,
            snr = snr,
            smsUnread = intOf(goformJson, "sms_unread_num") ?: -1,
            clients = clients,
            updateAt = now,
            stale = false
        )

        if (commit) WidgetSnapshotStore.write(context, snapshot)
        lastSnapshot = snapshot
        lastLoadAt = SystemClock.elapsedRealtime()
        return snapshot
    }

    // ───────────────────────── 解析辅助 ─────────────────────────

    /**
     * 从同批 goform 响应解析已连接设备数（无线 station_list + 有线 lan_station_list）。
     * 响应里完全没有站点字段时返回 -1（未知，UI 显示 --），避免把未登录态的缺字段误报为 0。
     */
    private fun clientsCount(json: JSONObject?): Int {
        if (json == null) return -1
        if (!json.has("station_list") && !json.has("lan_station_list")) return -1
        val list = mutableListOf<LanClient>()
        list += LanClientParser.parse(json.opt("station_list"))
        list += LanClientParser.parse(json.opt("lan_station_list"))
        return list.distinctBy { it.mac.ifBlank { it.ip } }.size
    }

    private fun textOf(json: JSONObject?, key: String): String? {
        val raw = json?.opt(key) ?: return null
        val s = raw.toString().trim()
        return if (s.isEmpty() || s == "null" || s == "--" || s == "{}") null else s
    }

    private fun intOf(json: JSONObject?, key: String): Int? =
        textOf(json, key)?.let { raw ->
            raw.replace(Regex("[^0-9-]"), "").toIntOrNull()
        }

    private fun normNetType(raw: String?): String = when (raw) {
        null, "", "--" -> "--"
        "20", "5G", "LTE-NSA" -> "5G"
        "13", "LTE", "4G" -> "4G"
        "2", "WCDMA", "3G" -> "3G"
        "1", "GSM", "2G" -> "2G"
        else -> raw
    }

    /** 频段：NR 优先（N41），否则 LTE（B3），再否则 AT 解析结果 */
    private fun bandText(nrBands: String?, lteBands: String?, atBand: String): String {
        val nr = nrBands?.trim()
        if (nr != null && Regex("^[0-9]+$").matches(nr)) return "N$nr"
        val lte = lteBands?.trim()
        if (lte != null && Regex("^[0-9]+$").matches(lte)) return "B$lte"
        return atBand.takeIf { it.isNotBlank() } ?: "--"
    }

    /** WiFi 频点 → 5G / 2.4G（无频点则 --） */
    private fun wifiBand(shellOutput: String): String {
        val match = Regex("frequency=\\s*(\\d+)").find(shellOutput) ?: return "--"
        val mhz = match.groupValues[1].toIntOrNull() ?: return "--"
        return if (mhz >= 4000) "5G" else "2.4G"
    }
}
