# 校序 UI V2

目标：独立重构原生表现层，不修改经验证的接口、业务、数据库、登录会话与同步。V1 正式版保留至 V2 完成功能及性能验收。禁止在五个旧页面继续堆叠临时样式；迁移期间旧组件只通过兼容入口接入。

## 当前切片

- `ui/v2/UiV2Shell.kt`：导航外壳与页面状态分离，底栏仍复用北化玻璃实现，页面状态由原 NativeShell 持有。
- `UiV2Window.kt`：统一手机、平板及短横屏窗口分级；600/840dp 宽度、480dp 高度边界。
- `UiV2Motion.kt`：统一触控反馈，工具栏、可点击卡与选择器已接入。优先图层更新，点击回调不等待动画。
- 原主导航即时切换、每页 saveable 状态和业务回调原样保留。
- 当前不代表五页已全部换新。

## 上游及许可

DSH Go 固定参考提交： https://github.com/ccch1mneyyy/dsh-Go/tree/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0

- AppMotion: https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/theme/motion.dart （MIT 标注，Copyright 2026 CikeSeven；当前 Compose 实现参考其参数及原则，非复制 Dart 文件）。
- AdaptiveLayout: https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/shell/adaptive/adaptive_layout.dart （窗口尺寸规则参考；独立 Compose 实现）。
- 第三方声明： https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/THIRD_PARTY_NOTICES.md
- 优先源码级移植明确 MIT 授权的通用组件；如实际移植，随代码和 APK 保留完整许可、版权、来源与修改声明。对 AGPL 文件须先处理校序整体许可及分发义务；不复用不在授权范围内的品牌、美术或专有资产。

## 迁移与验收

1. V2 shell 与 motion：构建、窗口边界单测、快速连续切页、深链接与返回栈、无障碍点击及系统动画比例。
2. V2 通用弹层、主题、输入及列表：统一关闭语义、输入法安全区、未保存内容保护，避免多层模糊。
3. 总览、课表、校园、任务、我的五页逐页迁移；保留旧版真实记录与业务回调的功能对照。
4. 平板、横屏和动态窗口；详情面板与返回手势在单页测量基线之上引入，禁止同时重组两个重型页面。
5. 使用已有 NavigationFrameBenchmark 和 StartupBenchmark 比较 V1/V2，记录设备、刷新率、构建类型、P50/P95/P99 帧耗时及掉帧。高刷为目标而非无实测保证。
6. 通过同包升级、本地数据库、通知、会话、课表和任务回归后，才清除已无引用的 V1 UI、发布 APK。

本分支尚未构建或真机验收。
