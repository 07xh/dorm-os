# DORM OS NFC Receiver · V0.9.0 Preview

本 APK 已从完整 NFC 管理应用缩减为**一次性 NFC 接收器与写卡入口**。场景管理、标签绑定、动作白名单、配置备份、执行记录和可视化界面全部迁移到 KernelSU 模块 WebUI。

## APK 只负责什么

- 接收 Android NFC 系统分发的私有 NDEF MIME：application/vnd.dormos.ncwu。
- 从标签读取 v2:tag:<UUID> 场景编号；不读取或执行标签提供的 Shell 命令。
- 通过固定命令调用 KernelSU 模块 dispatcher；只把经过 UUID 格式校验的编号传给固定入口。
- 从 KernelSU WebUI 启动一次性写卡 Activity，将场景 UUID 写入自有可写 NFC 标签，并对已格式化标签执行写后回读。
- 没有网络权限、位置权限、后台 Service、Boot Receiver、轮询或持续 WakeLock。

APK 主界面只说明它是 NFC 接收器；日常管理应在 KernelSU → 模块 → DORM OS NFC Bridge → WebUI 中完成。

## KernelSU 模块负责什么

- 持久化场景配置：/data/adb/dormos_nfc_bridge/，避免模块更新覆盖用户配置。
- 白名单 dispatcher：ncwu-check、wifi-status、open-url、dorm-scene。
- 默认关闭 NFC Root 触发；用户在 WebUI 明确启用后才接受 NFC 场景调用。
- 记录最多 100 条脱敏执行日志，不记录 URL、SSID、IP、MAC、账号、密码或网络响应正文。
- 提供配置管理、旧版 JSON 备份导入/导出、写卡入口、手动测试和执行日志。
- 不包含 service.sh、post-fs-data.sh、boot-completed.sh、initrc、sepolicy 或其他开机脚本。

## Ncwu 登录状态

Ncwu 自动登录仍锁定。当前 ncwu-check 仅识别 SSID 与 wlan0 IPv4 是否存在，不提交账号密码，也不代表 Dr.COM 认证成功。只有本人正常登录成功请求、服务端状态变化和安全传输证据闭合后，才允许增加自动认证动作。

## 旧版迁移

旧版 APK 的自定义标签管理中先导出 JSON；升级到此版本后，在 KernelSU WebUI 导入旧版备份。旧版 v2 UUID 标签格式保持兼容；配置导入完成后原标签可继续使用。旧版 v1:ncwu 标签映射为只读 Ncwu 检查，需要在 WebUI 开启 NFC 触发。

## 构建与验收

需要 JDK 17、Gradle 8.11.1、Android SDK Platform 36。GitHub Actions 构建 Debug APK、可选稳定签名 Release APK 和 KSU ZIP。自动化测试覆盖 UUID 校验、Root 命令固定性、WebUI 配置验证与 dispatcher 正反例。

仍需在一加 Ace 6 / Android 16 实测：系统“通过 NFC 启动”权限、亮屏/息屏与解锁边界、NDEF 写卡回读、KernelSU Root 授权、浏览器启动和实际待机耗电。未完成真机测试前不宣称完全兼容或绝对零耗电。
