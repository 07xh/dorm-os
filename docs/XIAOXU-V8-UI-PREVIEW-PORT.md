# 校序 V8 · 已确认交互预览的真实网站移植（第一批）

## 范围
- 延续原生 HTML/CSS/JS，不改动 GitHub 仓库、Cloudflare Pages 架构、Storage schema 或旧路由。
- 使用用户认可的蓝白界面作为设计基线：顶部五项导航、缩放响应布局、蓝色首页 Hero、真实统计卡片、主页左右分栏、课程/任务/通知与数据连接。
- 设计 Tokens 独立到 `public/xiaoxu-v8.css`，保证样式后加载以逐步收敛 V3/V4/V7 遗留 CSS；后续才计划彻底移除历史重复规则。
- 标题“今日安排”、三日切换、真实本地课程卡片、任务操作和完整课表导航均保留。
- 主题切换直接复用 `state.settings.theme`、原 `persist()` 与 `render()`，不另外储存易冲突的主题偏好。
- 对课程、未完成任务、官方公告只显示现有本地或 API 获取记录。青果成绩/考试未连接时绝不伪造演示成绩。
- CSS 被纳入 Service Worker 预缓存：已安装的 PWA 离线界面不会缺少新样式。

## 第三方组件使用策略
用户确认的 UI 预览采用 Web Awesome 的交互规范及 Lucide 风格线性图标。本批改造针对当前**无 React/Vue 构建过程、离线优先**的实际网站，优先复用已有原生语义组件和内联 SVG。暂不强制加载 Web Awesome、Motion 或其他 CDN，以免破坏离线入口及引入额外运行时成本。组件实体与动画适配需在本批功能回归后再分批引入。

## 未修改
- `/api/notices` 官方通知获取、缓存与刷新逻辑
- 未经授权接入青果成绩/课表/选课等个人后端
- NFC、KernelSU、个人备份及课程任务 Storage 格式
- GitHub Actions 部署工作流

## 验收清单
- [ ] `npm run check`
- [ ] `npm run test:e2e`：包括新增 V8 UI 回归
- [ ] Android 实机检查 360/393 宽度滚动、中文排版、浏览器地址栏动态尺寸和深浅主题
- [ ] 公开通知断网状态与缓存状态辨识
- [ ] 合并后验证 Cloudflare 生产部署的具体 revision

> 自动 Chromium 验收不能代替一加设备真机 NFC/Root 验收；未接入的教务源不得宣传为已成功实时同步。
