# 校序 M12 · 单人登录上线步骤

本次选择 **Cloudflare Pages 原生 WebCrypto + D1**：现有站点无需引入额外框架或邮件服务，不对公众开放自助注册。已调研 Better Auth / better-auth-cloudflare，但该项目已有独立身份和学业数据模型，迁移现有 API 与会话模型会扩大本次切换的风险；M12 首先采用最小单人方案。

## 门禁与安全边界

- 默认不开启单人登录：未设置 `ACADEMIC_AUTH_MODE=owner_password` 时仍沿用原 Cloudflare Access JWT 校验，缺失 Access 配置继续 fail closed。
- 开启后所有调用现有 `verifyAcademicAccess` 的私有 API 都统一验证 `__Host-xiaoxu_session`，拒绝客户端自报 user ID、邮件、Access header。
- 只保存 Session 的 SHA-256；7 天固定有效期；服务端可撤销；Secure + HttpOnly + SameSite=Strict。
- 不支持用户自注册。本人口令只存于 Cloudflare Pages 加密 Secrets 的 `OWNER_LOGIN_SECRET`，**不能在 GitHub、HAR 或聊天中发送**。
- 单人登录限流：每 IP 15 分钟最多 9 次，全部来源总计 48 次；D1 原子计数。登录与退出严格同源。
- 现阶段为 **配置式单人口令认证**，并非邮箱验证码或 Passkey。需使用随机、独一无二的高强度口令，至少 20 字符。

## 生产步骤（按顺序）

1. 合并 PR 后，GitHub Actions `Upgrade XIAOXU Owner Auth D1 M12 (forward-only)` 会核对 Pages 生产 `ACADEMIC_DB` 与真实数据库 UUID，检查 0001–0009 schema，再执行 `0010`（如已存在则不重复）。
2. 等该工作流 **success**，再在 Cloudflare `Workers & Pages → dorm-os → Settings → Variables and Secrets → Production` 添加：
   - `ACADEMIC_AUTH_MODE`（Plaintext）：`owner_password`
   - `OWNER_LOGIN_SECRET`（Secret）：自选唯一随机强口令，20–256 字符。只在 Cloudflare 输入。
3. 触发正式生产重部署，让变量生效。
4. 打开 `https://dorm-os-dk9.pages.dev/login/`，输入你设置的口令。第一次成功登录会自动创建唯一 `local-owner-v1` 账号与服务端会话。
5. 验证 `/api/v1/auth/me` 返回 authenticated true；`/api/v1/health` 返回 database_ready；退出后两接口都不再允许读取私人信息；无痕窗口必须返回 401。
6. Cloudflare Pages 预览分支若包含私人数据，必须同样有独立的身份认证与安全配置；勿将生产 Secret 复制到不受信任的预览构建。

**当前不包含**学校会话授权或学校资料实时更新。旧 Access 账号的 userId 与本地单一 userId 不同，如旧方案已存个人资料，需要独立审核迁移，不能自动改写归属。

## 回滚

移除生产 `ACADEMIC_AUTH_MODE`（恢复之前 fail-closed Access 验证）并重新部署；不要删除 D1 表或重建数据库。撤销会话可通过 /api/v1/auth/logout。若口令泄露，换 Cloudflare Secret 并清理 Sessions 需要另外的管理任务，不能仅改口令就认为历史会话自动失效。
