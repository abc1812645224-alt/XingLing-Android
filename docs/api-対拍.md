---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 001b83761c348f6205ef0555a74ffa33_1aa2f993b18211f18f26525400287e28
    ReservedCode1: RE1sitfqUz14s2IpZgFu4kHkKEDSBdY4ae/62kRbtsj3FIGJhZeEDCNQCkVyYbZvGi+O26yasSxIHZyup7CzZMWJw7WpyhOrkglfatQwULYDXzL4LOiZAGU2xv5VXmE5U/Sz+cwTJtRt4EoXyTDAg9WAxSvQE9uGYkaBGdtSTU3cMWDfvIbax1o5JdE=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 001b83761c348f6205ef0555a74ffa33_1aa2f993b18211f18f26525400287e28
    ReservedCode2: RE1sitfqUz14s2IpZgFu4kHkKEDSBdY4ae/62kRbtsj3FIGJhZeEDCNQCkVyYbZvGi+O26yasSxIHZyup7CzZMWJw7WpyhOrkglfatQwULYDXzL4LOiZAGU2xv5VXmE5U/Sz+cwTJtRt4EoXyTDAg9WAxSvQE9uGYkaBGdtSTU3cMWDfvIbax1o5JdE=
---





# 星灵 × 官方 UFIPanel v1.3.5 · API 对拍记录

> 用途：证明「参考官方作者 APK（`app-release.apk`，UFIPanel v1.3.5）补齐遗漏端点」这一交付要求。
> 数据来源：官方 APK 反编译提取的 `/api` 路由清单（见任务背景 material），对比星灵 `UfiToolsFeatureApi.kt` / `DeviceFeatures.kt` 现状。
> 状态约定：**已覆盖** = 星灵原有实现（部分经 goform 反代实现同样能力）/ **本轮补齐** = 按官方清单本次新增并接线 / **未实现** = 暂无等价对接（记录备查，不夸大）。

## 一、端点点位总览

