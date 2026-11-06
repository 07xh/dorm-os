# 校序近期代码整理基线（2026-11-06）

> 初始审计基线：`main@60f34ecd`；本次状态复核：`main@e783502958ec`。本记录区分 **PR 合并、CI、APK 编译、真机数据验收**；唯一现行开发约束为根目录 [AGENTS.md](../AGENTS.md)。

## 一、近期已合并代码与唯一归属

| 能力 | 近期 PR | 应复用的唯一入口/数据归属 | 不能误判为 |
| --- | --- | --- | --- |
| 原生首页、课程切换 | #293、#300 | `ui/RealDataWorkspace.kt`、`MainActivity.kt`，本机课表导入记录 | 官方当前教学周、学校已核验课表 |
| 真实任务中心、学习通批量核验 | #289、#294、#299 | `ui/RealDataWorkspace.kt`、`data/ChaoxingBatchAudit.kt`、`data/CloudClient.kt` | 所有作业官方提交状态已验收 |
| 本机截止提醒 | #298 | `data/LocalDeadlineReminders.kt`、现有 Room 任务 | 学校官方作业自动提醒已验证 |
| 本地课表 | #288 | `data/LocalScheduleStore.kt`、`ui/ScheduleScreen.kt` 和 `ui/WeeklyTimetableGrid.kt` | 课表云端同步完成 |
| 消息中心、教务导航 | #287、#291 | `ui/MessageCenter.kt`、`AcademicNavigation.kt` | 官方公告实时成功同步 |
| 第二课堂目录与积分 | #295、#297、#304、#305 | `SecondClassPage.kt`、`NativeSchoolConnectActivity.kt`、`assets/ncwu_secondclass_local.js`，保留已有云端快照 UI | 官方活动广场全部非空记录已验证、自动同步完成 |
| 校园跑本机数据契约 | #303 | `data/CampusRunContract.kt`、`data/LocalCampusRunStore.kt`、`ui/CampusRunPanel.kt` | 学校运动记录自动抓取成功 |

注意：`ui/SecondClassPanel.kt` 是仍被 `MainActivity.kt` 的云端面板使用的**旧数据来源展示入口**，并非可直接删除的 Demo。先保持现有双来源隔离，不做新登录器。

## 二、近期实际合并与旧 PR 处置（更新于 2026-11-06）

| 交付 | 真实状态 | 源码范围 / 验收边界 |
| --- | --- | --- |
| [#306](https://github.com/07xh/dorm-os/pull/306) 清理 import / 建立整理基线 | **已合并** | 移除经核对的五条未使用 Compose import；不删除仍有调用的 SecondClassPanel |
| [#307](https://github.com/07xh/dorm-os/pull/307) 校园跑账号隔离 | **已合并** | 本机手动数据按校序账号会话隔离，不是校园跑平台自动接入 |
| [#309](https://github.com/07xh/dorm-os/pull/309) 清理退役课表视图 | **已合并** | 移除无调用的旧云端课表 Compose 私有组件，保留本地课表导入与兼容测试模型 |
| [#301](https://github.com/07xh/dorm-os/pull/301) 消息和任务空状态 | **已合并** | 官方/微信来源独立滚动；无官方读取证据时不能显示已验证空状态 |
| [#308](https://github.com/07xh/dorm-os/pull/308) 启动与刷新性能 | **已合并** | 课表 IO 后台读取/提交与序号防旧数据覆盖，公告及个人记录并发刷新 |
| [#310](https://github.com/07xh/dorm-os/pull/310) 第二课堂三标签交互 | **已合并** | 三标签各自滚动位置、重复刷新保护；原生 Android JVM 与全仓 CI 通过 |
| [#311](https://github.com/07xh/dorm-os/pull/311) 旧内嵌网页帮助函数 | **已合并** | 清理退役的网页内联帮助逻辑；不是 Android UI 或连接器新功能 |
| [#279](https://github.com/07xh/dorm-os/pull/279)、[#284](https://github.com/07xh/dorm-os/pull/284) 历史分支 | **已关闭，未合并** | #279 已由 #308 重新移植；#284 的可用二课交互已由 #310 移植，**未完成**的我的外观偏好在 [#312](https://github.com/07xh/dorm-os/issues/312) 跟踪 |

[#247](https://github.com/07xh/dorm-os/pull/247) 仍是二课本机诊断探针，不代表三平台完成自动接入。#205、#207、#208、#175 等旧提案/工具 PR 须按现行有效性单独审查；TapFlow/NFC 分支不属于本次校序整理。

## 三、持续有效的代码边界

- `ui/ScheduleScreen.kt`：#306 删除 4 条未使用 import；#309 删除失效的私有云端视图，不影响本机导入与实际课表组件。
- `ui/SecondClassPanel.kt`：#306 删除 1 条未使用 import；该文件仍存在真实调用点，不因名称看似历史而直接删除。
- `MainActivity.kt` / `data/CloudClient.kt`：复用 #308 已合并的异步读写及并行刷新；不得回退到旧 #279 的同步 UI 阻塞实现。
- `ui/MessageCenter.kt` / `ui/RealDataWorkspace.kt`：#301 为已合并行为，必须保持未读取/读取失败/已验证空列表的真实证据门禁。
- `SecondClassPage.kt`：保留主线的活动广场、我的活动、素质积分三标签与来源区分；#310 仅改交互，**不证明**非空活动已成功读取或积分已与本人学校官网核对。
- 统一 Android JVM 回归复用现有 `.github/workflows/xiaoxu-campus-run-jvm.yml`，匹配原生 Kotlin 源码、Gradle 配置及 Manifest；执行 `:app:testDebugUnitTest` 编译和单测，**不构建 APK**。
- 暂不进行跨模块大规模拆分，不创建第二套认证器、持久化账户数据库、假课表、模拟作业或替代已存在的连接器。

## 四、实际验收与后续门禁

1. #310 的 GitHub Actions 原生 JVM 回归和 DORM OS CI 已通过；#308 也已通过 GitHub Kotlin JVM/通用 CI。但本轮**没有**重新产出 Android Debug APK、真机滚动截图或五次冷启动时间测量。
2. 新改动必须先对比最新 `main` 与并行 PR；对原生 Kotlin 先检查 JVM 编译/单测，再进行相关真机交互。测试绿色**不等于**三个平台本人真实数据完成接入。
3. 学习通本人所有课程与作业、青果官方完整课表、第二课堂积分与活动依旧以官方真实数据逐项比对；校园跑目前仍限本人本机只读记录契约。课表**仅本机导入**，不可把本机数据写成青果云端同步。
4. [#312](https://github.com/07xh/dorm-os/issues/312) 的系统/浅色/深色偏好**未交付**；不应把已关闭 #284 的旧设计系统代码误当已完成。
5. Android APK 仅依照 `docs/XIAOXU-REPO-HYGIENE-2026-11-05.md` 的明确构建请求规则申请；普通代码清理、文档同步、JVM 回归不产生 APK 构建请求。
