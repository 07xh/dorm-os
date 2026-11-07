# 校序 · Windows 微信采集端配置（App-first）

> 状态：PR #173 的服务端、设备配对、候选审核已合并，生产 D1 0017 迁移、主分支 CI 和 Cloudflare Pages 发布均已通过；PR #176 的 Windows 用户明确启用的定时任务已发布。**本人电脑的首次上传 → 校序确认 → 任务审核尚无真实验收证据，不能宣称微信完全接入。**

## 产品边界

- **校序 App/网页**：统一入口，任务审核、日历、截止提醒、设备绑定、同步状态及用户设置都在这里。
- **Windows 电脑**：本人微信正常运行时，充当本地采集节点。复用 `syskuku/wechat-todo` 打包的 `wechat-cli-plus`；不再重新实现微信数据库读取。
- **校序后端**：已发布个人设备授权、最小候选上传、幂等去重和审核队列。只有服务端实际确认接收后才显示「云端已验证同步」，不会将仅配对视作已连接。

## 电脑准备（Windows PowerShell）

用户已安装 PC 微信、Node.js、Git；本人已验证 `~/.wechat-cli/py312-venv/Scripts/python.exe`（Python 3.12.15）与 `click>=8.1,<9`、`pycryptodome>=3.19,<4`、`zstandard>=0.22,<1` 可正常导入和启动 CLI。**运行采集桥接时只设置 `PYTHONPATH` 指向 `tool`，不得再加入旧 `deps` 或缺失的 `deps_x64`。** 原 `wxcli.cmd` 会选择默认的 Python 3.15，不适合当前机器。

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
$env:PYTHONPATH="$env:USERPROFILE\.wechat-cli\tool"
$python="$env:USERPROFILE\.wechat-cli\py312-venv\Scripts\python.exe"
& $python "$env:USERPROFILE\.wechat-cli\tool\entry.py" sessions --limit 10
& $python "$env:USERPROFILE\.wechat-cli\tool\entry.py" history "你的课程群名称" --limit 20 --format text
```

第一条应返回可读的会话列表；第二条选择自己正常参与的课程群，确认历史消息时间与内容。此阶段不需要 AI Agent；如果希望体验上游现成的“更新待办”工作流，还需要在兼容的 AI Agent 中运行两个已安装 Skills。

若出现密钥初始化错误，请先确认自己已登录 PC 微信，并参考上游仓库的 `wxcli init` 排障指南。不要在聊天、Issue、日志里粘贴密钥、Cookie、群成员信息或完整原始聊天内容。遇到失败时，只反馈去敏后的错误类别即可。

## 校序电脑桥接：生产迁移与 Pages 发布已通过，待本人真实首次同步验收

以下步骤不会安装第二个微信读取工具，也不会把 WeChat / 学校账号凭据提交到 GitHub：

1. 在校序现有「任务」页面展开「微信待审核」，登录校序本人账号后主动点「读取我的候选消息」。在「电脑设备管理」中填写电脑名称，点击「生成一次性设备凭据」。复制**设备 ID** 和 **一次性凭据** 到本人的可信电脑；不得发给任何聊天机器人或贴到 Issue。正式网页已发布，但是否实际连接成功应通过设备最近同步时间验收。
2. 在 Windows 上获取**合并并完成验证后**的 `main` 分支 `integrations/wechat/` 目录。`run.ps1` 使用本机 `~/.wechat-cli/py312-venv/Scripts/python.exe`，只加载 `.wechat-cli/tool` 源码；不再要求 `deps_x64`，不会改变默认 Python 3.15。修复处于 PR 阶段时，不得把旧 `main` 脚本当成已修复版本。
3. 首次从 PowerShell 在项目目录执行 `& .\integrations\wechat\run.ps1 setup`。网址默认为 `https://dorm-os-dk9.pages.dev`；按提示输入设备 ID 和凭据（凭据输入不回显），逐个输入允许同步的群名。默认**不选任何群**。敏感凭据通过 Windows DPAPI 绑定当前登录用户保存，绝不写入 PowerShell 历史。
4. 先执行 `& .\integrations\wechat\run.ps1 preview`，在**本机**检查候选数量和标题，不上传任何消息。确认分类可接受后，再手动执行 `& .\integrations\wechat\run.ps1 sync`。
5. 到校序「任务 → 微信待审核」主动刷新，逐项「确认加入待办」或「忽略」。只有确认的候选进入原有云端待办；状态绝不冒充学习通/青果官方已提交。
6. 电脑同步游标保存在 `.wechat-cli/xiaoxu-sync-state.json`；仅在云端确认**全部批次**后推进。重复批次服务端按不透明 ID 去重。电脑断线不会删除已确认待办。首次默认回看 72 小时，后续回补 24 小时，单群单轮最多 1500 条记录以防止无意间无限读取。超限会失败并保留旧游标。

### 零候选任务也必须验证设备在线