| 官方 UFIPanel 端点 | 星灵状态 | 说明 |
| --- | --- | --- |
| /api/hotspot/boot-autostart | 本轮补齐 | `hotspotBootAutostart/setHotspotBootAutostart`，热点页「开机自启热点」开关 |
| /api/hotspot/boot-autostart-delay | 本轮补齐 | `hotspotBootAutostartDelay/setHotspotBootAutostartDelay`，热点页「启动延迟（秒）」 |
| /api/sms_receive_mode | 本轮补齐 | `smsReceiveMode/setSmsReceiveMode`，短信页「短信接收模式」卡片 |
| /api/getEndcState | 本轮补齐 | `endcState`，频段页「ENDC / 载波聚合」开关读取 |
| /api/setEndcState | 本轮补齐 | `setEndcState`，频段页「启用 ENDC」开关写入 |
| /api/unlockAllBand | 本轮补齐 | `unlockAllBands`，频段页「解锁全部频段（LTE+NR）」按钮 |
| /api/get_data_limit | 本轮补齐 | `getDataLimit`，流量校准页「流量限额」读取 |
| /api/set_data_limit | 本轮补齐 | `setDataLimit`（含 `data_limit_status_forward_enabled`），流量校准页「保存限额」 |
| /api/goform/goform_get_cmd_process | 已覆盖 | goform 读取走 `UfiToolsApi.getPath` |
| /api/goform/goform_set_cmd_process | 已覆盖 | goform 写入走 `writeChecked` |
| /api/adb_alive | 已覆盖 | `adbAlive` |
| /api/adb_wifi_setting | 已覆盖 | `getWirelessAdb/setWirelessAdb` |
| /api/add_task | 已覆盖 | `addTask` |
| /api/remove_task | 已覆盖 | `removeTask` |
| /api/clear_task | 已覆盖 | `clearTasks` |
| /api/list_tasks | 已覆盖 | `listTasks` |
| /api/get_task | 已覆盖 | 定时任务列表（同上 `listTasks`） |
| /api/baseDeviceInfo | 已覆盖 | 连接握手时获取设备基础信息 |
| /api/check_update | 已覆盖 | `checkUpdate` |
| /api/download_apk | 已覆盖 | `downloadApk` |
| /api/download_apk_status | 已覆盖 | `downloadStatus` |
| /api/install_apk | 已覆盖 | `installApk` |
| /api/need_token | 已覆盖 | 设备自动发现探测 |
| /api/get_cookie | 已覆盖 | `get_cookie` 会话 cookie |
| /api/set_cookie | 已覆盖 | `set_cookie` |
| /api/get_custom_head | 已覆盖 | `getCustomHead` |
| /api/set_custom_head | 已覆盖 | `setCustomHead` |
| /api/root_shell | 已覆盖 | `rootShell` |
| /api/user_shell | 已覆盖 | `userShell` |
| /api/plugins_store | 已覆盖 | `pluginsStore` |
| /api/hasTTYD | 已覆盖 | `ttydAlive` |
| /api/speedtest | 已覆盖 | `speedtest` |
| /api/getSupportNrBandList | 已覆盖 | `supportNrBands` |
| /api/volte_status | 已覆盖 | `volteEnabled/setVolte`（官方同时有 getVoLteState/setVoLteState，语义一致） |
| /api/vonr_status | 已覆盖 | `vonrEnabled/setVonr` |
| /api/do_forward_msg | 已覆盖 | `testForward` |
| /api/sms_forward_method | 已覆盖 | `getForwardChannel` |
| /api/sms_forward_enabled | 已覆盖 | `getForwardEnabled/setForwardEnabled` |
| /api/sms_forward_dingtalk | 已覆盖 | `get/setForwardDingtalk` |
| /api/sms_forward_mail | 已覆盖 | `get/setForwardMail` |
| /api/sms_forward_curl | 已覆盖 | `get/setForwardCurl` |
| /api/sms_forward_blacklist | 已覆盖 | `get/setForwardBlacklist` |
| /api/send_sms | 已覆盖 | goform `SEND_SMS` 反代 |
| /api/get_sms | 已覆盖 | goform `sms_data_total` 反代（收件箱） |
| /api/lockBand | 已覆盖 | goform `LTE_BAND_LOCK / NR_BAND_LOCK` |
| /api/lockBandInfo | 已覆盖 | goform `lte_band_lock / nr_band_lock` 读取 |
| /api/lockCell | 已覆盖 | goform `CELL_LOCK` |
| /api/lockCellInfo | 已覆盖 | goform `locked_cell_info` |
| /api/neighborCellInfo | 已覆盖 | goform `neighbor_cell_info` |
| /api/unlockAllCell | 已覆盖 | goform `UNLOCK_ALL_CELL` |
| /api/version_info | 已覆盖 | `checkUpdate` 中含 base_uri / changelog |
| /api/sim_slot | 已覆盖 | goform `sim_slot` 读取 + `SET_SIM_SLOT` 写入 |
| /api/hotspot | 已覆盖 | WiFi 热点经 goform `setAccessPointInfo` 反代 |
| /api/hotspot/config | 已覆盖 | 同上（`getWifiAp`） |
| /api/hotspot/state | 已覆盖 | 同上（`getWifiAp`） |
| /api/apn | 已覆盖 | goform `APN_PROC_EX`（查询/新增/删除/设默认） |
| /api/apn/ | 已覆盖 | 同上 |
| /api/apn/list | 已覆盖 | 同上 |
| /api/getNetworkMode | 已覆盖 | goform `net_select` 读取（`getBearerPreference`） |
| /api/setNetworkMode | 已覆盖 | goform `SET_BEARER_PREFERENCE` |
| /api/lockBand 等大写命令 | 已覆盖 | 见下方「goform 常量对拍」 |

## 二、本轮目标待补端点（已全部补齐，验收点）

