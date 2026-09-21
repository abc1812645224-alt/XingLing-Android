/*
 * 星灵 (XingLing) · 高级功能抽象与数据模型
 *
 * DeviceFeatures 是「总览/信号」之外全部设备控制能力的抽象接口，由
 * UfiToolsFeatureApi 实现（真正调用 UFI-TOOLS /api 与 goform 反代）。
 * 每个方法带失败默认实现，便于 UI 统一「后台不支持 → 空态说明」。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

import org.json.JSONArray
import org.json.JSONObject

/** 后台不支持该功能时的失败原因 */
class FeatureUnsupported : UnsupportedOperationException("当前设备后台不支持此功能")

// ── 数据模型 ──

/** 邻区 / 已锁基站信息（来自官方 neighbor_cell_info / locked_cell_info） */
data class CellInfo(
    val pci: Int = -1,
    val earfcn: Int = -1,
    val rat: String = "",
    val rsrp: Int = Int.MIN_VALUE,
    val rsrq: Int = Int.MIN_VALUE,
    val sinr: Int = Int.MIN_VALUE,
    val band: String = "",
    val bandwidth: String = ""
)

/** APN 当前配置摘要 */
data class ApnInfo(
    val mode: String = "",          // auto / manual
    val currentIndex: Int = -1,
    val profileName: String = "",
    val apn: String = "",
    val pdpType: String = "",
    val dialNumber: String = ""
)

/** 保存/编辑 APN 用的表单字段 */
data class ApnProfile(
    val index: Int = 0,
    val profileName: String = "",
    val apn: String = "",
    val pdpType: String = "IPV4",
    val username: String = "",
    val password: String = "",
    val authMode: String = "PAP",
    val pdpAddr: String = ""
)

/** 收件箱短信 */
data class SmsMessage(
    val id: String = "",
    val number: String = "",
    val body: String = "",
    val date: String = "",
    val unread: Boolean = false
)

/** 短信转发 · 钉钉 */
data class DingtalkConfig(
    val webhookUrl: String = "",
    val secret: String = "",
    val forwardDevInfo: Boolean = false
)

/** 短信转发 · SMTP 邮件 */
data class MailConfig(
    val smtpHost: String = "",
    val smtpPort: String = "465",
    val smtpTo: String = "",
    val smtpUsername: String = "",
    val smtpPassword: String = "",
    val forwardDevInfo: Boolean = false
)

/** 短信转发 · curl 模板 */
data class CurlForwardConfig(val curlText: String = "")

/** 短信转发 · 黑名单 */
data class BlacklistConfig(val phone: String = "", val keywords: String = "")

/** WiFi 热点配置 */
data class WifiApInfo(
    val enabled: Boolean = false,
    val ssid: String = "",
    val password: String = "",
    val authMode: String = "WPA2PSK",
    val encrypType: String = "TKIPAES",
    val maxStation: Int = 10,
    val broadcastDisabled: Boolean = false,
    val isolate: Boolean = false,
    val chipIndex: Int = 0,
    val accessPointIndex: Int = 0
)

/** 定时任务（list_tasks 返回） */
data class ScheduledTask(
    val key: Long = 0,
    val id: String = "",
    val time: String = "",
    val repeatDaily: Boolean = true,
    val actionMap: Map<String, String> = emptyMap(),
    val lastRunTimestamp: Long = 0,
    val hasTriggered: Boolean = false
)

/** OTA 检查结果 */
data class OtaInfo(
    val baseUri: String = "",
    val versions: List<String> = emptyList(),
    val changelog: String = ""
)

/** APK 下载 / 安装状态 */
data class DownloadStatus(
    val status: String = "idle", // idle / downloading / done / error
    val percent: Int = 0,
    val error: String = ""
)

/** 插件市场 */
data class PluginItem(
    val name: String = "",
    val modified: String = "",
    val size: Long = 0,
    val md5: String = ""
)

data class PluginStore(
    val downloadUrl: String = "",
    val plugins: List<PluginItem> = emptyList()
)

/** 测速结果 */
data class SpeedtestResult(
    val bytes: Long = 0,
    val elapsedMs: Long = 0,
    val mbps: Double = 0.0
) {
    val humanSize: String get() = formatBytes(bytes)
    val humanSpeed: String get() = if (elapsedMs <= 0) "--" else "%.1f MB/s".format(bytes / 1048576.0 / (elapsedMs / 1000.0))
}

