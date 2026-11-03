# UI 设计 Agent Skills（仅开发环境）

将 [Taste Skill](https://github.com/Leonxlnx/taste-skill) 和 [Impeccable](https://github.com/pbakaus/impeccable) 以 Git 子模块固定到本仓库，供 Codex 与 Claude Code 使用。它们不是网站运行时依赖，不改变 Cloudflare 构建、API、D1 数据、登录状态或前端已部署产物。

## 启用

从仓库根目录执行：

```bash
git submodule update --init --recursive
test -f .agents/skills/design-taste-frontend/SKILL.md
test -f .agents/skills/impeccable/SKILL.md
```

完成后重启 Codex / Claude Code 会话；二者都可从项目本地 `.agents/skills/` 或 `.claude/skills/` 的符号链接读取完整上游技能（含 references、scripts 和 assets）。不要求全局安装，也不自动启用 Hook、Live 模式或绕过批准的命令。Windows 需使用支持 Git 符号链接的环境（推荐 WSL）；否则可在本地按上游 README 安装到所用 Agent 的 skills 目录。

## 版本和许可

- Taste Skill：固定 `12af471f627f866411b4bd785b19646d3672784c`，MIT；用于概念、产品视觉与界面重设计。
- Impeccable：固定 `9fadc41729c38fd47d7ae622b501558653f9d2e9`，Apache-2.0；用于 `critique` / `audit` / `distill` / `adapt` / `polish` 和可选检测器。
- 上游更新必须通过独立 PR 更新 Git 子模块的 gitlink，并重新检查兼容性和潜在脚本行为。不要自动跟随 main。

## 校序 / DORM OS 使用边界

1. 保留现有真实动态 Web 应用、后端与持久化；没有真实授权的数据必须显示未连接状态，不能把模拟内容伪装成实时数据。
2. UI 优化优先减少重复页面、冗余文案及多余按钮，保持蓝白设计、移动端触控和已有路由/API；不得因设计审查擅改业务逻辑。
3. 设计变更先比较真实移动/桌面截图；再运行 `npm run check` 和涉及页面的 Playwright 测试。
4. 可选运行 `npx impeccable detect <前端源码目录>` / `npx impeccable detect http://localhost:<端口>`，仅在开发机有网络/浏览器时进行；扫描结果不是业务正确性的替代品。
5. 不执行自动 Hook 或高权限 Live Apply；如需使用，先单独审查其执行权限与本地环境。

此文件仅记录项目内技能接入与使用约束；不代表已完成视觉改版或真实浏览器验收。
