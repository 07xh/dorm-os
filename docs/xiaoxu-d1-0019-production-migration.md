# 校序生产 D1 0019 迁移门禁（只用于本人授权环境）

> 当前为待合并开发分支。以下步骤和测试不等于生产库执行记录；不得在未经过本人数据确认时标记发布完成。

## 为什么需要迁移

为网站本人的云端手动任务增加“先做 A、再做 B”的有向依赖。新增 `academic_task_dependencies`、`academic_task_graph_versions`、`academic_task_graph_guards` 与两个触发器，不更改学习通官方任务状态，也不另建用户数据库。

## 首次生产写入必须手动批准

**默认状态：拒绝自动迁移。** 普通 `main` CI 成功后触发的 Pages 部署可以读取 D1 结构、完整性和恢复点，但如果生产仍缺少 0019，最终会以 `task_graph_migration_manual_approval_required` 停止发布；它不能自行升级数据库。已经完成且检查通过的 0019 不要求每次发布再次批准。

仅在本人已核对目标 Cloudflare 项目、数据库 UUID、现有任务备份与恢复能力之后，才从 GitHub Actions 的 **Deploy DORM OS to Cloudflare Pages → Run workflow → main** 手动运行，并明确勾选 `approve_task_dependency_migration`。未勾选默认是 `false`。仅该次 `workflow_dispatch` 可为迁移脚本提供 `APPLY-0019`，脚本还检查事件类型，不能通过普通 push / workflow_run 绕过。

这个勾选**只是授权继续执行所有安全检查，不是跳过检查**。Cloudflare 凭据、Pages 生产绑定、D1 结构与完整性、Time Travel bookmark、七张既有业务表计数中的任何一项不满足要求，均须中止。迁移后的账号、作业和跨设备操作需要本人再验收；不得把通过 CI 当成生产任务同步成功。

## 发布前检查与获批迁移执行顺序

部署工作流在 Pages 发布之前调用 `scripts/ensure-task-dependency-schema.mjs`，其行为必须是：

1. 仅允许原仓库 `main` 的受保护生产部署调用；校验 Pages 项目 `dorm-os` 的生产 `ACADEMIC_DB` 与 D1 UUID。
2. 只读检查 0019 是否缺失、完整、或部分损坏。部分状态直接中止；校验现有外键和 `PRAGMA quick_check`。
3. 仅在确实需要迁移时，**只读**读取现有七张核心业务表的总记录数（不包含标题、内容、姓名、Cookie、学号，且不将计数写入日志）。
4. 通过 Cloudflare D1 Time Travel **GET bookmark API**取得迁移前的恢复书签；无法取得则中止。日志仅记录 UTC 时间，不暴露凭据或书签。记录的 UTC 时间可供授权管理员在 30 天恢复窗口中找到相应恢复点。
5. 执行仅追加的新表、索引和触发器迁移。
6. 再次核对原有七张业务表记录数量与迁移前一致。若不一致（包括其他设备并发新增/删除造成的差异），为保护个人数据应停止部署并要求人工核验，**不能自动恢复**。
7. 再次校验 0019 全部列与触发器、外键和 `PRAGMA quick_check`。任一失败则不执行 Pages 发布。
8. 生产完成后，由本人通过现有 Access 会话验收个人任务数据、学习通历史状态与两端更新。不得通过模拟数据冒充完成。

**重要限制：** 核心记录数与 SQLite 完整性检查不能证明“每条私人记录的内容逐字段未变”，更不能证明本人两端同步；这些必须额外按授权条件核对。Time Travel 也不是持久保留的数据库快照，恢复期有限，且实际恢复会覆盖当前生产数据，应只在经过充分核对后人工决定。

Cloudflare 官方参考：
- <https://developers.cloudflare.com/d1/reference/time-travel/>
- <https://developers.cloudflare.com/api/resources/d1/subresources/database/subresources/time_travel/methods/get_bookmark/>

## 当前进展

- 0019 迁移脚本及 SQLite 仿真回归：待以最新完整 CI 结果确认为准。
- 生产恢复点的确实取得：未核验。
- 生产 0019 迁移：未执行。
- 本人旧数据逐项和跨设备：未核验。

不能合并为正式版的条件：缺失恢复点、生产 D1 标识不匹配、SQLite 结构不完整、本人完成状态错分、正式签名或旧 Room 数据恢复未验收。
