# 校序 Academic OS · M1-A D1 学业存储与作业 API（2026-11-03）

## 本批边界
本批是**真实代码基础**，不是第三方登录已接通：新增 D1 学业数据表、Cloudflare Access RS256 JWT 校验、按用户隔离的手动作业 GET/POST API、真实 SQLite/鉴权测试。**不修改**现有 DORM OS 本地课表、任务、NFC、JSON 备份、UI 或公开的 `/api/notices`。

截至本批：学习通、青果、雨课堂的本人授权后台同步尚未实现；不能将导入 JSON 称为自动同步。此 PR 不自动部署数据库，也不开放生产 API，必须先配置 Access 和 D1。

## 安全配置（部署之前必须完成）
1. 在 Cloudflare D1 创建仅用于校序的数据库，执行 `migrations/0001_academic_core.sql`，为 Pages 项目添加 D1 绑定，**变量名必须为 `ACADEMIC_DB`**。预览/生产分别配置；迁移前保留数据库备份。
2. 在 Cloudflare Zero Trust 为学业私有 API 路径 `/api/v1/*` 启用 Access 应用，允许的登录身份仅限本人。按该应用配置 Pages 环境变量 `ACCESS_TEAM_DOMAIN`（形如 `example.cloudflareaccess.com`）及 `ACCESS_AUD`（Access 应用 AUD）。不把 Access 变量、Cookie 或 JWT 写入仓库。
3. 不得只在网页中隐藏入口而不保护 API。校序会验证 JWT RS256 签名、Cloudflare Access 的 issuer、audience、有效期和身份，再用 issuer + subject 的 SHA-256 作为用户隔离 ID。缺少配置返回 503，身份无效返回 401，数据库绑定缺失返回 503。
4. 私有接口禁止公共缓存和跨站 POST。不要给任何个人任务 API 配置公开 Cache Rules。数据库凭据不放浏览器本地存储。

## 新增 API
- `GET /api/v1/assignments?limit=50&offset=0&status=todo`：仅读取本人的作业；limit 1–100；status 可选 todo/in_progress/done。
- `POST /api/v1/assignments`：仅创建手动作业，JSON 示例：
```json
{
  "title": "工程制图习题",
  "deadline": {
    "precision": "exact",
    "date": "2026-11-20",
    "at": "2026-11-20T23:59:00+08:00"
  },
  "localStatus": "todo"
}
```
- 只知道日期时用 `{"precision":"date","date":"2026-11-20"}`；不知道截止日期用 `{"precision":"unknown"}`。不得推断 23:59。
- `personalTargetAt` 是个人计划完成时间，独立于官方截止时间。
- 存在 `courseId` 时，必须先在数据库中拥有该课程；跨用户课程被拒绝。
- 返回 `201` 表示新记录已提交数据库，不表示第三方平台已提交作业。
- GET 响应当前为底层 D1 字段名，是 M1 接口初稿；正式与 UI 对接前需统一版本化 DTO。

## 数据设计
- `academic_users`：只保存经验证的 Access 用户标识摘要。
- `academic_connections`：连接器状态，不保存第三方密码或 Cookie。
- `academic_courses`：课程。
- `academic_assignments`：任务本体、个人状态、截止日精度和个人目标时间。
- `academic_assignment_sources`：平台来源与稳定 external ID；个人状态与平台状态完全分离。
- `academic_sync_runs`、`academic_assignment_events`：同步审计与变化记录的表，**本批尚无数据源向它们写入**。

约定来源 ID 使用 `connection_id + external_id` 唯一。任务遗漏于部分读取结果时不得删除历史记录。导入适配器沿用 `xiaoxu-task-batch-v1` 契约，后续添加服务端同步合并逻辑。

## 验收与后续
- `npm run check` 自动运行 `scripts/xiaoxu-academic-db-tests.mjs`，实际在内存 SQLite 执行迁移，并测试日期、用户隔离、课程隔离、JWT 签名与失败拒绝。
- 原 Playwright 网页测试必须继续通过；本批不改变旧 UI 和本地存储行为。
- D1 是新数据库，旧浏览器数据**不自动迁移**。下一批必须提供带确认、幂等与回滚语义的本地备份导入，再接学习通只读同步，最后驱动现有 V8.1 UI。
- 生产验收需另查 D1 绑定、Access 策略、授权账户范围、API 真实持久化及跨设备隔离；CI 的模拟 JWT 不代表生产授权已完成。