/** 客户端管理（无线 + 有线终端 / 黑白名单） */
data class LanClient(
    val name: String = "",
    val mac: String = "",
    val ip: String = "",
    val online: Boolean = true
)

/**
 * 官方后台 station_list / lan_station_list 解析器（无线 / 有线在线终端）。
 *
 * 标准形态为 JSON 数组（部分固件以字符串形式承载）：
 *   [{"hostname":"手机","ip_addr":"192.168.0.102","mac_addr":"aa:bb:..","online":"1"}]
 * 少数固件为 mac → info 的对象形态，两者都兼容。无有效字段时返回空列表。
 */
object LanClientParser {

    fun parse(v: Any?): List<LanClient> {
        // 1) 数组形态（含 JSON 字符串包裹的数组）
        val array: JSONArray? = when (v) {
            is JSONArray -> v
            is String -> try {
                JSONArray(v)
            } catch (e: Exception) {
                null
            }
            else -> null
        }
        if (array != null) {
            return (0 until array.length()).mapNotNull { i ->
                val info = array.optJSONObject(i) ?: return@mapNotNull null
                val mac = info.optString("mac_addr", info.optString("mac", "")).trim()
                if (mac.isEmpty()) return@mapNotNull null
                LanClient(
                    name = info.optString("hostname", info.optString("name", "")),
                    mac = mac,
                    ip = info.optString("ip_addr", info.optString("ip", "")),
                    online = info.optString("online", "1") != "0"
                )
            }
        }

        // 2) mac → info 对象形态（同样可能是 JSON 字符串）
        val obj = when (v) {
            is JSONObject -> v
            is String -> try {
                JSONObject(v)
            } catch (e: Exception) {
                return emptyList()
            }
            else -> return emptyList()
        }
        return obj.keys().asSequence().mapNotNull { mac ->
            val entry = obj.opt(mac)
            val info = when (entry) {
                is JSONObject -> entry
                is String -> try {
                    JSONObject(entry)
                } catch (e: Exception) {
                    return@mapNotNull null
                }
                else -> return@mapNotNull null
            }
            LanClient(
                name = info.optString("name", "") + info.optString("hostname", ""),
                mac = mac,
                ip = info.optString("ip", "") + info.optString("IP", "") +
                    info.optString("ip_addr", ""),
                online = info.optString("online", "1") != "0"
            )
        }.toList()
    }
}

data class AccessControl(
    val mode: String = "0",          // 0=关闭 1=白名单 2=黑名单
    val whiteMacs: List<String> = emptyList(),
    val blackMacs: List<String> = emptyList(),
    val whiteNames: List<String> = emptyList(),
    val blackNames: List<String> = emptyList()
)

data class DhcpConfig(
    val lanIp: String = "",
    val netmask: String = "",
    val dhcpEnabled: Boolean = true,
    val start: String = "",
    val end: String = "",
    val leaseHours: Int = 24
)

/** 流量限额（对应官方 /api/get_data_limit / set_data_limit） */
data class DataLimit(
    val enabled: Boolean = false,              // data_flow_limit_enabled
    val maxLimit: String = "0",                // data_flow_max_limit（GB）
    val period: String = "monthly",            // data_flow_check_daily_or_monthly: daily / monthly
    val checkReference: String = "system",     // data_check_reference: system / manual
    val statusForwardEnabled: Boolean = false  // data_limit_status_forward_enabled
)

/** HTTP 代理调试结果（/api/proxy/--<目标URL>，仅展示请求/响应文本） */
data class ProxyDebugResult(
    val method: String = "GET",
    val targetUrl: String = "",
    val httpCode: Int = 0,
    val contentType: String = "",
    val body: String = ""
)

/**
 * ZTE 系统更新 / 设备管理组件清单（一键禁用系统更新目标）。
 * 依据高级后台插件「禁用系统更新」原文：pm disable + pm uninstall -k --user 0。
 */
val ZTE_UPDATE_COMPONENTS: List<String> = listOf(
    "com.zte.zdm",
    "cn.zte.aftersale",
    "com.zte.zdmdaemon",
    "com.zte.zdmdaemon.install",
    "com.zte.analytics",
    "com.zte.neopush"
)

