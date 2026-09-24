/*
 * 星灵 (XingLing) · UFI-TOOLS 设备后端实现
 *
 * 将 UFI-TOOLS(kanoqwq / Minikano, MIT License) v4.1.3 JSON API 封装为
 * DeviceBackend 抽象：
 *   - connect()：免认证探测 need_token + 带鉴权验证口令（baseDeviceInfo）
 *   - fetchOverview()：/api/baseDeviceInfo（电量/温度/CPU/内存/存储/当日当月流量/阈值）
 *   - fetchSignalInfo()：通过 /api/AT 执行 AT+CSQ / AT+QENG 尽力解析信号指标
 *   - fetchCellularUsage()：/api/cellularUsage
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

class UfiToolsBackend(
    host: String,
    port: Int,
    token: String,
    /** ZTE（官方后台）专用口令：连接页可单独填写，留空则复用 token */
    zteToken: String = ""
) : DeviceBackend {

    private val hostName: String = host.trim().removePrefix("http://").removePrefix("https://")
        .trimEnd('/').substringBefore('/')

    private val api = UfiToolsApi(UfiToolsApiFactory.baseUrl(hostName, port), token)

    private val goform = UfiToolsGoform(api, token, zteToken, "$host:$port")

    private var lastMonthlyDlBytes: Long = -1L
    private var lastMonthlyUlBytes: Long = -1L

    /** 高级功能（网络/设备/高级三批 38 项）真实实现 */
    override val features: DeviceFeatures by lazy {
        UfiToolsFeatureApi(api, goform, hostName)
    }

    /** P2 设备端二进制服务管理（ADGuardHome / EasyTier / EasyConnect，rootShell 直连） */
    val p2: P2FeatureHelper by lazy {
        P2FeatureHelper(api, goform, hostName)
    }

    /** P0 组功能（主题定制 / 文件管理 / 电量转发 / 推送测试 / 插件源） */
    val p0: P0FeatureHelper by lazy {
        P0FeatureHelper(api, goform, hostName)
    }

    override val deviceAddress: String = "$hostName:$port"

    override val displayName: String = "$hostName:$port"

    override suspend fun connect(): Result<BackendOverview> = runCatching {
        // 1) 免认证探测：设备是否可达 / 是否启用口令
        try {
            api.needToken()
        } catch (e: Throwable) {
            throw IllegalStateException("设备不可达：${e.message}", e)
        }
        // 2) 只拉一次核心信息 baseDeviceInfo 即可校验口令并快速完成连接。
        //    依赖 root shell 的增强项（QoS/QCI、流量分项、限速、开机时长、电池温/容量）
        //    不再阻塞“连接设备”，改为进入主界面后由 fetchOverview() 并行补齐。
        coreOverview()
    }

    /** 核心总览：仅一次 /api/baseDeviceInfo（也是验证口令的最小请求） */
    private suspend fun coreOverview(): BackendOverview =
        JSONObject(api.get("/api/baseDeviceInfo")).toOverview()

    override suspend fun fetchOverview(): Result<BackendOverview> = runCatching {
        val core = coreOverview()
        // 各增强项彼此独立，全部并发执行；任一项失败都不影响其余与核心数据。
        coroutineScope {
            val at = async { runCatching { readAtQos() } }
            val monthly = async { runCatching { readMonthly() } }
            val limit = async { runCatching { readDataLimit() } }
            val extras = async { runCatching { readExtras() } }

            var ov = core
            at.await().getOrNull()?.let { q ->
                ov = ov.copy(
                    qci = q.qci ?: ov.qci,
                    dlMaxMbps = if (q.dlMbps >= 0) q.dlMbps else ov.dlMaxMbps,
                    ulMaxMbps = if (q.ulMbps >= 0) q.ulMbps else ov.ulMaxMbps
                )
            }
            monthly.await().getOrNull()?.let { m ->
                ov = ov.copy(monthlyDlBytes = m.dl, monthlyUlBytes = m.ul)
            }
            limit.await().getOrNull()?.let { l ->
                ov = ov.copy(
                    dataLimitEnabled = l.enabled,
                    dataLimitMaxBytes = if (l.bytes > 0) l.bytes else ov.dataLimitMaxBytes
                )
            }
            extras.await().getOrNull()?.let { e ->
                ov = ov.copy(
                    batteryTemp = if (e.battTemp > 0) e.battTemp else ov.batteryTemp,
                    batteryCapacityMah = e.battCap,
                    uptimeSec = e.uptime
                )
            }
            ov
        }
    }

    /** AT+CGEQOSRDP：QCI 与上下行最大速率（解析不到返回 null/默认，不覆盖核心） */
    private suspend fun readAtQos(): AtQos {
        val text = api.get("/api/AT?command=AT%2BCGEQOSRDP%3D1&slot=0")
        val result = JSONObject(text).optString("result", "")
        // 格式: +CGEQOSRDP: 1,9,0,0,0,0,300000,100000
        val m = Regex("""CGEQOSRDP:\s*(\d+),(\d+),[\d,]+,(\d+),(\d+)""").find(result)
        return AtQos(
            qci = m?.groupValues?.get(2),
            dlMbps = m?.groupValues?.get(3)?.toIntOrNull()?.let { it / 1000 } ?: -1,
            ulMbps = m?.groupValues?.get(4)?.toIntOrNull()?.let { it / 1000 } ?: -1
        )
    }

    /** 当月上下行分流：goform monthly_rx_bytes(下行) / monthly_tx_bytes(上行)，带进程内缓存兜底 */
    private suspend fun readMonthly(): Monthly? {
        var dl: Long? = null
        var ul: Long? = null
        runCatching {
            val f = goform.read("monthly_rx_bytes,monthly_tx_bytes")
            fun gstr(key: String) = f.optString(key).trim()
                .takeIf { it.isNotBlank() && it != "--" && !it.equals("null", true) }
            dl = gstr("monthly_rx_bytes")?.toLongOrNull()?.also { lastMonthlyDlBytes = it }
            ul = gstr("monthly_tx_bytes")?.toLongOrNull()?.also { lastMonthlyUlBytes = it }
        }
        val fd = dl ?: if (lastMonthlyDlBytes >= 0) lastMonthlyDlBytes else null
        val fu = ul ?: if (lastMonthlyUlBytes >= 0) lastMonthlyUlBytes else null
        return if (fd != null && fu != null) Monthly(fd, fu) else null
    }

    /** 高级后台流量限制（data_volume_limit_size） */
    private suspend fun readDataLimit(): Limit? {
        val obj = features.getDataLimit().getOrNull() ?: return null
        return Limit(enabled = obj.enabled, bytes = obj.maxLimit.toLongOrNull() ?: -1L)
    }

    /** 电池温度/容量 + 开机时长：三个 root shell 并发，互不阻塞 */
    private suspend fun readExtras(): Extras = coroutineScope {
        val temp = async {
            runCatching {
                val t = features.rootShell("timeout 2s cat /sys/class/power_supply/battery/temp").getOrNull()
                t?.trim()?.toIntOrNull()?.let { it / 10f }
            }.getOrNull() ?: -1f
        }
        val cap = async {
            runCatching {
                val c = features.rootShell("timeout 2s cat /sys/class/power_supply/battery/charge_full").getOrNull()
                c?.trim()?.toLongOrNull()?.let { (it / 1000).toInt() }
            }.getOrNull() ?: -1
        }
        val up = async {
            runCatching {
                val r = features.rootShell("timeout 2s cat /proc/uptime").getOrNull()
                r?.split(".")?.firstOrNull()?.trim()?.toLongOrNull()
            }.getOrNull() ?: -1L
        }
        Extras(temp.await(), cap.await(), up.await())
    }

    private class AtQos(val qci: String?, val dlMbps: Int, val ulMbps: Int)
    private class Monthly(val dl: Long, val ul: Long)
    private class Limit(val enabled: Boolean, val bytes: Long)
    private class Extras(val battTemp: Float, val battCap: Int, val uptime: Long)

    override suspend fun fetchSignalInfo(): Result<BackendSignalInfo> = runCatching {
        parseSignal()
    }

    override suspend fun fetchCellularUsage(startTimeMs: Long, endTimeMs: Long): Result<Long> =
        runCatching {
            val path = "/api/cellularUsage?startTime=$startTimeMs&endTime=$endTimeMs&method=mills-range"
            val text = api.get(path)
            // {"result":"success","usage":"123456789"}
            JSONObject(text).optString("usage", "-1").toLongOrNull() ?: -1L
        }

    /**
     * 实时上下行速率：ZTE goform realtime_rx_thrpt / realtime_tx_thrpt，
     * 固件按自身统计窗口直接给出（B/s），比轮询流量计数器差值更准且天然分上下行。
     */
    override suspend fun fetchRealtimeSpeed(): Result<RealtimeSpeed> = runCatching {
        val j = goform.read("realtime_rx_thrpt,realtime_tx_thrpt,realtime_time")
        val rx = parseThrpt(j, "realtime_rx_thrpt")
        val tx = parseThrpt(j, "realtime_tx_thrpt")
        RealtimeSpeed(rxBps = rx, txBps = tx)
    }

    /** thrpt 字段兼容数字/带单位字符串，统一解析为 B/s（空、--、非法值按 0） */
    private fun parseThrpt(j: JSONObject, key: String): Long {
        val raw = j.opt(key)?.toString()?.trim().orEmpty()
        if (raw.isBlank() || raw == "--" || raw.equals("null", true)) return 0L
        // 纯数字直接用（固件口径为 B/s）

        raw.toLongOrNull()?.let { return it.coerceAtLeast(0L) }
        // 带单位兜底（如 "1.5MB/S"、"800KB/S"）
        val num = Regex("[0-9]+(\\.[0-9]+)?").find(raw)?.value?.toDoubleOrNull() ?: return 0L
        val lower = raw.lowercase()
        val factor = when {
            lower.contains("gb") -> 1073741824.0
            lower.contains("mb") -> 1048576.0
            lower.contains("kb") -> 1024.0
            else -> 1.0
        }
        return (num * factor).toLong().coerceAtLeast(0L)
    }

    private fun JSONObject.toOverview(): BackendOverview {
        val tempList = optString("cpu_temp_list").takeIf { it.isNotBlank() && it != "null" }
        val battTemp = parseFirstTemp(tempList)

        // 8 核频率 + 使用率合并
        val freqObj = optJSONObject("cpuFreqInfo")
        val usageObj = optJSONObject("cpuUsageInfo")
        val cores = if (freqObj != null) {
            val out = ArrayList<CpuCoreStat>(8)
            for (i in 0..7) {
                val key = "cpu$i"
                val fo = freqObj.optJSONObject(key) ?: continue
                val cur = fo.optInt("cur", -1)
                val max = fo.optInt("max", -1)
                val use = usageObj?.optString(key)?.toFloatOrNull() ?: -1f
                out.add(CpuCoreStat(key, cur, max, use))
            }
            out
        } else emptyList()

        // 内存详情
        val memObj = optJSONObject("memInfo")
        val memTotal = memObj?.optLong("mem_total_kb", -1L) ?: -1L
        val memUsed = memObj?.optLong("mem_used_kb", -1L) ?: -1L
        val swapPct = memObj?.optString("swap_usage_percent")?.toFloatOrNull() ?: -1f

        return BackendOverview(
            model = optString("model", "未知设备"),
            appVer = optString("app_ver", "--"),
            clientIp = optString("client_ip", "--"),
            battery = optString("battery").toIntOrNull() ?: -1,
            batteryTemp = battTemp,
            batteryVoltageMv = if (optLong("voltage_now", -1) > 0) (optLong("voltage_now") / 1000).toInt() else -1,
            batteryCurrentMa = current_nowToMa(optLong("current_now", 0)),
            cpuTemp = (optDouble("cpu_temp", -1.0) / 1000.0).toFloat(),
            cpuUsage = optDouble("cpu_usage", -1.0).toFloat(),
            memUsage = optDouble("mem_usage", -1.0).toFloat(),
            memTotalKb = memTotal,
            memUsedKb = memUsed,
            swapUsagePercent = swapPct,
            storageUsedMb = if (optLong("internal_used_storage", -1) >= 0) optLong("internal_used_storage") / 1_048_576 else -1,
            storageTotalMb = if (optLong("internal_total_storage", -1) >= 0) optLong("internal_total_storage") / 1_048_576 else -1,
            dailyBytes = optLong("daily_data", -1),
            monthlyBytes = optLong("monthly_data", -1),
            isReachedFlowLimit = optBoolean("is_reached_data_flow_limit", false),
            isCharging = optString("battery_charging") == "1" || current_nowSafePositive(optLong("current_now", 0)),
            cpuCores = cores
        )
    }

    private fun current_nowSafePositive(currentNow: Long): Boolean =
        currentNow > 50_000 // 充电时通常为正向大电流(µA)，放电为负

    /** current_now(µA) → mA；0 或异常返回 Int.MIN_VALUE（无数据） */
    private fun current_nowToMa(currentNow: Long): Int {
        if (currentNow == 0L) return Int.MIN_VALUE
        return (currentNow / 1000).toInt()
    }

    /** cpu_temp_list 形如 [{"type":"battery","temp":38900}, ...]（thmzone 温度单位 0.001℃）。
     *  U30 Air 固件无 battery 热区（仅 nr/apcpu/soc/pa 等 thmzone），此时电池温度返回 -1 显示 "--"。 */
    private fun parseFirstTemp(tempList: String?): Float {
        if (tempList.isNullOrBlank()) return -1f
        try {
            val arr = org.json.JSONArray(tempList)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = o.optString("type", "")
                if (type.contains("battery", true)) {
                    val temp = o.optDouble("temp", -1.0)
                    if (temp >= 0) return (temp / 1000.0).toFloat()
                }
            }
        } catch (_: Throwable) {
        }
        return -1f
    }

    /** 依次尝试 AT+QENG="servingcell" 与 AT+CSQ 解析信号；失败返回未知 */
    private suspend fun parseSignal(): BackendSignalInfo {
        var rsrp = Int.MIN_VALUE
        var rssi = Int.MIN_VALUE
        var sinr = Int.MIN_VALUE
        var rsrq = Int.MIN_VALUE
        var band = ""
        var pci = -1
        var arfcn = 0
        var netType = ""
        var carrier = ""

        // 1) 高扩展 AT 指令：servingcell（高通模组常见）
        try {
            val cmd = UfiToolsApi.encode("""AT+QENG="servingcell"""")
            val text = api.get("/api/AT?command=$cmd&slot=0")
            val result = JSONObject(text).optString("result", "")
            QengParser.parse(result).let { p ->
                p[QengParser.RSRP].trim().toIntOrNull()?.let { rsrp = it }
                p[QengParser.SINR].trim().toIntOrNull()?.let { sinr = it }
                band = p[QengParser.BAND]
                p[QengParser.PCI].trim().toIntOrNull()?.let { pci = it }
                netType = p[QengParser.NET]
            }
        } catch (_: Throwable) {
        }

        // 2) 兜底 AT+CSQ：信号强度 -> RSSI
        if (rssi == Int.MIN_VALUE) {
            try {
                val text = api.get("/api/AT?command=AT%2BCSQ&slot=0")
                val result = JSONObject(text).optString("result", "")
                Regex("""\+CSQ:\s*(\d+),""").find(result)?.groupValues?.get(1)?.toIntOrNull()?.let { csq ->
                    if (csq in 0..31) rssi = -113 + csq * 2 // 99=不可用，忽略
                }
            } catch (_: Throwable) {
            }
        }

        // 3) ZTE 官方后台 goform 兜底（U30 Air 等不支持 QENG、CSQ=99 不可用）
        //    ① network_information：U30 Air 固件 v4.1.5 的服务小区详情字段
        //      （Nr_pci / Nr_fcn / Nr_bands / Nr_snr / nr_rsrq / nr_rsrp）；
        //      平铺的 Nr_pci/Nr_snr 等字段该固件返回空，勿用作主源。
        //    ② 主状态字段补充：Z5g_rsrp / network_rssi / network_type / network_provider
        if (rsrp == Int.MIN_VALUE) {
            runCatching {
                val ni = goform.read("network_information")
                if (rsrp == Int.MIN_VALUE) {
                    rsrp = ni.optString("nr_rsrp").toIntOrNull()
                        ?: ni.optString("Nr_signal_strength").toIntOrNull()
                        ?: Int.MIN_VALUE
                }
                if (sinr == Int.MIN_VALUE) sinr = ni.optString("Nr_snr").toIntOrNull() ?: Int.MIN_VALUE
                if (rsrq == Int.MIN_VALUE) rsrq = ni.optString("nr_rsrq").toIntOrNull() ?: Int.MIN_VALUE
                if (pci < 0) pci = ni.optString("Nr_pci").toIntOrNull() ?: -1
                if (arfcn <= 0) arfcn = ni.optString("Nr_fcn").toIntOrNull() ?: 0
                if (band.isBlank()) {
                    val nrB = ni.optString("Nr_bands").trim()
                    if (nrB.isNotBlank() && nrB != "-1") band = "n$nrB"
                }
                val j = goform.read(
                    "network_rssi,Z5g_rsrp,lte_rsrp,network_type,network_provider"
                )
                if (rsrp == Int.MIN_VALUE) {
                    rsrp = j.optString("Z5g_rsrp").toIntOrNull()
                        ?: j.optString("lte_rsrp").toIntOrNull()
                        ?: Int.MIN_VALUE
                }
                if (netType.isBlank() || netType == "--") netType = j.optString("network_type")
                if (carrier.isBlank()) carrier = j.optString("network_provider")
            }
        }

        return BackendSignalInfo(
            rsrp = rsrp, rssi = rssi, sinr = sinr, rsrq = rsrq,
            band = band, pci = pci, arfcn = arfcn, networkType = netType,
            carrierName = if (carrier.isBlank()) "--" else carrier
        )
    }
}

