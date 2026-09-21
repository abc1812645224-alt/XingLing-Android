---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 001b83761c348f6205ef0555a74ffa33_c94533c3b18711f18f26525400287e28
    ReservedCode1: NtPdoCBjT8BhEl6E1wuEWOvm07jiynDqa8+X4SJJdCZMQD/ADnBHIl1JOgUI2PhAkEJwUo3xmjXoV0HGymGboNJ38iKiu3uXGMnNEXfCCYjy8uxkv6tjT1N5n3qYC3N4hOSrgU7eK6FbaLUZgzTb5O6UqSMkCeBbGC/JC/r4x47551DfbEStSuC35VA=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 001b83761c348f6205ef0555a74ffa33_c94533c3b18711f18f26525400287e28
    ReservedCode2: NtPdoCBjT8BhEl6E1wuEWOvm07jiynDqa8+X4SJJdCZMQD/ADnBHIl1JOgUI2PhAkEJwUo3xmjXoV0HGymGboNJ38iKiu3uXGMnNEXfCCYjy8uxkv6tjT1N5n3qYC3N4hOSrgU7eK6FbaLUZgzTb5O6UqSMkCeBbGC/JC/r4x47551DfbEStSuC35VA=
---



# 星灵 · 桌面小组件（Jetpack Glance）

> 版本：v0.1（2026-09-16）
> 参考物：`F:\星灵\桌面小组件参考\UfiPeek-v3.0 2.omni`（UFI-TOOLS 设备状态小组件，JS 脚本）
> 产物：`F:\星灵\app\build\outputs\apk\debug\app-debug.apk`

## 1. 交付内容

| 尺寸 | 类名 | 说明 |
|---|---|---|
| 4×2 横条（主） | `XingLingWidget` / `XingLingWidgetReceiver` | 全字段设备状态 |
| 2×2 迷你 | `XingLingWidgetSmall` / `XingLingWidgetSmallReceiver` | 精简字段 |

字段对齐参考实现：设备名/型号、信号格、电量、今日流量、本月流量、运营商、当前频段、CPU 占用、CPU 温度、WiFi 频段、内存占用、RSRP、SNR、未读短信、更新时间。