/** 系统更新组件逐条处理结果（disable / uninstall 分别回显设备原始输出） */
data class UpdateComponentResult(
    val pkg: String = "",
    val disableOutput: String = "",
    val uninstallOutput: String = ""
) {
    /** 该组件是否已从当前用户卸载（设备返回 Success 即视为生效） */
    val uninstalled: Boolean
        get() = uninstallOutput.contains("Success", true)

    val disabled: Boolean
        get() = disableOutput.contains("new state: disabled", true) ||
            disableOutput.contains("Success", true)
}

/** 当前分区 BOOT 镜像提取结果 */
data class BootImageInfo(
    val slotSuffix: String = "",     // ro.boot.slot_suffix，如 _a / _b
    val devicePath: String = "",     // 设备侧镜像路径（uploads 目录）
    val fileName: String = "",       // 文件名，如 boot_a.img
    val rawOutput: String = ""       // dd / ls 原始回显
)

// ── 补齐功能批数据模型（UFI-TOOLS 有但星灵缺失）──

/** CPU 核心状态快照 */
data class CpuCoreInfo(
    val core: Int = 0,
    val online: Boolean = true,
    val governor: String = "",
    val maxFreqKhz: Int = 0,
    val curFreqKhz: Int = 0
)

/** 电池充电控制信息 */
data class BatteryChargeInfo(
    val controlNode: String = "",     // 探测到的充电控制节点路径
    val chargeLimitPercent: Int = 100,
    val chargingEnabled: Boolean = true,
    val rawOutput: String = ""
)

/** 开机自启脚本条目 */
data class BootScript(
    val name: String = "",
    val content: String = "",
    val enabled: Boolean = true
)

/** Crontab 任务条目 */
data class CronJob(
    val expression: String = "",
    val command: String = "",
    val enabled: Boolean = true
)

/** 设备端二进制服务状态（ADGuardHome / EasyTier / EasyConnect 通用） */
data class BinaryServiceStatus(
    val installed: Boolean = false,
    val running: Boolean = false,
    val pid: String = "",
    val port: Int = 0,
    val version: String = "",
    val rawOutput: String = ""
)

/** EasyTier 启动配置 */
data class EasyTierConfig(
    val nodeAddress: String = "",
    val secret: String = "",
    val peers: String = ""
)

/** UFI-TOOLS Web 后台主题配置（/api/get_theme · /api/set_theme） */
data class ThemeConfig(
    val backgroundEnabled: Boolean = false,
    val backgroundUrl: String = "",
    val textColor: String = "rgba(255, 255, 255, 1)",
    val textColorPer: String = "100",
    val themeColor: String = "201",      // 色相 hue
    val colorPer: String = "67",
    val saturationPer: String = "100",
    val brightPer: String = "21",
    val opacityPer: String = "21",
    val blurSwitch: Boolean = true,
    val overlaySwitch: Boolean = true
)

/** 设备 uploads 目录中的已上传文件 */
data class UploadedFile(
    val name: String = "",
    val size: Long = 0,
    val modified: String = ""
)

// ── 抽象接口 ──

interface DeviceFeatures {

    // ═══════ A. 网络控制批 ═══════

    suspend fun volteEnabled(): Result<Boolean> = fail()
    suspend fun setVolte(enabled: Boolean, slot: Int = 0): Result<Unit> = fail()
    suspend fun vonrEnabled(): Result<Boolean> = fail()
    suspend fun setVonr(enabled: Boolean, slot: Int = 0): Result<Unit> = fail()
    suspend fun supportNrBands(): Result<List<Int>> = fail()

    suspend fun getLteBandLock(): Result<List<Int>> = fail()
    suspend fun setLteBandLock(bands: List<Int>): Result<Unit> = fail()
    suspend fun getNrBandLock(): Result<List<Int>> = fail()
    suspend fun setNrBandLock(bands: List<Int>): Result<Unit> = fail()

    suspend fun servingCells(): Result<List<CellInfo>> = fail()
    suspend fun neighborCells(): Result<List<CellInfo>> = fail()
    suspend fun lockedCells(): Result<List<CellInfo>> = fail()
    suspend fun lockCell(pci: Int, earfcn: Int, rat: String): Result<Unit> = fail()
    suspend fun unlockCell(): Result<Unit> = fail()

