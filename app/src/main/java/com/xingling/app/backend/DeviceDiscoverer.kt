/*
 * 星灵 (XingLing) · 局域网设备自动发现
 *
 * 发现顺序（命中即返回）：
 *   1. WiFi 网关（DhcpInfo / LinkProperties 路由，随身 WiFi 下网关即设备本身，
 *      绝大多数情况下 1 个请求即可命中，无需扫全网段）
 *   2. 当前 WiFi 接口所在 /24 网段 1..254 并发探测
 *   3. 兜底常见网段 192.168.0.0/24、192.168.1.0/24
 *
 * 探测目标：UFI-TOOLS 免认证端点 /api/need_token（HTTP 200 且响应含
 * true / false / need_token 即命中）。
 *
 * 与旧实现的区别：
 *   - 只扫描 WiFi 网络，蜂窝（rmnet 10.x）与 VPN（tun）网段不再污染目标；
 *   - 探测连接显式绑定到 WiFi Network，热点无互联网时也不会走移动数据；
 *   - 网关优先 + 命中即短路，不再 awaitAll 等满 254 个请求；
 *   - 单地址超时放宽到 1200ms，避免弱设备漏检。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

object DeviceDiscoverer {

    private const val TAG = "DeviceDiscoverer"
    private const val PROBE_PATH = "/api/need_token"
    private const val GATEWAY_TIMEOUT_MS = 1500
    private const val HOST_TIMEOUT_MS = 1200
    private const val RANGE_BUDGET_MS = 9000L
    private const val FALLBACK_BUDGET_MS = 6000L

    /**
     * 扫描设备，返回命中的 host（不含端口）；未命中返回 null（调用方回退手动输入）。
     */
    suspend fun discover(context: Context, port: Int = 2333): String? = withContext(Dispatchers.IO) {
        val network = DeviceNetwork.currentWifiNetwork(context)
        Log.i(TAG, "discover start: port=$port network=$network")

        // 1) 网关优先（网关通常就是设备本身）
        val gateways = wifiGateways(context, network)
        val prefixes = wifiIpv4Prefixes(context, network)
        Log.i(TAG, "gateways=$gateways prefixes=$prefixes")
        for (gw in gateways) {
            if (probe(network, gw, port, GATEWAY_TIMEOUT_MS, logFail = true)) {
                Log.i(TAG, "HIT gateway $gw")
                return@withContext gw
            }
        }

        // 2) WiFi 接口所在 /24 网段
        for (prefix in prefixes) {
            val hit = scanRange(network, prefix, port, RANGE_BUDGET_MS)
            if (hit != null) {
                Log.i(TAG, "HIT range $prefix -> $hit")
                return@withContext hit
            }
        }

        // 3) 兜底常见网段（与 WiFi 网段去重）
        for (prefix in listOf("192.168.0", "192.168.1")) {
            if (prefixes.contains(prefix)) continue
            val hit = scanRange(network, prefix, port, FALLBACK_BUDGET_MS)
            if (hit != null) {
                Log.i(TAG, "HIT fallback $prefix -> $hit")
                return@withContext hit
            }
        }
        Log.i(TAG, "discover: no device found")
        null
    }

    /** 并发扫描一个 /24 网段，命中任意主机即短路返回；总时长受 [budgetMs] 限制。 */
    private suspend fun scanRange(
        network: Network?,
        prefix: String,
        port: Int,
        budgetMs: Long
    ): String? = coroutineScope {
        val found = Channel<String>(capacity = Channel.BUFFERED)
        val jobs = (1..254).map { i ->
            launch(Dispatchers.IO) {
                val host = "$prefix.$i"
                if (probe(network, host, port, HOST_TIMEOUT_MS)) {
                    found.trySend(host)
                }
            }
        }
        val hit = withTimeoutOrNull(budgetMs) {
            found.receiveCatching().getOrNull()
        }
        jobs.forEach { it.cancel() }
        hit
    }

    /** 探测单个地址是否存在 UFI-TOOLS 服务（免认证端点，不校验口令）。 */
    private fun probe(network: Network?, ip: String, port: Int, timeoutMs: Int, logFail: Boolean = false): Boolean {
        return try {
            val url = URL("http://$ip:$port$PROBE_PATH")
            val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            val code = conn.responseCode
            val body = try {
                conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Throwable) {
                ""
            }
            conn.disconnect()
            val ok = code == 200 &&
                (body.contains("true") || body.contains("false") || body.contains("need_token"))
            if (!ok && logFail) Log.i(TAG, "probe $ip:$port code=$code body=${body.take(80)}")
            ok
        } catch (e: Throwable) {
            if (logFail) Log.i(TAG, "probe $ip:$port FAIL ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * 取 WiFi 网关地址：优先 DhcpInfo（需 ACCESS_WIFI_STATE），
     * 再从 LinkProperties 默认路由取，最后按本机 /24 推导 .1。
     */
    private fun wifiGateways(context: Context, network: Network?): List<String> {
        val result = LinkedHashSet<String>()

        // 路径一：DhcpInfo.gateway（小端整型）
        runCatching {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val gw = wm?.dhcpInfo?.gateway ?: 0
            if (gw != 0) {
                val ip = "${gw and 0xFF}.${(gw shr 8) and 0xFF}." +
                    "${(gw shr 16) and 0xFF}.${(gw shr 24) and 0xFF}"
                if (ip != "0.0.0.0") result.add(ip)
            }
        }

        // 路径二：LinkProperties 默认路由网关
        runCatching {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = network ?: DeviceNetwork.currentWifiNetwork(context)
            if (cm != null && net != null) {
                cm.getLinkProperties(net)?.routes?.forEach { route ->
                    val g = route.gateway
                    if (route.isDefaultRoute && g is Inet4Address && !g.isLoopbackAddress) {
                        result.add(g.hostAddress ?: "")
                    }
                }
            }
        }

        // 路径三：按本机 WiFi IPv4 推导 .1
        wifiIpv4Prefixes(context, network).forEach { result.add("$it.1") }

        return result.filter { it.isNotBlank() && it != "0.0.0.0" }
    }

    /** 仅取 WiFi 接口上的私有 IPv4 网段前缀（如 192.168.0），排除蜂窝 / VPN。 */
    private fun wifiIpv4Prefixes(context: Context, network: Network?): List<String> {
        val set = LinkedHashSet<String>()

        runCatching {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = network ?: DeviceNetwork.currentWifiNetwork(context)
            if (cm != null && net != null) {
                val lp = cm.getLinkProperties(net)
                val ifaceName = lp?.interfaceName

                lp?.linkAddresses?.forEach { la ->
                    (la.address as? Inet4Address)?.let { addPrivatePrefix(set, it) }
                }

                // 同网卡上的其他 IPv4 地址兜底
                if (ifaceName != null) {
                    runCatching {
                        NetworkInterface.getByName(ifaceName)?.inetAddresses?.toList()?.forEach { a ->
                            (a as? Inet4Address)?.let { addPrivatePrefix(set, it) }
                        }
                    }
                }
            }
        }

        if (set.isEmpty()) {
            set.add("192.168.0")
            set.add("192.168.1")
        }
        return set.toList()
    }

    private fun addPrivatePrefix(set: LinkedHashSet<String>, addr: Inet4Address) {
        if (addr.isLoopbackAddress) return
        val bytes = addr.address
        if (bytes.size != 4) return
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        if (b0 == 10 || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168)) {
            set.add("$b0.$b1.$b2")
        }
    }
}
