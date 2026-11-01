# NFC 只读扫描器 V1.10

## 交付
- 独立 Compose 前台 ReaderMode 读卡页，显示标签技术类型、NDEF 类型、容量、可写信息、文本与 URI 记录。
- 与脚本分发和 KernelSU 完全隔离；不会将被扫描的 URL 自动打开，也不会执行 Root。
- NFC 页面和主页可直接进入读卡器；原有写入 UUID / 回读验证 / 绑定流程保持不变。
- ReaderMode 仅在页面处于前台时启用，离开页面时关闭；无新增后台服务。

## 开源复用清单
- Android 原生 NFC API：使用系统 Ndef / NdefRecord / NfcAdapter。
- Jetpack Compose Material 3：现有项目中的开源 UI 组件框架，未另引入 React Native。
- NFCReader by mueller-ma（Apache 2.0）：参考其基于 AOSP 的 Text RTD 解码实现，按本项目错误边界重新整理；保留来源说明，并把许可原文打入 APK assets/licenses。
- 原版 WAKDEV NFC Tools 为专有 Android 应用，未复制其 APK、源码或资源。

## 尚待验收
- GitHub Actions 的 testDebugUnitTest 与 assembleDebug 结果。
- 一加 Ace 6 / Android 16：实体文字卡、URL 卡、DORM 卡、只读卡、非 NDEF 卡。
- 后台、锁屏 NFC 系统分发和 Root 互斥回归；实测耗电。
- 不能把通用读取成功表述成所有 NFC Tools 写入能力均已实现。
