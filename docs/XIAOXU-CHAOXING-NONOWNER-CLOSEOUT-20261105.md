# 校序 · 学习通非本人验收收尾清单（2026-11-05）

> **只代表可在无学校本人账号情况下验证的工程门禁，不代表学校真实数据接入成功。** 本文是 #30 学习通主线的收尾证据索引；不用它替代 Android 16 真机/官方本人账号比对。依 `AGENTS.md`，第二课堂仍暂停。

## 一、在原项目内已合并的实现（不重建）

| 能力 | 唯一现有实现 | 可独立核查的证据 |
| --- | --- | --- |
| 只读本人登录与课程、作业解析 | `lib/academic-chaoxing-provider.mjs`、`lib/academic-chaoxing-work-discovery.mjs`；依据 MIT `DanMo661/xuexitong-cli` | `scripts/xiaoxu-chaoxing-live-adapter-tests.mjs`、`scripts/xiaoxu-chaoxing-direct-homework-tests.mjs` |
| 作业状态可信同步、禁止覆盖本机完成标记 | `lib/academic-chaoxing-homework-sync.mjs` + 已有 D1 `academic_assignment_sources` | `scripts/xiaoxu-chaoxing-homework-sync-tests.mjs` |
| 官网作业总数证据、部分列表拒绝写入 | #260；只认官网明确的已完成/总数，不猜不存在的总数 | `scripts/xiaoxu-chaoxing-direct-homework-tests.mjs` |
| 原始截止时间只读、未标注年份不能生成绝对截止 | #254；既有 `readHomeworkDetail` | `scripts/xiaoxu-chaoxing-work-detail-tests.mjs` |
| Android 官方页面本机读取、分多页保存 | `NativeSchoolConnectActivity.kt` + 既有 Room DAO | `scripts/xiaoxu-chaoxing-native-reader-tests.mjs` |
| App 重进时主动续读课程并逐门核验、完整查看最多 200 条已读取作业 | #264；复用已有 `loadChaoxingCourses`、`syncChaoxingAllCourses` 和现有 Compose 列表 | `scripts/xiaoxu-android-bootstrap-tests.mjs` |
| 加密本人云端会话、学校身份约束、会话失效保护 | 原有校序 School API、`CloudClient.kt`，不传学校密码/Cookie 给 App | `scripts/xiaoxu-chaoxing-login-readiness-tests.mjs`、`scripts/xiaoxu-chaoxing-qr-tests.mjs` |
| 自愿开启的按小时云端检查 | 现有 `lib/academic-chaoxing-auto-sync.mjs`、`workers/academic-cron` | `scripts/xiaoxu-chaoxing-hourly-cron-tests.mjs`、`scripts/xiaoxu-chaoxing-hourly-cron-ops-tests.mjs` |

## 二、不需要本人账号也能执行的最终门禁

1. **PR 与主分支门禁：** `npm run check`、端到端页面回归应通过；测试的课程与作业都是**合成夹具**，不得用于平台数据成功声明。
2. **真正 Android 构建：** 在明确请求的现有 `Build Xiaoxu Android Alpha` Workflow 内执行 `gradle :app:assembleDebug :app:testDebugUnitTest --no-daemon`。该步骤验证 Kotlin/Compose 编译和已有 JVM 单测；通用前端 CI 并不代替此步骤。
3. **产物核验：** 测试 `app-debug.apk` 非空、`unzip -t` 正常、含 `AndroidManifest.xml` 和 `classes*.dex`，生成并随 APK 上传 `app-debug.apk.sha256`。
4. **来源和完整性：** 检查所有权、官方状态校验、数量不完整时拒绝写入、重复 ID、原有旧记录不被清空；不把本机快照显示为学校实时数据。
5. **生产静态发布：** 仅确认精确 main SHA 对应 CI、Cloudflare 工作流与静态部署成功；这无法证明本人学校会话在服务器上可读取。
6. **Debug 安装说明：** Debug 包可能与已装版签名不同，覆盖之前先备份本机课程与任务。工作流 Artifact 有保留期，不等同永久的正式 Release。

## 三、必须留给本人真实账号验收的工作（**禁止自动标绿**）

| 门禁 | 成功标准 | 无本人证据时状态 |
| --- | --- | --- |
| 学习通首次授权 | 本人官方页面完成登录，校序验证是同一账号的有效会话，返回 App | **待本人验收** |
| 全部课程 | 与官方学期课程名称和数量逐门一致，包括同名不同教学班 | **待本人验收** |
| 真实作业与状态 | 各课程标题、作业数、未交/已交/已批阅/退回逐项一致，分页不漏 | **待本人验收** |
| App 重进续读 | 完全关闭 App、再次进入后手动一键续读，网络/过期/重授权分支正确 | **待本人验收** |
| 更新与断网 | 官网新状态能按需刷新；断网旧记录标注为历史，不能冒充最新 | **待本人验收** |
| 每小时 Worker（若用户启用） | 实际心跳、本人学校会话与 D1 作业更新都验证成功 | **待本人验收** |

## 四、出厂结论规则

- **可说：** 开源能力已整合；合成契约 / CI / Android 构建与单测通过；APK 可用于本人验收。
- **不可说：** 已自动同步全部课程作业、全部提交状态已正确、工作流构建等于用户设备安装成功、测试假数据等于真实课程。
- 学习通的**非本人阶段**只有在本 PR 的 CI、合并后 main CI 以及明确 APK Workflow 均成功时，才标为完成；任一失败则回退对应提交修复，保持本人真实验收待办。
- 本文不要求上传 HAR、Cookie、密码或学号到仓库，也不新增登录器、数据库、Webhook、Worker 或学校数据字段。
