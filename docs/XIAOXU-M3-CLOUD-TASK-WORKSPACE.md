# 校序 Academic OS · M3 云端作业工作台后端（2026-11-03）

## 已实现（不需要真实学校数据）
D1 依次执行 0001—0005 migration；0005 为任务增加 revision 和历史索引。现有本地任务、NFC、公开通知、V8 UI 不改变。

**私有 API，全部沿用 Cloudflare Access JWT + D1 ACADEMIC_DB，服务端按真实身份隔离：**
- `GET /api/v1/assignments`：包含 revision、legacy_id、来源课程和平台状态；分页/个人状态过滤。
- `POST /api/v1/assignments`：创建自己的手动作业。
- `PATCH /api/v1/assignments/:id`：带 `revision` 的本地完成状态、个人目标时间修改；**只有 manual 任务**可改标题、官方截止日期。并发版本不匹配返回 409，服务端禁止用页面操作伪造平台提交。
- `GET /api/v1/assignments/:id/history`：本人任务变化日志。
- `GET /api/v1/sources`：平台连接状态与用户手动上传的五域快照分开。后者明确标记 `user_supplied_snapshot`，**不是**实时平台已连接证据。

示例 `PATCH`：
```json
{"revision":0,"localStatus":"done"}
```
返回 `{"item":{"id":"...","revision":1,"updatedAt":"...","unchanged":false}}`。
浏览器每次保存后必须更新 `revision`；如果是 409 必须重新读取，不能覆盖另一个设备刚才的修改。

## 安全与语义
- 个人 task status 和第三方 platform status 永远分离。
- 云端平台作业的标题/截止日期不能从个人 UI 擅自改为“平台已经修改”。
- D1 batch 事务：版本 CAS 更新和变更日志一起提交，无效的旧版本不会写入事件。
- 无 Access/D1 时拒绝；个人 API no-store；跨站 PATCH 拒绝。
- 时间遵循 Asia/Shanghai；只知日期时不推断精确时刻。
- 支持相同账户多设备，但本批不自动上传旧浏览器数据；需下一批显式迁移 UI。
- 已上传的快照仅是用户提供的数据，不能单凭 `verifiedSchool` 字段把它宣传为官方实时连接。

## 完成门禁
`npm run check` 已加入 `scripts/xiaoxu-academic-workspace-tests.mjs`。
覆盖 0001–0005 SQL 迁移、本人/他人隔离、乐观并发冲突、历史记录、平台只读字段、截止时间精度、未配置拒绝与跨站拒绝。现有 Playwright 回归继续跑。

下一批计划：数据连接与备份页加入旧任务迁移 UI、云端作业列表及个人完成勾选；所有操作必须明确开启云端连接和本人确认，不自动替换既有本地任务。