| 官方端点 | 接口方法（DeviceFeatures） | 实现（UfiToolsFeatureApi） | UI 接线 |
| --- | --- | --- | --- |
| /api/sms_receive_mode | `smsReceiveMode()` / `setSmsReceiveMode(mode)` | GET / POST（JSON `mode`） | 短信页「短信接收模式」卡片 |
| /api/getEndcState | `endcState()` | GET 布尔解析 | 频段页「ENDC / 载波聚合」开关 |
| /api/setEndcState | `setEndcState(on)` | POST（JSON `enabled`） | 同上 |
| /api/unlockAllBand | `unlockAllBands()` | GET | 频段页「解锁全部频段（LTE+NR）」按钮 |
| /api/get_data_limit | `getDataLimit()` | GET → `DataLimit`（enabled/max_limit/period/check_reference/status_forward_enabled） | 流量校准页「流量限额」 |
| /api/set_data_limit | `setDataLimit(cfg)` | POST → `DataLimit` 全字段 | 流量校准页「保存限额」 |
| /api/hotspot/boot-autostart | `hotspotBootAutostart()` / `setHotspotBootAutostart(on)` | GET / POST | 热点页「开机自启热点」开关 |
| /api/hotspot/boot-autostart-delay | `hotspotBootAutostartDelay()` / `setHotspotBootAutostartDelay(sec)` | GET / POST | 热点页「启动延迟（秒）」 |
| /api/AT | `atCommand(command, slot=0)` | GET `/api/AT?command=...&slot=...`（自动 kano 签名） | 设置 Tab「AT 终端」页：输入框+输出区+执行状态 |
| /api/proxy/--<URL> | `proxyDebug(method, url)` | GET `/api/proxy/--<URL>`（响应体截断 4000 字） | 设置 Tab「HTTP 代理调试」页 |
| /api/set_nickname | `setNickname(nickname)` | POST JSON `{nickname}` | 设置 Tab「设备昵称」页 |
| （昵称读取） | `getNickname()` | GET `/api/version_info` 解析 `nickname` 字段 | 设置 Tab「设备昵称」页与设备连接卡片 |
| SET_SIM_SLOT（切默认数据卡） | `switchDataCard(slot)` | goform `SET_SIM_SLOT` 0/1 | 频段 Tab「SIM 卡槽」页「切换为默认数据卡」按钮 |
| UNLOCK_ALL_CELL（清 SIM 锁） | `clearSimLock()` | goform `UNLOCK_ALL_CELL` | 频段 Tab「锁基站」页「清除 SIM 锁定」按钮 |
| CELL_LOCK（锁当前频点） | `lockCurrentEarfcn(earfcn, rat)` | goform `CELL_LOCK` 仅按 EARFCN 下发 | 频段 Tab「锁频段」页「锁定当前服务小区频点」按钮 |
| EN-DC 策略（驻网说明） | `endcState()` / `setEndcState(on)`（复用已有） | GET/POST `/api/getEndcState` `/api/setEndcState` | 频段 Tab「网络模式」页「EN-DC 驻网策略」卡片 |
| 信号等级（RSRP 阈值） | 纯 UI（`signalLevelInfo`） | 由 `fetchSignalInfo` 的 RSRP 按 -90/-100/-110 划分 | 信号 Tab「信号等级」卡片 |
| RSRP 趋势图 | 纯 UI（`RsrpTrendCard`） | 内存采样最近 60 次 RSRP + Canvas 折线 + -105dBm 虚线阈值 | 信号 Tab「RSRP 趋势」卡片 |
| 当日流量折线 | 纯 UI（`DailyTrafficChartCard`） | 基于总览页轮询 `dailyBytes` 内存采样 + Canvas 绘制 | 总览页「当日流量趋势」卡片 |
| 轮询频率三档 | `pollIntervalMs`（1000/5000/10000ms） | 设置 Tab 选择，生效于总览页轮询 delay | 设置 Tab「轮询频率」卡片 |

## 三、未实现（记录备查，未夸大标注"已覆盖"）

