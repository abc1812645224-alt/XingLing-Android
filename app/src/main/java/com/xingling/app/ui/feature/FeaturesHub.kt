/*
 * 星灵 (XingLing) · 功能导航中枢
 *
 * 总览一屏 → 深层分页：从设备总览页的「功能」入口卡片进入，
 * 按 网络控制 / 设备控制 / 高级功能 三批分组导航到 38 个功能页。
 * 所有页面均深色字段统一走 FeaturePage（iOS 玻璃卡片风格 + 空态引导）。
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xingling.app.ui.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingling.app.backend.DeviceBackend
import com.xingling.app.ui.theme.iOSBlue
import com.xingling.app.ui.theme.iOSLabel
import com.xingling.app.ui.theme.iOSOrange
import com.xingling.app.ui.theme.iOSSecondaryLabel
import com.xingling.app.ui.theme.iOSSeparator

/** 全部功能页路由（三批分组） */
enum class FeatureGroup(val label: String) {
    NETWORK("网络控制"),
    DEVICE("设备控制"),
    ADVANCED("高级功能")
}

enum class FeatureRoute(val title: String, val group: FeatureGroup, val desc: String) {
    // ═══ A. 网络控制批 ═══
    VOLTE("VoLTE / VoNR", FeatureGroup.NETWORK, "高清语音与 5G 通话开关"),
    BANDS("5G 频段 · 锁频段", FeatureGroup.NETWORK, "支持频段列表 / LTE·NR 锁频"),
    CELL_LOCK("锁基站", FeatureGroup.NETWORK, "PCI / EARFCN 锁定与解锁"),
    NETWORK_MODE("网络模式", FeatureGroup.NETWORK, "4G / 5G 制式偏好切换"),
    SIM_SLOT("SIM 卡槽", FeatureGroup.NETWORK, "主卡 / 副卡 / 双卡待机"),
    APN("APN 配置", FeatureGroup.NETWORK, "接入点新增 / 编辑 / 删除"),
    // ═══ B. 设备控制批 ═══
    SMS("短信收发", FeatureGroup.DEVICE, "发短信 / 收件箱 / 删除"),
    FORWARD("SMS 转发", FeatureGroup.DEVICE, "邮件 / 钉钉 / curl 通道"),
    HOTSPOT("WiFi 热点", FeatureGroup.DEVICE, "SSID / 密码 / 加密方式"),
    CALIBRATE("流量校准", FeatureGroup.DEVICE, "手动校准流量计数"),
    REBOOT("重启 / 关机", FeatureGroup.DEVICE, "远程重启或关机设备"),
    PERFORMANCE("性能模式", FeatureGroup.DEVICE, "高性能 / 省电切换"),
    TASKS("定时任务", FeatureGroup.DEVICE, "定时重启 / 定时开机等"),
    // ═══ C. 高级功能批 ═══
    SHELL("root Shell", FeatureGroup.ADVANCED, "任意命令 / root 交互"),
    TTYD("ttyd 终端", FeatureGroup.ADVANCED, "内嵌 Web 终端"),
    SSH("SSH", FeatureGroup.ADVANCED, "服务状态与远程命令"),
    ADB("无线 ADB", FeatureGroup.ADVANCED, "ADB over WiFi 自启"),
    OTA("OTA 更新", FeatureGroup.ADVANCED, "固件 / APK 升级"),
    PLUGIN("插件系统", FeatureGroup.ADVANCED, "插件市场与欢迎语"),
    SPEEDTEST("测速", FeatureGroup.ADVANCED, "宽带测速（后端流式）"),
    LAN("局域网管理", FeatureGroup.ADVANCED, "DHCP / 黑白名单 / 客户端"),
    SAMBA("SMB 共享", FeatureGroup.ADVANCED, "局域网文件共享开关"),
    // ═══ D. 高级后台兼容批（P0/P1，rootShell 软件侧能力）═══
    SYS_UPDATE("系统更新治理", FeatureGroup.ADVANCED, "一键禁用 ZTE 更新组件"),
    PARTITION("设备分区 · BOOT", FeatureGroup.ADVANCED, "AB 分区展示 / 提取镜像下载"),
    DIAG("DIAG 工具", FeatureGroup.ADVANCED, "展锐 DIAG 通道查 IMEI"),
    // ═══ E. 补齐功能批（UFI-TOOLS 有但星灵缺失）═══
    THEME_CUSTOM("主题定制", FeatureGroup.ADVANCED, "设备 Web 后台主题与背景管理"),
    FILE_MANAGER("文件管理", FeatureGroup.ADVANCED, "上传 / 下载 / 删除设备文件"),
    POWER_FORWARD("电量转发", FeatureGroup.DEVICE, "电量信息主动推送开关"),
    PUSH_TEST("推送测试", FeatureGroup.DEVICE, "主动推送一条测试消息"),
    CUSTOM_PLUGIN("自定义插件源", FeatureGroup.ADVANCED, "第三方插件仓库接入"),
    CPU_CONTROL("CPU 核心控制", FeatureGroup.ADVANCED, "核心开关 / 调频 / 调度策略"),
    BOOT_SCRIPTS("开机自启脚本", FeatureGroup.ADVANCED, "init.d 脚本管理与运行"),
    CRONTAB("Crontab 定时", FeatureGroup.ADVANCED, "系统 crontab 表达式编辑"),
    ADGUARD("ADGuardHome", FeatureGroup.ADVANCED, "广告过滤 DNS 服务管理"),
    EASYTIER("EasyTier 组网", FeatureGroup.ADVANCED, "异地组网虚拟局域网"),
    EASYCONNECT("EasyConnect", FeatureGroup.ADVANCED, "校园网 VPN 客户端"),
    DEVICE_MANAGER("多设备管理", FeatureGroup.DEVICE, "设备集群添加 / 切换 / 删除"),
}