    suspend fun getBearerPreference(): Result<String> = fail()
    suspend fun setBearerPreference(value: String): Result<Unit> = fail()

    /** 官方 /api/getEndcState · /api/setEndcState（ENDC 状态，供载波聚合卡使用） */
    suspend fun endcState(): Result<Boolean> = fail()
    suspend fun setEndcState(enabled: Boolean): Result<Unit> = fail()

    /** 官方 /api/unlockAllBand：一键解锁全部频段（LTE+NR） */
    suspend fun unlockAllBands(): Result<Unit> = fail()

    suspend fun getSimSlot(): Result<String> = fail()
    suspend fun setSimSlot(slot: String): Result<Unit> = fail()

    suspend fun getApn(): Result<ApnInfo> = fail()
    suspend fun setApnMode(mode: String): Result<Unit> = fail()
    suspend fun saveApn(profile: ApnProfile): Result<Unit> = fail()
    suspend fun deleteApn(index: Int): Result<Unit> = fail()
    suspend fun setDefaultApn(index: Int): Result<Unit> = fail()

    // ═══════ B. 设备控制批 ═══════

    suspend fun sendSms(number: String, body: String): Result<Unit> = fail()
    suspend fun inboxSms(): Result<List<SmsMessage>> = fail()
    suspend fun deleteSms(msgId: String): Result<Unit> = fail()
    suspend fun markSmsRead(msgId: String): Result<Unit> = fail()

    /** 官方 /api/sms_receive_mode（短信接收模式，get/set） */
    suspend fun smsReceiveMode(): Result<String> = fail()
    suspend fun setSmsReceiveMode(mode: String): Result<Unit> = fail()

    suspend fun getForwardChannel(): Result<String> = fail()
    suspend fun getForwardEnabled(): Result<Boolean> = fail()
    suspend fun setForwardEnabled(on: Boolean): Result<Unit> = fail()
    suspend fun getForwardDingtalk(): Result<DingtalkConfig?> = fail()
    suspend fun setForwardDingtalk(cfg: DingtalkConfig): Result<Unit> = fail()
    suspend fun getForwardMail(): Result<MailConfig?> = fail()
    suspend fun setForwardMail(cfg: MailConfig): Result<Unit> = fail()
    suspend fun getForwardCurl(): Result<String?> = fail()
    suspend fun setForwardCurl(curlText: String): Result<Unit> = fail()
    suspend fun getForwardBlacklist(): Result<BlacklistConfig?> = fail()
    suspend fun setForwardBlacklist(cfg: BlacklistConfig): Result<Unit> = fail()
    suspend fun testForward(): Result<Unit> = fail()


    suspend fun getWifiAp(): Result<WifiApInfo> = fail()
    suspend fun setWifiAp(cfg: WifiApInfo): Result<Unit> = fail()

    /**
     * 热点总开关（goform switchWiFiChip / switchWiFiModule）。
     * @param enabled true=开启（chipEnum 指定芯片）；false=关闭整机 WiFi 模块
     * @param chipEnum chip1 / chip2，对应 queryAccessPointInfo 的 ChipIndex 0 / 1
     */
    suspend fun setWifiEnabled(enabled: Boolean, chipEnum: String = "chip1"): Result<Unit> = fail()

    /** 官方 /api/hotspot/boot-autostart 与 /api/hotspot/boot-autostart-delay（开机自启） */
    suspend fun hotspotBootAutostart(): Result<Boolean> = fail()
    suspend fun setHotspotBootAutostart(enabled: Boolean): Result<Unit> = fail()
    suspend fun hotspotBootAutostartDelay(): Result<Long> = fail()
    suspend fun setHotspotBootAutostartDelay(delaySec: Long): Result<Unit> = fail()

    suspend fun calibrateFlow(usedBytes: Long?): Result<Unit> = fail()

    /** 官方 /api/get_data_limit · /api/set_data_limit（流量限额，配合校准） */
    suspend fun getDataLimit(): Result<DataLimit> = fail()
    suspend fun setDataLimit(config: DataLimit): Result<Unit> = fail()

    suspend fun rebootDevice(): Result<Unit> = fail()
    suspend fun shutdownDevice(): Result<Unit> = fail()

