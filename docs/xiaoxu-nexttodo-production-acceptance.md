# 校序 NextTodo：生产数据上线与本人跨设备验收

> 本文是验收步骤，不是验收通过记录。已通过 GitHub Actions 只读核验生产 D1 绑定与恢复点，但尚未执行 0019 迁移，也未获得本人两端真实任务验收；不得将“预检通过”写作“功能上线”。

## 一、生产 D1：只读核验与恢复点

1. 核对 Pages 项目 `dorm-os` 生产环境 `ACADEMIC_DB` 绑定的 D1 UUID 与 Cloudflare D1 列表严格一致。拒绝凭数据库显示名称猜测身份。
2. 确认该 D1 是 `version: production` 存储后端；旧 `alpha` 存储不是同一 Time Travel 机制。Cloudflare 文档：https://developers.cloudflare.com/d1/reference/time-travel/
3. **仅在生产 D1 本人授权环境**调用 Time Travel 查询获取当前恢复书签。不要把 bookmark、API Token、会话、原始任务数据上传 GitHub、聊天或 CI 日志。
4. 只读检查 `sqlite_master` / `PRAGMA table_info` / `PRAGMA foreign_key_check` / `PRAGMA quick_check`。核对以下对象：
   - `academic_assignments`、`academic_users`、`academic_assignment_events` 的现有结构
   - `academic_task_dependencies`、`academic_task_graph_versions`、`academic_task_graph_guards`
   - `trg_academic_task_graph_guard`、`trg_academic_task_done_prerequisites`
5. 确认没有意外的部分迁移。自动迁移脚本 `scripts/ensure-task-dependency-schema.mjs` 仅在 `main` 的成功部署链中运行：获取恢复书签失败/已有部分对象时必须 fail-closed；不能标记已通过。
6. 迁移后再检查全部表、触发器、外键与 `quick_check`，并确认旧作业来源、学校授权、个人任务未被改变。若要完整的行级不丢数据证明，须在授权且保护隐私的条件下对比迁移前后数量或校验和；**不要**把私有行内容发至公共日志。

官方文档：<https://developers.cloudflare.com/api/resources/d1/subresources/database/subresources/time_travel/methods/get_bookmark/>

## 二、真人任务跨设备核验（不得使用模拟或种子数据）

前提：手机、平板或电脑上的同一位本人账号均完成校序授权；已上线 `#tasks` 与 D1 0019 增量迁移。不得将第三方学习通提交状态当作可手动回写的任务。

1. 设备 A 与 B 分别打开 `#tasks`，在“任务工具 → 跨设备同步核对”获取**由本人当前云端数据生成的对照码**。如果未连接云端、依赖图接口缺失或没有本人个人云端任务，界面应明确报错而不是显示假成功。
2. 在没有同时编辑任务时，记录两端核对码、个人任务数、已完成数、依赖边数、图修订号；应完全相同。**只保留数值与结果**，不保留任务标题、UUID、学校标识。
3. 使用已存在的本人手动云端任务，选择一个任务 B，给它设置另一个可执行任务 A 作为前置（不修改学校官方作业）；设备 A 保存后设备 B 刷新，B 应进入“等待中”，关系图出现 A → B。
4. 在设备 B 将 A 标记完成，设备 A 刷新后 B 应进入“接下来”；随后在设备 A 恢复 A 为未完成，设备 B 刷新后 B 应重新等待。若之前 B 已完成，则先复位为未完成再验证。
5. 两端重新获取对照码，核对一致；并在验收结束后恢复原始任务的前置、完成状态和标题，记录撤销动作已完成。
6. 同时核对学习通已提交/待批阅/已批阅记录**没有**错误地进入手动待完成，历史/未知条目只保留待核对；并且任何 UI 操作没有回写学校平台。
7. 离线打开列表时允许展示缓存，但不得把缓存对照码标记为“最新云端核对”。断线重登、切换账号后，旧账号缓存和图必须清空。

**说明：** SHA-256 对照码可用于检验两台设备显示的个人云端任务快照是否一致，不等于双向写入功能验收，也无法代替本人账号真实性和服务端授权验证。

## 三、Android #429 真实升级门禁

- 统一 Android 任务中心仅取 #429（#430 已整合到 #429，重复 PR 不合并）。
- 旧 Room v1/v2/v3 数据库升级至 v4 的非破坏性验收，需使用保留本机数据的真机安装升级路径，不可只用全新安装判断。
- 测试 v3 备份与旧 v1/v2 备份的恢复、循环检测、重复前置、重启持久化、勾选失败提示/回滚。
- 在一加 Ace 6 Android 16 对“现在可做／等待前置／学习通待核对／按来源”及弹层流畅度、视觉割裂、列表跳动等逐项真机核对。
- 正式包必须走固定签名与完整备份；QA APK 使用隔离包名，**不可**直接认定可覆盖正式版。

## 状态记录（仅填本人已核验证据）

| 门禁 | 当前状态 |
| --- | --- |
| 0019 schema 静态/SQLite/CI | 已通过自动测试；非生产结果 |
| 生产 D1 只读预检与恢复点 | 已通过，2026-11-09 15:16:55 UTC；[Actions #42643012419](https://github.com/07xh/dorm-os/actions/runs/42643012419)，迁移前需重新获取有效恢复点 |
| 生产 D1 0019 迁移与完整性 | 未实际核验 |
| 本人双设备读取对照码一致 | 未实际核验 |
| 本人两端任务写入、解锁、恢复 | 未实际核验 |
| Android Room 老用户升级/备份 | 未真机核验 |
| 正式合并/发布 | 未进行 |
