# 触序 · TapFlow V1.18.0｜首页与 KSU 清理

- Android 16 状态栏：给首页留出安全区域，浅/深色主题分别设置图标对比度。
- 首页 NFC Hero：标签改为独立的 Canvas 插画，和文字处于不同的 Row 子区域；删除长文案、旧绝对定位和字符占位图标。
- 已全局核对无调用者后，删除旧 `NfcVisualComponents.kt`，不删除仍在使用的读卡/写卡/回读 Session、标签 MIME 或旧标签兼容分支。
- KernelSU 1.11：`--status` 不再启动附加 script-manager/awk；启用状态用唯一临时文件和原子替换，拒绝软链接；删除无应用调用的 `--write-tag` Shell 入口；修复场景写入临时名 `$`/PID 错误；重命名扩展名与保存规则一致；统一 App 和 NFC 的 Root 模块路径。
- 增加 Shell 回归测试：状态脚本计数、启用状态软链接拒绝、混合大小写 .sh 重命名。
- NFC、KernelSU、耗电、真实手机性能仍需 Ace 6 Android 16 线下验证；PR #12 保持 Draft，不自动合并或正式发布。
