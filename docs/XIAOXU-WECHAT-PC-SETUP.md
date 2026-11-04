# 校序 · Windows 微信采集端配置（App-first）

> 状态：本页仅用于**验证本人电脑上的本地读取环境**。不是校序账号连接教程；安装完成后不会自动将微信消息发送到校序，也不表示 App 已经具备微信同步功能。

## 产品边界

- **校序 App/网页**：统一入口，任务审核、日历、截止提醒、设备绑定、同步状态及用户设置都在这里。
- **Windows 电脑**：本人微信正常运行时，充当本地采集节点。复用 `syskuku/wechat-todo` 打包的 `wechat-cli-plus`；不再重新实现微信数据库读取。
- **校序后端**：后续新增个人设备授权、最小任务候选上传、幂等去重和待审核队列。未完成这些之前，绝不显示“微信已连接”。

## 电脑准备（Windows PowerShell）

需已安装 PC 微信、Node.js ≥ 18、Git、Python；安装器的 `wxcli.cmd` 直接调用 `python`，因此确认 Python 在 PATH 中。

```powershell
node -v
npm -v
git --version
python --version
```

先在电脑上**正常登录自己的微信账号**；再阅读上游仓库和安装器行为，确认能够接受本地微信数据读取及第三方依赖风险：

- https://github.com/syskuku/wechat-todo
- https://github.com/syskuku/wechat-todo/blob/main/bin/wechat-todo.js

```powershell
npx github:syskuku/wechat-todo
```

> 校序不依赖滴答清单，**不需要** `--token`；不需要把令牌或微信聊天记录发送到 ChatGPT。

安装器默认把微信工具链放到 `%USERPROFILE%\.wechat-cli\`，并且仅在 Windows 上自动尝试本地初始化。未能初始化不等于安装失败，需按工具提示检查微信是否运行、版本是否兼容。

### 本地验收（不包含云端上传）

```powershell
& "$env:USERPROFILE\.wechat-cli\wxcli.cmd" sessions --limit 10
& "$env:USERPROFILE\.wechat-cli\wxcli.cmd" history "你的课程群名称" --limit 20 --format text
```

第一条应返回可读的会话列表；第二条选择自己正常参与的课程群，确认历史消息时间与内容。此阶段不需要 AI Agent；如果希望体验上游现成的“更新待办”工作流，还需要在兼容的 AI Agent 中运行两个已安装 Skills。

若出现密钥初始化错误，请先确认自己已登录 PC 微信，并参考上游仓库的 `wxcli init` 排障指南。不要在聊天、Issue、日志里粘贴密钥、Cookie、群成员信息或完整原始聊天内容。遇到失败时，只反馈去敏后的错误类别即可。

## 后续接入校序的最小工作

1. 使用现有 `lib/academic-wechat-candidate-contract.mjs` 约束输入：设备 ID、批次 ID、稳定的不透明消息 ID、任务候选、日期与时间精度。
2. 新建专用个人设备绑定与私有导入接口；用户须在校序 App/网页中明确确认设备及开启哪些群的同步。
3. 电脑本地解析，仅上传已选群经筛选的任务候选。默认**不上传全部聊天记录**；原文优先本地保存，App 提供来源核验路径。
4. 服务端 D1 保存候选、审核状态、同步游标、日志；先让用户在校序 App 中审核确认，再进入原有 `academic_assignments`。
5. 电脑断网时不抹除已审核任务；状态显示最近成功同步时间和失败原因；重连后分页补拉，重复批次不能重复入库。
6. 真实验收通过后，才允许把 App 的设备状态从“未连接”更新为“已连接”；还需要单独检查系统级通知，现有站内提醒不等于 Android 推送。

## 已知差异与禁止误判

- 上游 `npx` **不是**校序接入命令：它只安装上游工具和 Skills/可选滴答 MCP。
- 上游 `wechat-todo` Skill 的固定 `sessions --limit 80` / `history --limit 300` 不能证明大群零遗漏。实际桥接必须引入分页、持久游标和边界回补。
- 校序现有 `academic_connections` 的 provider CHECK 不包含微信，因此不能把微信本地导入伪装成学校官方已验证会话；应设计独立的设备来源与候选表。
- 不允许将聊天数据采集范围默认为全部群、不得上传无关成员私聊；应提供选择/关闭/删除配置。
- 不依赖学校专门开放 API，但仍需遵守微信使用规则、隐私边界及用户对所采集数据的权限。

## 验收进度

- [x] 已核查上游安装器、Skills 和本地 CLI 命令
- [x] 校序已有 D1 任务中心、来源追踪、站内提醒可供复用
- [x] 建立候选消息最小字段契约（PR #173）
- [x] 用户在 Windows 电脑上确认 `sessions` 与一个课程群的 `history` 实测成功（没有上传群聊内容）
- [x] 设备配对/撤销、独立候选 D1 表、云端批量接收/幂等去重、待审核列表、显式接受/忽略接口已提交（PR #173）
- [ ] 0017 迁移在生产 D1 完整执行与验证，PR #173 经 CI 验收后合并
- [ ] App 原有设置/任务页面接入设备配对与审核 UI
- [ ] 已选群增量采集、可恢复的游标/分页、电脑端同步脚本和一键配置
- [ ] 真实设备凭据/课程群增量数据端到端验收及上线
- [ ] 手机端后台系统推送验收（既有站内提醒不能替代）

## 设备同步 API 草案（尚未上线）

- 使用现有校序本人登录：`POST /api/v1/wechat/devices`，JSON `{"name":"我的 Windows 电脑"}`。同源校序页面调用；响应的设备 token **仅展示一次**，D1 只保存 SHA-256。
- 使用刚绑定设备的 `Authorization: Bearer wxu_...`：`POST /api/v1/wechat/ingest`，按候选输入契约提交每批最多 25 条。不同设备/来源数据强隔离，不接受全量聊天、微信凭据或未经许可的群。
- 校序本人登录：`GET /api/v1/wechat/candidates` 返回个人待审核队列；`POST /api/v1/wechat/candidates/{id}` 仅在用户明确 `{"decision":"accept"}` 后写入**现有** `academic_assignments`；忽略为 `{"decision":"dismiss"}`。
- `DELETE /api/v1/wechat/devices/{id}` 需要校序本人登录和同源请求，随时撤销电脑同步权限。
- **请不要手动调用以上接口或粘贴设备 token 到聊天中**。待 App 界面及电脑端一键配对完成后，通过校序已有登录界面操作。
- CI 使用 SQLite 合成记录验证隔离、重放、去重与审批；**没有真实微信群内容参与 CI**。
