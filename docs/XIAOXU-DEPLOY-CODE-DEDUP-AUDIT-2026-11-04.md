# 校序代码与部署去冗余审计（2026-11-04）

## 证据与范围

审查 main 提交 `e139df812d75c95b7897c5fafb30a6fd504ee055`，递归树 496 个条目；重点核对 5 个 GitHub Actions 工作流、Cloudflare Pages 发布门禁、生产 D1 迁移、前端 `index.html` / `sw.js` 资源引用、微信 Windows 同步与现有 CI 测试。

**范围说明：**这是针对高风险部署与重复静态缓存的静态审计；不能声称逐行验证全部 496 个条目。业务代码、个人数据和历史迁移均不因“看起来旧”而删除。

## 确定性去冗余与必须保留的触发

1. **必要部署保留，勿误删**：华水通知工作流使用仓库 `GITHUB_TOKEN` 向 main 推送数据，GitHub 官方明确此类 push **不会**触发新一轮 `on: push` CI。故已有的 `gh workflow run deploy-cloudflare.yml --ref main` 必须保留，且仅在 `archive.outputs.changed == 'true'` 时执行；Pages 手动调度分支仍执行 `npm run check` 和完整 E2E。已补注释和防回退测试。此步骤不属于冗余部署。
2. `bootstrap-academic-d1.yml`：生产数据库已存在、校序 D1 迁移正常。历史“仅空库初始化”保留**手动灾备**，移除修改该工作流文件就自动触发生产初始化的 push 入口。
3. `upgrade-owner-auth-d1.yml`：0010/0011/0012/0015 等历史前向迁移保留人工灾备脚本，移除修改历史 SQL/工作流即再次启动生产升级的 push 入口。不得把历史迁移脚本从仓库删除，否则可能破坏已有部署的审计与重建能力。
4. `public/sw.js`：主页引用版本化 `/xiaoxu-owner-auth.css?v=98-profile-styles`，离线 precache 之前同时缓存了此 URL 和未带查询串的同一 CSS；删除未被主页引用的重复预缓存条目（保留源 CSS 与版本化资源）。

## 必须保留，不能误删

- `check.yml`：单一主分支代码/浏览器质量门禁，含 Windows 微信调度校验。
- `deploy-cloudflare.yml`：CI 通过后唯一正式 Pages 发布入口；具备 SHA 校验、D1 迁移、HTTPS/PWA 版本核验。手动 dispatch 自带 E2E 验证，仍是人工恢复路径。
- `collect-ncwu-notices.yml`：华水公开通知确有正在使用的低频定时采集及持久化 JSON，保留采集/写入流程、条件化显式部署及必要的 `actions: write` 权限。
- `workers/academic-cron/`：学习通 Cron 是独立业务同步，不是第二个 Pages 发布器；是否正常读取本人作业需单独验收。
- `functions/api/v1/wechat/`、`integrations/wechat/`：设备配对、候选上传、审核、Windows 同步均在现有项目中，不能再制造第二条采集或定时任务。
- `migrations/0001_*.sql` 到 `0017_*.sql`：生产数据库结构历史必须可追溯。绝不删除生产 D1 表、任务、凭据或迁移记录。
- `public/login/index.html`、`public/xiaoxu-owner-auth-ui.js`：独立登录入口仍有引用；不因 UI 多层 CSS 就删除核心登录逻辑。
- `public/index.html` 中的 NFC/旧本地存储与备份：README 明确存在兼容要求，后续如要下线必须独立做迁移方案和真机回归，而非直接删。

## 质量门禁

- `npm run check` 及现有 `npm run test:e2e` 不得回退。
- `scripts/xiaoxu-deploy-workflow-tests.mjs` 新增检查：通知 bot push 后必须保留按变化条件执行的 `workflow_dispatch`（因为 `GITHUB_TOKEN` push 不触发 CI）；历史 D1 工具只能手动触发。
- `scripts/xiaoxu-d1-bootstrap-tests.mjs` 改为校验只允许手动初始化。
- `scripts/xiaoxu-sw-cache-tests.mjs` 保证预缓存资源以 URL path 去重。

## 未处理的潜在重构

`public/index.html` 含本地旧 DORM OS 兼容、校序课程/任务、微信审核等功能，属于大型集中页面。应独立进行按功能拆分与状态保持 E2E，不适宜和部署清理同 PR 执行。旧文档包含历史要求，当前先保留审计轨迹，不应批量删除。

参考：[GitHub 官方文档 — 从工作流触发工作流](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow)。此规则是保留通知显式发布入口的重要依据。
