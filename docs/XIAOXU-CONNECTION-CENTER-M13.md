# 校序 M13｜无损精简 + 统一数据连接中心（2026-11-03）

## 目的与边界
- **一个校序网页登录入口**：复用现有 `/login/` 及 `verifyAcademicAccess`，不创建第二个校序账号库，保留旧 Cloudflare Access 模式的服务器兼容性；不擅自更改生产认证配置。
- **一个前台学校数据连接中心**：我的 → 数据连接；三平台按 provider 显示。云端任务、站内提醒、公开网站检测和手动导入折叠收纳，不删除原有路径。
- **一套实际学业数据账本**：继续使用已有 D1 `academic_connections`、`academic_verified_batches`、`academic_assignments`；不增加表、不迁移或删除用户记录。

## 已新增
- `GET /api/v1/connections`：私有、只读、需本人校序认证、`no-store`。由 `lib/academic-connection-center.mjs` 从现有 D1 按 `user_id` 读取，仅输出三平台连接状态与已保存数据范围，**不返回个人记录/学校会话/密码/令牌**。
- 学校平台统一卡片：华水青果教务、学习通、第二课堂。只提供固定、真实官方入口链接；**没有未经验证的“立即绑定”按钮或假自动同步**。
- 校序数据读取按钮只是读取私人 D1；不能将它解读为已成功登录学习通或华水青果。
- “清除此页私人数据”只清除当前网页缓存，**不是退出校序，也不是解除学校授权**。真正退出登录仍使用现有 `/login/` 的“退出当前设备”。

## 向后兼容
- 旧私人接口 `/api/v1/sources`、`/api/v1/verified/*` 不删除；已有个人任务、快照、历史迁移、课表、NFC、提醒、备份行为不删除。
- “官网连通性”只检查公开页面，不能把其 HTTP 响应等同个人账号连接。诊断工具保留在折叠的高级区域。
- 学习通 JSON 手动导入与 `xuexitong-cli` 本机桥接器原样保留，不能升级为自动同步证据。
- 账号认证在生产仍需确认 `ACADEMIC_AUTH_MODE` 和个人 D1 的真实状态。此 PR 不修改 Cloudflare Secrets、数据库 schema 或部署设置。

## 接入华水青果真正账号的下一道门禁
1. 用户在学校官方页面完成正常授权；确认当前 NCWU KINGO 的实际只读授权可用路径，且能绑定到校序本人的身份。不能把学校密码或 Cookie 上传到 GitHub。
2. 利用既有 `lib/academic-upstream-bridge.mjs` 对齐真实课表的学期、周次、节次；成绩和考试遵守严格字段契约。开源实现仅供适配参考，须核查许可证与学校实际版本。
3. 若得到学校许可且可以安全、可撤销地托管授权，再补 `POST /api/v1/connections/kingo/start` 的真实授权流程；当前只提供只读状态接口，不宣称学校支持 OAuth 或无人值守同步。
4. 再次验收失败/过期、部分分页、零条、跨账号隔离、数据库无绑定、手机端和生产环境。不满足则继续显示未连接。

## 自动化验收
- `scripts/xiaoxu-unified-connections-tests.mjs`：个人导入不能冒充绑定、受信只读记录与登录区分、数据必须 owner scoped、匿名失败关闭。
- `tests/e2e/xiaoxu-academic-private.spec.mjs`：私人数据读取、页面状态和跨页面保留与清除。
- `tests/e2e/xiaoxu-academic-readiness.spec.mjs`：折叠的公共诊断仍能使用。
- `scripts/verify-release.mjs`：新增 `/api/v1/connections` 的匿名拒绝校验。

**完成定义**：只有前端和服务器测试、GitHub CI、真实 Cloudflare 发布和匿名保护均通过，才能声称本中心已上线；不得因代码提交自动宣称学校账号已连通。
