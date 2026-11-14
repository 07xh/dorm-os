# 学习通单一本机读取 + 独立云端同步｜真实账号验收

基线：07xh/dorm-os main 1ed3e0ba6a88ad6e4da0979894e2e065dfd8c1ce
用途：记录用户本人 Ace 6 真实官方页面的可核对结果。**此文档不是已通过的验收证明。**
禁令：不重写 ChaoxingLocalReader，不新建第五套请求器，不上传学校密码、WebView Cookie、HAR、原始响应、签名 URL、个人作业正文或可识别身份的截图。

## 现有架构与边界

- 本机：NativeSchoolConnectActivity → ChaoxingWebSession / ChaoxingLocalReader → NativeSchoolRecordDao.mergeChaoxingRead → RealAcademicTaskHub。
- 云端：已经存在的 /api/v1/connections/chaoxing/auto-sync → academic-chaoxing-auto-sync.mjs → academic-chaoxing-homework-sync.mjs → D1 → CloudClient.refreshPrivate。
- 两者登录会话**独立**。本机扫描成功不是服务端授权成功；云端开关只递交 `{ "enabled": true/false }` 到现有校序接口。
- 服务端每小时最多扫描三门课程；最后运行时间和结果来自受保护的 /api/v1/connections 响应。一个 `enabled=true` 回执**不是**作业全量读取证据。
- 作业身份必须以 courseId/classId/workId 三元组核对；旧通知 `cxn:` 与官方作业不能凭标题猜测合并。服务器云端会话与手机当前学习通账号是否相同，尚需本人在官方页面双端核验。

## 1. 准备和验收表

实际测试构建 SHA：**待填写**
QA APK SHA-256：**待填写**
设备及 Android 版本：**待填写**
测试日期：**待填写**
测试人本人确认（不填姓名）：**待填写**

| 项目 | 官方页面实测 | 校序手机记录 | 结论 |
|---|---|---|---|
| 本人课程总数及各课程名称 | 待验 | 待验 | BLOCKED |
| 各门课程作业总数和分页末页 | 待验 | 待验 | BLOCKED |
| 未提交 / 退回 / 已提交 / 待批阅 / 已批阅 | 待验 | 待验 | BLOCKED |
| 完成互评后，旧通知不再算待办 | 待验 | 待验 | BLOCKED |
| 老师删除或撤回的作业仅按有证据的范围撤下 | 待验 | 待验 | BLOCKED |
| 作业截止时间：完整日期 / 缺少年份 / 无日期 | 待验 | 待验 | BLOCKED |
| 考试通知与考试状态区别 | 待验 | 待验 | BLOCKED |
| 仅学习计划或考试通知时也显示保存回执 | 待验 | 待验 | BLOCKED |
| 关闭重开后本机 Room 仍保留最新数据 | 待验 | 待验 | BLOCKED |
| 中断网络 / 部分失败不清空旧记录 | 待验 | 待验 | BLOCKED |
| 切换本人账号时不混入前账号记录 | 待验 | 待验 | BLOCKED |
| 云端开关仅在本人明确点击确认后开启 | 待验 | 待验 | BLOCKED |
| 云端服务端会话和手机账号一致 | 待验 | 待验 | BLOCKED |
| 云端最近一次调度成功、实际检查门数和 D1 状态核对 | 待验 | 待验 | BLOCKED |
| 关闭云端自动同步后状态确实刷新为关闭 | 待验 | 待验 | BLOCKED |

## 2. Ace 6 实际操作

1. 通过经 CI 验证的独立 `io.github.x07xh.xiaoxu.unifiedqa` APK 测试，不覆盖现有正式版。打开「我的 → 数据连接 → 学习通」。
2. 在本机官方网页完成本人正常登录。点「读取全部」，记录通知页数、课程门数、作业 / 学习计划 / 考试通知条数和失败类别。不上传私密正文或 Cookie。
3. 在学习通官方页面逐门对比课程、作业、状态和截止时间；重点检查分页、互评、已经提交和老师撤回的情形。若官网无该情形，标记 NOT_APPLICABLE，不能写 PASS。
4. 返回任务中心核对当前可做、待完成、已完成、待核对及本机记录。无作业但有学习计划 / 考试通知时也检查返回回执。
5. 退出 / 重启校序后不重新登录先检查本机 Room 记录；随后重新读取，确认会话和状态。再关闭网络读取一次，核对是否保留旧记录。
6. 如本人拥有不同学习通测试账号，仅在获本人授权且能够安全登录时验证切换隔离；不能替他人登录。
7. 另行在校序网站确认**已经存在**的本人云端学习通授权（本机登录不转存为云端 Cookie）。再返回 App「数据连接」展开云端学习通卡片，观察「已关闭 / 未授权 / 调度未就绪」。
8. 主动确认开启云端每小时同步，在服务端有成功调度及官方作业记录后，刷新云端连接状态，逐项核对最近运行时间、成功/部分失败状态、云端任务身份。仅开关成功而无真实读数记 BLOCKED。
9. 在 App 关闭云端同步，重新获取 /api/v1/connections 后验证 enabled=false。确认本机任务并未被删除。

## 3. 发布门禁（必须逐项有证据）

- [ ] PR 精确 HEAD 的 Node `npm run check` 通过，不能用上一个 main SHA 的成功替代。
- [ ] 同一 HEAD Gradle `:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:assembleRelease` 通过。
- [ ] 同一 HEAD Android instrumentation 全部通过，包括 Room 迁移。若 SecondClassAutoReadDriverTest / 模拟器异常仍存在，门禁失败。
- [ ] 独立 QA 包可安装；签名、SHA-256、包名和源码 SHA 一致。
- [ ] 上方本人账号项目完成核对，任何 P0 FAIL / BLOCKED 时不发布正式版。
- [ ] 新旧版本升级验证本机 Room、既有签名、云端会话无损。
- [ ] 使用原正式签名证书；**不重建签名密钥**。
- [ ] 由用户额外明确授权正式签名发布，才提交单独的 final/formal release 请求。

## 4. 明确不计入通过的证据

fixture、网络模拟、静态断言、JS/JUnit 全绿、Web/Cloudflare 部署成功、云端 enabled=true、没有数据的空列表、已读取通知数量、单次匿名网页响应，均不足以证明用户本人所有课程和作业真实可用。

验收状态：**BLOCKED — 等待本人设备与云端学校会话核对；本 PR 只发 QA，不授权正式发布。**
