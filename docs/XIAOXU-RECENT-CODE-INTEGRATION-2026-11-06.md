# 校序近期代码整理基线（2026-11-06）

> 代码基线：`main@60f34ecd71eb3558b4cb35a679e30c5ceb045d06`。本记录仅整理**已经合入 main 的事实**与当前未合并变更；PR 合并、CI、APK 编译、真机数据验收分别统计。唯一现行执行规则为根目录 [AGENTS.md](../AGENTS.md)。

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

## 二、并行 PR：合并前必须重新对比 main

- [#301](https://github.com/07xh/dorm-os/pull/301)：消息来源独立滚动与任务空状态。覆盖 `ui/MessageCenter.kt`、`ui/RealDataWorkspace.kt`；与 #284 的 MessageCenter 改动重叠，也依赖已更新的主分支。应先 rebase、检查差异、再跑对应 Android 单测。
- [#284](https://github.com/07xh/dorm-os/pull/284)：我的外观设置和第二课堂快照。覆盖 `MainActivity.kt`、`CloudPanels.kt`、`MessageCenter.kt` 等；主线后续已大幅更新。不能按历史补丁直接合并覆盖新二课、消息或个人 UI。
- [#279](https://github.com/07xh/dorm-os/pull/279)：本机课表 IO 与云端并发刷新。覆盖 `MainActivity.kt`、`data/CloudClient.kt`；须以当前课表仅本地导入、学习通批量核验为前提重新审查。
- [#247](https://github.com/07xh/dorm-os/pull/247)：二课本机只读协议探针。它是诊断工具，不是完成的 Android 自动接入。
- #205、#207、#208、#175 等旧提案/工具 PR 要单独判断现行相关性，**不因开着就按编号全部合并**；TapFlow/NFC 分支不归入校序本轮整理。

## 三、此次已实施的最小整理

- `ui/ScheduleScreen.kt`：移除不再使用的 `Crossfade`、`LazyColumn`、`rememberCoroutineScope`、`launch` imports。
- `ui/SecondClassPanel.kt`：移除未使用的 `OutlinedButton` import。
- 不触碰学校认证、连接器、数据库迁移、既有用户数据、工作流构建请求与本机任务。暂不拆分 `MainActivity.kt` / `CloudClient.kt` / `RealDataWorkspace.kt`，避免与未合并工作重叠。

## 四、后续清理与验收门禁

1. PR 对比最新 `main`，先核对新旧 UI 的真实调用点与云端/本机数据边界；优先解决 #301/#284/#279 重叠与回归，再考虑结构拆分。
2. `npm run check`、必要的 Android `:app:testDebugUnitTest` / `:app:assembleDebug` 必须有实际执行结果；本轮仅 import 清理，**未在本地执行 Android 构建或真机验证**。
3. 学习通课程作业、青果本人课表和第二课堂积分活动仍以官方本人数据逐项比对；校园跑仍限本机记录契约。静态测试、HAR 空响应、源代码合并不能取代真实数据验收。
4. Android APK 仍按 `docs/XIAOXU-REPO-HYGIENE-2026-11-05.md` 的显式请求规则构建，不要靠普通整理提交反复触发 APK。