/**
 * 首页「常用功能」入口清单（高频操作，最多 6 项）。
 *
 * 全工程入口唯一性归属（每个 FeatureRoute 只在此处出现一次）：
 *   首页常用（3）：WiFi 热点 / 重启关机 / 定时任务
 *   频段 Tab（8） ：VoLTE·VoNR / 锁频段 / 锁基站 / 网络模式 / SIM 卡槽 / APN
 *                  + 流量校准 / 性能模式
 *   短信 Tab（2） ：短信收发 / SMS 转发
 *   设置 Tab（12）：设备维护 4（OTA / 系统更新治理 / 设备分区 / DIAG）
 *                  + 高级工具 8（root Shell / ttyd / SSH / ADB / 插件 / 测速 / 局域网 / SMB）
 */
val QuickFeatureRoutes: List<FeatureRoute> = listOf(
    FeatureRoute.HOTSPOT,
    FeatureRoute.REBOOT,
    FeatureRoute.TASKS
)

/** 首页「常用功能」入口卡片（iOS 玻璃卡片，行数 ≤ 6） */
@Composable
fun QuickFeatureCard(onOpen: (FeatureRoute) -> Unit, modifier: Modifier = Modifier) {
    FeatureCard(
        title = "常用功能",
        subtitle = "高频操作直达 · 其余功能见 频段 / 短信 / 设置 Tab"
    ) {
        QuickFeatureRoutes.forEachIndexed { index, route ->
            if (index > 0) {
                Divider(color = iOSSeparator, thickness = 1.dp)
            }
            // 每个功能配彩色圆角图标
            val icon: ImageVector
            val iconColor: Color
            when (route) {
                FeatureRoute.HOTSPOT -> { icon = Icons.Filled.Star; iconColor = Color(0xFF007AFF) }
                FeatureRoute.REBOOT -> { icon = Icons.Filled.Refresh; iconColor = Color(0xFFFF9500) }
                FeatureRoute.TASKS -> { icon = Icons.Filled.Refresh; iconColor = Color(0xFF5856D6) }
                else -> { icon = Icons.Filled.Star; iconColor = iOSBlue }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(route) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(iconColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(route.title, style = MaterialTheme.typography.bodyLarge, color = iOSLabel)
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(route.desc, style = MaterialTheme.typography.bodySmall, color = iOSSecondaryLabel)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("›", color = iOSSecondaryLabel.copy(alpha = 0.5f), fontSize = 18.sp)
            }
        }
    }
}

/** 单功能页分发（深层分页），未配置设备由 FeaturePage 统一空态引导 */
@Composable
fun FeatureRouteHost(
    route: FeatureRoute,
    backend: DeviceBackend?,
    onBack: () -> Unit,
    onAddDevice: () -> Unit
) {
    when (route) {
        FeatureRoute.VOLTE -> VolteScreen(backend, onBack, onAddDevice)
        FeatureRoute.BANDS -> BandsScreen(backend, onBack, onAddDevice)
        FeatureRoute.CELL_LOCK -> CellLockScreen(backend, onBack, onAddDevice)
        FeatureRoute.NETWORK_MODE -> NetworkModeScreen(backend, onBack, onAddDevice)
        FeatureRoute.SIM_SLOT -> SimSlotScreen(backend, onBack, onAddDevice)
        FeatureRoute.APN -> ApnScreen(backend, onBack, onAddDevice)
        FeatureRoute.SMS -> SmsScreen(backend, onBack, onAddDevice)
        FeatureRoute.FORWARD -> ForwardScreen(backend, onBack, onAddDevice)
        FeatureRoute.HOTSPOT -> HotspotScreen(backend, onBack, onAddDevice)
        FeatureRoute.CALIBRATE -> CalibrateScreen(backend, onBack, onAddDevice)
        FeatureRoute.REBOOT -> RebootScreen(backend, onBack, onAddDevice)
        FeatureRoute.PERFORMANCE -> PerformanceScreen(backend, onBack, onAddDevice)
        FeatureRoute.TASKS -> TasksScreen(backend, onBack, onAddDevice)
        FeatureRoute.SHELL -> ShellScreen(backend, onBack, onAddDevice)
        FeatureRoute.TTYD -> TtydScreen(backend, onBack, onAddDevice)
        FeatureRoute.SSH -> SshScreen(backend, onBack, onAddDevice)
        FeatureRoute.ADB -> AdbScreen(backend, onBack, onAddDevice)
        FeatureRoute.OTA -> OtaScreen(backend, onBack, onAddDevice)
        FeatureRoute.PLUGIN -> PluginScreen(backend, onBack, onAddDevice)
        FeatureRoute.SPEEDTEST -> SpeedtestScreen(backend, onBack, onAddDevice)
        FeatureRoute.LAN -> LanScreen(backend, onBack, onAddDevice)
        FeatureRoute.SAMBA -> SambaScreen(backend, onBack, onAddDevice)
        FeatureRoute.SYS_UPDATE -> SysUpdateScreen(backend, onBack, onAddDevice)
        FeatureRoute.PARTITION -> PartitionScreen(backend, onBack, onAddDevice)
        FeatureRoute.DIAG -> DiagScreen(backend, onBack, onAddDevice)
        FeatureRoute.THEME_CUSTOM -> ThemeCustomScreen(backend, onBack, onAddDevice)
        FeatureRoute.FILE_MANAGER -> FileManagerScreen(backend, onBack, onAddDevice)
        FeatureRoute.POWER_FORWARD -> PowerForwardScreen(backend, onBack, onAddDevice)
        FeatureRoute.PUSH_TEST -> PushTestScreen(backend, onBack, onAddDevice)
        FeatureRoute.CUSTOM_PLUGIN -> CustomPluginScreen(backend, onBack, onAddDevice)
        FeatureRoute.CPU_CONTROL -> CpuControlScreen(backend, onBack, onAddDevice)
        FeatureRoute.BOOT_SCRIPTS -> BootScriptsScreen(backend, onBack, onAddDevice)
        FeatureRoute.CRONTAB -> CrontabScreen(backend, onBack, onAddDevice)
        FeatureRoute.ADGUARD -> AdGuardScreen(backend, onBack, onAddDevice)
        FeatureRoute.EASYTIER -> EasyTierScreen(backend, onBack, onAddDevice)
        FeatureRoute.EASYCONNECT -> EasyConnectScreen(backend, onBack, onAddDevice)
        FeatureRoute.DEVICE_MANAGER -> DeviceManagerScreen(backend, onBack, onAddDevice)
    }
}