电脑采集器现增加云端确认机制：本轮若没有任何任务候选，仍会上传**空批次（0 条消息）**，验证设备 token、网络和 D1 后再推进本地进度；若被服务器拒绝，则本次不得显示同步成功。正常收到候选后也需要审核才进入现有任务中心。页面设备状态区分「已绑定未验证」「云端已验证同步」「超过 90 分钟未收到新同步」，不将电脑休眠或关闭时的旧状态冒充实时在线。

安全边界：电脑只向 `https://dorm-os-dk9.pages.dev` 发送设备授权请求；筛选候选时会遮蔽网址、电话、邮箱和长数字编号。**任务标题仍可能是群消息的短片段**，因此务必先在本地 `preview` 检查并只选择必要的群，不要把原始聊天发给第三方。

### 可选自动同步（首次手动验收成功后）

每 15 分钟调用现有 `run.ps1 sync`，不另安装微信读取器。电脑不登录或关机时不会执行。Windows 计划任务不保存明文设备 token，凭据仍由现有 Windows DPAPI 保护。**安装不会立即上传消息**。

必须先成功运行 `run.ps1 preview`、`run.ps1 sync`，并在校序确认候选任务已到达，再手动启用：

```powershell
& .\integrations\wechat\schedule.ps1 status
& .\integrations\wechat\schedule.ps1 install
```

关闭自动同步：

```powershell
& .\integrations\wechat\schedule.ps1 remove
```

断网时原有游标保留，重新登录 Windows 后按计划重试。删除计划任务只停止本机同步，仍需通过校序设备管理撤销云端设备授权。睡眠或关机期间不会采集微信消息。

**当前限制：**第一版仅分析纯文本、使用保守关键词和明确日期；无法保证识别所有课程任务，不会猜测相对截止时间，也不能识别图片、语音、文件或其他媒体中的作业要求。`run.ps1` 可以手动同步；Windows 定时任务需本人明确运行 `schedule.ps1 install` 才启用。电脑关机后不能读取新消息；App 系统级推送仍需单独开发。

## 上传 HTTP 403 的安全排查（无需重新选群）

如果本地 `preview` 已识别候选但 `sync` 返回 HTTP 403，先在仓库目录运行：

```powershell
git pull
& .\integrations\wechat\run.ps1 verify
```

`verify` 不读取群聊或任务，不改变本地同步游标，只向校序正式域名发送一条空记录授权验证请求。反馈只需要错误代码，不要提供设备 token、完整配置文件或聊天原文。

- `device_mismatch`：本地设备 ID 与所保存 Token 不属于同一次配对；核对网站设备管理，并撤销旧配对重新绑定。
- `forbidden_origin_or_format`：请求格式未通过服务端检查；核对校序电脑脚本是否为最新版。
- `HTTP 403，非校序已知错误`：可能是网络/Cloudflare 安全检查，需进一步核对云端策略。
- 验证成功后再运行 `run.ps1 sync`，按需刷新「任务 → 微信待审核」。

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
- [x] 0017 迁移生产验证通过、PR #173 合并、主分支 CI 与 Pages 发布通过（GitHub Actions #41790117278）
- [x] App 原有任务页面接入设备配对与审核 UI（已发布；仍需本人真机验收）
- [x] 已选群增量采集、可恢复的游标/分页、电脑端手动同步脚本（代码已提交，未本人真机同步）
- [x] Windows 任务计划程序可选安装/移除（PR #176 已发布，需在本人 Windows 机上测试）
- [ ] 本人电脑到云端的首次真实同步与审核验收
- [ ] 真实设备凭据/课程群增量数据端到端验收及上线
- [ ] 手机端后台系统推送验收（既有站内提醒不能替代）

## 设备同步 API（生产已发布，真实个人数据尚未验收）

- 使用现有校序本人登录：`POST /api/v1/wechat/devices`，JSON `{"name":"我的 Windows 电脑"}`。同源校序页面调用；响应的设备 token **仅展示一次**，D1 只保存 SHA-256。
- 使用刚绑定设备的 `Authorization: Bearer wxu_...`：`POST /api/v1/wechat/ingest`，按候选输入契约提交每批最多 25 条。不同设备/来源数据强隔离，不接受全量聊天、微信凭据或未经许可的群。
- 校序本人登录：`GET /api/v1/wechat/candidates` 返回个人待审核队列；`POST /api/v1/wechat/candidates/{id}` 仅在用户明确 `{"decision":"accept"}` 后写入**现有** `academic_assignments`；忽略为 `{"decision":"dismiss"}`。
- `DELETE /api/v1/wechat/devices/{id}` 需要校序本人登录和同源请求，随时撤销电脑同步权限。
- **请不要手动调用以上接口或粘贴设备 token 到聊天中**。待 App 界面及电脑端一键配对完成后，通过校序已有登录界面操作。
- CI 使用 SQLite 合成记录验证隔离、重放、去重与审批；**没有真实微信群内容参与 CI**。