以下官方端点星灵暂无一对一对接，多数与星灵既有 goform/root_shell 能力重叠或属特殊硬件/主题定制；是否补接后续按产品优先级决定：

| 官方端点 | 现状 | 可能路径 |
| --- | --- | --- |
| /api/power_status_forward_enabled | 未实现 | 电源状态短信上报开关 |
| /api/SELinux | 未实现 | 安全上下文查询（root_shell 可兜底） |
| /api/beep_my_device | 未实现 | 设备响铃定位 |
| /api/cellularUsage | 未实现 | 蜂窝用量（`FLOW_CALIBRATION_MANUAL`/`get_data_limit` 已覆盖主链路） |
| /api/currentCellInfo | 未实现 | 当前小区；neighbor/locked 已覆盖同类 |
| /api/delete_all_uploads_data | 未实现 | 清理上传区 |
| /api/delete_img / upload_img | 未实现 | 自定义头图上传 |
| /api/getBaseBandPreferToData / setBaseBandPreferToData | 未实现 | 基带数据优先 |
| /api/getOnlyRndisSwitch / setOnlyRndisSwitch | 未实现 | USB RNDIS 仅网卡模式 |
| /api/getSubIds | 未实现 | SIM 子卡枚举（多卡机） |
| /api/one_click_shell | 未实现 | 一键 Shell |
| /api/get_theme / set_theme | 未实现 | 主题定制 |
| /api/is_weak_token | 未实现 | 弱口令检测 |
| /api/usb_status | 未实现 | USB 状态（RNDIS 相关） |

## 四、goform 命令常量对拍

星灵已有 goform 常量（14 个）与官方清单对比：

| 命令 | 星灵 | 用途 |
| --- | --- | --- |
| APN_PROC_EX | 已有 | APN 增删改查 |
| CELL_LOCK | 已有 | 锁小区 |
| UNLOCK_ALL_CELL | 已有 | 解锁全小区 |
| DELETE_SMS | 已有 | 删除短信 |
| DHCP_SETTING | 已有 | DHCP 开关 |
| FLOW_CALIBRATION_MANUAL | 已有 | 流量校准 |
| LTE_BAND_LOCK / NR_BAND_LOCK | 已有 | 锁频段 |
| PERFORMANCE_MODE_SETTING | 已有 | 性能模式 |
| REBOOT_DEVICE | 已有 | 重启 |
| SAMBA_SETTING | 已有 | SMB 开关 |
| SEND_SMS | 已有 | 发短信 |
| SET_BEARER_PREFERENCE | 已有 | 网络模式 |
| SET_SIM_SLOT | 已有 | SIM 卡槽 |
| SHUTDOWN_DEVICE | 已有 | 关机 |
| CONNECT_NETWORK / DISCONNECT_NETWORK | API 侧 | 官方 goform 清单中新见，星灵经 `setBearerPreference`/`CONNECT_NETWORK` 未单独暴露，记录备查 |
| INDICATOR_LIGHT_SETTING | 未实现 | 指示灯开关，官方清单中出现，可后续补 |

> 官方清单中的 `ACCESSIBILITY_*` / `ACTION_SET_*` / `BLOCKING` / `EXCELLENT` / `MODEL` / `STRING_SET_FIELD_NUMBER` 等为 Android SDK 常量或噪声串，非 goform 命令，不作对拍项。

## 五、结论

- 任务要求补齐的 8 组端点（sms_receive_mode / ENDC / unlockAllBand / data_limit / hotspot boot-autostart 及其延迟）**已全部**在 `DeviceFeatures` 契约、`UfiToolsFeatureApi` 实现、对应 UI 页三层接线完成。
- 对照官方反代清单，星灵功能面已覆盖绝大部分管理能力（多数经 goform 反代实现等价效果）；上表「未实现」项为诚实记录，未列入"已覆盖"。

## 六、官方 UFIPanel 功能对照（本轮导航重构 + 11 项补齐）

