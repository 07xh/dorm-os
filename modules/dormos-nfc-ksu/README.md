# DORM OS NFC Bridge · KernelSU 模块 V0.9.0

本模块将场景配置、标签管理、SH 白名单分发、执行记录和可视化界面集中到 KernelSU WebUI。Android APK 缩减为一次性 NFC 接收器与写卡入口。

## 架构

- KernelSU WebUI：管理场景、启停 NFC 触发、导入/导出旧版标签备份、写卡、手动测试和查看日志。
- NFC Receiver APK：接收 Android NDEF Intent；WebUI 通过固定 Activity Intent 启动写卡模式。它没有管理页面、网络请求、后台服务或轮询。
- KernelSU dispatcher：只接受 UUID 场景 ID；读取本机白名单配置后分发固定动作。标签不能携带命令、脚本路径或 Shell 参数。
- 数据目录：/data/adb/dormos_nfc_bridge/，位于模块目录之外，模块升级不会覆盖配置。配置与日志使用 root-only 权限。
- 空闲状态：不安装 service.sh、post-fs-data.sh、boot-completed.sh，不创建守护进程、定时器或 NFC 轮询。

## 支持动作

| 动作 | 当前行为 |
| --- | --- |
| ncwu-check | 只读识别 Ncwu SSID 和 wlan0 IPv4 是否存在；不提交账号密码 |
| wifi-status | 只读检查 wlan0 网卡状态 |
| open-url | 只允许经过校验的 HTTPS 地址，交给 Android 浏览器打开 |
| dorm-scene | 仅允许 study/rest/dev/away 四种 DORM OS 场景 |
| Ncwu 自动登录 | 锁定：真实成功请求、认证状态和传输安全尚未完成验证 |

## 安装和使用

1. 在 KernelSU 管理器安装 dormos-nfc-ksu.zip。
2. 安装配套 DORM NFC Receiver APK。APK 只负责 NFC 接收与写卡，不承担场景管理。
3. 打开 KernelSU → 模块 → DORM OS NFC Bridge → WebUI。
4. 创建场景，选择固定动作；NFC 触发开关默认关闭。
5. 点击“写入 NFC 标签”，接收器 Activity 会进入一次性写卡模式。标签只写入 v2:tag:<UUID>，不写账号、密码或脚本。
6. 确认模块中启用 NFC 触发后，碰标签即可调用 dispatcher。首次 Root 调用需要 KernelSU 授权。
7. 手动运行和 NFC 运行都会产生脱敏日志；日志只记录时间、场景 ID、固定动作和结果。

## 旧版迁移

旧版 APK 的 JSON 备份可在 WebUI 的“导入旧版备份”中恢复。导入时完整校验版本、数量、UUID、动作与目标；任何一条不支持或不安全都会拒绝整份备份，不会静默丢弃条目。旧版实体标签仍使用同一 MIME 与 UUID payload，可在导入配置后继续使用。旧版 v1:ncwu 标签会映射到只读 ncwu-check，但需要在 WebUI 启用 NFC 触发。

## 安全边界

- 默认 NFC 触发关闭；KSU 对接收器应用的 Root 授权仍由 KernelSU 管理。
- 仅执行模块内固定动作；不支持任意 Shell、用户输入命令、标签脚本或外部脚本路径。
- HTTPS URL 禁止凭据、Token、Cookie、内网地址和控制字符。
- 日志不记录 URL、SSID、IP、MAC、账号、密码或网络响应正文。
- ncwu-check 是本地只读识别，不代表校园网认证成功。
- 不根据其他学校 Dr.COM/ePortal 的参数猜测 Ncwu 登录请求。自动登录保持锁定，直到有本人正常登录的脱敏成功请求、服务端成功状态及传输安全证据。

## 构建与测试

WebUI 源码位于 webui-src/，通过 KernelSU 官方 kernelsu JavaScript API 执行固定命令。模块构建会执行 WebUI 单元测试、Shell 语法检查、dispatcher 正反例测试和 ZIP 内容检查。

本版本不宣称已经通过一加 Ace 6 Android 16 实机验证。仍需测试 NFC 启动权限、息屏/解锁分发、写卡回读、KernelSU 授权和浏览器 Activity 启动边界。
