# 星灵 (XingLing) · 随身WiFi 后台控制 App — 设计文档

- 日期：2026-09-14
- 版本：v1.0（设计定稿）
- 工程位置：`F:\cpeview`（全新独立工程）
- 包名：`com.xingling.app`
- 目标设备：Android 手机（minSdk 与现有工程对齐，沿用 Kotlin + Jetpack Compose）

## 1. 产品定位

「星灵」是一款**纯手机端**的移动随身WiFi 后台控制面板。用户连接随身WiFi 的局域网后，App 自动发现设备、直接调用设备端已部署的管理后台（第一版：UFI-TOOLS），无需打开网页即可查看实时信号/流量并深度控制。架构预留多态后台适配层，后期兼容紫光展锐、高通等其它后台。

## 2. 关键决策记录

| 项 | 决策 |
|---|---|
| 工程形态 | 方案A：全新独立工程 `F:\cpeview`，从零重写 UI；仅迁移现有「信号强度环形仪表」「载波聚合」两张卡片设计与代码 |
| 后台对接 | 第一版 `UfiToolsBackend`：HMAC 签名 + 调 `:2333` JSON API；`DeviceBackend` 接口做多态，后期加 `QualcommBackend` / `UnisocBackend` |
| 连接前提 | 必须连接随身WiFi 局域网（登录/控制均走该网段） |
| 设备发现 | 局域网自动发现 + 手动输入 IP 兜底 + 扫码添加 |
| 设备管理 | 多设备，图形化卡片列表，点卡片进入该设备全功能界面 |
| 导航 | 方案B：点设备先进「总览」一屏（信号+流量+常用控制快捷键），再进深层操作页 |
| 鉴权 | 首次连接手动输口令 → 本机加密存储 → 自动登录；账密页带「扫码登录」入口 |
| UI 风格 | 沿用现有 iOS 风格：圆角玻璃卡片 + Canvas 环形仪表 + 动态变色，简洁实用、一眼明白 |
| 动画 | 数值变化平滑过渡（animateFloatAsState）、仪表缓动、卡片按压反馈、后台轮询自动刷新 |
| 启动页 | 首启展示"参考 UFI-TOOLS（作者 kanoqwq / Minikano，MIT License）"致谢，确认一次后不再弹 |
| 应用名 | 星灵（XingLing） |
| 图标 | 方案C：灵动圆环（双层圆环 + 流星拖尾，简洁几何，浅底风格） |

## 3. 架构

```
┌────────────────────────────────────────────┐
│  UI 层 (Compose)                           │
│  Launch → DeviceList → DeviceOverview →    │
│    Signal/BandLock/Network/SMS/Hotspot/    │
│    Device/Speedtest/AT 深层页              │
└──────────────┬─────────────────────────────┘
               │ 状态(StateFlow) / 数据模型
┌──────────────▼─────────────────────────────┐
│  领域层                                     │
│  DeviceBackend 接口（发现/连接/信令/控制/轮询）│
│  AuthManager（口令加解密, 自动登录/扫码解析）│
│  DeviceRegistry（设备配置持久化, 多设备）    │
└──────────────┬─────────────────────────────┘
               │
┌──────────────▼─────────────────────────────┐
│  适配层 (backend/)                          │
│  UfiToolsBackend  ← 签名 + HTTP :2333       │
│  (预留 QualcommBackend / UnisocBackend)     │
└──────────────┬─────────────────────────────┘
               │ HTTP
     [ 随身WiFi 设备 · UFI-TOOLS 服务 ]
```

### 3.1 模块职责

- `DeviceBackend`（接口）：`discover()`、`connect(ip, port, token)`、`fetchOverview()`、`signal/network/traffic` 各查询、`execAt()`、`setBandLock()`、`setCellLock()` 等控制动作、`signalChange` 状态流。
- `UfiToolsBackend`（实现）：实现 kano 签名（HMAC-MD5 + SHA256 三段式）、`authorization=SHA256(口令)hex`、JSON 编解码、轮询刷新。
- `AuthManager`：`EncryptedSharedPreferences` 存口令/设备列表；自动登录；扫码结果解析（IP+端口+口令）。
- `DeviceRegistry`：设备别名、IP、端口、口令引用、最后在线时间；多设备卡片数据源。

