# TapFlow 通用聊天草稿 RC · 交付与验收

## 设计范围

- **不内置微信、任何群名或消息**。首次安装原生场景列表为空；已安装过旧 RC 的设备仅清除未被编辑的旧演示场景。
- 在「场景 > 原生动作场景 > 新建 > 添加动作 > 指定聊天草稿」中，选择已安装且支持文本分享的应用，或手动填写其包名，再填写群聊名称与草稿文本。
- 标签写入本机随机场景 URI，草稿保存在 App 内，不写进 NFC 标签。
- 系统分享接口若能打开聊天应用，**仍需自己选择正确的聊天/群**并确认文字。分享入口不可用时退回到复制文字和启动应用；不能保证自动进入目标会话或填写它的输入框。
- 永不自动发送；不通过 SQLite 私有数据库猜测群 ID，不依赖未公开的 ChattingUI 路由。
- 原旧 KernelSU 绑定场景与 SH 系统保留。

## 安装与签名

调试包名为 `xyz.dormos.nfc.rc`，可与原包名 `xyz.dormos.nfc` 并存。
两者 App 私有数据不会自动同步。系统可能为相同 NFC 标签显示应用选择器。
首次运行应独立确认必要的 KernelSU 授权；真机上不要预期模块对新包名已有授权。

## 构建验收

GitHub Actions 在代码提交后执行 Android JVM 单元测试、
`:app:assembleDebug`、`:app:assembleRelease`、APK 签名检查和
`aapt dump badging` 包名检查；通过后才允许发布测试安装包。
此处仍需要一加 Ace 6 / Android 16 真机测试。

## 下载与完整性

由 `build-nfc-helper.yml` 的 `publish-chat-preview` 工作发布
`TapFlow-Chat-Config-RC-debug.apk` 和 `SHA256SUMS.txt` 至 GitHub Release。
不能把旧 CI 构建、网页预览或源码 ZIP 当作本次新 APK。
