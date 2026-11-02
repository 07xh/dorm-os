# 校序 Academic OS｜M1-B 五域数据准备与 UI 联动

## 本轮已提交代码
- 直接复用已合并 PR #57 的 Cloudflare Access RS256 JWT 验签、按用户隔离身份和 D1 数据库，不另建 Bearer 认证与数据库。
- 保持已有 /api/v1/assignments 手动任务服务和本地存储不变，新增 /api/v1/verified/{connections,schedule,assignments,grades,exams,second-class,snapshot}。
- 使用 migrations/0003_academic_verified_snapshots.sql 扩展 0001_academic_core.sql 的数据库；按 user_id、kind、source、externalId、term 区分记录和批次。
- 仅最新完整验证批次可见；同一时间戳下重新核验零条结果也不会展示上一批旧记录，过去数据保留审计。
- 网页「我的→数据连接与备份」通过 Cloudflare Access 授权后读取，课表、任务、成绩、考试、二课和全站搜索读取已核验记录；作业只读展示，不把平台已提交当作个人本地完成。
- 前端不保存密码/Cookie/JWT；保留旧本地课表、任务、NFC、备份和官方通知接口。

## 明天用户本人需要完成（尚未在本轮执行）
1. 在 Cloudflare Pages dorm-os 绑定本人 D1：变量名称 ACADEMIC_DB；按顺序执行 migrations/0001_academic_core.sql、migrations/0002_academic_legacy_links.sql 和 migrations/0003_academic_verified_snapshots.sql（数据库存在时不要重复执行已执行的步骤）。生产与预览分开；迁移前做数据库备份。
2. 在 Cloudflare Zero Trust 为 /api/v1/* 创建 Access 应用，只允许本人身份；设置 Pages 的 ACCESS_TEAM_DOMAIN、ACCESS_AUD。必须核对 Access 路由策略确实覆盖私有接口。
3. 在官方学习通与青果教务登录后，仅用合法本人授权的读取器取课表、考试、成绩、作业、二课记录。确认原始稳定 ID、时间、周次、状态和来源，不能拿演示账号代替华水本人数据。
4. 由本人的受控程序将完整且核验过的结果生成 xiaoxu/academic-snapshot-v1，使用已授权的 Access 会话向 POST /api/v1/verified/snapshot 提交快照。不要把 Cookie、签名 URL、密码、原始 HTML 发给网页或 GitHub。
5. 进入校序「我的 → 数据连接与备份」点击「读取授权数据」，逐项核验记录、是否过期、来源与更新时间；未收到真实数据的业务仍显示「未连接」。

## 统一快照契约
- schema=xiaoxu/academic-snapshot-v1；source=ncwu/chaoxing/second_class；kind=schedule/assignments/grades/exams/second-class；verifiedSchool=ncwu。
- capturedAt 为有时区的时间戳、complete=true、records 为经验证的完整记录，最多 250 条；空批次须经过核验并设 verifiedEmpty=true。
- 每条记录拥有不含账号密钥的稳定 externalId 和 title，青果课程/成绩/考试额外需要 term。
- 课表使用 weekday、startSection、endSection、weeks，不会擅自推断节次对应时钟时间；作业 platformStatus 与个人 done 独立。数据缺失不能推断平台没有作业。

## 边界与验收
- PR #57 的 Cloudflare Access、SQLite 契约测试继续保留；本轮新增 M1+M2 真实 SQLite、JWT、业务快照/空批次、隔离、E2E 测试。
- **尚未完成**：生产 D1 绑定和迁移、真实青果/学习通本人自动读取、自动 Cron 同步、跨设备手动任务双向合并、Android 真机数据验收。部署代码不能算这些平台已接入。
- Access 失效返回 401；未配置返回 503；任何私有响应一律 no-store。不会自动签到、刷课、选课或代提交作业。
