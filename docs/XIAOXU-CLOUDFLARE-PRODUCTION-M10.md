# 校序 M10｜Cloudflare 生产上线审计与本人授权闭环

> 本文档为**实施记录/安全验收步骤**，不代表生产环境已完成配置。
> 不创建第二个 Pages 站点，也不重启 Cloudflare 自动 Builds；
> 沿用已有 GitHub Actions + Wrangler 发布 `dorm-os`。

## 这次加入的可运行生产检查

- `GET /api/v1/health`：**仅 Access 有效的本人**可以调用。
  使用 D1 `sqlite_master` 和表结构，判定 M9 所需数据库是否齐全。
  如果缺少 `ACADEMIC_DB` 或数据库表，明确返回 503，不返回任何个人记录。
  `schoolConnection:not_verified` 和 `automaticSync:false` 不会因 D1 就绪改变。
- `scripts/audit-cloudflare-academic-production.mjs`：在 GitHub Actions
  使用已有 Cloudflare Secrets 调用官方 Pages **只读项目配置接口**，
  核对生产绑定名称 `ACADEMIC_DB`、变量名称
  `ACCESS_TEAM_DOMAIN` / `ACCESS_AUD`、生产分支 `main`、
  Pages Functions 是否启用。**不会在日志打印变量值、数据库 ID 或 Token**。
  若 Pages API 权限不足，报告“无法核验”，不误判为没有配置。
- 现有网站发布门禁新增匿名拒绝 `/api/v1/health`；
  生产绑定审计采用 `continue-on-error`，所以静态站发布成功
  不会因未配置个人服务就被误报为部署回滚。
- 上述项目配置检查**不是** D1 迁移检查，也不能证实 Access 策略已经
  只允许本人、学校登录有效或后台自动同步已启用。

## Step 1：查 GitHub Actions 发布实际状态

进入仓库的 `Actions → Deploy DORM OS to Cloudflare Pages`。
当前合并的新代码应触发 `main` 的部署任务：
- Verify：Node/SQLite/Playwright 检查成功。
- Deploy：Wrangler 发布成功、线上不可变 `revision.json` 与当前 `main` SHA 相同。
- 新的 `Read-only audit of Academic OS...` 步骤会在执行摘要报告缺失配置
  （若 Cloudflare 项目读取权限不足则显示不可核验）。
- 检查自己的生产和独立部署域名两处页面，切勿仅依据 GitHub CI 判定上线。

若站点未发布，先查 `CLOUDFLARE_API_TOKEN` /
`CLOUDFLARE_ACCOUNT_ID` 已有 Secrets 的**是否存在**及权限；
不要复制密钥到聊天或公开日志。

## Step 2：绑定 D1（先看是否已有）

在 Cloudflare → Workers & Pages → 原 Pages 项目 `dorm-os`
→ Settings → Bindings，检查 **Production** 环境有名为
`ACADEMIC_DB` 的 **D1 database**。如果缺少：
- 先到 D1 列表确认是否已有校序数据库，避免重复新建。
- 如无，则新建一份仅个人使用的 D1，生产和预览使用不同数据库。
- Pages 添加绑定 `ACADEMIC_DB` → 选正确数据库 → 保存 → **重新部署**。
  仅创建 D1 不等于 Pages 自动绑定完成。

## Step 3：检查迁移，备份，然后仅补缺失版本

在拥有 Cloudflare 账号的可信本地环境中，从仓库根目录执行，
将 `ACTUAL_D1_NAME` 替换成 Cloudflare 上的实际数据库名称：
```bash
npx wrangler d1 list
npx wrangler d1 execute ACTUAL_D1_NAME --remote --command="SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
npx wrangler d1 execute ACTUAL_D1_NAME --remote --command="PRAGMA table_info(academic_verified_batches)"
```

