# 校序｜网站内学校账号登录与会话重连（阶段一，2026-11-03）

## 统一入口（不新建第二套账号系统）

使用原来的「我的 → 数据连接」，沿用校序自身 `/login/`、`verifyAcademicAccess`、`academic_connections`、`academic_verified_batches`、`academic_verified_records`，不改旧 NFC、课表、任务、备份与手工导入。三张来源卡片：华水青果教务、学习通、第二课堂。

用户期望流程：在校序网站填写本人学校账号 → 服务端按学校实际允许的登录方式验证 → 只读查询完整课表、成绩、考试或作业 → 校序保存经加密的可续用学校会话而非密码 → 后续同步 → 失效后在同一平台卡片重新连接。每个平台失效不强制退出校序。

## 本次已提交代码（不是宣布真账号已通）

- 私有 `GET /api/v1/connections` 仍只返回本人 D1 数据摘要；增加 `loginMode`、`sessionState` 与 `accountAuthorization`，不会输出学号、密码、Cookie、Token、会话原文。
- 私有 `POST /api/v1/connections/:provider/login`：严格同源 JSON、小包、先校验已安装的服务端学校适配器，才读取账号口令；只在后端内存传递本次密码；完成真实身份与全量数据校验后才存储会话。
- `POST /api/v1/connections/:provider/sync`：仅使用本人服务端存储且未过期的会话同步；失败保留原快照；授权拒绝会撤销过期会话。
- `POST /api/v1/connections/:provider/disconnect`：解除此数据来源的会话授权，历史学业快照仍保留。
- `migrations/0011_academic_provider_sessions.sql`：对原学业数据体系补充本人+来源的会话保险库，AES-256-GCM 加密，随机 nonce、AAD 绑定来源和校序用户。Secret `SCHOOL_SESSION_KEY` 是**全新独立的 32 字节随机值的 base64url 编码（43 字符）**，只能放 Cloudflare 加密 Secrets；不能写进 Git、浏览器、本聊天。
- 前端学校账号表单仅在**真实学校适配器已注册、会话保险库已部署且密钥有效**时出现，否则只有明确的“尚待验证”，不会主动收集密码。支持同卡片登录、重新连接、立即同步、解除授权。
- 单人校序登录仍由已有 `ACADEMIC_AUTH_MODE` 和 `OWNER_LOGIN_SECRET` 控制。**学校密码不是校序密码**。

## 真实数据接入的未完成门禁

- **目前没有已注册的真实青果/学习通学校登录适配器。** 现在用户即使在学校官网登录，也无法让校序读取第三方 Cookie；网页表单不会出现，更不会伪造“已连接”。
- 华水当前青果可能使用 CAS 或教务直登、验证码；必须核验学校真实登录授权机制，选择可合法使用且经本人测试的服务端只读接口。适配器需实现 `login({username,password})`（返回 `{session,expiresAt}`）和 `forSession({session,userId})`（返回项目现有 `verifySession/readSection` 或 `verifySession/readPage` 的受信任适配器）；由服务端可信 Pages middleware 提供 `context.data.academicSchoolAdapters[provider]`。**切勿让前端参数或用户 JSON 注入此对象**。
- M14 `runVerifiedNcwuSync` 仍负责青果三类记录完整性和 owner 绑定；M15 `runTrustedConnector` 负责学习通分页面作业。不更改独立的第二课堂只读数据模型；第二课堂尚不接受本网站密码。
- 当前仅有**用户主动点击的同步端点**。原 `academic-sync-jobs` 仍默认关闭；Cloudflare Pages 无须假称已设置真实 Cron。学校适配器注册且经过真实会话续期测试、调度器接入后，才能承诺后台自动同步；学校会话失效时依然可能需要用户重新登录。
- 0011 迁移应用到生产前应先完成 0001–0010 的就绪检查，执行兼容性回归、生产匿名访问拒绝与手机端登录体验验收。切勿仅凭 GitHub CI 通过宣称已连接学校。

## 安全验收

`node scripts/xiaoxu-school-login-tests.mjs` 验证：无服务端 Secret 不能处理学校密码、同源约束、对称加密密文不泄漏 Cookie/明文口令、owner 隔离、未过期会话主动同步、断连撤销、未注册适配器拒绝。以及原项目 `npm run check` / Playwright 回归。正式学校上游不得用测试模拟证明可用。

## 加密会话保险库前向迁移（D1 M17 补充）
- 在**已有** `upgrade-owner-auth-d1.yml` 中增加 `0011_academic_provider_sessions.sql` 迁移（复用该工作流和现有 Wrangler 安全检查；不新建第二套数据库或绑定系统）。
- 先校验 Cloudflare Pages 生产绑定 UUID 与 D1 UUID 完全一致，确认原 0001–0009 学业表及 0010 校序本人登录表完整；0011 缺失时才执行，已经存在且字段完整时直接跳过；部分表结构则拒绝自动改写。
- 检查 0011 六个必要字段、外键和 SQLite 完整性；全程不查询任何个人学业/学校会话明文。
- 合并后的 Actions 运行结果才是生产迁移证据；迁移成功也**不会**开启网页登录、后台 Cron 或生成学校凭据，仍需真实适配器验证及 `SCHOOL_SESSION_KEY` 由用户自行安全配置。
