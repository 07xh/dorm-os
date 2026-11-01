# 白秋然雨 2.1 APK — 静态逆向分析记录

日期：2026-11-01
样本：用户此前上传的 `白秋然雨_2.1.apk`
大小：4,724,175 bytes
SHA-256：`8ea50c8fe1ac77720198c7364fbc17c9e8f0f5dafa6982f73d6ab1ff9c67bf23`
包名：`com.android.baiqiuranyu`
版本：2.1（versionCode 13）
minSdk：31；targetSdk：36；compileSdk：37

## 1. 分析边界

这是对 APK 本身进行的静态分析，不是原始 Kotlin 工程。DEX 类名大范围经过 R8 混淆，不能把反编译结果当成作者维护的源码，也不能保证重建后的代码与原 APK 每一处行为完全相同。用户表示作者已许可复用；该许可来自用户陈述，本 APK 内没有随包提供单独的许可文本或源码仓库地址。

未将 APK、DEX 或其二进制资源嵌入 DORM OS。当前采用功能级重写：参考行为和界面，再用本项目的 Kotlin/Compose 与 KernelSU Shell 代码实现。

## 2. 已核验的技术结构

- Android UI 使用 Jetpack Compose / Material3；DEX 中可见 Compose runtime 与相关 UI 类。
- 使用 AndroidX DataStore Preferences；DEX 和 APK 依赖元数据可见 DataStore 相关组件。
- 应用包名为 `com.android.baiqiuranyu`；主要入口为 `MainActivity`，另有 `fluidcloud.LiveUpdateReceiver` 与 `fluidcloud.VibrationActivity`。
- 存在 Root 状态检测、Shell 进程测试和命令执行封装。DEX 中出现 `hasRootAccess`、`echo SHELL_TEST`、`Shell check timeout`、`Root Shell 获取成功但无 Root 权限` 等符号/文案。
- DEX 中出现字符串 `LIBSU`，但类型引用表没有发现可确认的 `com.topjohnwu.superuser` API 类型。因此只能确认 Root/Shell 执行逻辑，不能仅凭字符串断言 APK 使用了某个特定 LibSU 版本。
- Shell 进程相关代码使用 Process 输入、输出和错误流、执行器/FutureTask；另有 Runtime.exec 调用。具体完整源码因 R8 混淆和当前工具限制未恢复。

## 3. SH 管理功能的直接证据

- 原脚本目录字符串：`/storage/emulated/0/Android/OnePlus/拓展命令`。
- DEX 中出现 `CreateScript`、`SaveScript`、`ExecuteScript`、`DeleteScript`、`ScriptFile`、`ScriptItem`、`ScriptsViewState` 等模型/动作名称。
- 文件校验方法关联字符串 `.sh` 与错误提示“只能操作 .sh 脚本”。
- 文件树遍历代码调用 `File.listFiles`，并检查目录、文件名与扩展名；资源字符串存在根目录面包屑、文件夹计数、空目录、文件夹创建、移动、重命名和脚本创建/保存/删除提示。
- 日志路径字符串：`命令执行日志.md`、`运行日志.md`。
- 这些证据足以支持重写“SH 文件库、目录浏览、编辑/保存、Root 执行、日志”功能；具体各个混淆方法的参数语义仍有部分不确定，不能伪称已经恢复完整原始源码。

## 4. 可借鉴的 UI / 辅助功能

- 截图和资源字符串支持：浅紫蓝配色、圆角面板、底部四栏导航、搜索、脚本卡片、运行/编辑/删除动作。
- 额外通知功能包含 Toast、振动、手电筒命令；本项目只保留必要的系统 Toast/状态提示，不复制流体云常驻能力。
- 手势/侧键模块有全局和应用作用域、按下/松开阶段、组合键等资源与代码线索；用户明确不需要，全部排除。

## 5. 权限与安全边界

原 APK Manifest 声明了 READ/WRITE_EXTERNAL_STORAGE（旧版兼容）、MANAGE_EXTERNAL_STORAGE、POST_NOTIFICATIONS、POST_PROMOTED_NOTIFICATIONS、VIBRATE、FLASHLIGHT、SCHEDULE_EXACT_ALARM、REQUEST_IGNORE_BATTERY_OPTIMIZATIONS、QUERY_ALL_PACKAGES 等权限；还声明了导出的 LiveUpdateReceiver 和 VibrationActivity。Manifest 未发现 INTERNET 权限。

DORM OS 不照搬这些宽权限或导出组件：使用 SAF 让用户选取 SH 文件，脚本存放在 KernelSU 管理的受保护目录；不实现侧键 Hook、应用列表扫描、流体云常驻通知或网络下载执行。

## 6. 对 DORM OS 的落实

1. 采用独立 Kotlin/Compose 管理 App，不使用 KernelSU WebUI。
2. 仅 KernelSU，单人本地使用；不提供 Magisk/LSPosed 兼容层。
3. SH 作为唯一功能核心：导入、新建、编辑、搜索、语法检查、运行、删除、日志。
4. NFC 标签只保存 UUID；标签不能提供脚本路径或任意 Shell 命令。
5. 校园网不单列功能，不硬编码 Ncwu 登录逻辑；用户的校园网脚本只是普通的 `校园网.sh`。
6. 模块不安装 service/boot 脚本，不轮询；Android 原生 NFC 分发后才按需请求 Root 执行。

## 7. 未完成项

- 完整还原所有混淆后的 Kotlin 函数并非本轮目标；如需逐方法等价重建，需要继续对 DEX 指令和资源进行更细粒度分析。
- 目录管理（创建文件夹、移动、重命名）尚未完整迁移到 DORM UI。
- 需要在一加 Ace 6 / Android 16 实测 NFC 启动权限、Root 授权、NDEF 写后回读、执行超时和待机功耗。