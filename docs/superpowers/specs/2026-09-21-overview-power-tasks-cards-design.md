# 星灵 · 总览页「电源控制」「定时任务」卡片设计

日期：2026-09-21

## 背景

总览页（`OverviewScreen`）目前没有独立的电源与定时任务卡片，这两类操作只能通过页尾「常用功能」卡（`QuickFeatureCard`）的两条列表行进入二级页（`RebootScreen` / `TasksScreen`）。

源码中另有 `PowerControlCard`、`TasksOverviewCard` 两段历史遗留实现，零调用（死代码）。

## 目标

1. 总览页新增两张**独立卡片**：电源控制、定时任务，全部为页内直接操作，**不进入二级页**。
2. 卡片视觉沿用总览现有 `GlassCard` 玻璃卡风格与彩色圆角图标（40dp 方块 / 圆角 10dp / 白色图标）。
3. 完成后**移除「常用功能」卡**及其在总览页的挂载点。
4. 清理死代码 `PowerControlCard` / `TasksOverviewCard`。

> 移除「常用功能」卡不会造成入口丢失：WiFi 热点由总览页已有的 `HotspotOverviewCard`（可开关 / 改 SSID / 密码 / 连接数）覆盖；重启关机与定时任务由本次新增的两张卡片覆盖。

## 卡片设计

### 一、电源控制卡

- **位置**：WiFi 热点卡之后。
- **结构**：同一行两张并排按钮卡（`weight(1f)`）——
  - 「立即重启」：橙 `0xFFFF9500` + `Icons.Filled.Refresh`
  - 「立即关机」：红 `iOSRed` + `Icons.Filled.Close`
- **交互**：点击 → 确认对话框（重启：设备将短暂离线，约 1~3 分钟恢复；关机：设备将断电，需现场通电）→ 执行期间按钮禁用并显示进度 → 卡片下方一行回执文案（成功用 `iOSGreen`、失败用 `iOSRed`）。
- **后端**：`backend.features.rebootDevice()` / `shutdownDevice()`。

### 二、定时任务卡

- **结构**：
  - 标题行：「定时任务」+ 右侧任务计数。
  - 任务列表：每行「时间 · 动作中文名 · 每天重复/单次」，行右侧「删除」；无任务时显示空态「暂无定时任务」；读取中显示「读取中…」。
  - 底部：「＋ 新建任务」按钮。
- **新建弹窗**：小时 / 分钟输入 + 动作三选（定时重启 / 定时关机 / 切换性能模式）+「每天重复」开关 → 提交。
- **后端**：`listTasks()` / `addTask(id, time, repeatDaily, actionMap)` / `removeTask(id)`。
- **动作映射**（沿用现有 `TASK_ACTIONS` 口径）：
  - `REBOOT_DEVICE` 定时重启
  - `SHUTDOWN_DEVICE` 定时关机
  - `PERFORMANCE_MODE_SETTING` 切换性能模式（`actionMap` 附带 `performance_mode`）
- **任务 id 规则**：与现有 `TasksScreen` 保持一致，`"{action}-{HH}{mm}"`。

## 数据流

- 任务列表在卡片挂载时拉取一次；新增、删除后重新拉取。
- 电源操作使用卡片局部 busy 状态，不阻塞总览页的轮询刷新。
- 总览页已有的 5s 轮询与数据管线不变。

## 影响面

- **改**：`ui/overview/OverviewScreen.kt`（新增两张卡片并挂载、删除死代码与「常用功能」卡挂载）、`ui/feature/FeaturesHub.kt`（移除 `QuickFeatureCard` 与 `QuickFeatureRoutes`）。
- **不改**：后端接口层；`RebootScreen` / `TasksScreen` 及其路由保留（代码可用，总览页不再跳转）。

## 验收

- 总览页可见「电源控制」「定时任务」两张卡片，页尾不再有「常用功能」卡。
- 重启 / 关机可在页内发起，有确认框与结果回执。
- 定时任务可在页内查看、新增、删除，结果与设备后台一致。
- `assembleDebug` 构建通过，装机后功能可见。