/** 校验/ping 探测辅助：构造 base url */
object UfiToolsApiFactory {
    fun baseUrl(host: String, port: Int): String {
        val h = host.trim().removePrefix("http://").removePrefix("https://")
            .trimEnd('/').substringBefore('/')
        return "http://$h:$port"
    }

    /** 挑出第一个可联网网段做扫描，或回退 192.168.0/24 */
    fun defaultHost(): String = "192.168.0.1"
}

/** AT+QENG="servingcell" 响应解析（尽力而为，字段缺失返回 MIN_VALUE/空） */
internal object QengParser {
    const val RSRP = 0
    const val SINR = 1
    const val BAND = 2
    const val PCI = 3
    const val NET = 4

    /**
     * 示例响应：
     * +QENG: "servingcell","NOCONN"
     * +QENG: "servingcell","LTE","FDD",460,0,321-12,123456,9,4,16A3B,-93,-8,5,14,-99,11,12,...
     *   index: 0 1 2 3 4 5 6 ... 7=pci 8=rsrp 9=sinr 10 11=band
     */
    fun parse(raw: String): Array<String> {
        val out = arrayOf("", "", "", "-1", "") // [rsrp, sinr, band, pci, net]
        val m = Regex("""\+QENG:\s*"servingcell",?"?([^",]*)"?"?,([^)]*)""").find(raw)
            ?: Regex("""\+QENG:\s*"servingcell",([^)]*)""").find(raw)
        if (m == null) return out

        val net = m.groupValues[1]
        out[NET] = net
        val segments = m.groupValues[2].split(",")
        // 找 PCI(整数) 与其后的 RSRP/SINR
        for (i in 0 until segments.size) {
            val v = segments[i].trim().trim('"')
            if (v.toIntOrNull() != null && v.length >= 4 &&
                segments.size > i + 2
            ) {
                val rsrp = segments.getOrNull(i + 1).orEmpty().trim().toIntOrNull()
                val sinr = segments.getOrNull(i + 2).orEmpty().trim().toIntOrNull()
                if (rsrp != null && sinr != null) {
                    out[PCI] = v
                    out[RSRP] = rsrp.toString()
                    out[SINR] = sinr.toString()
                }
                // band(16A3B / 数字) 通常在 [mcc,mnc,tac,id 后再组合], 近似取第一个不含负号且非常用量纲
                for (j in i + 1 until segments.size) {
                    val sv = segments[j].trim().trim('"')
                    if (sv.isNotBlank() && sv[0].isDigit() && sv.toIntOrNull() == null && !sv.startsWith("-")) {
                        out[BAND] = sv
                        break
                    }
                }
                break
            }
        }
        return out
    }
}