    suspend fun getPerformanceMode(): Result<Boolean> = fail()
    suspend fun setPerformanceMode(on: Boolean): Result<Unit> = fail()

    suspend fun listTasks(): Result<List<ScheduledTask>> = fail()
    suspend fun addTask(
        id: String,
        time: String,
        repeatDaily: Boolean,
        actionMap: Map<String, String>
    ): Result<Unit> = fail()
    suspend fun removeTask(id: String): Result<Unit> = fail()
    suspend fun clearTasks(): Result<Unit> = fail()

    // ═══════ C. 高级功能批 ═══════

    suspend fun rootShell(command: String): Result<String> = fail()
    suspend fun userShell(command: String): Result<String> = fail()
    suspend fun setAdvancedFeatures(on: Boolean): Result<String> = fail()
    suspend fun ttydAlive(port: Int): Result<Boolean> = fail()
    fun ttydUrl(port: Int): String = "http://127.0.0.1:$port/"

    /** SSH 相关通过 root shell 实现 */
    suspend fun sshStatus(): Result<String> = fail()
    suspend fun sshRun(command: String): Result<String> = fail()

    suspend fun getWirelessAdb(): Result<Boolean> = fail()
    suspend fun setWirelessAdb(on: Boolean, password: String): Result<Unit> = fail()
    suspend fun adbAlive(): Result<Boolean> = fail()

    suspend fun checkUpdate(): Result<OtaInfo?> = fail()
    suspend fun downloadApk(apkUrl: String): Result<Unit> = fail()
    suspend fun downloadStatus(): Result<DownloadStatus?> = fail()
    suspend fun installApk(): Result<Unit> = fail()
    suspend fun disableFota(): Result<Unit> = fail()

    suspend fun pluginsStore(): Result<PluginStore?> = fail()
    suspend fun getCustomHead(): Result<String> = fail()
    suspend fun setCustomHead(text: String): Result<Unit> = fail()

    /** 自定义插件源：通过 /api/proxy/--<url> 拉取第三方插件仓库 */
    suspend fun fetchCustomPluginStore(storeUrl: String): Result<PluginStore?> = fail()

    // ═══════ F. 主题与文件管理批（新增）═══════

    /** 读取 UFI-TOOLS Web 后台主题配置（/api/get_theme，免认证） */
    suspend fun getTheme(): Result<ThemeConfig> = fail()
    /** 保存主题配置（/api/set_theme） */
    suspend fun setTheme(config: ThemeConfig): Result<Unit> = fail()

    /** 上传文件到设备 uploads 目录（multipart/form-data，/api/upload_img） */
    suspend fun uploadFile(fileName: String, fileBytes: ByteArray): Result<String> = fail()
    /** 删除上传文件（/api/delete_img） */
    suspend fun deleteUploadedFile(fileName: String): Result<Unit> = fail()
    /** 清空 uploads 目录（/api/delete_all_uploads_data） */
    suspend fun clearAllUploads(): Result<Map<String, Boolean>> = fail()
    /** 列出 uploads 目录文件（通过 root_shell ls -l） */
    suspend fun listUploads(): Result<List<UploadedFile>> = fail()

    suspend fun speedtest(ckSize: Int): Result<SpeedtestResult> = fail()

    suspend fun getAccessControl(): Result<AccessControl?> = fail()
    suspend fun setAccessControl(config: AccessControl): Result<Unit> = fail()
    suspend fun getLanClients(): Result<List<LanClient>> = fail()
    suspend fun getDhcp(): Result<DhcpConfig?> = fail()
    suspend fun setDhcp(config: DhcpConfig): Result<Unit> = fail()

    suspend fun getSambaSwitch(): Result<Boolean> = fail()
    suspend fun setSamba(on: Boolean): Result<Unit> = fail()

    // ═══════ D. 官方 UFIPanel 兼容批（本轮补齐）═══════

    /** AT 终端：/api/AT?command=...&slot=N，返回设备输出文本 */
    suspend fun atCommand(command: String, slot: Int = 0): Result<String> = fail()

    /** 数据卡一键切换（SET_SIM_SLOT 0/1，官方「切换为默认数据卡」） */
    suspend fun switchDataCard(slot: String): Result<Unit> = fail()

