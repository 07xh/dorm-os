# 校序 Android V0.2 · Kotlin 原生基座（待 CI 与真机验收）

## 本次变更：从 TWA 入口转为真正的原生 Activity

- 首页、课表、任务、消息、我的使用 Jetpack Compose + Material 3，手机底部导航、平板 NavigationRail。
- **真正可写的本地任务**：Room 持久化新增、勾选、删除；只统计本机手动任务，断网可用。
- 课表、学习通、青果、第二课堂均不填充模拟内容。不展示尚未验收的账号为“已连接”。
- 现有 Cloudflare 网站继续提供真实公开通知、本人授权入口、已上线的私有 API、云端任务和微信人工审核。通过系统浏览器在固定可信站点打开，不复制学校 Cookie 或账号密码。
- 原 TWA 依赖及非导出 LauncherActivity 暂留作非默认兼容入口；**App 主界面不再是网页套壳**。
- 不迁移或覆盖旧网站的 D1、PWA 存储与连接器；不删除 TapFlow 的任何代码。

## 上游开源复用

- AndroidX Jetpack Compose / Material 3 / Activity Compose（Apache-2.0），用于原生 UI 和响应式导航。
- AndroidX Room 2.8.5（Apache-2.0），用于设备本地数据。
- Kotlin 2.2.20 + Compose Compiler 插件、KSP2 2.2.20-2.0.4。
- 原有 Google android-browser-helper 2.7.4（Apache-2.0），暂时保留为兼容能力。
- 北化课表 APK 仅作交互参考，不直接复制未核查授权的反编译代码；时光课表 / shiguang_warehouse 的具体可复用适配器需继续核对许可、维护和学校兼容性。

## 建立环境与构建

需要 JDK 17、Android SDK API 36、Gradle 8.13。独立构建，避免改变 Cloudflare 网站发布：

```sh
gradle -p apps/xiaoxu-android :app:assembleDebug --no-daemon
```

GitHub Actions：`Build Xiaoxu Android Alpha`，推送 Android 路径变动或手动触发。
该构建只能验证编译，不证明 Android 16 真机启动、学校个人数据连接或云端生产可用。

## 安全与数据边界

- 本机 Room 只保存手动创建的普通任务，不保存学校密码、Cookie、微信 Token。
- 设置 `allowBackup=false`；目前本机任务没有导出/云端同步，卸载 App 可能丢失数据。
- 原有官网登入会话在浏览器内管理，不会自动回传原生 App；**网页登录成功 ≠ 原生 API 已授权**。
- 后续接入统一的真实业务接口时，先做好独立安全授权与真实来源验证，不在 App 里新建未经验证的抓取器。
- 未接入学业数据时 App 明确显示未验证，不把演示课程当真实课表。

## 下一阶段门禁

1. CI `assembleDebug` 实际成功，Android 16 设备安装、旋转及平板检查。
2. 本机任务增删完成与断网、重启恢复；不得影响网站上的原有任务。
3. 优先打通学习通 **官方登录 → 本人真实课程/作业 → 退出重进再次刷新**。
4. 然后青果真实课表、第二课堂活动/积分；最后再接通知、桌面组件与本地/云端选择性同步。