## 2. 新增文件

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/xingling/app/widget/WidgetSnapshot.kt` | 数据快照 + JSON 序列化 + 文本格式化（流量/信号格/电量/温度/百分比/时间） |
| `app/src/main/java/com/xingling/app/widget/WidgetSnapshotStore.kt` | 快照缓存读写（SharedPreferences），刷新失败时回显 |
| `app/src/main/java/com/xingling/app/widget/WidgetDataLoader.kt` | 取数编排：三路并行 + 独立超时 + 降级兜底 |
| `app/src/main/java/com/xingling/app/widget/XingLingWidgetUI.kt` | Glance UI（iOS 玻璃卡片风格，深浅色自适应） |
| `app/src/main/java/com/xingling/app/widget/XingLingWidget.kt` | 两个尺寸的 GlanceAppWidget、刷新 ActionCallback、AppWidgetReceiver |
| `app/src/main/java/com/xingling/app/widget/WidgetRefreshWorker.kt` | WorkManager 30 分钟周期刷新 + 调度器 |
| `app/src/main/res/xml/xingling_widget_4x2_info.xml` | 4×2 appwidget-provider |
| `app/src/main/res/xml/xingling_widget_2x2_info.xml` | 2×2 appwidget-provider |
| `app/src/main/res/values/colors.xml`、`res/values-night/colors.xml` | 小组件配色（浅色 / 深色） |

修改文件：`app/build.gradle.kts`（+glance-appwidget 1.0.0、+work-runtime-ktx 2.9.0）、`AndroidManifest.xml`（注册两个 Receiver）、`res/values/strings.xml`（小组件标签/描述）、`XingLingApplication.kt`（启动时幂等注册周期刷新任务）。

## 3. 数据链路（复用既有实现，未新增端点）

| 字段 | 来源 |
|---|---|
| 设备型号、电量、充电、CPU 占用/温度、内存占用、今日/本月流量 | `UfiToolsBackend.fetchOverview()` → `/api/baseDeviceInfo` |
| 运营商、网络类型、信号格、RSRP、SNR、未读短信、NR/LTE 频段 | `UfiToolsGoform.goformGet()` → 官方后台 goform（自动登录会话复用） |
| WiFi 频段（5G / 2.4G） | `DeviceFeatures.rootShell()` → `dumpsys wifi` 频点解析 |
| RSRP/SNR 兜底 | `UfiToolsBackend.fetchSignalInfo()`（AT 通道，仅当 goform 无值时触发） |

设备地址与口令来自本地已存 `DeviceStore`（host/port/token），首次接入后小组件无需用户重新输入口令。

降级策略：三路请求各自设超时（12s / 14s / 8s）；主链路全部失败时读取上次成功快照并标注「缓存」；任一子项失败仅该字段显示 `--`，不影响其余字段。

## 4. 刷新与交互

- 后台刷新：`WidgetRefreshWorker`（WorkManager，30 分钟周期，`ExistingPeriodicWorkPolicy.KEEP` 幂等注册），在小组件 `onEnabled` 与应用启动时注册。
- 系统周期：`updatePeriodMillis=1800000`（30 分钟，系统兜底触发）。
- 渲染顺序：先用缓存快照即时出图 → 后台取数 → 成功后重组界面。
- 点击整张卡片：打开星灵 App（`MainActivity`）。
- 卡片右下「刷新」：`actionRunCallback<WidgetRefreshAction>` 立即刷新全部星灵小组件。

## 5. 视觉规范

沿用 App 内 iOS 玻璃卡片风格：圆角 22dp（迷你 20dp）+ 半透明玻璃底 + 主/次/三级文字层级；强调色使用系统蓝、信号格按强度用绿/蓝/红；深浅色由 `values-night` 颜色资源自动切换。未使用参考实现的浅灰蓝配色。

## 6. 编译与校验记录

- 编译命令：`gradlew :app:assembleDebug`
- 产物：`F:\星灵\app\build\outputs\apk\debug\app-debug.apk`（32,670,081 字节，2026-09-16 12:10）
- 合并清单校验：`XingLingWidgetReceiver`、`XingLingWidgetSmallReceiver` 及其 `android.appwidget.provider` meta-data 均已注册；Glance 与 `androidx.work.WorkManagerInitializer` 正常合并。
- APK 内容校验：`res/xml/xingling_widget_4x2_info.xml`、`res/xml/xingling_widget_2x2_info.xml` 已打包；`XingLingWidgetReceiver`、`XingLingWidgetSmallReceiver`、`WidgetRefreshWorker` 类已进入 dex。
- 既有功能影响：仅新增 `widget` 包与两个 Receiver；`XingLingApplication.onCreate` 仅追加幂等刷新注册（异常自动吞掉），未改动任何既有页面/接口逻辑。

## 7. 添加方式

长按桌面空白处 → 小组件 → 找到「星灵」→ 选择「星灵 · 设备状态」（4×2）或「星灵 · 迷你状态」（2×2）拖入桌面。

## 8. 字段取舍（按布局余量量算，先量后定）

量算口径：行高 = 字号×1.35；全角字符宽 = 字号×1.0；半角 = 字号×0.55；空格 = 字号×0.30。可用区 = appwidget-provider 尺寸 − Glance 内边距（水平 14dp / 垂直 10dp）→ 4×2 = 222.0dp × 90.0dp，2×2 = 82.0dp × 90.0dp。

| 组件 | 改前 | 改后 | 结论 |
|---|---|---|---|
| 4×2 | 高 102.0dp（超 12.0dp）；技术行宽 275.5dp（超 53.5dp） | 高 86.9dp（余 +3.1dp）；最宽行 199.8dp | 有余量 → **保留全部字段** |
| 2×2 | 高 89.0dp（余 +1.0dp）；页脚宽 166.9dp（超 84.9dp） | 高 84.3dp（余 +5.7dp）；最宽行 56.1dp | 无余量 → **砍到信号 / 电量 / 流量三项** |

调整手段（不删字段）：4×2 合并「设备名 + 网络类型 + 信号 + 电量」为单行、流量改 12sp 双栏、技术行压到 9sp 并合并为一行；2×2 移除设备名 / 更新时间 / 刷新入口，仅保留信号格、电量、今日流量、本月流量。

最终字段清单：

- **4×2**：设备名、网络类型、信号格、电量（含充电态）、今日流量、本月流量、运营商、频段（N/B）、CPU 占用、CPU 温度、WiFi 频段（5G/2.4G）、RSRP、SNR、内存占用、未读短信（ZTE 特有）、更新时间（含「缓存」标记）、刷新按钮。
- **2×2**：信号格、电量（充电态显示为「100%·充」）、今日流量、本月流量。

充电最坏文案行宽 71.5dp，未超 2×2 的 82.0dp 可用宽度。

## 9. 登录方式优化（参考 UfiPeek v3.0 降级登录链路）

`UfiToolsGoform.login()` 改为四步链路：

1. `GET goform_get_cmd_process?cmd=loginfo,LD&multi_data=1`（不带会话）→ 探测登录态 + 取 LD；
2. `loginfo != ok` → ZTE 原生 `POST goform_set_cmd_process`（`goformId=LOGIN`），纯算法实现，**未引入 zreq 等外部二进制**；
3. 登录后重取 `loginfo` 校验（`result=0` 或 `loginfo=ok` 视为成功）→ 持久化 Kano-Cookie；
4. 全链路失败 → 回落既有 `LOGIN_MULTI_USER` 链路，保持原行为。

- `passwordHash = SHA256(ZTE口令).toUpperCase()`；`loginHash = SHA256(passwordHash + LD).toUpperCase()`。
- 口令区分：连接页「后台口令」= UFI-TOOLS 口令；「高级设置」新增 ZTE 专用口令，**留空则复用后台口令**；持久化于 `DeviceStore.zteToken`。
- 失败回显：抛出后台原始错误（HTTP 状态、loginfo、响应正文片段），连接页原样展示，不做静默吞掉。

改动文件：`UfiToolsGoform.kt`（链路重写 + `zteNativeLogin`）、`UfiToolsBackend.kt`（透传 `zteToken`）、`DeviceStore.kt`（`KEY_ZTE_TOKEN`）、`DeviceSetupScreen.kt`（ZTE 口令输入框）、`AppRoot.kt` 与 `WidgetDataLoader.kt`（传参）。

编译校验：`gradlew :app:assembleDebug` → BUILD SUCCESSFUL，产物 `F:\星灵\app\build\outputs\apk\debug\app-debug.apk`（32,670,081 字节，2026-09-16 12:31）。
*（内容由AI生成，仅供参考）*
*（内容由AI生成，仅供参考）*
