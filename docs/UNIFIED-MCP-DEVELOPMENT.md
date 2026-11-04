# 统一 MCP 开发环境｜校序、绘序、触序

> 2026-11-04。**开发环境配置，不是产品业务 API。** 本文件不会接入学校账号、不会新增 MCP Server、不会修改线上部署或凭据。

## 原则和现有设施

- GitHub 账号：`07xh`。校序：`07xh/dorm-os`，绘序：`07xh/engineering-drawing-study`。TapFlow 的确切 GitHub 仓库**尚未确认**，禁止误将其他项目当成 TapFlow 仓库。
- ChatGPT 已有 GitHub 连接时，直接复用它读取文件、Issue、PR、CI；不重复部署 GitHub MCP。
- IDE / Codex 使用两家**官方托管**远程 MCP：GitHub `https://api.githubcopilot.com/mcp/`；Cloudflare API `https://mcp.cloudflare.com/mcp`。不部署中转 Workers，不创建新的 Cloudflare 项目或数据表。
- 同一项目的功能实现遵循“先审查现有代码→复用上游开源及仓库已有模块→必要时补缺→测试/PR/线上逐级验收”。MCP 是工具接入协议，并不代替已有项目 API、学校合法会话、Android NFC/KernelSU 原生能力。
- 禁止把学校账号、Cookie、CAS 票据、Cloudflare Token、GitHub PAT、个人课表/HAR、密钥提交到仓库或发给聊天助手。官方 OAuth 在你自己的客户端完成授权；选择**最小权限**。
- MCP 服务器可以发起有副作用的 API 操作。写入、部署、数据库修改、合并 PR 等应由用户审查和确认；优先只读调研、独立分支、草稿 PR。

## 配置

这两个已核实仓库根目录中的 `.mcp.json` 为可移植的 GitHub Copilot / VS Code MCP 配置；均复用**同一**远程 GitHub 和 Cloudflare 服务，不额外运行本地 MCP 进程。

1. 在受信任的电脑上 clone / pull 原仓库并用 VS Code 打开。若你的 VS Code 使用 Copilot MCP，可在 MCP 服务器管理界面查看已识别的项目 `.mcp.json`；按界面授权 GitHub、Cloudflare OAuth。
2. 若已在 VS Code **用户级**配置这两个地址，不需要再增加重复的服务器。可使用用户级 MCP 配置统一覆盖三个项目；项目文件仅用于可移植的团队/跨设备配置。
3. Codex CLI/IDE 使用单独的用户级配置，不会因为仓库的 `.mcp.json` 就自动继承；可按其官方文档执行：
   `codex mcp add github --url https://api.githubcopilot.com/mcp/`
   `codex mcp add cloudflare-api --url https://mcp.cloudflare.com/mcp`
   `codex mcp list`
   连接远程 GitHub 是否支持自动 OAuth 取决于具体客户端；不应在代码里硬编码 PAT。
4. ChatGPT 中的 GitHub 应用已经可用；Cloudflare 自定义 MCP 若因 ChatGPT 套餐/工作空间权限限制无法写入，请使用受信任的本地开发客户端、官方控制台和既有 Actions，不要为了绕限制建转发服务。

## 不得跨越的真实验收门禁

| 项目 | 唯一可信主线 | 现成检查 |
| --- | --- | --- |
| 校序 Academic OS | 现有统一账号/数据连接中心；学习通真实作业同步→青果首次授权与真实课表→第二课堂 CAS 会话关联；本人数据与官网比对；重进后刷新 | `npm run check`；`npm run test:e2e`；原 `main` → GitHub Actions → Cloudflare Pages |
| 绘序 DraftFlow | 原 Issue #377、#372、#497 与 M0–M5、唯一 Geometry Scene / V5；真实 C1 整题证据门禁优先 | 现有 `check.yml` 和数学 / 几何 CI；Worker 构建以及 Production Active SHA 单独核对 |
| 触序 TapFlow | 复用当前 Android/Kotlin、NFC、KernelSU/SH、DORM OS URI 协议；真机 NFC→动作→回执验收 | 待定位原始仓库和真实 Android 构建流程，不建立空仓库 |

- 任何一次执行都必须区分：**代码已提交 / CI 通过 / PR 合并 / 部署成功 / 本人或实体硬件验收**，禁止混称“已接入”。
- 校序学校数据源、D1 生产迁移、绘序 Production Active 和 TapFlow Root 动作都需要额外的真实凭据/权限/设备验证。配置 `.mcp.json` 只完成开发工具的**连接声明**，不意味着这些业务能力完成。

## 安全与发布

- 配置文件只包含官方 MCP 端点，不含任何 token、环境变量或密码；只有在客户端完成 OAuth 后才有实际访问权。
- 校序 Cloudflare Pages 项目 `dorm-os`、现有 D1 和原 `.github/workflows/deploy-cloudflare.yml` 必须保留。
- 绘序既有 `engineering-drawing-study` Worker、`./dist` 白名单与手动确认的生产发布门禁必须保留。
- 变更必须在独立分支与 PR 中提交；**不为了验证 MCP 而触发线上发布，不新增并行账号系统或站点**。

官方参考：GitHub `github/github-mcp-server`、Cloudflare `developers.cloudflare.com/agents/model-context-protocol/cloudflare/servers-for-cloudflare/`、VS Code MCP configuration reference。
