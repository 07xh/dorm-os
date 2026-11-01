# DORM-SRC.zip 对照复用审计（2026-11-01）

样本：用户此前提供的 `DORM-SRC.zip`（17 个条目，Kotlin clean-room 等价重写，**并非原 APK 官方源码**）。

| 参考模块 | 压缩包实现 | DORM NFC 当前取舍 |
|---|---|---|
| core/PathGuard.kt | trim、拒绝路径分隔符 / 空白 / 控制字符，canonical 边界 | 采用名称/UUID 校验思路，Shell 层固定文件路径；用户不直接传绝对路径 |
| core/ScriptRepository.kt | 文件夹遍历、新建、重命名、移动、搜索、删除 | 当前采用基于 UUID 的逻辑文件夹；脚本 UUID 和 NFC 绑定在移动后不变 |
| core/LogStore.kt | Markdown 日志读写与 512KiB 尾部截断 | 保持 root-only 脱敏执行日志，最近 80 条、可确认清空；不导入复杂旧格式 |
| root/RootShell.kt | 常驻 su shell，会话检测与重试 | **未移植**，每次运行走一次 `su -c`，避免管理 App 常驻 root 进程 |
| root/ScriptRunner.kt | `cd parent && sh script`、stdout 回调、超时 | 采用固定 KSU dispatcher 和 root-only 脚本目录，超时和有限输出；后续可加逐行 UI 流 |
| core/BackupManager.kt | 配置备份/恢复 | 尚未迁移；暂不允许从文件导入 Root 动作配置 |
| feedback/* | Toast、流体云通知、振动等 | 仅用系统 Toast/原生界面状态，不使用常驻流体云 |

## 关键差异

压缩包把脚本放在 `/storage/emulated/0/Android/DORM/scripts`，需要宽文件权限；当前项目保持 `/data/adb/dormos_nfc_bridge/scripts` 作为 Root-only 私有存储，不复制这一默认路径。

每个脚本以 UUID 命名，文件夹仅保存元数据 ID / 父目录 ID / Base64 名称；移动/改名不影响 NFC UUID→脚本 UUID 映射。目录删除只允许空目录，避免误删脚本。目录层数和同名冲突均需要验证。

## 验收

Shell 正反例：创建两级文件夹、重复名称拒绝、无效名称拒绝、循环移动拒绝、已绑定脚本移动/重命名后 NFC 仍触发、非空目录删除拒绝、移回根目录、删除空目录。

Compose：脚本页提供目录导航、新建/删除/重命名文件夹、脚本移动/重命名和只读预览；设置页展示日志和清空确认；NFC 标签详情可查看关联脚本与解除绑定。

未完成：源压缩包的文件夹物理移动方案、全量递归搜索路径、配置备份恢复、长输出实时流式渲染。它们不能在没有真机验证时宣称已完成。