# DORM OS NFC Actions · KernelSU 按需模块（V0.8.1）

此可选模块只提供一份已审核的 **wifi-status** 只读诊断 SH，不包含校园网密码登录。NFC 系统事件由 DORM OS 安卓 APK 接收，APK 在用户明确启用 Root 选项、KernelSU 允许权限后调用本模块固定入口。

- 无 `service.sh` / `post-fs-data.sh` / `system.prop` / SEPolicy 修改 / 常驻进程
- 不改变系统分区，包含 `skip_mount` 文件
- 不联网、不记录密码、不显示 IP、MAC、SSID、BSSID
- 不允许 NFC 标签提供脚本路径/命令行/参数；仅接受 `wifi-status` 这一固定动作
- 若 Android App 没有 Root 授权或模块未安装，直接安全失败；原生 NFC 和其他网页动作不受影响

模块脚本：
- `run.sh wifi-status` — 只读检查 Wi-Fi 网卡存在与否
- `action.sh` — 在 KernelSU 管理器手动测试一次

注意：这是一项 Root 链路验证，**不代表实现 Ncwu 认证**。真正登录仍需先取得并核对校园网真实成功请求、参数、Cookie 会话和传输安全。请不要把随意下载的脚本放到此模块替换。


## V0.8.2 · KernelSU WebUI（首个可视化模块页）

模块新增 KernelSU Manager 内的 WebUI：展示模块状态、说明 NFC 接收边界，并提供用户主动点击的一次性只读 Wi-Fi 网卡诊断按钮。界面通过 KernelSU 官方 JavaScript exec API 调用固定命令，不接受输入命令、标签参数或任意脚本路径。

- WebUI 资源位于 webroot/index.html；构建时将 kernelsu npm API 与界面脚本打包为 webroot/app.js。
- 不增加 service.sh、post-fs-data.sh、后台监听或轮询。
- WebUI 的手动按钮只运行现有 wifi-status 只读脚本。
- 实体 NFC 标签仍由 DORM OS NFC Helper 的 Android Activity 接收；本次没有声称纯 KSU 模块可以接收 NFC Intent。
- 当前版本尚未将 APK 的标签管理界面迁移到 WebUI，也不支持任意自定义 Root 脚本；后续必须在白名单、参数校验和用户确认下逐步扩展。
