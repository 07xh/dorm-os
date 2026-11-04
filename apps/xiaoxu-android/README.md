## V0.4｜学习通本人课程/作业主动读取与错误分类
- 本版本沿用现有 `/api/v1/chaoxing-homework?view=courses` 和 `/api/v1/chaoxing-homework/sync`，Android「任务」页可主动读取本人课程、选择课程、同步本门官方作业状态。未认证/未连接时不自动采集、不伪造。
- 服务端复用 PR #190 的登录跳转、临时网络故障、人机验证误报、批量错误不覆盖修复；增加伪造场景回归测试。
- 仍必须先在原有校序连接中心完成学习通**本人官方授权**；Android 校序身份会话和学习通上游会话不是同一种凭据。
- 在已核验并按课程同步的作业条目中，可按需只读查看题目文本和选项；复用现有 `GET /api/v1/chaoxing-homework?view=detail` 和原有只读解析器，不新增登录、抓包或作答接口。仅当前页面能解析的前20题在 App 内显示，遇到图片、更多题目或不可解析页面提示前往官方学习通。详情仅保存于当前内存状态；课程切换、重新同步、退出或会话失效时清空。
- 这是手动按课程同步，不代表云端后台定时更新或学校本人端到端读取已签收。真正成功应在 Android 16 真机用本人课表/作业与官方逐一对照。
- PR 构建与 GitHub Actions artifact 非正式 APK 签名；不应直接覆盖有未备份本地任务的旧 Debug 包。

---

# 校序 Android V0.3 · 原生云端任务 / 通知接入（待 CI 与本人验收）

## 本次新增（保留 V0.2 原生基础）

- 原生展示 **华水官方公开教务公告与考试通知**：GET `/api/notices`，仅允许打开固定 `www2.ncwu.edu.cn/jiaowu/info/` 文章链接；保留数据抓取时间，局部失败不冒充完整。
- 原生校序**云端任务只读列表 + 手动任务新增**：复用 GET/POST `/api/v1/assignments`，与 Room 本机任务严格分组，不自动混合、覆盖或造假。
- 原生显示**本人学校连接状态**（只读 GET `/api/v1/connections`）：只有服务端可信会话证明且状态匹配才提示“已验证会话”，依然不代表真实学校个人数据已验收。
- 原生显示**微信待审核候选数量（最多查询30条）**，实际审核仍须在现有网站人工完成。
- 自有校序账号采用服务端现有 `POST /api/v1/auth/login` + `GET /api/v1/auth/me`、`POST /api/v1/auth/logout`，**不是学校密码**；HttpOnly Cookie 由原生 HTTPS 响应读取、Android Keystore AES-GCM 本地加密，过期失效，退出清理；只对固定校序 HTTPS 主机发送会话，禁用跳转。
- 失败或断网时明确区分无法刷新与不曾连接，网页继续保留已部署的上游连接器；所有学校个人数据仍按真实来源核验门禁。

## 限制与验收

- **今天的原生开发不包含**：首次学校登录的本机自动迁移、从学习通直接抓取作业、青果真实课表网格、第二课堂真实分数、后台通知推送。它们必须先有本人端到端验收。
- 本机 Room 手动任务和 D1 云端任务是两组数据，不会自动合并。必须明确选择保存位置。
- Cloudflare Auth 模式需配置为已启用的 `owner_password`，且必须完成线上会话端到端验收；如配置为 Cloudflare Access，目前原生校序口令登录不可用，需走网站。
- 公开通知依赖学校官网和 Cloudflare Pages Functions，失败时不能当作实时更新。
- 微信数量仅为前30条候选，并非完整总数；实际审核继续在 Web 端人工进行。
- 先通过 CI 编译，再安装 Android 16 真机进行会话创建/过期、断网、刷新、任务新增和通知打开测试。Debug 包不是正式版，签名不可保证覆盖升级。

---

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
