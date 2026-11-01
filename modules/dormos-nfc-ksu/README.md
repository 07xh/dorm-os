# 触序 · TapFlow KernelSU 模块 V1.22.1

本模块只服务于个人 Android 设备上的本地 SH 脚本管理与 NFC 场景触发。配套 App 独立安装；模块不内置 APK，也不使用 KernelSU WebUI。

## 兼容与定位

- 目标：KernelSU / KernelSU Next 提供的标准 `su` 授权环境。
- 不承诺 Magisk、APatch 或 LSPosed 兼容。
- 模块 ID：`dormos_nfc_actions`；App 固定调用路径：`/data/adb/modules/dormos_nfc_actions/run.sh`。
- NFC 触发默认关闭；必须在 App 内启用、绑定标签并确认写卡回读。
- 标签只保存 UUID/MIME，不保存脚本和账号密码。UUID 标签可被复制，不是安全凭证。

## 低耗电设计

- 不安装 `service.sh`、`post-fs-data.sh`、开机广播、守护进程或轮询任务。
- 不申请常驻唤醒锁；只有 App 操作或 NFC 触发时执行一次 Shell 入口。
- `action.sh` 仅用于 KernelSU 手动运行一次健康检查。
- 不内置校园网逻辑、自动登录服务、远程下载器或 APK 安装器。个人脚本由用户自己导入和审核。

## 数据与备份

脚本、绑定、场景、文件夹、启用状态和执行日志保存在：

```
/data/adb/dormos_nfc_bridge/
```

数据位于模块目录之外，升级模块不会覆盖个人资料。**卸载模块不会自动删除这份数据**，以避免误删个人脚本；重新安装同 ID 模块后可继续使用。卸载前请在 App 的“配置与备份”中导出备份，并妥善保管。不要手工删除上述目录，除非确认不再需要其中数据。

## 执行与安全边界

- 脚本以 Root 权限执行，单个脚本最大 16 KiB，单次执行最多 20 秒。
- App 仅向固定 dispatcher 传递白名单参数；UUID、Base64 参数在进入 Shell 前校验，拒绝拼接任意命令。
- 导入和保存只做 Shell 语法检查，不代表脚本安全。语法正确的脚本仍可能修改系统、删除数据或访问网络。运行或绑定 NFC 前必须自行审阅脚本。
- NFC 关闭状态、未绑定 UUID、禁用场景、错误 UUID、脚本缺失或并发操作均应拒绝执行。
- NFC 绑定解析、脚本执行、脚本编辑、标签绑定、全局 NFC 开关和文件夹修改使用同一执行锁；优先使用可用的 `flock`，否则使用 fail-closed 的 `mkdir` 锁。陈旧 fallback 锁不会被自动抢占。

## 安装与升级

1. 在 KernelSU 管理器中安装 `TapFlow-KernelSU-V1.22.1.zip` 并按提示重启（若管理器要求）。
2. 单独安装配套 TapFlow APK。
3. 在 KernelSU 管理器中仅向 TapFlow 授予 Root 权限。
4. 打开 App 检查模块状态；确认脚本、NFC 绑定和备份数据后再启用 NFC。

升级时保持模块 ID 不变，直接通过 KernelSU 安装新 ZIP。不要先卸载旧模块；模块更新保留外部数据目录。

## 自动化验证

CI 检查 Shell 语法、模块 ID/版本元数据、默认关闭、启用/禁用、NFC 绑定和场景、脚本保存/读取/重命名/删除、16 KiB 限制、语法错误拒绝、并发执行、共享锁、文件夹层级和锁回退行为。

CI 不等于真机验收。Ace 6 / Android 16 仍需确认 KernelSU 授权弹窗、实际 `flock` 能力、NFC 前后台/锁屏触发、ColorOS 后台限制、耗电和脚本超时后的子进程退出。
