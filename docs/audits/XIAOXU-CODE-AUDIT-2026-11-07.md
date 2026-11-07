# 校序代码审查与修正｜2026-11-07

## 目的与边界

面向本人日常使用，审查功能方向、现有架构、重复入口、无用实现、数据来源、同步与研发流程。继续原 Kotlin/Compose App、Room、Cloudflare/D1 和三平台读取器；保留既定 UI/玻璃导航，不另建 Shell、连接器或登录系统。此报告记录源码审查，不能替代本人账号或 Ace 6 验收。

基线：`07xh/dorm-os` 的 #351（`7357ea97`），同步 main `cf0d1c9b`，并继续整合 #351 `ac8a443f`（任务来源默认折叠、V11 本地课表、周次定位与设置行内报错）及 main `26c29f95`（#367 校园跑只读查询层）。审查修改在独立分支，目标仍为 #351；不直接推 main，不解除 #351 的 Draft/真机门禁。

## 审查路径与执行记录

- [x] 核对 main、#351、#331、#349、#365 及当前开放 PR，读取现有 AGENTS.md。
- [x] 检查 Android Manifest、NativeShell/PrimaryNavigation、任务来源与列表、CloudClient、Room 与本机课表路径。
- [x] 检查学校会话 Vault、学习通手动/后台读取、二课退役入口及现有只读服务端。
- [x] 检查微信采集/契约/候选审核/任务链接、D1 增量迁移与发布脚本。
- [x] 复现学习通轮换 Cookie 未持久化；复用 #331，保留并发重连保护及错误分类。
- [x] 整合 main 微信链接和 #349 清理；解冲突时保留双方新增测试及 #351 当前规则。
- [x] 去掉任务列表 80 条截断、CloudClient 无消费者的云端课表状态和不可达分支。
- [x] 将只读 CI 和显式 APK 请求的 PR 目标扩展至唯一整合分支；main 发布条件与 APK 显式请求要求不变。
- [x] 独立只读复核：未发现有证据的阻断性回归；再次执行会话轮换测试与 diff 检查通过。
- [ ] 本轮尚未推送、未创建 PR、远端 CI 未启动：用户已在当前回合明确许可，但自动审批仍将许可误判为历史引用并拒绝推送。仓库归属与管理员权限已由连接器再次核实；未绕过拦截。后续当前回合的 CLI 推送已执行，但以退出码 128 报缺少 GitHub HTTPS 凭据；改用本人已连接且验证具备 admin/push 权限的 GitHub 接口提交同一内容，远端提交/PR/CI 按最终结果另记。本人账号/真机另行验收。

## 已确认问题与修正

| 等级 | 问题与证据 | 本轮处理 |
| --- | --- | --- |
| P1 | #351 已有 Android 微信链接 UI，但尚缺 main #364 的服务端契约、存储和 `0018` 迁移 | 合并 main；保留人工审核、owner 隔离、撤销设备禁止补链和已审核内容不可修改 |
| P1 | 学习通会话轮换修复 #331 仍未进入整合线；轮换测试在原基线第 78 行失败，Vault 密文未更新 | 复用 #331，加密保存已验证本人 Cookie；前台/后台共享路径，网络及下游拒绝不直接冒充全局退出，旧请求不覆盖新登录 |
| P1 | 任务中心统计全部记录，UI 却 `visible.take(80)`，第 81 条起无法直接滚动访问；静态门禁还强制保留提示 | 移除截断和无用提示，所有筛选结果交给原 LazyColumn；保留原顺序、分组、稳定 key、审核与来源语义 |
| P2 | 二课已转 App 官方授权，但旧 CAS 预检 API、密码探测/Sudy 实验、CI 和部署探测仍活跃 | 复用 #349，删除退役实现及无调用方方法；旧密码 POST 继续在读取请求体/联系学校前返回 410；保留只读适配器、官方读取 JS、Room/D1 数据和会话刷新 |
| P2 | 本机课表成为显示来源后，CloudClient 仍保留永远为空的 lessons、scheduleStatus/Fresh 等字段和无法触达的 ncwu 分支 | 清除无消费者的状态/临时变量/重置赋值与不可达验证分支；保留共享 VerifiedLesson 模型及实际本机课表 |
| P2 | 原 CI 只对 main 目标 PR 运行；#351 子 PR 缺同等门禁，历史上因此另开 QA-only PR | 为既有综合 CI/JVM/离线 WebView/显式 APK 构建加入整合分支目标；不增加工作流、调度或部署 |