**已有个人数据的数据库先备份**到本地私有目录；完整备份可能包含
私有学习记录，不能提交 Git、上传 CI artifacts 或聊天：
```bash
npx wrangler d1 export ACTUAL_D1_NAME --remote --output=./private-academic-before-m10.sql
```

确认现有迁移状态后，仅对**尚未执行**的迁移按编号顺序执行：
`migrations/0001_academic_core.sql` 到
`migrations/0009_ncwu_second_class_official_scores.sql`。
例如已执行 0001–0008，才运行：
```bash
npx wrangler d1 execute ACTUAL_D1_NAME --remote --file=./migrations/0009_ncwu_second_class_official_scores.sql
```
不要对已有数据库重复执行包含 `ALTER TABLE` 的迁移；不要在
不知道当前表结构和备份是否可用时批量覆盖。先对**独立预览库**
执行相同迁移，再处理生产。

## Step 4：Cloudflare Access 本人登录

使用 Cloudflare Zero Trust 建立或核对 **仅本人**可进入的 Access 应用，
覆盖正式自定义域名、`dorm-os-dk9.pages.dev`、独立部署/预览域名中的
所有 `/api/v1/*` 私人请求。若不能用单一应用覆盖，分别配置，
并确认没有能绕过身份验证的备用域名。

Pages → Production → Variables and Secrets：
- `ACCESS_TEAM_DOMAIN`：当前自己的 `*.cloudflareaccess.com` 团队域名。
- `ACCESS_AUD`：**保护校序 API 的 Access 应用** Audience。
  多个域名对应不同 Access 应用/AUD 时，必须先设计一致的签发/验证策略，
  不能仅靠 UI 隐藏链接。
- 保存后重新部署，验证签名 JWT 通过服务器校验。
- 不将 Access JWT、Cookie、个人学校身份标识放进公开仓库。

## Step 5：通过真实 API 判断是否部署完成

未经授权打开私有 API，必须被 Access 拦截或返回 401/403/503，
绝不可返回带私有记录的 200/HTML SPA 回退。

本人在 Access 正常登录后：
- `GET https://dorm-os-dk9.pages.dev/api/v1/health`
  - `status=database_ready` 且 `databaseReady=true`：已验证
    **本人登录、D1 绑定和 M9 最低数据库结构**。
  - `status=database_not_configured`：没有绑定 D1。
  - `status=database_schema_incomplete`：看 `missingTables` 和
    `sourceProvenanceColumn`，仅补缺少的迁移。
  - `status=auth_not_configured`：先修 Pages 的 Access 变量。
- `GET /api/v1/second-class` 返回 `not_connected`：
  **未完成学校授权**时属于正确状态，不能因此认为 D1 失败。

## Step 6：学校本人授权读取闭环（仍待实际对接）

原始 HAR 已确认华水第二课堂四个只读 POST 请求路径和字段，但
**HAR 的会话凭据不等于可长期使用的官方授权**。生产阶段需要：
1. 在本人控制的可信客户端/服务端完成学校正常登录，
   将当前校序 Access 身份严格绑定到学校本人身份。
2. 仅复用已确认的只读查询：
   `/sys/stu/center/signupactivity/list`、
   `/sys/stu/score/list`、`/sys/stu/score/types`、
   `/sys/activitytype/all`；不执行报名、签到或修改。
3. 本人一条活动、学校“素质分”合计、志愿服务时长与官方页面一致，
   分页完整，无其他账号数据；凭据/授权过期时不会冒充继续同步。
4. 此后才对具有持续合法授权、适合定时访问的接口启用 Cron。
   若只有前台手动登录，就维持“重新登录后同步”，不能承诺无人值守。

## 验收状态

`PR #74` 的代码已合并并通过 CI；**这不能证明 Cloudflare 生产 D1、
Access、学校会话/续期和定时 Worker 已配置好**。以本轮 GitHub
部署摘要、本人 `/api/v1/health` 实际返回及学校页面对照为准。
