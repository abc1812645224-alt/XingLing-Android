/*
 * 星灵 (XingLing) · 设备热点网络绑定
 *
 * 随身 WiFi 热点在设备本身没有蜂窝数据（未插卡 / 欠费 / 无服务）时，
 * 会被 Android 判定为"无互联网"网络，此时系统默认网络仍是移动数据
 * （或正在运行的 VPN）。未绑定网络的 HttpURLConnection 发出的局域网
 * 请求不会走 wlan0，表现为扫描 / 连接设备全部超时。
 *
 * 本对象在进程内监听 WiFi 网络：
 *   - WiFi 存在但未通过互联网验证（设备热点的典型特征）→ 把进程默认
 *     网络绑定到该 WiFi，强制局域网流量走 wlan；
 *   - WiFi 已验证可联网（家用路由等）→ 不抢占系统路由，保留 VPN
 *     异地组网（EasyTier 等）场景；
 *   - WiFi 断开 → 解除绑定。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

object DeviceNetwork {

    private const val TAG = "DeviceNetwork"

    @Volatile
    private var wifiNetwork: Network? = null

    @Volatile
    private var bound = false

    /** Application  onCreate 时调用一次，进程存活期间持续跟踪 WiFi 网络。 */
    fun register(context: Context) {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiNetwork = network
                rebindIfNeeded(cm)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (network == wifiNetwork) rebindIfNeeded(cm)
            }

            override fun onLost(network: Network) {
                if (network == wifiNetwork) {
                    wifiNetwork = null
                    if (bound) {
                        cm.bindProcessToNetwork(null)
                        bound = false
                        Log.d(TAG, "WiFi lost, unbind process network")
                    }
                }
            }
        })

        // 注册时 WiFi 可能已连接，主动同步一次
        cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }?.let { n ->
            wifiNetwork = n
            rebindIfNeeded(cm)
        }
    }

    private fun rebindIfNeeded(cm: ConnectivityManager) {
        val n = wifiNetwork ?: return
        val caps = cm.getNetworkCapabilities(n) ?: return
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!validated) {
            if (!bound) {
                cm.bindProcessToNetwork(n)
                bound = true
                Log.d(TAG, "bind process to unvalidated WiFi (device hotspot)")
            }
        } else if (bound) {
            cm.bindProcessToNetwork(null)
            bound = false
            Log.d(TAG, "WiFi validated, release process binding")
        }
    }

    /** 当前 WiFi 网络，供扫描器用 network.openConnection 显式绑定（双保险）。 */
    fun currentWifiNetwork(context: Context): Network? {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return wifiNetwork
        return cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: wifiNetwork
    }
}
