# 校序仓库整理：Android 构建请求归档与开发工具边界

基线 main：`4bb1805f8b896a7764b685fa654d889e10a09680`（2026-11-05）。本轮是仓库维护，不改变 Android UI、学校连接器、D1、Web 页面或生产数据。

## 2026-11-11 更新：历史请求可以安全归档

以下新规则覆盖本文旧的“删除会触发打包，因此暂不清退”限制。构建工作流已有只读请求方向门禁：仅 PR 合并基准之后 / main 本次 push 中**新增或修改**的 pending 顶层 `.txt` 才构建；归档和删除跳过编译、签名、发布。Git 范围不可核验则停止，不以失败回退成构建。

- 新请求继续放原 pending 目录；普通代码改动不申请 APK，manual workflow_dispatch 继续可用。
- 旧请求无损移动到带日期的 archive，保留 Git blob 和原说明，不放回 pending。
- PR 用完整 merge-base 范围；落后于 main 的分支不会重新激活 main 已归档的旧请求。
- 不新增自动发布路径。原有 main CI、签名与发布门禁保持有效。

下方 2026-11-05 内容保留为当时的整理记录，涉及旧分支、工作流名称和删除触发行为的描述以本节及当前源码为准。

## APK 构建触发规则

- 手动：GitHub Actions → **Build Xiaoxu Android Alpha** → `workflow_dispatch`。
- 审核请求：新建**唯一文件名** `.github/xiaoxu-apk-build-requests/pending/<YYYYMMDD-purpose>.txt`，在 PR 审核后合并 main。只匹配 `pending/*.txt`；不能因为普通 Android 代码推送自动构建。
- 历史请求：原 `.github/xiaoxu-apk-build-requests/*.txt` 的 12 个文件**按原 Git blob SHA 无损移动**到 `docs/archive/xiaoxu-apk-build-requests/2026-11-05/`，保留文件内容以供审计。
- **不得**把 archived 文件放回 `pending/`；单纯修改归档或本次清理不应该触发 Android APK 构建。已审核的新请求不需要在合并后立刻删除，避免删除本身匹配触发条件。
- Workflows 的 `push.paths` 本身不能识别新增与删除方向；不要将匹配模式恢复为整个请求目录 `/**`。如要做请求清退，另行设计不触发构建的独立方案。
- 构建仍执行 `:app:assembleDebug :app:testDebugUnitTest`、校验 APK ZIP/DEX 和 SHA-256，artifact 保留 14 天；历史归档**不保存 APK 文件**。

## AI 设计技能边界

- `.agents/skills/design-taste-frontend` 和 `.agents/skills/impeccable` 是**有意引入的开发工具**，而不是 App 安装包的一部分。现存 `.claude/skills/` 符号链接依赖它们，因此不得整目录删。
- 清理仅剔除设计技能的 benchmark `evals/evals.json`（与产品功能及技能日常指令无关），保留原 SKILL、脚本、字体数据、素材库和第三方许可证。
- Git 不再收录 `.taste-skill/`、`.impeccable/` 等本地生成结果。第三方精确版本及验证入口见 `docs/UI-DESIGN-SKILLS.md`。

## Kotlin import

- 只移除扫描确认无源码引用的普通 import；**保留 `getValue` / `setValue`** 代理属性操作符 import，避免 Compose `by` 属性编译失败。
- 避开进行中的 PR #284 与 #279 修改的 UI / CloudClient 文件，确保不与并行开发冲突。

## 验收与回滚

- 先过 `npm run check`、`npm run test:e2e`，必要时再以手动工作流验证 Android Gradle 编译/单测；未经过实际 Android 编译前不能声称该项通过。
- 回滚只需撤销本 PR；12 个历史构建请求在 Git 历史和归档中保留，不涉及用户数据迁移。
