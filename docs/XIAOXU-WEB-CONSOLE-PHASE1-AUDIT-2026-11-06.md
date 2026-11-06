# 校序 Web 工作台｜第一阶段审计与迁移边界（2026-11-06）

> 执行依据：根目录 `AGENTS.md`、GitHub `main` 代码与 #30 的 App-first 主线。首次审计基线 `main@c403eb5a`。本文件是迁移清单，不是新增后端/学校平台接入的完成证明。

## 一、本轮真正交付的变更

- 现有首页“近期待办”增加“审核消息”快捷入口，指向 `#review`。
- `/#review` 是既有 `tasks` 页面 `taskFilter='review'` 的 Web-only 别名，不新建消息存储、任务服务、学校授权或登录器。
- 从 `#review` 切回普通任务时 URL 回到 `#tasks`；刷新 `#review` 仍进入待确认视图。
- 与现有 `/#tasks`、`/#timetable`、`/#settings`、`/#nfc`、`/#devices` 和携带 `dormTag` / `dormScene` 的历史入口保持兼容。
- App 原生代码、D1 迁移、Pages Functions、Worker、学校来源 API 均未改动；无生产数据迁移或学校本人验收。

## 二、源码与产品归属清单

| 现存功能 | Web 实现 / 入口 | 真实数据归属 | 下一阶段处理 |
| --- | --- | --- | --- |
| 首页、旧五导航 | `public/index.html`、`xiaoxu-workspace.css` | 浏览器本地 + 可选可信云端视图 | 先重建信息架构，不直接删除旧页面 |
| 旧本地课程及 ICS 日历 | `public/index.html` 的 `timetable`、导入逻辑 | **浏览器** `dorm-os-state-v1` | 保留导出/兼容入口；不当成 App 本机课表 |
| Android 课表 | `apps/xiaoxu-android/.../LocalScheduleStore.kt` | **Android 本机**，不是 Web/云端课表 | 不增加隐式上传或云同步 |
| 浏览器本地任务/生活/NFC | `public/index.html` 的 `tasks`、`life`、`nfc`、`scenes` | 浏览器 `localStorage` | 提供备份与数据确认，迁移后才收缩导航 |
| Android 本地任务 | `apps/xiaoxu-android/.../LocalTaskDatabase.kt` | **Android Room** | Web 不应虚报已同步或可读 |
| 云端任务 | `public/xiaoxu-cloud-tasks.js` → `/api/v1/assignments` | D1 个人云端工作区 | 保留 API 和来源标签；桌面批量操作另行设计 |
| 微信候选审核 | `public/xiaoxu-wechat-sync.js` → `/api/v1/wechat/candidates` | 账号隔离的云端候选，人工接受后创建任务 | 以现有任务页为唯一审核真源，逐步增强大屏交互 |
| 学习通候选/作业 | `public/xiaoxu-chaoxing-inbox.js`、`xiaoxu-chaoxing-homework.js` | 经本人验证的来源与快照（可能不完整） | 必须区分收到消息、本人完成、官方已提交 |
| 青果 / 二课 / 学习通连接 | `xiaoxu-academic-client.js`、`/api/v1/connections/**` | 独立学校来源会话、可信个人快照 | 同一连接中心复用；不能新建 Web 专用连接器 |
| 官方公共通知 | `xiaoxu-notices.js`、既有 notice API/JSON | 官方公开信息及浏览器本地已读状态 | 保留来源、时间、真实空/失败状态 |
| 校序本人登录 | `public/login/index.html`、`xiaoxu-owner-auth-ui.js` | 服务端私人会话 | 保留服务端权限；不可用本地登录提示替代认证 |
| Android 授权回跳 | `xiaoxu-android-return.js` 及既有 App 请求 | 本次授权的受约束临时状态 | 不删除，不把回跳视作平台数据核验 |
| PWA / 离线 | `public/sw.js`、`revision.json` | 发布版本缓存；非私人 API 数据源 | 继续测试缓存更新与离线回退 |

## 三、逐阶段处理（不得提前越界）

**保留（本阶段）：** `public/index.html` 既有页面和哈希、`public/login/`、学校连接器、所有 `/api/v1` 及 D1 数据、浏览器本地状态字段、NFC 实体标签兼容、当前 App 本机记录。

**候选迁移（后续 PR）：** 先拆出网页菜单/路由/表格渲染，逐页迁移概览、任务、消息审核；抽取复用 UI 组件，维持同一个数据服务与既有 API。大屏批量操作必须复用已有候选/任务契约，新增写接口之前先证明现有接口不足。

**候选收缩（需数据迁移验收后）：** 旧生活场景、天气组件、浏览器本地课表、NFC 页面从校序主导航逐步移至兼容工具区；确认旧链接和备份还可用后再考虑删除。不得按名称（legacy、v4 等）直接删源码。

## 四、数据迁移保护与验收门禁

1. 对 `dorm-os-state-v1` 的每个字段建立使用方和恢复方案，提供 JSON/加密备份实际回放。**不修改本地存储键或 schema**，直至有迁移测试。
2. 使用两个虚拟账号覆盖：退出/切换后不会回填前一账号的云任务、微信候选、连接状态；不在前端持久化学校密码、Cookie、Ticket 或设备凭据。
3. 保留任务来源、本人完成状态、平台官方提交状态三者的语义，不将浏览器课程视作 App 课表。
4. 维护 `/#review` 直达、页面刷新、返回 `/#tasks`、旧 `/#devices` 兼容，以及未登录状态不读取私人微信候选的 E2E 回归。
5. 改版只能在独立 PR 通过 `npm run check` + Playwright 桌面/手机测试后合并；主分支 CI、生产 SHA、旧 PWA 升级是后续独立验收。**PR 成功不等于已部署。**
6. 三平台的本人数据闭环仍按 `AGENTS.md` 与 #30 的 P0 主线单独验收；不得因为 Web UI 变更将其标记成功。

## 五、下一批可提交范围

- **Web Shell 试点：** 不变更 API 的情况下，把菜单及导航状态从内联大脚本移到一个可测试的 Web 模块；确保 `#review` 等旧路由兼容。
- **消息审核工作区：** 先做来源筛选、原文对照与单条确认 UX；批量审核的写端点和幂等策略单列设计，不预先伪造。
- **历史数据保护：** 为浏览器旧状态加最小化可测的导出/导入回放，明确迁移前后的校验字段。