### 3.2 复用的两张卡片

1. `DeviceAndSignalGaugeCard` —— 信号强度 RSRP 环形仪表（Canvas 动态变色，四级标准分级图例）。
2. `CarrierAggregationCard` —— 载波聚合 PCC/SCC 展示（多载波状态 + 详情）。

> 从 `F:\ccc` 现有工程迁入，适配数据源由"本机 Shizuku 采集"改为"后台 API 返回"。

### 3.3 关键数据来源（UFI-TOOLS API）

- 总览：`GET /api/baseDeviceInfo`（电量/温度/CPU/内存/流量/网络）、`GET /api/goform` 主页 38 字段轮询（RSRP/RSSI/速率/运营商/卡槽…）
- 信号详情：`GET /api/AT?command=...`、`getSupportNrBandList`
- 控制：`/api/goform` 写操作（频段锁 LTE/NR_BAND_LOCK、CELL_LOCK、SET_BEARER_PREFERENCE、SEND_SMS、setAccessPointInfo、DATA_LIMIT_SETTING、REBOOT 等）
- 工具：`/api/speedtest`（测速）、`/api/root_shell` / `user_shell`（AT 调试/高级命令，按权限分级降级隐藏）

## 4. 页面流

1. **启动致谢页（仅首启）**：展示项目参考声明，按钮「开始使用」。
2. **设备列表（主页）**：自动扫描发现 → 图形化设备卡片（信号格/电量/别名/运营商/在线状态）；「+ 手动添加」「扫码」入口；空态提示"请连接随身WiFi"。
3. **账密页**：首次连接输入口令 + 可编辑端口(2333)；「扫码登录」入口；验证失败回显错误。
4. **设备总览**（点卡片进入）：
   - 顶栏：设备别名 + 运营商/网络模式 + 在线状态
   - 信号强度环形仪表（复用卡片，动态刷新 + 动画）
   - 实时上下行速率 + 当月流量进度环
   - 常用一键开关（蜂窝数据连接 / 漫游 / 省电性能模式）
   - 载波聚合卡片（复用）
   - 模块入口宫格：信号详情 / 锁频锁网 / 网络切换 / 短信 / 热点与设备 / 测速 / AT
   - 下拉刷新
5. **深层功能页**：
   - 信号详情（RSRP/SINR/PCI/Band、邻区、VoLTE/VoNR）
   - 锁频锁网（支持频段列表选择、4G/5G 频段锁、锁基站 PCI+EARFCN、当前锁配置展示与解锁）
   - 网络切换（5G/4G/自动、SIM 卡槽切换、APN 管理）
   - 短信（列表、发送、单条删除/标记已读、未读数）
   - 热点与设备（WiFi 参数、接入设备列表、黑白名单、局域网 DHCP）
   - 测速（上下行实时速率、进度）
   - AT 调试（命令输入、历史、输出展示；root shell 按可用性分级显示）

## 5. 错误处理与边界

- 未连随身WiFi / 设备离线：统一空态提示 + 引导"请连接随身WiFi"。
- 口令错误 / 401：回到账密页重输，不清除已存其它设备口令。
- 签名失败或会话失效：自动重连一次，仍失败提示登录态失效。
- 超时（HTTP 30s / 轮询 10s）：标记设备离线、停止轮询。
- 权限分级降级：无 root（高级功能未开）时隐藏 root_shell/部分 AT 高级入口，仅提示"需设备开启高级功能"。

## 6. 测试要点

- 真机联测：连随身WiFi → 自动发现 → 登录 → 总览数据刷新 → 锁频锁网 → 热点/短信操作 → 断网空态。
- 多设备：新增两台设备配置，切换管理、离线标记。
- 兼容：Android 8.0+（minSdk 26 起），深色/浅色主题适配。

## 7. 产出物清单（v1 里程碑）

1. `F:\cpeview` 全新 Compose 工程骨架（包名 com.xingling.app）
2. `backend/` 适配层 + `UfiToolsBackend` 实现
3. 启动致谢页 / 设备列表 / 账密页 / 设备总览 / 深层功能页（先总览+信号+锁频，其余迭代）
4. 复用信号仪表 + 载波聚合两张卡片
5. 图标（方案C 灵动圆环）与启动图
