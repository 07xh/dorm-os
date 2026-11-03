# 校序 M11：生产 D1 一次性初始化

本任务用于用户刚创建并绑定到 Cloudflare Pages `dorm-os` 的生产数据库 `ACADEMIC_DB`。
继续采用已有 GitHub Actions 和 Wrangler；Cloudflare Builds 无需开启。

## 自动运行

首次合并 `.github/workflows/bootstrap-academic-d1.yml` 到 main 会触发一次。
以后也可以在 GitHub Actions 中手动重新运行此工作流。

## 安全限制

1. 限制仓库为 07xh/dorm-os 且分支为 main；只读取 Pages production 的确切数据库绑定 UUID。
2. 从 Cloudflare D1 数据库列表以 UUID 严格匹配目标数据库，不猜测数据库名称，也不操作 preview 数据库。
3. 首先通过 SQLite 离线预演九份迁移；随后只读查询生产的表名及 schema。
4. 只有业务表为空才自动依次执行 0001–0009；完整已有结构不重复写入；存在任何部分或非预期业务表则拒绝修改。
5. 所有远程查询只读取 schema，不读取任何学生记录，日志不打印 Cloudflare Token、数据库 ID、姓名或学校会话。
6. 初始化完成后检查所需数据表、provenance 列及外键。任何一步失败立即停止；部分成功的库必须人工审计后才允许继续。

## GitHub Action 凭据

使用现有 GitHub Secrets CLOUDFLARE_API_TOKEN 与 CLOUDFLARE_ACCOUNT_ID。
现有 API Token 如只有 Pages 发布权限、没有 D1 读取/编辑权限，会失败且不执行迁移；此时需在 Cloudflare 重新赋予最小必要 D1 权限，更新 GitHub Secret。
不要把 Token 发到聊天或公开 Issues。

## 后续仍需用户亲自完成的授权

Cloudflare Access 必须只允许本人，并保护正式域名及其它可访问的预览/备用域名的所有 /api/v1/* 请求。
Pages Production 变量 ACCESS_TEAM_DOMAIN、ACCESS_AUD 应对应真实 Access 应用；没有已知本人 Access 策略不能自行猜身份或自动开放。
完成后本人登录 /api/v1/health，确认 database_ready。

这一步初始化数据库不代表华水第二课堂已建立长期登录，也不启用无人值守同步；自动同步必须另行验证本人合法读取与续期。
