# 校序 M5：可信学业快照与个人云端提醒收件箱（2026-11-03）

## 本批实际完成
- 在已存在的 Cloudflare Access RS256 JWT 身份与 D1 用户隔离基础上，新增 `0006_academic_reminder_inbox.sql`。
- 本人登录后由 `GET /api/v1/reminders` 从当前个人考试快照和 D1 云端任务生成站内待办提醒。
- `PATCH /api/v1/reminders` 保存个人开启/关闭、提前天数及逐条已读状态，读写仅限 JWT 验证后的同一用户；跨设备重新访问同一账号可恢复。
- UI 复用「任务」「设置与备份」现有卡片，不新建一套重复导航；PWA 缓存客户端 JS。
- 个人导入成绩、考试、二课不再标为 `snapshot_verified`，改为 `personal_snapshot`；`GET verified/*` 返回 `provenance: user_import`。快照字段结构校验**不是**学校官方来源证明。
- 严格保留“日期只有天，没有时刻”的精度，不编造具体考试、作业时间。原有本地课表和任务不覆盖。

## 提醒行为边界
- 用户授权成功以后才请求私有接口；默认不开启提醒订阅。
- 开启仅表示使用云端 **站内** 提醒收件箱与未读回执；每次打开网站可刷新，**不代表**关闭网页后能收手机系统 Push。
- 考试日期来自本人上传且已通过 schema 校验的考试快照；来源标注“个人快照”，建议以学校实际考试安排为准。
- 云端任务来自本人 D1 记录；个人完成状态不会更改学习通/青果/二课平台状态。
- 暂不为第二课堂自动生成活动提醒，因为目前没有可核实的本人报名状态、活动开始时间与官方订阅接口；二课记录仍由原来的 `verified/second-class` 查看。
- 不持久化第三方密码、Cookie、会话 token 或签名 URL。

## Cloudflare 上线依赖与操作
1. 首先为 `/api/v1/*` 配置 Cloudflare Access，只允许本人登录。配置 `ACCESS_TEAM_DOMAIN`、`ACCESS_AUD`，Pages 项目绑定已有 D1 为 `ACADEMIC_DB`。不要把任何密钥提交仓库。
2. 对正式 D1 备份后，按顺序执行 `migrations/0001_academic_core.sql` 至 `0006_academic_reminder_inbox.sql`，且此前每个 migration 只能应用一次。若生产已应用前五个，仅执行 `0006`；先在预览环境跑迁移和端点验证。
3. 在本人访问 `/api/v1/reminders` 的有效 Access 会话中，确认首次返回 `enabled:false`、`delivery:in_app_only`；无个人数据返回空列表而不是模拟。
4. 两台设备使用同一经授权 Access 身份，手动启用站内订阅并将一条真实任务标已读；另一台刷新后状态一致。另一身份应该读不到记录。
5. 实机检查手机任务页、设置页、授权过期时错误状态、PWA 更新；Cloudflare Functions/D1 绑定与生产 SHA 通过后才能说“云端提醒上线”。

## 仍然没有实现
- 华水本人青果账号的成绩/考试实际接口适配；第二课堂本人活动/积分真实 API；自动获取学校端新增记录。
- 个人数据云端同步目前覆盖 D1 云端任务及此次提醒偏好/已读，不等于全站 localStorage、课表、NFC、物品全面同步。
- 离线背景实时提醒、Web Push 订阅和服务端定时推送。要做真 Push，需要额外的 Web Push VAPID 密钥安全存储、用户明确授权的订阅生命周期、定时触发去重、端到端真机验收。
- 数据库迁移和生产 Access、D1 实际配置：本 PR 的代码及单测不能代替生产操作。

## 测试门禁
`npm run check` 包含 `scripts/xiaoxu-reminder-inbox-tests.mjs`，覆盖 SQLite 迁移、真实签名 JWT 验证、跨用户隔离、同源 PATCH、订阅默认关闭、已读持久化、日期精度、无数据时不造数、浏览器 XSS 转义与不主动调用私有 API。
原有 Playwright 全量 E2E 需仍通过。未获用户本人学校凭据时不得测试真实第三方私有记录。

## 复用来源
Cloudflare D1、Access 和 Pages Functions 均已作为校序项目基础设施使用；没有另起数据库或引入大量前端依赖。学校平台读取适配继续沿用项目已有正方 KINGO / 拾光 / xuexitong-cli 调研结果，并独立核验目标学校接口。
