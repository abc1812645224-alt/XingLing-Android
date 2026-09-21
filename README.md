# 星灵 (XingLing)

手机直连随身 WiFi 设备后台的控制 App，全程走局域网 HTTP，不依赖网页、不依赖云端。

> 版本：`versionName 0.1.0`（versionCode 9）· 包名 `com.xingling.app`

## 它能做什么

手机连上随身 WiFi 后，星灵直接与设备后台（默认 `http://192.168.0.1:2333`）通信，把原本要在手机浏览器里操作的设备后台，做成 5 个 Tab 的原生界面：

- **总览**：设备型号、电量、CPU/内存、今日本月流量、信号仪表、载波聚合、常用入口（热点 / 重启 / 定时任务）
- **信号**：实时信号仪表盘，RSRP / RSRQ / SINR / RSSI、小区与频段、载波聚合状态
- **频段**：VoLTE / VoNR、锁频段、锁基站、网络模式、双卡切换、APN、流量校准、性能模式
- **短信**：收件箱、未读标记、删除、已读、短信转发（钉钉 / 邮箱 / Curl / 黑名单）
- **设置**：Root 终端、Web 终端（ttyd）、SSH、无线 ADB、OTA、插件商店、测速、局域网设备、SMB、系统更新治理、分区备份、DIAG

另提供 **4×2 / 2×2 桌面小组件**，分层自适应刷新（点击 / 亮屏 15s / App 内预览 / WorkManager 15 分钟 / 系统 30 分钟兜底）。

## 技术栈

Kotlin + Jetpack Compose + Glance（桌面小组件）+ Hilt + WorkManager；网络层零第三方依赖，全部 `HttpURLConnection` + `org.json`。

首发适配 **UFI-TOOLS（ZTE 系）** 后台；紫光展锐 / 高通适配器的抽象层（`DeviceBackend` / `DeviceFeatures`）已就位。

## 编译

本地需要 JDK 17 与 Android SDK（compileSdk 34）。

```powershell
# Debug
.\gradlew.bat assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或推送 GitHub 后在 **Actions** 运行 `XingLing Build CI`，自动构建 Release 并在打 `v*` tag 时发布。

> 工程路径含中文（`F:\星灵`），`gradle.properties` 中的 `android.overridePathCheck=true` 为必需，勿删。

## 使用

1. 手机连上随身 WiFi 发出的热点。
2. 打开星灵，自动扫描设备；或手动输入设备后台地址（默认 `192.168.0.1`，端口 `2333`）。
3. 输入后台口令（默认 `admin`）即可。需要 Root 终端等高级功能时，设备须刷入对应高级后台。

## 致谢

核心协议、签名算法与接口定义参考了开源项目 **UFI-TOOLS（Minikano / kanoqwq，MIT）**。完整致谢见 [`docs/credits.md`](docs/credits.md)。

## 许可证

本项目遵循 **GPL-3.0**。
