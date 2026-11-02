# 校序 Academic OS M2-A：可信只读快照差异合并引擎

## 目标与当前验收边界
本批新增 `lib/academic-readonly-sync.mjs`，仅供未来经过本人授权与字段核验的后端**只读连接器**调用。**没有建立任何可由浏览器向“已验证平台”写入任务的公开 API**，也**没有声称**学习通/青果后台会话已连接。

上游可继续复用已有 `public/xiaoxu-chaoxing-cli.js` 的输出规范 `xiaoxu-task-batch-v1`，但真正接通之前必须核实授权会话、平台字段语义和正确分页。只接受明确指向 `chaoxing` 或 `ncwu` 的内部快照，单批最多 20 条。

## 数据库迁移
依次执行 `0001_academic_core.sql`、`0002_academic_legacy_links.sql`、main 已有的 `0003_academic_verified_snapshots.sql` 和 `0004_academic_source_course.sql`。0003 仅为来源映射增加 `course_title` 文本，不伪造课程正式 ID。

## 与 PR #59 五域快照的关系
PR #59 的 `academic_verified_records` 是授权提交快照的版本化暂存和展示，不代表平台已建立真实自动连接。本批只负责在后续真实只读连接验证之后，将作业投影到 `academic_assignments` 账本，不替换现有 `/api/v1/verified/*` 入口，也不把客户端声明 `verifiedSchool=ncwu` 当作独立真实性证明。

## 安全契约
调用 `applyReadOnlySnapshot(db, authenticatedUserId, connectionId, snapshot, options)` 前，连接服务必须完成真实平台的合法授权和登录确认。函数还会从 D1 再次验证：
- 连接归属该用户；
- `mode=readonly`；
- 连接 `status=connected|partial`；
- 连接 provider 与快照 source 完全一致。

未配置的、导入模式的、失效的、跨用户连接，全部拒绝写入。用户主动 JSON 导入走 M1-B 历史迁移通道，**不允许通过该函数升级成已同步证据**。

## 合并语义
- 以 `connection_id + external_id` 匹配平台作业；
- 由 M1-B 历史导入且具有匹配来源 ID 的旧任务，只在**唯一无歧义**时与后续真实只读记录关联；
- 保留 `local_status`；平台提交状态独立维护；
- 未提供可信的精确截止日时，不清除之前已保存的截止日期；
- 同步输入缺失旧作业，不删除历史作业，也不推定它已提交；
- 标题、已知截止日与平台状态发生变化时新增审计事件；
- 已读、个人备注和课程 ID 不由外部标题强制覆盖；
- `coverage=partial` 记录部分同步，不宣称全量完整；
- 通过 D1 原子批处理提交作业、来源、事件、同步记录与数据源状态。

`academic_sync_runs` 的每一行是**本次合并成功应用**的记录，真实网络采集失败日志需要后续连接器另行提交，不能用空快照假装请求成功。

## 测试
`node scripts/xiaoxu-academic-sync-tests.mjs` 在内存 SQLite 中执行三份实际 D1 迁移，验证：
- 跨用户、未验证或导入模式的来源拒绝；
- 同一外部 ID 反复同步无重复；
- 已提交状态变化不覆盖本人已完成勾选；
- 截止时间变更生成可回溯事件；
- 已知截止时间不被无日期的查询覆盖；
- 部分响应不删除缺席任务；
- 与旧版导入的来源 ID 唯一匹配时关联而不是新建；
- 历史重复同一外部 ID 造成歧义时拒绝自动关联。

## 下一步
接入本人明确授权的学习通只读查询运行环境，并验证在具体真实课程和作业上的外部 ID、截止日期、平台提交状态；然后把已核验的结果交给本合并引擎。未经实际账号验证，页面仍必须显示“未连接”。不得在公共仓库、日志或 D1 普通明文字段记录学习通账号密码/Cookie。
