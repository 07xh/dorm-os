# TapFlow V1.22.2 — NFC 自动触发开关流畅性修复

「NFC 自动触发」是 KernelSU 的**脚本执行总开关**，不是系统 NFC 硬件开关，也不启动长期扫描服务。只有已绑定且单独启用的标签才能触发个人 SH 脚本。

## 根因
旧版 `Switch` 在异步 Root 调用时因 `enabled = !busy` 被暂时淡化；`checked` 要等 Root 返回才变化；成功后又运行耗时的整套 Root 状态刷新，造成视觉停顿。

## 改动
- `NfcTriggerToggleState` 区分用户视觉目标与已由 KSU 确认的状态。点击立即启动 205ms 拇指位移 + 200ms 轨道色变化；失败回滚，绝不把视觉目标当成已生效。
- 56×34dp 固定尺寸轻量 Compose 开关，无灰色闪烁、默认波纹和过多图层。
- 状态文案区分等待确认、Root 未连接、已开启和已关闭。
- 成功后不再调用整页多项 Root 刷新；后端仍使用已存在的 KSU --enable dispatcher 互斥逻辑。
- 防止 pending 时连续重复切换；补充状态机单元测试。
- versionCode 40；无模块代码、NDEF 写卡/回读协议变更。

## 待验证
Ace 6 Android 16：KSU 已授权、未授权、Root 被拒绝、快速点击、应用重进、NFC 实体触发与实际 UI 帧率。无真机证据前不宣称 60/120 FPS。