    /** 清除 SIM 锁定（UNLOCK_ALL_CELL 等价解锁全部小区） */
    suspend fun clearSimLock(): Result<Unit> = fail()

    /** 锁定当前服务小区频点（CELL_LOCK 仅按 EARFCN 下发，PCI 由设备匹配） */
    suspend fun lockCurrentEarfcn(earfcn: Int, rat: String): Result<Unit> = fail()

    /** 设备昵称（别名）：/api/set_nickname 写入，/api/version_info 读取 */
    suspend fun setNickname(nickname: String): Result<Unit> = fail()
    suspend fun getNickname(): Result<String> = fail()

    /** HTTP 代理调试：/api/proxy/--<目标URL>，仅展示请求/响应文本（低风险只读为主） */
    suspend fun proxyDebug(method: String, url: String): Result<ProxyDebugResult> = fail()

    /** 设备标识信息：IMEI/IMSI/ICCID 等（goform 读取） */
    suspend fun deviceIdentity(): Result<Map<String, String>> = fail()

    /** 数据开关：连接/断开蜂窝数据（goform CECULLAR 切换） */
    suspend fun toggleCellularData(): Result<Unit> = fail()

    /** 网络漫游开关 */
    suspend fun toggleRoaming(): Result<Unit> = fail()

    /** 指示灯开关 */
    suspend fun toggleIndicatorLight(): Result<Unit> = fail()

    // ═══════ E. 高级后台兼容批（rootShell 软件侧能力）═══════

    /**
     * 一键禁用系统更新：对 ZTE_UPDATE_COMPONENTS 逐条执行
     * `pm disable-user --user 0 <pkg>` 与 `pm uninstall -k --user 0 <pkg>`，
     * 返回每个组件的设备原始回显（顺序即清单顺序）。
     */
    suspend fun disableSystemUpdateComponents(): Result<List<UpdateComponentResult>> = fail()

    /** 查询设备上现存 ZTE 相关包（pm list packages 过滤，用于禁用前后对比） */
    suspend fun zteComponentStatus(): Result<String> = fail()

    /** 当前系统 AB 分区后缀（getprop ro.boot.slot_suffix，如 _a / _b） */
    suspend fun currentSlotSuffix(): Result<String> = fail()

    /**
     * 提取当前分区 BOOT 镜像到后台 uploads 目录（dd if=/dev/block/by-name/boot<slot>），
     * 返回设备侧路径与文件名，供 [downloadUpload] 下载。
     */
    suspend fun extractBootImage(): Result<BootImageInfo> = fail()

    /** 经后台 /api/uploads/<file> 下载文件到本机 destFile，返回落盘字节数 */
    suspend fun downloadUpload(fileName: String, destFile: java.io.File): Result<Long> = fail()

    /** 检测展锐 DIAG 通道设备节点（ls -l /dev/sdiag*） */
    suspend fun diagChannels(): Result<String> = fail()

    /**
     * DIAG 通道透传：把十六进制帧写入 /dev/sdiag_nr（slot 1 为 /dev/sdiag_nr2），
     * 随后读取设备响应并以十六进制回显（读不到内容时返回 "无响应"）。
     */
    suspend fun diagSend(frameHex: String, slot: Int = 0, readBytes: Int = 512): Result<String> = fail()

    // ═══════ F2. 补齐功能批 · P0 剩余（电量转发 / 主动推送）═══════

    /** 电量信息转发开关：GET /api/power_status_forward_enabled */
    suspend fun getPowerForwardEnabled(): Result<Boolean> = fail()
    /** 设置电量信息转发开关：POST /api/power_status_forward_enabled?enable=0/1 */
    suspend fun setPowerForwardEnabled(enabled: Boolean): Result<Unit> = fail()
    /** 主动推送测试：POST /api/do_forward_msg（自定义 address/body/is_sms） */
    suspend fun sendForwardTest(address: String, body: String, isSms: Boolean): Result<Unit> = fail()

    // ═══════ G. 补齐功能批 · P1（root_shell 系统控制）═══════