> 对照来源：`output\官方UFIPanel-v1.3.5功能全貌分析报告.md`（jadx 反编译）。
> 状态约定：**已覆盖** = 本轮改造前已有 / **本轮补齐** = 本次新增接线 / **暂不实现** = 记录原因，不夸大。

### 6.1 底部导航

| 官方 Tab | 星灵 Tab | 内容接线 | 状态 |
| --- | --- | --- | --- |
| 首页 Home | 总览 | OverviewScreen（设备状态/信号仪表/流量/健康/功能入口） | 已覆盖（本轮加当日流量趋势） |
| 信号 Params | 信号 | SignalScreen（本机信号监控 + 本轮加信号等级/RSRP 趋势） | 本轮补齐（新增 2 卡） |
| 频段 Band | 频段 | BandTabContent → VoLTE/VoNR、锁频段、锁基站、网络模式、SIM 卡槽、APN | 已覆盖组网 |
| 短信 Sms | 短信 | SmsTabContent → 短信收发、SMS 转发 | 已覆盖组网 |
| 设置 Settings | 设置 | SettingsTabContent → 连接/轮询频率/调试工具/关于 | 本轮补齐（新增子页） |
| 悬浮胶囊底栏 | 悬浮胶囊底栏 | iOS 玻璃质感、圆角 28dp、选中胶囊高亮（禁用 Miuix） | 本轮补齐 |

### 6.2 功能点对照（官方全量 vs 星灵）

