/*
 * 星灵 (XingLing) · 设备后台适配层统一抽象
 *
 * 定义总览 / 信号 / 流量 / 设备信息等能力接口，物理实现（如 UFI-TOOLS
 * JSON API）通过 DeviceBackend 注入 UI，页面只依赖抽象不感知具体协议。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.backend

/** 单核 CPU 频率与使用率快照 */
data class CpuCoreStat(
    val core: String,       // "cpu0".."cpu7"
    val curMhz: Int,
    val maxMhz: Int,
    val usage: Float = -1f
)

/** 总览页核心数据快照 */
data class BackendOverview(
    val model: String = "未知设备",
    val appVer: String = "--",
    val clientIp: String = "--",
    val battery: Int = -1,                // 电量百分比，-1 表示未知
    val batteryTemp: Float = -1f,         // 电池温度 ℃（从 sysfs 读取）
    val batteryCapacityMah: Int = -1,    // 电池当前满电容量 mAh
    val batteryVoltageMv: Int = -1,       // 电池电压 mV（voltage_now µV/1000）
    val batteryCurrentMa: Int = Int.MIN_VALUE, // 电池电流 mA（current_now µA/1000，负=放电 正=充电）
    val cpuTemp: Float = -1f,             // CPU 温度 ℃
    val cpuUsage: Float = -1f,            // CPU 使用率 %
    val memUsage: Float = -1f,            // 内存使用率 %
    val memTotalKb: Long = -1,            // 内存总量 KB
    val memUsedKb: Long = -1,             // 内存已用 KB
    val swapUsagePercent: Float = -1f,    // Swap 使用率 %
    val storageUsedMb: Long = -1,         // 内部存储已用 MB
    val storageTotalMb: Long = -1,        // 内部存储总量 MB
    val dailyBytes: Long = -1,            // 当日流量（字节）
    val monthlyBytes: Long = -1,         // 当月流量（字节）
    val monthlyDlBytes: Long = -1,       // 当月下行流量（字节）
    val monthlyUlBytes: Long = -1,        // 当月上行流量（字节）
    val isReachedFlowLimit: Boolean = false, // 是否已达流量阈值
    val dataLimitEnabled: Boolean = false,   // 是否启用流量阈值提醒
    val dataLimitMaxBytes: Long = -1,        // 流量阈值上限（字节）
    val dataLimitPeriod: String = "monthly", // daily / monthly
    val isCharging: Boolean = false,
    val signalBars: Int = -1,              // 信号格数 0~5
    val qci: String = "9",                // QCI 等级
    val dlMaxMbps: Int = -1,              // 下行最大速率 Mbps（AT+CGEQOSRDP）
    val ulMaxMbps: Int = -1,              // 上行最大速率 Mbps
    val cpuCores: List<CpuCoreStat> = emptyList(), // 各核频率/使用率
    val uptimeSec: Long = -1,              // 设备运行时长（秒）
) {
    /** 电池功率 W（正=充电，负=放电），由电压×电流推算；缺数据返回 0 */
    val batteryPowerW: Float
        get() = if (batteryVoltageMv > 0 && batteryCurrentMa != Int.MIN_VALUE)
            (batteryVoltageMv / 1000.0 * batteryCurrentMa / 1000.0).toFloat()
        else 0f
}

/** 信号指标快照 */
data class BackendSignalInfo(
    val rsrp: Int = Int.MIN_VALUE,
    val rssi: Int = Int.MIN_VALUE,
    val sinr: Int = Int.MIN_VALUE,
    val rsrq: Int = Int.MIN_VALUE,
    val band: String = "",
    val pci: Int = -1,
    val arfcn: Int = 0,
    val networkType: String = "--",
    val carrierName: String = "--"
) {
    val isKnown: Boolean get() = rsrp != Int.MIN_VALUE
}

/**
 * 实时上下行速率（设备固件统计，非流量差值估算）。
 * 单位：字节/秒（B/s），来自 ZTE goform realtime_rx_thrpt / realtime_tx_thrpt。
 */
data class RealtimeSpeed(
    val rxBps: Long = 0,
    val txBps: Long = 0
)

/**
 * 设备后台能力抽象。所有方法均为挂起函数，UI 侧通过轮询取值；
 * 失败返回 Result.failure，由调用方决定是重试、空态还是提示。
 */
interface DeviceBackend {
    /** 设备显示名（别名/型号 + 地址） */
    val displayName: String

    /** 设备地址 host:port（用于 UI 展示） */
    val deviceAddress: String

    /**
     * 探测设备可达性并校验口令。
     * 先免认证探测 /api/need_token，再以带鉴权请求（/api/baseDeviceInfo）
     * 验证口令是否正确；口令错误返回 failure。
     */
    suspend fun connect(): Result<BackendOverview>

    /** 取设备总览/健康/流量信息 */
    suspend fun fetchOverview(): Result<BackendOverview>

    /** 取信号指标（RSRP/RSSI/SINR/Band/PCI 等），尽力而为 */
    suspend fun fetchSignalInfo(): Result<BackendSignalInfo>

    /** 取指定区间蜂窝流量（字节） */
    suspend fun fetchCellularUsage(startTimeMs: Long, endTimeMs: Long): Result<Long>

    /** 取设备固件统计的实时上下行速率（B/s，realtime_rx/tx_thrpt） */
    suspend fun fetchRealtimeSpeed(): Result<RealtimeSpeed> =
        Result.failure(UnsupportedOperationException("当前后台不支持实时速率"))

    /**
     * 高级功能（网络/设备/高级三批共 38 项）统一入口。
     * 未接入具体实现的后台默认返回 UnsupportedFeatures（页面展示空态说明）。
     */
    val features: DeviceFeatures
        get() = UnsupportedFeatures

    /** 释放资源（若底层持有连接等） */
    fun close() {}
}
