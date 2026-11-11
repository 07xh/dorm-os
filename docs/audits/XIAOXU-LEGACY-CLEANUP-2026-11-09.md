# 校序旧代码全面审查与清理｜2026-11-09

## 基线与结论

以 `main@3909384` 为基线，独立分支清理；不覆盖现有任务中心或网站功能分支。核对 GitHub #351 的关闭记录及 main #410 提交：旧整合线已被等价纳入 main，再要求所有子 PR 进入 #351 属于失效指令。

本轮删除的是无运行时消费者的旧旁路与无引用导入；未删除数据库、迁移、会话、备份、学校记录、NFC 或其他项目。未执行生产发布或生产 D1 操作，也没有宣称本人学校数据和 Android 真机已验收。

## 审查范围与判据

对已跟踪的 JS/MJS/Kotlin/Python 运行时文件做引用、入口及精确重复扫描：Android main 源码/资产 75，Functions 36，lib 47，public 16，Cron Worker 1，微信集成 2，校园跑集成 3，共 180 个文件。数量包含集成目录的 Python 测试；这是一轮范围扫描与重点调用链审查，不是每一行均完成形式验证。大于 100 字节的这些源文件没有完全相同的文件副本。

删除前同时检查 main 与 #429、#431、#432、#433 的运行时引用和差异。lib/functions/public/workers 中被删导出只在定义或旧链路内部出现，其他命中为旧测试。#429 与共享设计系统的同文件修改已避让；最终改动与上述四条功能线没有同文件重叠。

## 已实施清理

| 残留 | 删除证据与处理 | 实际保留的当前路径 |
| --- | --- | --- |
| `academic-scheduler.mjs` 的 `dueJob` / `runDueJobs` | 只有旧测试调用；旧框架还能调度 ncwu，与课表手动更新方向不符 | 保留 `nextAttempt`；唯一实际后台调度是 `academic-chaoxing-auto-sync.mjs` → 既有 Cron Worker，受本人开关和租约约束 |
| `runAuthorizedSecondClassSync` | 没有运行时调用，只写活动、不写官方积分；重复维护 D1 入库 SQL | 保留 `collectAuthorizedSecondClass`；实际入口沿 `refreshSchoolAccount` → `runVerifiedNcwuSecondClassSync`，活动/积分/批次/审计同一事务 |
| `candidateAcademic` / `candidateSecondClass` | 只有合成测试，没有登录/接口/页面消费者，不能作为成绩考试或二课接入成果 | 保留实际课表解析所需 `candidateSchedule`、`academic-contract` 的成绩考试模型与现有查询入口；二课使用已存在的真实学校格式解析器 |
| `ownerModeEnabled` / `errorCodes` | 前者没有调用、只包装 `ownerConfig`；后者无消费者 | 不改变 owner/Access 校验、会话、配置或错误映射 |
| 14 个 Android 显式 import | 符号只出现在 import 行；不涉及隐式 Kotlin 委托或运算符 | 删除于 SecondClassPage、CloudPanels、CourseEditorDialog、ProfileSettingsPage、ScheduleScreen、SemesterSettingsDialog；无业务/视觉变化 |
| #351 旧 CI 分支目标 | PR 已关闭；新功能实际以 main 为基线 | 4 个既有工作流保留 main、手动恢复、只读权限与显式 APK 请求门禁，不增加工作流 |
| AGENTS/README 多轮冲突指令 | 旧文档仍写 #351 为唯一整合线，Android README 还将 0.8.10 当作当前源码 | AGENTS 收敛为当前执行规则；Android README 核对到源码 0.8.15 / versionCode 36，旧版本记录明确为历史；源码版本不代表已发布 APK |

## 各模块审查结果与保留理由

| 范围 | 结论 |
| --- | --- |
| Android Shell/导航/主题 | 当前唯一原生 Shell、响应式导航与北化式玻璃组件均有实际引用，保留。没有借清理更换外观、重构导航或改变弹层。 |
| 任务中心/Room/备份 | 本机任务、官方任务、云端个人任务与微信候选具有不同来源和状态，不是相同列表的重复存储。保留 Room 迁移、D1 历史、归档、个人编辑及旧 JSON 恢复。#429 新 DAG/关系图仍是未合并代码，不在本轮反向覆盖。 |
| 学习通 | 服务器 legacy/modern 官方列表有互补调用，不因名称 legacy 删除；本机 ChaoxingLocalReader 与服务器读取分别承担不同运行环境，不能视为两份无用实现。官方作业身份与历史通知、本人完成与官方提交状态仍独立。近期重复/已完成回流需以本人真实任务另行核验，清理不能冒充问题已解决。 |
| 课表/青果 | App 本机导入是当前展示来源；仍在使用的网页解析、按需查询及云端兼容端点保留。没有删空态导入入口、学期设置或提醒。 |
| 第二课堂 | NativeSchoolConnectActivity 与 SecondClassHomeActivity 分别承担官方授权/读取、结果返回，不是重复登录页；保留本机活动/积分/后台续读。旧密码 POST 的拒绝分支是安全边界，仍保留。 |
| 后端/授权 | owner 登录、各学校独立会话、Vault、来源核验、轮换及失效保护保留；不改请求方法、响应格式或授权范围。 |
| Web/PWA/微信/NFC | 现有网站辅助入口、Service Worker、候选人工审核、正文链接、设备撤销和 NFC/备份兼容保留。没有删除 DORM OS/TapFlow，也没有清理公开通知归档数据。 |
| 生产迁移/构建 | 所有已应用 migrations、生产恢复门禁、APK 签名及发布校验保留。未关闭历史 PR，未删除分支/Release/QA 资产。 |

