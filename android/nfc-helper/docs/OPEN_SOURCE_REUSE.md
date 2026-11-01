# DORM OS NFC Manager — 参考 APK 逆向与复用记录

本项目已实际检查用户提供的《白秋然雨 2.1.apk》，静态逆向记录见 [BAIQIU_APK_REVERSE_NOTES.md](BAIQIU_APK_REVERSE_NOTES.md)。

## 采用的功能设计

- Jetpack Compose / Material3 的独立 Android 管理 App。
- SH 文件列表、搜索、创建、编辑、保存、删除、Root 执行和执行日志。
- `.sh` 扩展名校验、文件名安全校验和保存前 Shell 语法检查。
- NFC 标签只保存 UUID，模块在本地把 UUID 解析为脚本 ID。
- 用户表示作者许可复用；当前不嵌入 APK、DEX 或原始二进制资源，而是按功能行为重写。

## 明确不做

- 侧键、音量键、电源键 Hook 和全局/应用手势。
- 流体云常驻通知、应用列表扫描、额外手电筒/振动控制面板。
- Magisk、LSPosed 兼容。
- KernelSU WebUI、开机常驻服务、后台轮询。
- 独立校园网功能。校园网脚本只是脚本库里的普通 `校园网.sh`。

## 权限边界

原 APK 申请了较宽的文件、应用列表和后台权限。DORM OS 使用 SAF 导入文件，不申请 MANAGE_EXTERNAL_STORAGE 或 QUERY_ALL_PACKAGES。Root 执行必须由用户主动确认；NFC 绑定前应检查脚本内容。