    /** 探测全部 CPU 核心状态（online/governor/max_freq/cur_freq） */
    suspend fun getCpuCoreInfo(): Result<List<CpuCoreInfo>> = fail()
    /** 开关指定 CPU 核心（写 /sys/devices/system/cpu/cpuN/online） */
    suspend fun setCpuCoreOnline(core: Int, online: Boolean): Result<Unit> = fail()
    /** 设置指定核心调频策略（scaling_governor） */
    suspend fun setCpuGovernor(core: Int, governor: String): Result<Unit> = fail()
    /** 设置指定核心最大频率（scaling_max_freq，单位 kHz） */
    suspend fun setCpuMaxFreq(core: Int, freqKhz: Int): Result<Unit> = fail()
    /** 获取可用调频策略列表（scaling_available_governors） */
    suspend fun getCpuAvailableGovernors(): Result<List<String>> = fail()
    /** 获取指定核心可用频率列表（scaling_available_frequencies） */
    suspend fun getCpuAvailableFreqs(core: Int): Result<List<Int>> = fail()
    /** 探测电池充电控制节点并读取当前状态 */
    suspend fun getBatteryChargeInfo(): Result<BatteryChargeInfo> = fail()
    /** 设置充电上限百分比（写入 charge_control_limit 或等效节点） */
    suspend fun setBatteryChargeLimit(percent: Int): Result<Unit> = fail()
    /** 开启/关闭充电（battery_charging_enabled 或等效节点） */
    suspend fun setBatteryChargingEnabled(enabled: Boolean): Result<Unit> = fail()
    /** 列出开机自启脚本（/data/local/tmp/init.d/ 目录） */
    suspend fun listBootScripts(): Result<List<BootScript>> = fail()
    /** 保存开机自启脚本（写入脚本文件） */
    suspend fun saveBootScript(name: String, content: String): Result<Unit> = fail()
    /** 删除开机自启脚本 */
    suspend fun deleteBootScript(name: String): Result<Unit> = fail()
    /** 启用/禁用开机自启脚本（chmod +/-x） */
    suspend fun setBootScriptEnabled(name: String, enabled: Boolean): Result<Unit> = fail()
    /** 立即运行指定开机自启脚本，返回输出 */
    suspend fun runBootScript(name: String): Result<String> = fail()
    /** 读取系统 crontab 内容 */
    suspend fun getCrontab(): Result<String> = fail()
    /** 写入系统 crontab */
    suspend fun setCrontab(content: String): Result<Unit> = fail()

    // ═══════ H. 补齐功能批 · P2（设备端二进制服务管理）═══════

    /** ADGuardHome 状态检测（which / ps / 端口 3000） */
    suspend fun getAdGuardStatus(): Result<BinaryServiceStatus> = fail()
    /** 下载并安装 ADGuardHome 二进制到 /data/local/tmp */
    suspend fun installAdGuard(): Result<String> = fail()
    /** 启动 ADGuardHome */
    suspend fun startAdGuard(): Result<Unit> = fail()
    /** 停止 ADGuardHome */
    suspend fun stopAdGuard(): Result<Unit> = fail()
    /** EasyTier 状态检测 */
    suspend fun getEasyTierStatus(): Result<BinaryServiceStatus> = fail()
    /** 下载并安装 EasyTier 二进制 */
    suspend fun installEasyTier(): Result<String> = fail()
    /** 启动 EasyTier（节点地址/密钥/对端） */
    suspend fun startEasyTier(config: EasyTierConfig): Result<Unit> = fail()
    /** 停止 EasyTier */
    suspend fun stopEasyTier(): Result<Unit> = fail()
    /** EasyConnect 状态检测 */
    suspend fun getEasyConnectStatus(): Result<BinaryServiceStatus> = fail()
    /** 下载并安装 EasyConnect 二进制 */
    suspend fun installEasyConnect(): Result<String> = fail()
    /** 启动 EasyConnect（账号/密码/服务器） */
    suspend fun startEasyConnect(username: String, password: String, server: String): Result<Unit> = fail()
    /** 停止 EasyConnect */
    suspend fun stopEasyConnect(): Result<Unit> = fail()

    private fun <T> fail(): Result<T> = Result.failure(FeatureUnsupported())
}

/** 未实现后台的占位实现（页面据此展示空态） */
object UnsupportedFeatures : DeviceFeatures

/** 字节数格式化 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "--"
    val kb = bytes / 1024.0
    val mb = bytes / 1048576.0
    val gb = bytes / 1073741824.0
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}