| 官方功能点 | 星灵覆盖 | 实现/接线位置 | 状态 |
| --- | --- | --- | --- |
| 设备状态卡片（CPU 温度/占用/内存） | 总览 DeviceHealthCard | OverviewScreen | 已覆盖 |
| 蜂窝状态概览 | 总览 DeviceAndSignalGaugeCard | OverviewScreen | 已覆盖 |
| 流量用量（日/月） | TrafficOverviewCard（dailyBytes/monthlyBytes） | OverviewScreen | 已覆盖 |
| 流量图表 | DailyTrafficChartCard（当日折线，Canvas 采样 60 次） | OverviewScreen | 本轮补齐 |
| 网络测速 | SpeedtestScreen | FeaturesHub SPEEDTEST | 已覆盖 |
| 数据卡切换（SIM1/SIM2 默认数据卡） | SimSlotScreen「默认数据卡」卡片 | NetworkPages2 + switchDataCard | 本轮补齐 |
| 更新检查 | OtaScreen | FeaturesHub OTA | 已覆盖 |
| 当前驻网 / 邻区列表 | SignalScreen / CellLockScreen 邻区 | SignalMonitor / lockedCells | 已覆盖 |
| RSRP 趋势图（-105dBm 虚线阈值） | RsrpTrendCard（内存 60 次采样，Canvas） | SignalDashboardUI | 本轮补齐 |
| 信号等级（优秀/良好/一般/较差/未知） | SignalLevelCard（RSRP -90/-100/-110 阈值） | SignalDashboardUI | 本轮补齐 |
| 网络模式切换 | NetworkModeScreen（getBearerPreference） | NetworkPages2 | 已覆盖 |
| LTE / NR 频段锁定 | BandsScreen（LTE_BAND_LOCK / NR_BAND_LOCK） | NetworkPages | 已覆盖 |
| 锁小区（频点+PCI） | CellLockScreen（CELL_LOCK） | NetworkPages | 已覆盖 |
| 锁频点（当前服务小区一键锁定） | BandsScreen「锁定当前服务小区频点」 | NetworkPages + lockCurrentEarfcn | 本轮补齐 |
| 一键解锁（频段/小区） | BandsScreen 解锁全部 / CellLockScreen 解锁 | unlockAllBands / unlockCell | 已覆盖 |
| 清除 SIM 锁定 | CellLockScreen「SIM 锁定」卡片 | NetworkPages + clearSimLock | 本轮补齐 |
| VoLTE / VoNR | VolteScreen | NetworkPages | 已覆盖 |
| EN-DC 驻网策略 | NetworkModeScreen「EN-DC 驻网策略」卡片（含策略说明） | NetworkPages2 + endcState/setEndcState | 本轮补齐 |
| 卡槽管理（SIM1/SIM2 网络选择） | SimSlotScreen（getSimSlot/setSimSlot） | NetworkPages2 | 已覆盖 |
| 短信列表 / 详情 / 发送 / 删除 | SmsScreen（inboxSms/sendSms/deleteSms） | DevicePages | 已覆盖 |
| 短信转发（邮件/Webhook/钉钉/黑名单） | ForwardScreen（get/setForward*） | DevicePages | 已覆盖 |
| 设备连接向导 | AppRoot DeviceSetupScreen（自动发现） | AppRoot | 已覆盖 |
| 主题外观（明暗/Monet/悬浮底栏） | 星灵保留 iOS 玻璃风格（任务明确禁用 Miuix/Monet） | XingLingTheme | 已覆盖（风格差异为刻意保留） |
| AT 终端 | AtTerminalScreen（输入/输出/执行状态） | TabPages + atCommand | 本轮补齐 |
| Shell 终端 | ShellScreen（rootShell/userShell） | FeaturesHub SHELL | 已覆盖 |
| 轮询频率（1s/5s/10s） | SettingsTabContent「轮询频率」三档 | TabPages + pollIntervalMs | 本轮补齐 |
| APN 管理 | ApnScreen（APN_PROC_EX） | NetworkPages2 | 已覆盖 |
| 流量限额 | CalibrateScreen（get/setDataLimit） | DevicePages | 已覆盖 |
| HTTP 代理调试 | ProxyDebugScreen（GET/POST，响应文本只读） | TabPages + proxyDebug | 本轮补齐 |
| 昵称设置 | NicknameScreen（get/setNickname） | TabPages | 本轮补齐 |
| OTA 升级 | OtaScreen | FeaturesHub OTA | 已覆盖 |
| 热点管理 | HotspotScreen | DevicePages | 已覆盖 |
| 省电与自启 | PerformanceScreen | DevicePages | 已覆盖 |
| 网络 ADB / ADB 一键脚本 | AdbScreen（getWirelessAdb/setWirelessAdb/adbAlive） | FeaturesHub ADB | 已覆盖 |
| 插件管理 | PluginScreen | FeaturesHub PLUGIN | 已覆盖 |
| 定时任务 | TasksScreen（六类动作等价） | FeaturesHub TASKS | 已覆盖 |
| 设备 Web 主题 / 插件注入 | PluginScreen setCustomHead | FeaturesHub PLUGIN | 已覆盖 |
| 设备重启 / 关机 | RebootScreen | FeaturesHub REBOOT | 已覆盖 |
| 关于（版本/作者/开源清单） | SettingsTabContent「关于」卡片 | TabPages | 本轮补齐（精简版） |

### 6.3 暂不实现（官方有而星灵未接，原因）

| 官方功能点 | 原因 |
| --- | --- |
| 网络测速（内网/外网自定义 URL） | 星灵 SpeedtestScreen 已覆盖后端流式测速，官方自定义 URL 参数属增强，暂不接 |
| 定时任务动作参数（锁频段任务 JSON） | 星灵 TASKS 已支持动作映射，官方锁频任务参数 JSON 细分暂不逐项对齐 |
| 设备 Web 主题下发 / 主题保存 | 属设备端主题定制（get_theme/set_theme），星灵保留本机 iOS 风格，暂不接 |
| Monet 动态取色 / Miuix 组件库 | 任务明确保留星灵 iOS 玻璃卡片风格、禁用 Miuix，刻意不实现 |
| 多语言（约 80 种） | 星灵当前为中文界面，任务未要求国际化 |
| 网络 ADB 存活轮询 UI / ADB 密码分级提示 | 星灵 AdbScreen 已提供开关+状态，官方细分提示暂不逐项对齐 |
| 流量限额状态短信上报（power_status_forward_enabled） | 属电源状态上报联动，星灵 DataLimit 主链路已覆盖，暂不接 |
| 插件市场/导入插件.txt/插件注入 Web head | PluginScreen 已覆盖市场与自定义头，导入 txt 暂不接 |
| 灯效/蜂鸣（beep_my_device / INDICATOR_LIGHT_SETTING） | 特殊硬件能力，非 UFI-TOOLS 通用端点，暂不接 |
| RNDIS / USB 状态 / SELinux 查询 | 属设备底层调试项，root_shell 可兜底，星灵暂未单独接 |

