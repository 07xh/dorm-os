# 触序 · TapFlow V1.17.0 · 原生交互流畅度改善

- 对齐已有 `docs/prototypes/dorm_os_nfc_interactive_v2_3.html`：只保留现有「首页 / 场景 / 工具」，不添加第二套 Web UI。
- 使用 Compose AnimatedContent，将生硬的页面跳变改为 160–180ms 短位移与淡入淡出；三页独立滚动状态不变，真实 NFC / KSU 回调原样保留。
- 原有 V2BottomSheet 使用轻量 GPU 图层淡入/上浮 240ms，遮罩同步淡入，避免拖动重排内部长文本和输入框。没有常驻动画或后台进程。
- 通过 `remember` 缓存脚本名称映射、常用场景映射与筛选结果，降低无关状态变化时的重复列表计算。
- Android 版本：`1.17.0-motion-preview` / versionCode 31。
- **此项只是交互层重构，尚未取得 FPS/卡顿帧/耗电实测数据**。需要精确 HEAD 运行 Kotlin 单测、Debug APK 编译与 KernelSU 测试；一加 Ace 6 Android 16 需实测窄屏、输入法、动画缩放关闭、长 SH 列表、前台 NFC 弹窗及取消/中断保护。
- PR #12 保持 Draft；无真机验收不得直接合并或正式发布。