## 功能方向与重复实现判定

| 范围 | 源码事实与处理结论 |
| --- | --- |
| 唯一 UI | NativeShell 管路由，PrimaryNavigation 只负责手机底栏/平板侧栏显示；两种响应式呈现不是两个 App Shell。保留当前玻璃组件，不重做视觉。 |
| 任务 | RealAcademicTaskHub 是统一入口；LocalTaskEditor 是实际 Room 增删改管理，不能因外观有列表就直接删。官方提交状态与个人完成保持独立；云端日期不自动成为官方截止。 |
| 消息/微信 | 待审核候选不等于任务；只有 accept 后才能入任务查询。保留源消息摘要和链接，不能靠标题把课程任务、快照与微信合并。 |
| 学习通 | 轮换持久化缺口本轮修复，但本人全部课程/作业数量、官方状态、关闭重进持续同步仍须真实对照。 |
| 青果/课表 | 当前 App 显示本机已导入课表，复用 LocalQingguoImportActivity 与本机网格读取。云端兼容端点/历史记录不因 App 改为本机显示而删除；不新增云端课表第二入口。 |
| 第二课堂 | SecondClassHomeActivity 是结果回传宿主；NativeSchoolConnectActivity 是学校本人授权/读取宿主。保留现有自动读取、后台开关、取消保护、绑定与保存回执；它们不是本轮的死代码。 |
| 校序与学校登录 | 校序 owner 会话保护私人 API，学校会话证明数据来源，两者用途不同；不能为了“免登录”直接删现有 owner 鉴权。 |
| 开发分支 | #331/#349 的源码进入本轮整合分支，不等于这些 PR 已合并 main。历史 QA/设计 PR 需按祖先关系、产物和未完成验收逐个归档，不能一键关闭。 |
| 其他项目 | 仓库内 TapFlow/NFC、DORM OS 兼容路径属于用户其他项目，不能当作校序无用代码批量删除。 |

## 仍需后续处理的开发问题

1. `CloudClient.kt` 与 `RealDataWorkspace.kt` 仍是较大文件。按状态归属拆分有价值，但本轮不同时改登录、导航和页面架构，避免覆盖 #351 的并行修改。
2. 静态门禁大量依赖源码文本，本轮已有“强制保留截断/空状态”的反例。后续逐批把重要行为迁入现有 JVM/数据库/浏览器测试，不再增加类似无业务意义的布局字符串断言。
3. README/旧设计文档仍混有不同阶段的状态与分工。它们不是当前功能验收凭据；后续以本报告、AGENTS、真实提交/CI/本人数据为准，避免重新执行历史方案。
4. 未解决的实际交付证据：三平台本人数据全量对照、授权回跳与重启刷新、Android16 后台持续读取/提醒、Ace6 原版玻璃对照和帧数据。代码/CI 通过不能关闭这些缺口。

## 验证

- `npm run check`：本轮整合 main + #331 + #349、任务/状态/工作流修改后的完整检查退出码 0。
- `node scripts/xiaoxu-android-bootstrap-tests.mjs`、`node scripts/xiaoxu-android-ics-import-tests.mjs`、`node scripts/xiaoxu-deploy-workflow-tests.mjs`：修改后退出码 0。
- `python3 -m unittest discover -s integrations/wechat -p 'test_*.py'`：20 项通过。
- 本地 Playwright 已尝试：缺少 Chromium；自动下载返回损坏/截断 ZIP，安装失败，因此本地浏览器 E2E 未通过验收，待获准推送后交由本轮 CI 执行；远端 CI 尚未启动。
- 本地无 Android SDK/Gradle；不把静态检查称为 Kotlin 编译或真机测试，待获准推送后启动 GitHub JVM/Compose 构建。

- `node --test scripts/xiaoxu-secondclass-local-reader-tests.mjs scripts/xiaoxu-secondclass-local-activities-tests.mjs`：15 项通过。
- 已将本轮确认的唯一整合入口、本机课表来源、退役路径和全任务列表规则补入 AGENTS.md，避免后续执行偏回历史方案。
