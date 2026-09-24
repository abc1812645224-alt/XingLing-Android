/*
 * 星灵 (XingLing) · UFI-TOOLS 高级功能实现
 *
 * 将功能清单中的高级功能项全部接入真实后台：
 *   - UFI-TOOLS 自有 /api 模块：volte/vonr、getSupportNrBandList、root_shell、
 *     user_shell、smbPath(高级功能)、hasTTYD、adb_wifi_setting、OTA、插件、
 *     speedtest、短信转发、定时任务、cellularUsage 等
 *   - 官方后台 goform 反代：锁频段/锁基站/网络模式/SIM/APN/短信/热点/流量校准/
 *     重启关机/性能模式/DHCP黑白名单/SMB
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class UfiToolsFeatureApi(
    private val api: UfiToolsApi,
    private val goform: UfiToolsGoform,
    private val host: String
) : DeviceFeatures {

    /**
     * 后台 uploads 目录（高级后台插件提取 BOOT 时的落盘目录，
     * 对应下载端点 /api/uploads/<file>）。
     */
    private val uploadsDir: String = "/data/data/com.minikano.f50_sms/files/uploads"

    // ═══════════════════ A. 网络控制批 ═══════════════════

    override suspend fun volteEnabled(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/volte_status?slot=0")).optBoolean("enabled", false)
    }

    override suspend fun setVolte(enabled: Boolean, slot: Int): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("enabled", if (enabled) "1" else "0")
            .put("slot", slot.toString())
        api.post("/api/volte_status", body.toString())
        Unit
    }

    override suspend fun vonrEnabled(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/vonr_status?slot=0")).optBoolean("enabled", false)
    }

    override suspend fun setVonr(enabled: Boolean, slot: Int): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("enabled", if (enabled) "1" else "0")
            .put("slot", slot.toString())
        api.post("/api/vonr_status", body.toString())
        Unit
    }

    override suspend fun supportNrBands(): Result<List<Int>> = runCatching {
        val arr = JSONObject(api.get("/api/getSupportNrBandList?slot=0")).optJSONArray("band_list")
            ?: JSONArray()
        (0 until arr.length()).map { arr.getInt(it) }
    }

    override suspend fun getLteBandLock(): Result<List<Int>> = runCatching {
        parseBands(goform.read("lte_band_lock").s("lte_band_lock"))
    }

    override suspend fun setLteBandLock(bands: List<Int>): Result<Unit> = runCatching {
        goform.writeChecked("LTE_BAND_LOCK", mapOf("lte_band_lock" to bands.joinToString(",")))
        Unit
    }

    override suspend fun getNrBandLock(): Result<List<Int>> = runCatching {
        parseBands(goform.read("nr_band_lock").s("nr_band_lock"))
    }

    override suspend fun setNrBandLock(bands: List<Int>): Result<Unit> = runCatching {
        goform.writeChecked("NR_BAND_LOCK", mapOf("nr_band_lock" to bands.joinToString(",")))
        Unit
    }

    override suspend fun unlockAllBands(): Result<Unit> = runCatching {
        api.get("/api/unlockAllBand")
        Unit
    }

        override suspend fun servingCells(): Result<List<CellInfo>> = runCatching {
        val o = goform.read("network_information")
        val pci = firstInt(o, "Nr_pci", "nr_pci", "pci")
        if (pci < 0) return@runCatching emptyList()
        val netType = firstInt(o, "network_type")
        listOf(CellInfo(
            pci = pci,
            earfcn = firstInt(o, "Nr_fcn", "nr_fcn", "earfcn"),
            rat = if (netType == 20) "5G NR" else o.optString("network_type", "NR"),
            rsrp = firstInt(o, "nr_rsrp", "Nr_signal_strength", "rsrp"),
            rsrq = firstInt(o, "nr_rsrq", "rsrq"),
            sinr = firstInt(o, "Nr_snr", "snr", "sinr"),
            band = o.optString("Nr_bands", ""),
            bandwidth = o.optString("Nr_band_widths", "")
        ))
    }
    override suspend fun neighborCells(): Result<List<CellInfo>> = runCatching {
        parseCells(goform.read("neighbor_cell_info").opt("neighbor_cell_info"))
    }

    override suspend fun lockedCells(): Result<List<CellInfo>> = runCatching {
        parseCells(goform.read("locked_cell_info").opt("locked_cell_info"))
    }

    override suspend fun lockCell(pci: Int, earfcn: Int, rat: String): Result<Unit> = runCatching {
        goform.writeChecked(
            "CELL_LOCK",
            mapOf("pci" to pci.toString(), "earfcn" to earfcn.toString(), "rat" to rat)
        )
        Unit
    }

    override suspend fun unlockCell(): Result<Unit> = runCatching {
        goform.writeChecked("UNLOCK_ALL_CELL", emptyMap())
        Unit
    }

    override suspend fun getBearerPreference(): Result<String> = runCatching {
        goform.read("net_select").s("net_select")
    }

    override suspend fun setBearerPreference(value: String): Result<Unit> = runCatching {
        goform.writeChecked("SET_BEARER_PREFERENCE", mapOf("BearerPreference" to value))
        Unit
    }

    override suspend fun endcState(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/getEndcState")).boolKey("enabled", "endc_state", "value")
    }

    override suspend fun setEndcState(enabled: Boolean): Result<Unit> = runCatching {
        api.post("/api/setEndcState", JSONObject().put("enabled", if (enabled) "1" else "0").toString())
        Unit
    }

    override suspend fun getSimSlot(): Result<String> = runCatching {
        goform.read("sim_slot").s("sim_slot")
    }

    override suspend fun setSimSlot(slot: String): Result<Unit> = runCatching {
        goform.writeChecked("SET_SIM_SLOT", mapOf("sim_slot" to slot))
        Unit
    }

    override suspend fun getApn(): Result<ApnInfo> = runCatching {
        val j = goform.read(
            "apn_mode,apn_Current_index,index,profile_name,apn_wan_apn,apn_pdp_type,apn_wan_dial"
        )
        ApnInfo(
            mode = j.s("apn_mode"),
            currentIndex = j.s("apn_Current_index").ifBlank { j.s("index") }.toIntOrNull() ?: -1,
            profileName = j.s("profile_name"),
            apn = j.s("apn_wan_apn"),
            pdpType = j.s("apn_pdp_type"),
            dialNumber = j.s("apn_wan_dial")
        )
    }

    override suspend fun setApnMode(mode: String): Result<Unit> = runCatching {
        goform.writeChecked("APN_PROC_EX", mapOf("apn_mode" to mode))
        Unit
    }

    override suspend fun saveApn(profile: ApnProfile): Result<Unit> = runCatching {
        goform.writeChecked(
            "APN_PROC_EX",
            mapOf(
                "apn_mode" to "manual",
                "apn_action" to "save",
                "index" to profile.index.toString(),
                "profile_name" to profile.profileName,
                "apn_wan_apn" to profile.apn,
                "apn_pdp_type" to profile.pdpType,
                "apn_ppp_username" to profile.username,
                "apn_ppp_passwd" to profile.password,
                "apn_ppp_auth_mode" to profile.authMode,
                "apn_pdp_addr" to profile.pdpAddr
            )
        )
        Unit
    }

    override suspend fun deleteApn(index: Int): Result<Unit> = runCatching {
        goform.writeChecked("APN_PROC_EX", mapOf("apn_mode" to "manual", "apn_action" to "delete", "index" to index.toString()))
        Unit
    }

    override suspend fun setDefaultApn(index: Int): Result<Unit> = runCatching {
        goform.writeChecked("APN_PROC_EX", mapOf("apn_mode" to "manual", "apn_action" to "set_default", "index" to index.toString()))
        Unit
    }

    // ═══════════════════ B. 设备控制批 ═══════════════════

    override suspend fun sendSms(number: String, body: String): Result<Unit> = runCatching {
        goform.sendSms(number, body)
        Unit
    }

    override suspend fun inboxSms(): Result<List<SmsMessage>> = runCatching {
        val raw = goform.getPath(
            "goform_get_cmd_process?isTest=false&cmd=sms_data_total&page=0&data_per_page=100&mem_store=1&tags=100&order_by=order%20by%20id%20desc&multi_data=1&_=${System.currentTimeMillis()}"
        )
        val j = JSONObject(raw)
        val list = j.optJSONArray("sms_content_list") ?: JSONArray()
        (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            SmsMessage(
                id = o.s("id").ifBlank { o.s("sms_id") },
                number = o.s("number").ifBlank { o.s("phone") },
                body = decodeSmSBody(o.s("content")),
                date = o.s("date").ifBlank { o.s("time") },
                unread = o.s("tag") == "1"
            )
        }
    }

    override suspend fun deleteSms(msgId: String): Result<Unit> = runCatching {
        goform.writeChecked("DELETE_SMS", mapOf("msg_id" to msgId, "notCallback" to "true"))
        Unit
    }

    override suspend fun markSmsRead(msgId: String): Result<Unit> = runCatching {
        goform.writeChecked("SET_MSG_READ", mapOf("msg_id" to msgId, "notCallback" to "true"))
        Unit
    }

    override suspend fun smsReceiveMode(): Result<String> = runCatching {
        JSONObject(api.get("/api/sms_receive_mode")).s("sms_receive_mode")
    }

    override suspend fun setSmsReceiveMode(mode: String): Result<Unit> = runCatching {
        api.post("/api/sms_receive_mode", JSONObject().put("sms_receive_mode", mode).toString())
        Unit
    }

    override suspend fun getForwardChannel(): Result<String> = runCatching {
        JSONObject(api.get("/api/sms_forward_method")).s("sms_forward_method")
    }

    override suspend fun getForwardEnabled(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/sms_forward_enabled")).s("enabled") == "1"
    }

    override suspend fun setForwardEnabled(on: Boolean): Result<Unit> = runCatching {
        api.get("/api/sms_forward_enabled?enable=${if (on) "1" else "0"}")
        Unit
    }

    override suspend fun getForwardDingtalk(): Result<DingtalkConfig?> = runCatching {
        val j = JSONObject(api.get("/api/sms_forward_dingtalk"))
        if (j.s("webhook_url").isBlank() && j.s("secret").isBlank()) null
        else DingtalkConfig(
            webhookUrl = j.s("webhook_url"),
            secret = j.s("secret"),
            forwardDevInfo = j.s("forward_dev_info") == "1"
        )
    }

    override suspend fun setForwardDingtalk(cfg: DingtalkConfig): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("webhook_url", cfg.webhookUrl)
            .put("secret", cfg.secret)
            .put("forward_dev_info", if (cfg.forwardDevInfo) "1" else "0")
        api.post("/api/sms_forward_dingtalk", body.toString())
        Unit
    }

    override suspend fun getForwardMail(): Result<MailConfig?> = runCatching {
        val j = JSONObject(api.get("/api/sms_forward_mail"))
        if (j.s("smtp_host").isBlank() && j.s("smtp_username").isBlank()) null
        else MailConfig(
            smtpHost = j.s("smtp_host"),
            smtpPort = j.s("smtp_port").ifBlank { "465" },
            smtpTo = j.s("smtp_to"),
            smtpUsername = j.s("smtp_username"),
            smtpPassword = j.s("smtp_password"),
            forwardDevInfo = j.s("forward_dev_info") == "1"
        )
    }

    override suspend fun setForwardMail(cfg: MailConfig): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("smtp_host", cfg.smtpHost)
            .put("smtp_port", cfg.smtpPort.ifBlank { "465" })
            .put("smtp_to", cfg.smtpTo)
            .put("smtp_username", cfg.smtpUsername)
            .put("smtp_password", cfg.smtpPassword)
            .put("forward_dev_info", if (cfg.forwardDevInfo) "1" else "0")
        api.post("/api/sms_forward_mail", body.toString())
        Unit
    }

    override suspend fun getForwardCurl(): Result<String?> = runCatching {
        val j = JSONObject(api.get("/api/sms_forward_curl"))
        j.s("curl_text").takeIf { it.isNotBlank() }
    }

    override suspend fun setForwardCurl(curlText: String): Result<Unit> = runCatching {
        api.post("/api/sms_forward_curl", JSONObject().put("curl_text", curlText).toString())
        Unit
    }

    override suspend fun getForwardBlacklist(): Result<BlacklistConfig?> = runCatching {
        val j = JSONObject(api.get("/api/sms_forward_blacklist"))
        val p = j.s("phone")
        val k = j.s("keywords")
        if (p.isBlank() && k.isBlank()) null else BlacklistConfig(p, k)
    }

    override suspend fun setForwardBlacklist(cfg: BlacklistConfig): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("phone", cfg.phone)
            .put("keywords", cfg.keywords)
        api.post("/api/sms_forward_blacklist", body.toString())
        Unit
    }

    override suspend fun testForward(): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("address", "00000000000")
            .put("body", "星灵测试消息：短信转发链路正常")
            .put("is_sms", true)
            .put("timestamp", System.currentTimeMillis())
        api.post("/api/do_forward_msg", body.toString())
        Unit
    }

    override suspend fun getWifiAp(): Result<WifiApInfo> = runCatching {
        val j = goform.read("queryWiFiModuleSwitch,queryAccessPointInfo")
        val sw = j.s("WiFiModuleSwitch")
        // ResponseList 是数组，取激活的那个（AccessPointSwitchStatus=1）
        val list = j.optJSONArray("ResponseList")
        var info = JSONObject()
        if (list != null && list.length() > 0) {
            // 优先找 AccessPointSwitchStatus=1 的
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                if (item.optString("AccessPointSwitchStatus") == "1") {
                    info = item
                    break
                }
            }
            // 没找到激活的就取第一个
            if (info.length() == 0) {
                info = list.getJSONObject(0)
            }
        }
        WifiApInfo(
            enabled = sw == "1",
            ssid = info.s("SSID"),
            password = decodeBase64(info.s("Password")),
            authMode = info.s("AuthMode"),
            encrypType = info.s("EncrypType"),
            maxStation = info.s("ApMaxStationNumber").toIntOrNull() ?: 10,
            broadcastDisabled = info.s("ApBroadcastDisabled") == "1",
            isolate = info.s("ApIsolate") == "1",
            chipIndex = info.s("ChipIndex").toIntOrNull() ?: 0,
            accessPointIndex = info.s("AccessPointIndex").toIntOrNull() ?: 0
        )
    }

    override suspend fun setWifiAp(cfg: WifiApInfo): Result<Unit> = runCatching {
        goform.writeChecked(
            "setAccessPointInfo",
            mapOf(
                "SSID" to cfg.ssid,
                "AuthMode" to cfg.authMode,
                "EncrypType" to cfg.encrypType,
                "Password" to Base64.encodeToString(cfg.password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
                "ApMaxStationNumber" to cfg.maxStation.coerceAtLeast(1).toString(),
                "ApBroadcastDisabled" to if (cfg.broadcastDisabled) "1" else "0",
                "ApIsolate" to if (cfg.isolate) "1" else "0",
                "ChipIndex" to cfg.chipIndex.toString(),
                "AccessPointIndex" to cfg.accessPointIndex.toString()
            )
        )
        Unit
    }

    override suspend fun setWifiEnabled(enabled: Boolean, chipEnum: String): Result<Unit> = runCatching {
        if (enabled) {
            // 开启：switchWiFiChip（ChipEnum=chip1/chip2，GuestEnable=0）
            goform.writeChecked(
                "switchWiFiChip",
                mapOf("ChipEnum" to chipEnum, "GuestEnable" to "0")
            )
        } else {
            // 关闭：switchWiFiModule（SwitchOption=0），会断开当前 WiFi 连接
            goform.writeChecked("switchWiFiModule", mapOf("SwitchOption" to "0"))
        }
        Unit
    }

    override suspend fun hotspotBootAutostart(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/hotspot/boot-autostart"))
            .boolKey("enabled", "boot_autostart", "autostart", "value")
    }

    override suspend fun setHotspotBootAutostart(enabled: Boolean): Result<Unit> = runCatching {
        api.post("/api/hotspot/boot-autostart", JSONObject().put("enabled", if (enabled) "1" else "0").toString())
        Unit
    }

    override suspend fun hotspotBootAutostartDelay(): Result<Long> = runCatching {
        val j = JSONObject(api.get("/api/hotspot/boot-autostart-delay"))
        val raw = j.s("delay").ifBlank { j.s("value") }.ifBlank { j.s("boot_autostart_delay") }
        raw.toLongOrNull() ?: 0L
    }

    override suspend fun setHotspotBootAutostartDelay(delaySec: Long): Result<Unit> = runCatching {
        api.post("/api/hotspot/boot-autostart-delay", JSONObject().put("delay", delaySec.toString()).toString())
        Unit
    }

    override suspend fun calibrateFlow(usedBytes: Long?): Result<Unit> = runCatching {
        goform.writeChecked(
            "FLOW_CALIBRATION_MANUAL",
            mapOf(
                "calibration_way" to "0",
                "time" to "0",
                "data" to (usedBytes ?: 0L).toString()
            )
        )
        Unit
    }

    override suspend fun getDataLimit(): Result<DataLimit> = runCatching {
        val j = JSONObject(api.get("/api/get_data_limit"))
        DataLimit(
            enabled = j.s("data_flow_limit_enabled") == "1",
            maxLimit = j.s("data_flow_max_limit").ifBlank { "0" },
            period = j.s("data_flow_check_daily_or_monthly").ifBlank { "monthly" },
            checkReference = j.s("data_check_reference").ifBlank { "system" },
            statusForwardEnabled = j.s("data_limit_status_forward_enabled") == "1"
        )
    }

    override suspend fun setDataLimit(config: DataLimit): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("data_flow_limit_enabled", if (config.enabled) "1" else "0")
            .put("data_flow_max_limit", config.maxLimit.ifBlank { "0" })
            .put("data_flow_check_daily_or_monthly", config.period.ifBlank { "monthly" })
            .put("data_check_reference", config.checkReference.ifBlank { "system" })
            .put("data_limit_status_forward_enabled", if (config.statusForwardEnabled) "1" else "0")
        api.post("/api/set_data_limit", body.toString())
        Unit
    }

    override suspend fun rebootDevice(): Result<Unit> = runCatching {
        goform.writeChecked("REBOOT_DEVICE", emptyMap())
        Unit
    }

    override suspend fun shutdownDevice(): Result<Unit> = runCatching {
        goform.writeChecked("SHUTDOWN_DEVICE", emptyMap())
        Unit
    }

    override suspend fun getPerformanceMode(): Result<Boolean> = runCatching {
        goform.read("performance_mode").s("performance_mode") == "1"
    }

    override suspend fun setPerformanceMode(on: Boolean): Result<Unit> = runCatching {
        goform.writeChecked("PERFORMANCE_MODE_SETTING", mapOf("performance_mode" to if (on) "1" else "0"))
        Unit
    }

    override suspend fun listTasks(): Result<List<ScheduledTask>> = runCatching {
        val raw = api.get("/api/list_tasks")
        val arr = if (raw.trim().startsWith("{")) {
            JSONObject(raw).optJSONArray("tasks") ?: JSONArray()
        } else {
            JSONArray(raw)
        }
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val action = o.optJSONObject("actionMap") ?: o.optJSONObject("action") ?: JSONObject()
            ScheduledTask(
                key = o.optLong("key", 0),
                id = o.s("id"),
                time = o.s("time"),
                repeatDaily = o.optBoolean("repeatDaily", true),
                actionMap = action.keys().asSequence().associateWith { action.s(it) },
                lastRunTimestamp = o.optLong("lastRunTimestamp", 0),
                hasTriggered = o.optBoolean("hasTriggered", false)
            )
        }
    }

    override suspend fun addTask(id: String, time: String, repeatDaily: Boolean, actionMap: Map<String, String>): Result<Unit> = runCatching {
        val action = JSONObject()
        actionMap.forEach { (k, v) -> action.put(k, v) }
        val body = JSONObject()
            .put("id", id)
            .put("time", time)
            .put("repeatDaily", repeatDaily)
            .put("action", action)
        api.post("/api/add_task", body.toString())
        Unit
    }

    override suspend fun removeTask(id: String): Result<Unit> = runCatching {
        api.post("/api/remove_task", JSONObject().put("id", id).toString())
        Unit
    }

    override suspend fun clearTasks(): Result<Unit> = runCatching {
        api.post("/api/clear_task", "{}")
        Unit
    }

    // ═══════════════════ C. 高级功能批 ═══════════════════

    override suspend fun rootShell(command: String): Result<String> = runCatching {
        val body = JSONObject().put("command", command).put("timeout", 100000)
        val j = JSONObject(api.post("/api/root_shell", body.toString()))
        // result 可能是字符串直接返回，也可能是 {content: "..."}
        val result = j.opt("result")
        when (result) {
            is String -> result
            is JSONObject -> result.optString("content", "")
            else -> throw IllegalStateException("root_shell 执行失败：${j.optString("error")}")
        }
    }

    override suspend fun userShell(command: String): Result<String> = runCatching {
        val body = JSONObject().put("command", command)
        val j = JSONObject(api.post("/api/user_shell", body.toString()))
        val r = j.optJSONObject("result")
        r?.s("content") ?: throw IllegalStateException("user_shell 执行失败：${j.s("error")}")
    }

    override suspend fun setAdvancedFeatures(on: Boolean): Result<String> = runCatching {
        api.get("/api/smbPath?enable=${if (on) "1" else "0"}", timeout = 30000)
    }

    override suspend fun ttydAlive(port: Int): Result<Boolean> = runCatching {
        val j = JSONObject(api.get("/api/hasTTYD?port=$port", timeout = 15000))
        j.s("code") == "200"
    }

    override fun ttydUrl(port: Int): String = "http://$host:$port/"

    override suspend fun sshStatus(): Result<String> = runCatching {
        rootShell("ps -ef | grep -Ei 'sshd|dropbear' | grep -v grep || echo 'ssh: 未运行'; which sshd dropbear || echo 'ssh: 无内置服务端'").getOrThrow()
    }

    override suspend fun sshRun(command: String): Result<String> = runCatching {
        rootShell(command).getOrThrow()
    }

    override suspend fun getWirelessAdb(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/adb_wifi_setting")).s("enabled") == "true"
    }

    override suspend fun setWirelessAdb(on: Boolean, password: String): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("enabled", on)
            .put("password", password)
        api.post("/api/adb_wifi_setting", body.toString())
        Unit
    }

    override suspend fun adbAlive(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/adb_alive")).s("result") == "true"
    }

    override suspend fun checkUpdate(): Result<OtaInfo?> = runCatching {
        val j = JSONObject(api.get("/api/check_update", timeout = 30000))
        val versions = mutableListOf<String>()
        val alist = j.optJSONObject("alist_res")?.optJSONObject("data")
            ?.optJSONArray("content")
        if (alist != null) {
            for (i in 0 until alist.length()) {
                alist.getJSONObject(i).s("name").takeIf { it.isNotBlank() }?.let { versions.add(it) }
            }
        }
        if (j.s("base_uri").isBlank() && versions.isEmpty() && j.s("changelog").isBlank()) null
        else OtaInfo(j.s("base_uri"), versions, j.s("changelog"))
    }

    override suspend fun downloadApk(apkUrl: String): Result<Unit> = runCatching {
        api.post("/api/download_apk", JSONObject().put("apk_url", apkUrl).toString())
        Unit
    }

    override suspend fun downloadStatus(): Result<DownloadStatus?> = runCatching {
        val j = JSONObject(api.get("/api/download_apk_status"))
        val status = j.s("status")
        DownloadStatus(
            status = status,
            percent = j.optInt("percent", 0),
            error = j.s("error")
        ).takeIf { status.isNotBlank() }
    }

    override suspend fun installApk(): Result<Unit> = runCatching {
        api.post("/api/install_apk", "{}", timeout = 30000)
        Unit
    }

    override suspend fun disableFota(): Result<Unit> = runCatching {
        api.get("/api/disable_fota")
        Unit
    }

    override suspend fun pluginsStore(): Result<PluginStore?> = runCatching {
        val j = JSONObject(api.get("/api/plugins_store", timeout = 30000))
        val url = j.s("download_url")
        val content = j.optJSONObject("res")?.optJSONObject("data")?.optJSONArray("content")
        val list = mutableListOf<PluginItem>()
        if (content != null) {
            for (i in 0 until content.length()) {
                val o = content.getJSONObject(i)
                list.add(
                    PluginItem(
                        name = o.s("name"),
                        modified = o.s("modified"),
                        size = o.optLong("size", 0),
                        md5 = o.optJSONObject("hash_info")?.s("md5") ?: ""
                    )
                )
            }
        }
        if (url.isBlank() && list.isEmpty()) null else PluginStore(url, list)
    }

    override suspend fun getCustomHead(): Result<String> = runCatching {
        val j = JSONObject(api.getNoAuth("/api/get_custom_head"))
        j.s("text")
    }

    override suspend fun setCustomHead(text: String): Result<Unit> = runCatching {
        api.post("/api/set_custom_head", JSONObject().put("text", text).toString())
        Unit
    }

    override suspend fun speedtest(ckSize: Int): Result<SpeedtestResult> = runCatching {
        val (bytes, elapsed) = api.speedtestDownload(ckSize.coerceIn(1, 1024))
        val mbps = if (elapsed > 0) bytes * 8.0 / elapsed / 1000.0 else 0.0 // kbps
        SpeedtestResult(bytes, elapsed, mbps)
    }

    override suspend fun getAccessControl(): Result<AccessControl?> = runCatching {
        val j = goform.read("queryDeviceAccessControlList")
        val raw = j.opt("queryDeviceAccessControlList")
        val obj = when (raw) {
            is JSONObject -> raw
            is String -> try { JSONObject(raw) } catch (e: Exception) { return@runCatching null }
            else -> return@runCatching null
        }
        AccessControl(
            mode = obj.s("AclMode").ifBlank { "0" },
            whiteMacs = parseMacList(obj.opt("WhiteMacList")),
            blackMacs = parseMacList(obj.opt("BlackMacList")),
            whiteNames = parseNameList(obj.opt("WhiteNameList")),
            blackNames = parseNameList(obj.opt("BlackNameList"))
        )
    }

    override suspend fun setAccessControl(config: AccessControl): Result<Unit> = runCatching {
        goform.writeChecked(
            "setDeviceAccessControlList",
            mapOf(
                "AclMode" to config.mode,
                "WhiteMacList" to config.whiteMacs.joinToString("\n"),
                "BlackMacList" to config.blackMacs.joinToString("\n"),
                "WhiteNameList" to config.whiteNames.joinToString("\n"),
                "BlackNameList" to config.blackNames.joinToString("\n")
            )
        )
        Unit
    }

    override suspend fun getLanClients(): Result<List<LanClient>> = runCatching {
        val j = goform.read("station_list,lan_station_list")
        val result = mutableListOf<LanClient>()
        result.addAll(LanClientParser.parse(j.opt("station_list")))
        result.addAll(LanClientParser.parse(j.opt("lan_station_list")))
        result
    }

    override suspend fun getDhcp(): Result<DhcpConfig?> = runCatching {
        val j = goform.read("lan_ipaddr,lan_netmask,dhcpEnabled,dhcpStart,dhcpEnd,dhcpLease_hour")
        DhcpConfig(
            lanIp = j.s("lan_ipaddr"),
            netmask = j.s("lan_netmask"),
            dhcpEnabled = j.s("dhcpEnabled") == "1",
            start = j.s("dhcpStart"),
            end = j.s("dhcpEnd"),
            leaseHours = j.s("dhcpLease_hour").toIntOrNull() ?: 24
        )
    }

    override suspend fun setDhcp(config: DhcpConfig): Result<Unit> = runCatching {
        goform.writeChecked(
            "DHCP_SETTING",
            mapOf(
                "lanDhcpType" to if (config.dhcpEnabled) "1" else "0",
                "dhcpStart" to config.start,
                "dhcpEnd" to config.end,
                "dhcpLease" to config.leaseHours.coerceAtLeast(1).toString(),
                "dhcp_reboot_flag" to "1",
                "mac_ip_reset" to "0"
            )
        )
        Unit
    }

    override suspend fun getSambaSwitch(): Result<Boolean> = runCatching {
        goform.read("samba_switch").s("samba_switch") == "1"
    }

    override suspend fun setSamba(on: Boolean): Result<Unit> = runCatching {
        goform.writeChecked("SAMBA_SETTING", mapOf("samba_switch" to if (on) "1" else "0"))
        Unit
    }

    // ── 解析辅助 ──

    private fun JSONObject.s(key: String): String {
        val v = opt(key) ?: return ""
        return when (v) {
            is String -> v
            else -> v.toString()
        }
    }

    /** 多键名容错布尔解析：兼容 "1"/"0"、"true"/"false"、"on"/"off" */
    private fun JSONObject.boolKey(vararg keys: String): Boolean {
        for (k in keys) {
            val v = opt(k) ?: continue
            val s = when (v) {
                is String -> v
                is Boolean -> if (v) "1" else "0"
                is Int -> v.toString()
                is Long -> (v as Long).toString()
                else -> continue
            }
            if (s == "1" || s.equals("true", true) || s.equals("on", true)) return true
            if (s == "0" || s.equals("false", true) || s.equals("off", true)) return false
        }
        return false
    }

    private fun parseBands(raw: String): List<Int> =
        raw.split(',', ' ', '[', ']')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }

    private fun parseCells(raw: Any?): List<CellInfo> {
        val arr = when (raw) {
            is JSONArray -> raw
            is String -> try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
            is JSONObject -> JSONArray().put(raw)
            else -> return emptyList()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            CellInfo(
                pci = firstInt(o, "pci", "PCI", "scell_pci", "cell_pci", "nr_pci"),
                earfcn = firstInt(o, "earfcn", "EARFCN", "scell_earfcn", "cell_earfcn", "nr_earfcn", "lte_earfcn", "arfcn"),
                rat = o.s("rat").ifBlank { o.s("RAT") }.ifBlank { o.s("network_type") },
                rsrp = firstInt(o, "rsrp", "RSRP", "lte_rsrp", "Z5g_rsrp", "scell_rsrp"),
                rsrq = firstInt(o, "rsrq", "RSRQ", "lte_rsrq", "nr_rsrq"),
                sinr = firstInt(o, "sinr", "SINR", "snr", "NR_snr", "lte_sinr"),
                band = o.s("band").ifBlank { o.s("BAND") }.ifBlank { o.s("nr_band") },
                bandwidth = o.s("bandwidth").ifBlank { o.s("band_width") }.ifBlank { o.s("Nr_band_widths") }
            )
        }
    }

    private fun firstInt(o: JSONObject, vararg keys: String): Int {
        for (k in keys) {
            val v = o.opt(k)
            val s = when (v) {
                is String -> v
                is Int -> v.toString()
                is Long -> (v as Long).toString()
                is Double -> v.toString()
                else -> continue
            }.trim()
            s.toIntOrNull()?.let { return it }
            // 处理 "1800" 样式的字符串数字；跳过含非数字后缀
            if (s.isNotEmpty() && s.all { it.isDigit() }) s.toIntOrNull()?.let { return it }
        }
        return -1
    }

    private fun parseMacList(v: Any?): List<String> =
        parseListOf(v).filter { it.isNotBlank() }.distinct()

    private fun parseNameList(v: Any?): List<String> =
        parseListOf(v).filter { it.isNotBlank() }.distinct()

    /** 从对象(取键)/数组/字符串 提取字符串列表 */
    private fun parseListOf(v: Any?): List<String> {
        return when (v) {
            null -> emptyList()
            is JSONArray -> (0 until v.length()).map { idx ->
                when (val x = v.opt(idx)) {
                    is String -> x
                    is JSONObject -> x.keys().asSequence().firstOrNull() ?: ""
                    else -> x?.toString() ?: ""
                }
            }
            is JSONObject -> v.keys().asSequence().toList()
            is String -> v.split('\n', ',', ' ')
            else -> listOf(v.toString())
        }
    }

    private fun decodeSmSBody(base64: String): String = try {
        val raw = Base64.decode(base64, Base64.DEFAULT)
        String(raw, Charsets.UTF_8)
    } catch (e: Exception) {
        base64
    }

    private fun decodeBase64(v: String): String = try {
        if (v.isBlank()) ""
        else String(Base64.decode(v, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        v
    }

    /** UTF-16BE hex 编码（SEND_SMS） */
    private fun utf16Hex(text: String): String =
        text.toByteArray(java.nio.charset.StandardCharsets.UTF_16BE).joinToString("") { "%02x".format(it) }

    // ═══════════════════ D. 官方 UFIPanel 兼容批 ═══════════════════

    /** AT 终端：/api/AT?command=...&slot=N，返回设备输出文本 */
    override suspend fun atCommand(command: String, slot: Int): Result<String> = runCatching {
        val cmd = command.trim()
        require(cmd.isNotEmpty()) { "请输入 AT 指令" }
        require(cmd.startsWith("AT", ignoreCase = true)) { "指令必须以 AT 开头" }
        val path = "/api/AT?command=${UfiToolsApi.encode(cmd)}&slot=$slot"
        val text = api.get(path)
        try {
            val obj = JSONObject(text)
            val out = obj.optString("output").ifBlank { obj.optString("result") }
            if (out.isNotEmpty()) out else text
        } catch (e: Exception) {
            text
        }
    }

    /** 数据卡一键切换（SET_SIM_SLOT 0/1，官方「切换为默认数据卡」） */
    override suspend fun switchDataCard(slot: String): Result<Unit> = runCatching {
        goform.writeChecked("SET_SIM_SLOT", mapOf("sim_slot" to slot))
        Unit
    }

    /** 清除 SIM 锁定（UNLOCK_ALL_CELL 等价解锁全部小区，官方 u91 同源操作） */
    override suspend fun clearSimLock(): Result<Unit> = runCatching {
        goform.writeChecked("UNLOCK_ALL_CELL", emptyMap())
        Unit
    }

    /** 锁定当前服务小区频点（CELL_LOCK 仅按 EARFCN 下发，PCI=0 由设备匹配） */
    override suspend fun lockCurrentEarfcn(earfcn: Int, rat: String): Result<Unit> = runCatching {
        require(earfcn > 0) { "当前频点无效" }
        goform.writeChecked(
            "CELL_LOCK",
            mapOf("pci" to "0", "earfcn" to earfcn.toString(), "rat" to rat)
        )
        Unit
    }

    /** 设备昵称（别名）：/api/set_nickname 写入 */
    override suspend fun setNickname(nickname: String): Result<Unit> = runCatching {
        api.post("/api/set_nickname", JSONObject().put("nickname", nickname.trim().take(255)).toString())
        Unit
    }

    /** 设备昵称读取：/api/version_info 免认证，按常见字段依次尝试 */
    override suspend fun getNickname(): Result<String> = runCatching {
        val text = api.getNoAuth("/api/version_info")
        val obj = try {
            JSONObject(text)
        } catch (e: Exception) {
            return@runCatching ""
        }
        obj.optString("nickname").ifBlank { obj.optString("alias") }
            .ifBlank { obj.optString("device_alias") }
            .ifBlank { obj.optString("device_name") }
            .ifBlank { obj.optString("name") }
            .ifBlank { obj.optString("别名") }
    }

    /** HTTP 代理调试：/api/proxy/--<目标URL>，签名使用原始完整路径（含 query） */
    override suspend fun proxyDebug(method: String, url: String): Result<ProxyDebugResult> = runCatching {
        val target = url.trim().removePrefix("--").trim()
        require(target.startsWith("http://") || target.startsWith("https://")) { "目标 URL 必须以 http:// 或 https:// 开头" }
        val path = "/api/proxy/--$target"
        val resp = api.requestEx(method, path, signPath = path)
        ProxyDebugResult(
            method = method,
            targetUrl = target,
            httpCode = resp.statusCode,
            contentType = resp.header("content-type") ?: "",
            body = resp.body.take(4000) // 响应体截断展示，避免超大
        )
    }

    // ═══════════════════ E. 高级后台兼容批（rootShell 软件侧能力）═══════════════════

    /** 数据开关：CONNECT_NETWORK / DISCONNECT_NETWORK 切换 */
    override suspend fun toggleCellularData(): Result<Unit> = runCatching {
        goform.writeChecked("DISCONNECT_NETWORK", emptyMap())
        Unit
    }

    /** 网络漫游：SET_CONNECTION_MODE + roam_setting_option 切换 */
    override suspend fun toggleRoaming(): Result<Unit> = runCatching {
        val cur = goform.read("roam_setting_option").optString("roam_setting_option", "off")
        val next = if (cur == "on") "off" else "on"
        goform.writeChecked(
            "SET_CONNECTION_MODE",
            mapOf(
                "ConnectionMode" to "auto_dial",
                "roam_setting_option" to next,
                "dial_roam_setting_option" to "on"
            )
        )
        Unit
    }

    /** 指示灯：INDICATOR_LIGHT_SETTING + indicator_light_switch 切换 */
    override suspend fun toggleIndicatorLight(): Result<Unit> = runCatching {
        val cur = goform.read("indicator_light_switch").optString("indicator_light_switch", "1")
        val next = if (cur == "1") "0" else "1"
        goform.writeChecked("INDICATOR_LIGHT_SETTING", mapOf("indicator_light_switch" to next))
        Unit
    }

    /** 设备标识：goform 读 imei/imsi/iccid/msisdn 等 */
    override suspend fun deviceIdentity(): Result<Map<String, String>> = runCatching {
        val j = goform.read("imei", "imsi", "iccid", "sim_imsi", "sim_iccid", "msisdn", "local_ip", "client_ip")
        mapOf(
            "IMEI" to j.optString("imei"),
            "IMSI" to j.optString("imsi"),
            "ICCID" to j.optString("iccid"),
            "SIM IMSI" to j.optString("sim_imsi"),
            "SIM ICCID" to j.optString("sim_iccid"),
            "本机号码" to j.optString("msisdn"),
            "客户端 IP" to j.optString("client_ip"),
            "本地 IP" to j.optString("local_ip")
        ).filterValues { it.isNotBlank() }
    }

    /** 一键禁用系统更新：逐条 disable-user + uninstall -k --user 0，保留设备原始回显 */
    override suspend fun disableSystemUpdateComponents(): Result<List<UpdateComponentResult>> = runCatching {
        ZTE_UPDATE_COMPONENTS.map { pkg ->
            val disableOut = rootShell("pm disable-user --user 0 $pkg 2>&1")
                .getOrElse { "执行失败：${it.message ?: it}" }
            val uninstallOut = rootShell("pm uninstall -k --user 0 $pkg 2>&1")
                .getOrElse { "执行失败：${it.message ?: it}" }
            UpdateComponentResult(
                pkg = pkg,
                disableOutput = disableOut.trim().take(400),
                uninstallOutput = uninstallOut.trim().take(400)
            )
        }
    }

    /** 现存 ZTE 相关包查询（禁用前后对比用） */
    override suspend fun zteComponentStatus(): Result<String> = runCatching {
        rootShell("pm list packages | grep -Ei 'zte|zdm|aftersale|neopush|analytics' || echo '未发现 ZTE 相关包'")
            .getOrThrow().trim()
    }

    /** 当前系统 AB 分区后缀（getprop ro.boot.slot_suffix） */
    override suspend fun currentSlotSuffix(): Result<String> = runCatching {
        rootShell("getprop ro.boot.slot_suffix").getOrThrow().trim()
    }

    /** 提取当前分区 BOOT 镜像到后台 uploads 目录 */
    override suspend fun extractBootImage(): Result<BootImageInfo> = runCatching {
        val slot = rootShell("getprop ro.boot.slot_suffix").getOrThrow().trim()
        require(slot.isNotBlank()) { "未读取到 ro.boot.slot_suffix，该设备可能不是 A/B 分区机型" }
        val part = "/dev/block/by-name/boot$slot"
        val fileName = "boot$slot.img"
        val dest = "$uploadsDir/$fileName"
        val cmd = "mkdir -p $uploadsDir && dd if=$part of=$dest bs=4096 && ls -l $dest"
        val out = rootShell(cmd).getOrThrow().trim()
        BootImageInfo(
            slotSuffix = slot,
            devicePath = dest,
            fileName = fileName,
            rawOutput = out.take(600)
        )
    }

    /** 经 /api/uploads/<file> 下载后台文件到本机 */
    override suspend fun downloadUpload(fileName: String, destFile: java.io.File): Result<Long> = runCatching {
        val name = fileName.trim().substringAfterLast('/')
        require(name.isNotBlank()) { "文件名不能为空" }
        api.downloadFile("/api/uploads/${UfiToolsApi.encode(name)}", destFile)
    }

    /** 展锐 DIAG 设备节点检测 */
    override suspend fun diagChannels(): Result<String> = runCatching {
        rootShell("ls -l /dev/sdiag* 2>/dev/null || echo '未发现 /dev/sdiag* 节点（可能非展锐平台或未开放 DIAG）'")
            .getOrThrow().trim()
    }

    /** DIAG 帧透传：写 /dev/sdiag_nr 后读取响应（十六进制回显） */
    override suspend fun diagSend(frameHex: String, slot: Int, readBytes: Int): Result<String> = runCatching {
        val hex = frameHex.replace(" ", "").replace("0x", "").replace("\\x", "").trim()
        require(hex.isNotEmpty()) { "请输入 DIAG 帧（十六进制，可含空格）" }
        require(hex.length % 2 == 0) { "DIAG 帧十六进制长度必须为偶数" }
        require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "DIAG 帧只允许 0-9 / a-f" }
        val dev = if (slot == 1) "/dev/sdiag_nr2" else "/dev/sdiag_nr"
        val count = readBytes.coerceIn(16, 4096)
        val cmd = "printf '$hex' | xxd -r -p > $dev 2>/dev/null || printf '$hex' | toybox xxd -r -p > $dev 2>/dev/null; " +
            "sync; sleep 1; " +
            "timeout 3 dd if=$dev bs=1 count=$count 2>/dev/null | od -An -tx1 | tr -d ' \\n'"
        val out = rootShell(cmd).getOrThrow().trim()
        out.ifBlank { "无响应（设备未返回 DIAG 数据，请确认机型与帧格式）" }
    }

    // ═══════════════════ F. 主题与文件管理 / 增强转发（新增）═══════════════════

    override suspend fun getPowerForwardEnabled(): Result<Boolean> = runCatching {
        JSONObject(api.get("/api/power_status_forward_enabled")).optString("enabled", "0") == "1"
    }

    override suspend fun setPowerForwardEnabled(enabled: Boolean): Result<Unit> = runCatching {
        api.get("/api/power_status_forward_enabled?enable=${if (enabled) 1 else 0}")
        Unit
    }

    override suspend fun sendForwardTest(address: String, body: String, isSms: Boolean): Result<Unit> = runCatching {
        val payload = JSONObject()
            .put("address", address)
            .put("body", body)
            .put("is_sms", isSms)
            .put("timestamp", System.currentTimeMillis())
        api.post("/api/do_forward_msg", payload.toString())
        Unit
    }

    override suspend fun fetchCustomPluginStore(storeUrl: String): Result<PluginStore?> = runCatching {
        val target = storeUrl.trim()
        require(target.startsWith("http://") || target.startsWith("https://")) { "插件源地址必须以 http:// 或 https:// 开头" }
        val path = "/api/proxy/--$target"
        val text = api.requestEx("GET", path, signPath = path, timeout = 30000).body
        val j = JSONObject(text)
        val url = j.optString("download_url", "")
        val content = j.optJSONObject("res")?.optJSONObject("data")?.optJSONArray("content")
        val list = mutableListOf<PluginItem>()
        if (content != null) {
            for (i in 0 until content.length()) {
                val o = content.getJSONObject(i)
                list.add(
                    PluginItem(
                        name = o.optString("name", ""),
                        modified = o.optString("modified", ""),
                        size = o.optLong("size", 0),
                        md5 = o.optJSONObject("hash_info")?.optString("md5") ?: ""
                    )
                )
            }
        }
        if (url.isBlank() && list.isEmpty()) null else PluginStore(url, list)
    }

    override suspend fun getTheme(): Result<ThemeConfig> = runCatching {
        val text = api.getNoAuth("/api/get_theme")
        val j = JSONObject(text)
        ThemeConfig(
            backgroundEnabled = j.optString("backgroundEnabled", "false") == "true",
            backgroundUrl = j.optString("backgroundUrl", ""),
            textColor = j.optString("textColor", "rgba(255, 255, 255, 1)"),
            textColorPer = j.optString("textColorPer", "100"),
            themeColor = j.optString("themeColor", "201"),
            colorPer = j.optString("colorPer", "67"),
            saturationPer = j.optString("saturationPer", "100"),
            brightPer = j.optString("brightPer", "21"),
            opacityPer = j.optString("opacityPer", "21"),
            blurSwitch = j.optString("blurSwitch", "true") == "true",
            overlaySwitch = j.optString("overlaySwitch", "true") == "true"
        )
    }

    override suspend fun setTheme(config: ThemeConfig): Result<Unit> = runCatching {
        val payload = JSONObject()
            .put("backgroundEnabled", if (config.backgroundEnabled) "true" else "false")
            .put("backgroundUrl", config.backgroundUrl)
            .put("textColor", config.textColor)
            .put("textColorPer", config.textColorPer)
            .put("themeColor", config.themeColor)
            .put("colorPer", config.colorPer)
            .put("saturationPer", config.saturationPer)
            .put("brightPer", config.brightPer)
            .put("opacityPer", config.opacityPer)
            .put("blurSwitch", if (config.blurSwitch) "true" else "false")
            .put("overlaySwitch", if (config.overlaySwitch) "true" else "false")
        api.post("/api/set_theme", payload.toString())
        Unit
    }

    override suspend fun uploadFile(fileName: String, fileBytes: ByteArray): Result<String> = runCatching {
        val name = fileName.trim().substringAfterLast('/')
        require(name.isNotBlank()) { "文件名不能为空" }
        val text = api.uploadFile(name, fileBytes)
        val url = try { JSONObject(text).optString("url", "") } catch (_: Exception) { "" }
        url.ifBlank { text }
    }

    override suspend fun deleteUploadedFile(fileName: String): Result<Unit> = runCatching {
        val name = fileName.trim().substringAfterLast('/')
        require(name.isNotBlank()) { "文件名不能为空" }
        api.post("/api/delete_img", JSONObject().put("file_name", name).toString())
        Unit
    }

    override suspend fun clearAllUploads(): Result<Map<String, Boolean>> = runCatching {
        val text = api.post("/api/delete_all_uploads_data", "{}")
        val j = JSONObject(text)
        val deleted = j.optJSONObject("deleted_list")
        val result = mutableMapOf<String, Boolean>()
        if (deleted != null) {
            for (k in deleted.keys()) {
                result[k] = deleted.optBoolean(k, false)
            }
        }
        result
    }

    override suspend fun listUploads(): Result<List<UploadedFile>> = runCatching {
        val out = rootShell("ls -l --time-style=long-iso $uploadsDir 2>/dev/null || echo 'EMPTY'")
            .getOrThrow().trim()
        if (out.isBlank() || out == "EMPTY" || out.startsWith("ls:")) return@runCatching emptyList()
        out.lines().mapNotNull { line ->
            // -rw-rw---- 1 u0_aXXX u0_aXXX 12345 2026-01-01 12:00 filename.ext
            val parts = line.split(Regex("\\s+"), limit = 7)
            if (parts.size < 7) return@mapNotNull null
            val size = parts.getOrNull(4)?.toLongOrNull() ?: 0
            val date = parts.getOrNull(5) ?: ""
            val time = parts.getOrNull(6)?.substringBeforeLast(' ') ?: ""
            val name = parts.getOrNull(6)?.substringAfterLast(' ') ?: ""
            if (name.isBlank() || name == "." || name == "..") return@mapNotNull null
            UploadedFile(name = name, size = size, modified = "$date $time")
        }
    }

    // ═══════════════════ G. CPU 核心控制（P1）═══════════════════

    override suspend fun getCpuCoreInfo(): Result<List<CpuCoreInfo>> = runCatching {
        val countOut = rootShell("cat /sys/devices/system/cpu/possible 2>/dev/null || echo 0-7").getOrThrow().trim()
        val maxCore = try {
            countOut.substringAfterLast('-').toIntOrNull() ?: 7
        } catch (_: Exception) { 7 }
        (0..maxCore).map { core ->
            val online = try {
                rootShell("cat /sys/devices/system/cpu/cpu$core/online 2>/dev/null || echo 1").getOrThrow().trim() == "1"
            } catch (_: Exception) { true }
            val governor = try {
                rootShell("cat /sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor 2>/dev/null").getOrThrow().trim()
            } catch (_: Exception) { "" }
            val maxFreq = try {
                rootShell("cat /sys/devices/system/cpu/cpu$core/cpufreq/scaling_max_freq 2>/dev/null").getOrThrow().trim().toIntOrNull() ?: 0
            } catch (_: Exception) { 0 }
            val curFreq = try {
                rootShell("cat /sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq 2>/dev/null").getOrThrow().trim().toIntOrNull() ?: 0
            } catch (_: Exception) { 0 }
            CpuCoreInfo(core, online, governor, maxFreq, curFreq)
        }
    }

    override suspend fun setCpuCoreOnline(core: Int, online: Boolean): Result<Unit> = runCatching {
        require(core > 0) { "CPU0 为引导核心，不可关闭" }
        rootShell("echo ${if (online) 1 else 0} > /sys/devices/system/cpu/cpu$core/online").getOrThrow()
        Unit
    }

    override suspend fun getCpuAvailableGovernors(): Result<List<String>> = runCatching {
        val out = rootShell("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null").getOrThrow().trim()
        out.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    override suspend fun setCpuGovernor(core: Int, governor: String): Result<Unit> = runCatching {
        rootShell("echo $governor > /sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor 2>/dev/null").getOrThrow()
        Unit
    }

    override suspend fun getCpuAvailableFreqs(core: Int): Result<List<Int>> = runCatching {
        val out = rootShell("cat /sys/devices/system/cpu/cpu$core/cpufreq/scaling_available_frequencies 2>/dev/null").getOrThrow().trim()
        out.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }.sorted()
    }

    override suspend fun setCpuMaxFreq(core: Int, freqKhz: Int): Result<Unit> = runCatching {
        rootShell("echo $freqKhz > /sys/devices/system/cpu/cpu$core/cpufreq/scaling_max_freq").getOrThrow()
        Unit
    }

    // ═══════════════════ G. 电池定量停充（P1）═══════════════════

    override suspend fun getBatteryChargeInfo(): Result<BatteryChargeInfo> = runCatching {
        val nodes = listOf(
            "/sys/class/power_supply/battery/charge_control_limit",
            "/sys/class/power_supply/battery/charge_control_limit_max",
            "/sys/class/power_supply/battery/battery_charging_enabled",
            "/sys/class/power_supply/battery/charging_enabled"
        )
        var foundNode = ""
        var limit = 100
        var charging = true
        val raw = StringBuilder()
        for (node in nodes) {
            val out = rootShell("cat $node 2>/dev/null").getOrNull()?.trim()
            if (!out.isNullOrBlank()) {
                raw.append("$node = $out\n")
                if (foundNode.isBlank()) foundNode = node
                if (node.contains("charge_control_limit") && !node.contains("max")) {
                    limit = out.toIntOrNull()?.coerceIn(0, 100) ?: 100
                }
                if (node.contains("charging_enabled")) {
                    charging = out == "1" || out.equals("true", ignoreCase = true)
                }
            }
        }
        BatteryChargeInfo(foundNode, limit, charging, raw.toString().trim())
    }

    override suspend fun setBatteryChargeLimit(percent: Int): Result<Unit> = runCatching {
        val p = percent.coerceIn(0, 100)
        rootShell("echo $p > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null || echo $p > /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null").getOrThrow()
        Unit
    }

    override suspend fun setBatteryChargingEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val v = if (enabled) 1 else 0
        rootShell("echo $v > /sys/class/power_supply/battery/battery_charging_enabled 2>/dev/null || echo $v > /sys/class/power_supply/battery/charging_enabled 2>/dev/null").getOrThrow()
        Unit
    }

    // ═══════════════════ G. 开机自启脚本（P1）═══════════════════

    private val initDDir = "/data/local/tmp/init.d"

    override suspend fun listBootScripts(): Result<List<BootScript>> = runCatching {
        rootShell("mkdir -p $initDDir && ls -la $initDDir").getOrThrow()
        val out = rootShell("for f in $initDDir/*; do [ -f \"\$f\" ] && echo \"\$(basename \$f)|\$(test -x \"\$f\" && echo 1 || echo 0)|\$(cat \"\$f\")\"; done 2>/dev/null").getOrThrow().trim()
        if (out.isBlank()) return@runCatching emptyList()
        out.lines().mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size < 3) return@mapNotNull null
            BootScript(name = parts[0], enabled = parts[1] == "1", content = parts[2])
        }
    }

    override suspend fun saveBootScript(name: String, content: String): Result<Unit> = runCatching {
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        require(safeName.isNotBlank()) { "脚本名不能为空" }
        val path = "$initDDir/$safeName"
        val script = if (content.startsWith("#!")) content else "#!/system/bin/sh\n$content"
        rootShell("mkdir -p $initDDir && cat > $path << 'SCRIPT_EOF'\n$script\nSCRIPT_EOF\nchmod +x $path").getOrThrow()
        Unit
    }

    override suspend fun deleteBootScript(name: String): Result<Unit> = runCatching {
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        rootShell("rm -f $initDDir/$safeName").getOrThrow()
        Unit
    }

    override suspend fun setBootScriptEnabled(name: String, enabled: Boolean): Result<Unit> = runCatching {
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val path = "$initDDir/$safeName"
        rootShell(if (enabled) "chmod +x $path" else "chmod -x $path").getOrThrow()
        Unit
    }

    override suspend fun runBootScript(name: String): Result<String> = runCatching {
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        rootShell("sh $initDDir/$safeName 2>&1").getOrThrow().trim()
    }

    // ═══════════════════ G. Crontab（P1）═══════════════════

    override suspend fun getCrontab(): Result<String> = runCatching {
        rootShell("crontab -l 2>/dev/null || echo '# 暂无 crontab 任务'").getOrThrow().trim()
    }

    override suspend fun setCrontab(content: String): Result<Unit> = runCatching {
        val tmp = "/data/local/tmp/crontab_tmp"
        rootShell("cat > $tmp << 'CRON_EOF'\n$content\nCRON_EOF\ncrontab $tmp && rm -f $tmp").getOrThrow()
        Unit
    }

    // ═══════════════════ H. 二进制服务管理（P2）═══════════════════

    private val binaryDir = "/data/local/tmp"

    private suspend fun detectBinary(binaryName: String, port: Int): BinaryServiceStatus {
        val which = rootShell("which $binaryName 2>/dev/null || ls $binaryDir/$binaryName 2>/dev/null").getOrNull()?.trim()
        val installed = !which.isNullOrBlank()
        val ps = rootShell("ps | grep $binaryName | grep -v grep").getOrNull()?.trim() ?: ""
        val running = ps.isNotBlank()
        val pid = ps.split(Regex("\\s+")).getOrNull(1) ?: ""
        val version = rootShell("$binaryName --version 2>&1 | head -1").getOrNull()?.trim() ?: ""
        return BinaryServiceStatus(installed, running, pid, port, version, ps)
    }

    override suspend fun getAdGuardStatus(): Result<BinaryServiceStatus> = runCatching {
        detectBinary("AdGuardHome", 3000)
    }

    override suspend fun installAdGuard(): Result<String> = runCatching {
        val arch = rootShell("uname -m").getOrThrow().trim()
        val url = when {
            arch.contains("aarch64") -> "https://github.com/AdguardTeam/AdGuardHome/releases/latest/download/AdGuardHome_linux_arm64.tar.gz"
            arch.contains("arm") -> "https://github.com/AdguardTeam/AdGuardHome/releases/latest/download/AdGuardHome_linux_armv7.tar.gz"
            else -> "https://github.com/AdguardTeam/AdGuardHome/releases/latest/download/AdGuardHome_linux_amd64.tar.gz"
        }
        rootShell("cd $binaryDir && curl -L -o adguard.tar.gz '$url' && tar xzf adguard.tar.gz && mv AdGuardHome/AdGuardHome . && chmod +x AdGuardHome && rm -rf AdGuardHome adguard.tar.gz").getOrThrow()
        "安装完成：$binaryDir/AdGuardHome"
    }

    override suspend fun startAdGuard(): Result<Unit> = runCatching {
        rootShell("cd $binaryDir && nohup ./AdGuardHome -s run > /dev/null 2>&1 &").getOrThrow()
        Unit
    }

    override suspend fun stopAdGuard(): Result<Unit> = runCatching {
        rootShell("pkill -f AdGuardHome 2>/dev/null; $binaryDir/AdGuardHome -s stop 2>/dev/null").getOrThrow()
        Unit
    }

    override suspend fun getEasyTierStatus(): Result<BinaryServiceStatus> = runCatching {
        detectBinary("easytier-core", 11010)
    }

    override suspend fun installEasyTier(): Result<String> = runCatching {
        val arch = rootShell("uname -m").getOrThrow().trim()
        val url = "https://github.com/EasyTier/EasyTier/releases/latest/download/easytier-linux-${if (arch.contains("aarch64")) "aarch64" else "x86_64"}.zip"
        rootShell("cd $binaryDir && curl -L -o easytier.zip '$url' && unzip -o easytier.zip && chmod +x easytier-core && rm -f easytier.zip").getOrThrow()
        "安装完成：$binaryDir/easytier-core"
    }

    override suspend fun startEasyTier(config: EasyTierConfig): Result<Unit> = runCatching {
        val cmd = buildString {
            append("cd $binaryDir && nohup ./easytier-core --instance-name xingling ")
            append("--listen ${config.nodeAddress.ifBlank { "tcp://0.0.0.0:11010" }} ")
            if (config.secret.isNotBlank()) append("--secret ${config.secret} ")
            if (config.peers.isNotBlank()) append("--peers ${config.peers} ")
            append("> /dev/null 2>&1 &")
        }
        rootShell(cmd).getOrThrow()
        Unit
    }

    override suspend fun stopEasyTier(): Result<Unit> = runCatching {
        rootShell("pkill -f easytier-core 2>/dev/null").getOrThrow()
        Unit
    }

    override suspend fun getEasyConnectStatus(): Result<BinaryServiceStatus> = runCatching {
        detectBinary("easyconnect", 443)
    }

    override suspend fun installEasyConnect(): Result<String> = runCatching {
        "EasyConnect 需手动下载 ARM 版本二进制到 $binaryDir 目录并 chmod +x。官方下载：https://vpn.sangfor.com.cn"
    }

    override suspend fun startEasyConnect(username: String, password: String, server: String): Result<Unit> = runCatching {
        rootShell("cd $binaryDir && echo '$password' | nohup ./easyconnect --server $server --user $username --passwd-stdin > /dev/null 2>&1 &").getOrThrow()
        Unit
    }

    override suspend fun stopEasyConnect(): Result<Unit> = runCatching {
        rootShell("pkill -f easyconnect 2>/dev/null").getOrThrow()
        Unit
    }
}