---

## 7. 高级后台兼容批（E 批，2026-09-16）

> 依据：`高级后台刷入文件 参考` 与 `UFI-TOOLS-PLUGINS` 插件脚本结论（详见调研报告）。
> 实现路径：`DeviceFeatures.kt` 契约 → `UfiToolsFeatureApi.kt` 实现（rootShell / uploads）→ UI 接线（FeaturePage / FeatureCard），不涉及分区擦写等高风险操作。

| 能力 | 星灵实现 | 关键命令 / 接口 | 状态 |
| --- | --- | --- | --- |
| 一键禁用系统更新 | SysUpdateScreen（路由 SYS_UPDATE） | rootShell 逐条 `pm disable-user` + `pm uninstall -k --user 0`，组件：com.zte.zdm / cn.zte.aftersale / com.zte.zdmdaemon / com.zte.zdmdaemon.install / com.zte.analytics / com.zte.neopush | 本轮新增（逐条回显） |
| AB 分区展示 | PartitionScreen（路由 PARTITION） | rootShell `getprop ro.boot.slot_suffix` | 本轮新增 |
| 当前分区镜像提取 | PartitionScreen | rootShell `dd if=/dev/block/by-name/boot_${slot} of=<uploads 目录>/boot_${slot}.img` | 本轮新增 |
| 镜像下载到本机 | PartitionScreen | `GET /api/uploads/<fileName>`（星灵本轮首次对接该端点） | 本轮新增 |
| DIAG 查 IMEI | DiagScreen（路由 DIAG） | rootShell 读写 `/dev/sdiag_nr`（slot 1 为 `/dev/sdiag_nr2`），帧模板命令码 5E81 / 5E82 / 5E90 | 本轮新增（帧长度/CRC 待真机校准） |
| CPU / 内存占用趋势 | OverviewScreen `CpuMemTrendCard` | 复用轮询 `baseDeviceInfo` 的 cpuUsage / memUsage 内存采样（近 60 点 Canvas 折线，与当日流量图同风格） | 本轮新增 |
| LTE 频段列表 | BandsScreen「支持频段（LTE）」 | 内置 `DEFAULT_LTE_BANDS` 参考列表，点击选择与锁频输入联动 | 本轮补齐 |
| 当前服务小区实时频段 | BandsScreen「当前服务小区（实时频段）」 | 后台信号快照解析：制式 / 实时频段 / PCI / RSRP / RSSI / SINR | 本轮补齐 |

### 7.1 诚实标注

- `/api/uploads` 端点：官方插件确认其存在（`GET /api/uploads/<file>` 下载设备 uploads 目录文件）；星灵本轮首次对接，字段与返回体以真机为准，失败时 UI 明确回显错误文本。
- DIAG 帧：仅落地 5E81 / 5E82 / 5E90 三个命令码模板（来源为高级后台插件记录），完整帧的长度字节与 CRC 随机型不同，**未做臆断补全**，需真机校准。
- 高风险操作（分区擦写、NV 降级、显串修补）**未**做进 App，仅作为电脑端教程知识，符合调研报告 4.2 / 4.3 结论。

*（内容由AI生成，仅供参考）*
*（内容由AI生成，仅供参考）*
*（内容由AI生成，仅供参考）*