## 测试处理与结果

基线 `npm run check` 通过。删除旧函数前先将保留行为放到当前路径验证，删除后再次运行同一检查：

- `npm run check`：完整通过，含 Node 语法/安全、SQLite、学校只读接口、鉴权、会话轮换、微信、PWA/发布等现有检查。
- `node scripts/xiaoxu-android-bootstrap-tests.mjs`：通过；这是静态安全检查，不是 Kotlin/Compose 编译。
- `node --test scripts/xiaoxu-secondclass-local-reader-tests.mjs scripts/xiaoxu-secondclass-local-activities-tests.mjs`：15 项通过。
- `python3 -m unittest discover -s integrations/wechat -p 'test_*.py'`：20 项通过。
- 当前第二课堂 writer 新增两个持久化检查：第二账号成功读取不修改首账号批次；在活动与 batch 写入之后，用 SQLite trigger 强制积分 UPDATE 失败，验证活动、批次、积分、审计四类记录完整回滚及数据库完整性。保留原有身份、分页、异常空结果与旧快照保护测试。
- 旧 scheduler 的租约/过期/启用行为由实际 `runChaoxingAutoCycle` 的既有 hourly-cron 测试验证；共享退避新增非法值、边界、partial 和封顶检查。
- `git diff --check`：通过。
- 本地 `npm run test:e2e` 未通过执行门槛：缺少 Playwright Chromium v1248；尝试 `npx playwright install chromium`，下载返回损坏/截断 ZIP（`End of central directory record signature not found`）。这不是已通过浏览器验收，交由本 PR 既有远端 CI 实际运行。
- 本地无 Android SDK/Gradle，未编译 APK；Kotlin/JVM 由本 PR 的既有 Android CI 运行。未申请 APK、未部署生产、未进行 Ace 6 或本人学校账号操作。

另安排独立只读审查：核对删除符号、当前调用链、开放分支、测试迁移与授权/兼容约束；定向套件通过，未发现已证实的阻断性问题。远端 CI 结果以 PR 的精确提交为准，不将本地静态检查替代完整 CI。

## 仍需跟进的实际问题

1. 当前开放的 #412–#416、#429 及网站 #431–#433 仍需按源码差异、本人数据和真机验证决定整合。未因“旧 PR”批量关闭，也未自动把草稿合并发布。
2. CloudClient、RealDataWorkspace、public/index.html 与 bootstrap 静态门禁较大；拆分必须按数据/状态职责与行为测试分批进行。本轮不混入导航、认证或数据库重构。
3. 学习通真实任务重复/老师删除/互评后状态、本人跨设备读写、Room 升级备份、一加 Ace 6 帧表现和授权持久化仍须实测。没有把旧代码清理说成这些问题已经修复。
4. 旧文档作为历史证据保留。后续依据 AGENTS、最新 main 和真实验收，避免从历史说明重新恢复旧云端课表/二课密码路径。

## 2026-11-11 续轮：同步主线、退役历史构建请求

- 核实 #434 仍 open/Draft、未合并。其原 HEAD `5380ec66` 的 Android JVM、DORM OS CI 与二课 WebView Runtime 均 success；这不是最新改动的 CI 结果。
- 已将最新 main `ff87406d` 合入同一清理分支，保留新加的手动 D1 审计/0019 迁移脚本与通知数据；未执行这些生产工作流，未变更学习通/课表/微信正在开发的功能线。
- 发现 pending 下仍保留 0.8.13、0.8.14、0.8.15 与 #428 的四份历史构建请求。它们没有运行时代码消费者，但直接删除也会匹配 GitHub Actions paths 条件，因而不能直接删掉。
- 修复构建请求方向判断：既有构建工作流添加一个只读 request-gate，使用 Git 实际范围、`--no-renames --diff-filter=AM`，仅新增或修改 pending 顶层 `.txt` 才进入构建。纯归档/删除跳过 APK 编译、签名和发布；manual workflow_dispatch 保留。PR 使用 base...head 合并基准，避免主线已归档的请求在旧分支里被当作新请求重新激活。fork 在 job 与脚本拒绝；范围缺失/无效/对象不可读时失败关闭。
- 四文件移动到 `docs/archive/xiaoxu-apk-build-requests/2026-11-11/`，逐份比对 Git blob SHA 一致；不删除历史安装包、Release、用户记录、Room/D1 迁移或数据兼容代码。
- 新增真实临时 Git 仓库行为测试覆盖归档/删除、新增/修改、普通代码变更、PR 早期提交请求、旧 PR、fork、手动构建、非 main push、无效/缺失对象及 CLI 输出。归档/删除与旧 PR 误触发均观察到断言失败，再实现修复并通过；工作流接线测试确认 build 依赖 gate、publish 依赖 build。
- 本轮 `npm run check` 全量通过；行为测试、工作流接线测试、归档内容校验、`git diff --check` 通过。独立只读复审未发现可证实问题。
- 本地已尝试系统 Chromium 浏览器回归，浏览器在启动业务测试前因 `socket() failed: Operation not permitted` 退出。不能称本地 E2E 通过，需用更新后的 PR 远端 CI 验证；未本地编译 APK、未进行学校本人数据或 Ace 6 真机验收。
