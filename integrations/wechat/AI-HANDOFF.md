# 校序 · Wechat-Todo 原生 AI Skill 适配试验（先手动验收）

> 这是 **本地 AI → 校序候选审核** 的最小闭环，不是已上线的定时 AI。
> 不替换现有 `collector.py`、`wechat-cli-plus`、Cloudflare D1、登录或任务中心。
> 本分支尚未通过真实个人微信、模型服务或 Windows 真机验收。

## 复用边界

上游 [syskuku/wechat-todo](https://github.com/syskuku/wechat-todo) 的 `wechat-todo` Skill
已经定义任务判断、改期/作废、旧待办衔接、去重、日期纪要与日报等 AI 工作流程。
这些是 **Agent 的指令和工具调用工作流**，不是可直接 `import` 的 Python 分类器。

校序继续使用已安装的 `wechat-cli-plus` 0.2.4 和原有采集器，只为本人主动选择的群
生成本地、脱敏的 Agent 输入。对上游 Skill **复用分类和整理规则，不直接运行其全量扫描**
（上游默认包括新会话/私聊）或滴答 MCP 操作；不自行开发第二套关键词/LLM 分类器。

1. 在 Windows 以本人账户正常登录微信，先按
   [Windows 设置](../../docs/XIAOXU-WECHAT-PC-SETUP.md) 完成设备绑定及手动 `preview`、`sync` 验收。
2. 若计划任务已经开启，试验前通过
   `.\integrations\wechat\schedule.ps1 remove` 暂停普通自动上传，避免 AI 快照后游标变化。
3. 在仓库根目录执行：
   ```powershell
   & .\integrations\wechat\run.ps1 ai-prepare
   ```
   产生 `%USERPROFILE%\.wechat-cli\xiaoxu-ai-input.json`。
   **只读本地群消息，不向 D1 上传或推进游标**；范围限于当前配置已选群。
4. 用已经安装原版 `wechat-todo` Skill 的兼容 Agent，采用
   [AI Agent 指令模板](./AI-AGENT-PROMPT.md) 处理这份本地快照。
   Agent 只写 `%USERPROFILE%\.wechat-cli\xiaoxu-ai-decisions.json`。
   AI 是否走云端、处理政策、账号费用由你使用的 Agent/模型决定。运行前确认只处理本人有权使用的消息。
5. 本机检查 AI 生成的文件；先执行无网络、无游标变化的验收命令：
   ```powershell
   & .\integrations\wechat\run.ps1 ai-check
   ```
   只有确认签名、范围、任务计数和截止时间都合理，再继续上传。
   不要把群消息、消息快照、密钥或任务输入复制到 GitHub Issue 或对话中。
6. 执行：
   ```powershell
   & .\integrations\wechat\run.ps1 ai-submit
   ```
   仅接受本次本机 HMAC 签名快照中的消息 ID；AI 不能直接添加其他群、伪造消息 ID 或改写设备凭据。
   上传沿用现有 25 条/批、云端去重与人工审核。**只有所有批次经校序服务端确认，才推进原同步游标。**
7. 在校序「任务 → 微信待审核」逐条核对并确认。没有自动把 AI 判断当成官方完成状态。

## JSON 契约

输入的顶层字段：`schema`、`createdAt`、`deviceId`、`selectedGroups`、
`baseCursors`、`nextCursors`、`messages`、`signature`。

`messages` 每条包含 `messageKey`、`chatKey`、`chatLabel`、`sentAt`、
脱敏 `text` 和经过安全筛选的 `links`。不包括发送人姓名、明文消息数据库路径、微信密钥或校序设备 Token。
群名称和剩余文本仍可能属于个人信息，**不可将该文件公开分享**。

Agent 输出示意（以下 ID/日期仅为形状示例，不用于实际同步）：

```json
{
  "schema": "xiaoxu/wechat-ai-decisions-v1",
  "snapshotSignature": "<从本次输入文件的 signature 原样复制>",
  "tasks": [
    {
      "messageKey": "<引用 messages 中实际的 messageKey>",
      "slot": 0,
      "kind": "homework",
      "title": "提交实验报告",
      "deadline": {"precision": "date", "date": "2026-11-12"}
    }
  ]
}
```

- `slot` 取 `0,1,2`，同条消息多个独立任务可分开，同一对 `(messageKey, slot)` 不能重复。
- `kind` 仅限 `homework / exam / activity / signup / other`。
- `deadline` 必须是 `{"precision":"unknown"}`，或带 `date` 的 `date`，
  或带 `date` 和 `at`（带时区 ISO 时间）的 `exact`。
- 只有确有时间证据才使用 `exact`；不推测日期、完成状态、作者身份、用户责任范围。
- AI 输出不能携带 `links`、`excerpt` 或其他新增字段，避免模型将私密聊天写入云端。
- 本地文件超过 48 小时、签名不符、来源或游标改变、重复 slot 或格式不合法时一律拒绝，重新生成输入。
- 如果没有可确认的任务，可返回空 `tasks`；校序仍进行无消息心跳验证。

## 特别注意

- 首次导出最多 5000 条脱敏文本；超过安全上限直接失败，游标保持不变。
- 当前阶段仅支持文字消息，图片、语音、文件内容需日后单独设计授权与核验。
- 模型判断「作废」「已完成」「改期」尚不自动改动既有正式任务：
  避免把误判覆盖本人已审核或已完成状态；先在 Agent 本地报告，后续专门增加人工审核的变更提案。
- 本地输入采用 Windows 用户目录下的普通 JSON 文件，不是加密文件；试验后成功上传会尝试删除输入和结果。
  如使用云端 Agent，必须先自行核对第三方服务的数据处理方式及消息授权。
- 原有规则同步的 Windows 计划任务现已具备**北京时间 07:00–23:00 / 每 15 分钟**窗口守卫：
  通过 `schedule.ps1 install` 由本人启用；当前 AI Skill 整理仍是**单独的手动试验**。
  在 AI 试验期间应先移除规则同步计划任务，避免两种流程争用游标。
- 自动 AI Agent 调度及 **23:10 AI 日报** 尚未实现，不可宣称无人值守或已发布；不要求为了 AI 安装 Docker/n8n。
- 保持单一任务库 `academic_assignments`，不新增 NextTodo、滴答清单或第二套同步状态。
