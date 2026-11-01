# DORM OS NFC · 原生 UI V2（alpha1）

这是独立 Android 原生工程，**不是网站**、**不是 WebView**、**不是已验收 APK**。基于旧 v0.1 源码的 NFC 协议重新组织为 Kotlin + Jetpack Compose，沿用 `com.dormos.nfc` 包名、`dormos://scene/<id>` NDEF URL 与既有 `/data/adb/modules/dormos_nfc/bin/dispatch.sh` 白名单调度器。

## 已实现
- 三个主入口：**首页 / 场景 / 工具**，去除重复页面。
- NFC 前台扫描与可写 NDEF 标签写入（需手机真实 NFC）；写入有覆盖确认、取消扫描，支持系统派发的 NFC intent。
- 四个内置动作：学习 / 打开钉钉 / Ncwu 网络检查 / 打开 DORM OS。场景可增、改名、选择动作、启停；场景 ID 保持稳定。
- KernelSU/Root UID 检查；固定模块白名单动作通过 `su -c` 执行，15 秒超时，失败有文本回执。
- 最近 30 条运行状态本机保存；无轮询常驻服务、无开机自启、无联网权限。
- NFC 扫描 3 秒内同场景去重；对非 NFC intent、未知场景 ID 和停用场景拒绝执行。

## 与早期 APK 的差异 / 当前限制
- **脚本页当前仅支持模块内四种预设动作运行，不提供文件编辑器或任意 SH 文件管理**；后续应接入你提供的 ScriptRepository / PathGuard 并做独立测试。
- 没有接入侧键/三段式的原生驱动管理；只是为后续共享的「场景动作模型」留出接口。
- 新建自定义场景 ID 只存在本机 SharedPreferences。重装或清数据后不会恢复；需要后续备份/恢复。
- 未实现后台持续扫描。锁屏时是否弹出 NFC 系统分发，取决于 Android 系统及设备配置；不能保证任何锁屏状态触发。
- 未对 KernelSU 模块安装、真实 `dispatch.sh` 内容和返回语义进行端到端硬件测试。
- 新版本若与旧版使用不同签名，安装替换可能失败；请备份旧版配置后再评估卸载安装。不要把调试 APK 当稳定版本。

## 本地编译
安装 JDK 17、Android SDK Platform 36、Android Gradle Plugin 8.11.1 对应的 Gradle 8.13。工程根目录执行：

```sh
gradle :app:assembleDebug
```

编译结果位于 `app/build/outputs/apk/debug/app-debug.apk`。CI 见 `.github/workflows/android-nfc-build.yml`；仓库集成时放入 `android-nfc-assistant/`，将 workflow 放回仓库根目录的 `.github/workflows/`。**独立站点构建与 Cloudflare 部署不受影响。**

## 测试清单
1. 无 NFC / NFC 关闭：显示不可用，不假称扫描成功。
2. Root 不授权：显示未授权，执行失败可见。
3. 扫描未知或其他应用标签：不执行。
4. 扫描本应用旧标签：识别 `dormos://scene/study` 等四种旧 ID。
5. 重复刷同标签：3 秒内同场景不重复触发。
6. 写入需人工确认覆盖，停止扫描不应遗留 ReaderMode。
7. 场景改名、更换动作、关闭后，逻辑正确且重启仍保存。
8. 执行超时：进程终止并显示失败。
9. 不授予网络访问权限；App 也不在后台轮询。
10. 一加 Ace 6 / Android 16 + KernelSU 真机 NFC 和脚本调度验收。

## 安全约束
执行器仅允许 `study`、`dingtalk`、`ncwu-check`、`dorm` 四个固定标识符，不接收任意 Shell 参数；任何场景外发动作都应由用户明确配置。
