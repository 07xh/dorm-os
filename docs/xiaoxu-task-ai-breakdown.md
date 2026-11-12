# 校序 Android｜任务拆解与可选 AI 建议

## 位置与数据边界

唯一任务中心 → 本机任务详情 → **分解成步骤**。

- 手动模式：每行填写一个步骤，每次最多 12 条，保存到同一 Room v5 的个人执行步骤表；可单独新增一步，父子归属与前置关系分开。
- 默认各步骤可并行；高级前置设置支持多前置，全部完成后才解锁。原有父任务的前置门禁继续生效。
- 步骤全完成后，原任务仍需用户确认结束；个人步骤不修改学校提交状态。
- 旧 DAG 的「前置进度」仍按真实祖先任务展示，不按标题猜测其为父子关系。
- JSON 备份 v4 同时保留步骤、依赖与账户范围；仍读取旧 v1/v2/v3，原地迁移不清空数据。不新增 D1 迁移或第二个数据库。
- 新编辑区接管旧拆解弹窗；AI 建议填入同一编辑框，用户确认后才保存为步骤。

## AI（默认关闭，切勿假装可用）

可选服务端：已有校序 Cloudflare Pages Function `POST /api/v1/task-breakdown-ai`，仅复用登录态校验。App 只有在用户展开「AI 分解建议（可选）」并点击「生成建议」后才向 **同一校序服务器** 发送所选**个人任务标题**和用户主动填写的**补充说明**。不上传其余任务列表、学校凭证、HAR、学习通 Cookie 或作业内容快照。

服务端仅在以下全部条件满足时调用 Workers AI：

1. 校序原有 owner session 有效；其他身份或跨站请求被拒绝。
2. Cloudflare Pages 当前环境存在名为 `AI` 的 **Workers AI binding**。
3. 显式设置环境变量 `TASK_BREAKDOWN_AI_ENABLED=true`。

模型：`@cf/meta/llama-3.1-8b-instruct`，固定于服务端，不允许客户端传自定义模型、API Key 或外部 URL。仅允许 `title`（120 字以内）与 `notes`（600 字以内），模型返回 JSON Schema `steps` 数组，服务端再次验证 2～12 条、单条 120 字以内、无重复和换行后才返回。用户必须**点击“填入编辑框”**，自行调整，再**点击“添加步骤”**，才写入 Room。服务端从不自动提交或变更官方状态。

如账号未登录、AI binding 未配置、功能开关关闭、模型返回结构错误或网络异常，均明示失败，不产生离线假建议。**Workers AI 可能产生调用费用**，不得在未确认 Cloudflare 账户费用与配额的情况下启用，也不要在 APK 中硬编码模型服务令牌。

## 上线门槛

- [ ] 最新准确 Git SHA 的 Node 测试、Android Kotlin/JVM、Android Room instrumentation、综合 CI 均成功。
- [ ] 在测试环境配置 Workers AI binding、开关后，用本人云端账号主动触发一次端到端 AI 建议；核对失败回退和隐私最小化。
- [ ] 真机：手动输入/AI 建议两种入口、编辑确认、保存后立即在「现在可做」看到第一步、完成后解锁下一步、Room 重启保留。
- [ ] 真机：旧 Room v1/v2/v3/v4→v5 迁移与本机 JSON v1/v2/v3/v4 备份恢复不丢记录，真实学习通、微信任务官方状态不受影响。
- [ ] 正式签名及 APK 校验、main 合并与发布需要独立验收。开发 PR / 隔离 QA 包不能冒充已安装正式版。

参考 Cloudflare 官方： https://developers.cloudflare.com/pages/functions/bindings/ （Workers AI binding）。
