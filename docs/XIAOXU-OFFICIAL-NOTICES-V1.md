# 华水公开教务通知聚合 V1 · 开源复用记录

## 产品目标与真实限制

把华北水利水电大学教务处**公开通知公告、考试安排**作为只读信息源，接入校序已有「教务 → 通知」和首页，不嵌入第三方应用 UI，不需要学校账号密码。

**当前第一阶段使用 Cloudflare Pages Functions + 边缘缓存。没有部署 D1、没有个人登录会话，也不保证跨地区始终能够请求到学校服务器。** 请求失败时前端保留上次成功的浏览器快照并明确提示，不能显示“通知为零”或模拟通知。

| 来源 | 官方栏目 URL | 详情范围 |
| --- | --- | --- |
| 华水教务处通知公告 | https://www2.ncwu.edu.cn/jiaowu/jwdt/tzgg.htm | `/jiaowu/info/1008/<ID>.htm` |
| 华水教务处考试安排 | https://www2.ncwu.edu.cn/jiaowu/ksap.htm | `/jiaowu/info/1034/<ID>.htm` |

日期在列表通常只有月日，因此**不猜年份**。仅显示真实原文链接和学校标出的日期。官方考试公告 **≠** 本人考场安排。

## 开源发现与采用方案

- [notice-radar](https://github.com/Yang-Yin734/notice-radar) · MIT，参考配置化高校源、源级稳定 ID、去重、失败保留历史、限频与健康检查。其 Node CLI、Playwright 浏览器兜底不适合直接放入 Cloudflare Pages Functions，本期只采纳这些公开的架构做法，未复制代码。
- [rss-worker](https://github.com/ProfessorManhattan/rss-worker) · MIT，参考 Cloudflare Worker 抽取页面内容的部署方式。校序不提供任意网址抓取，避免 SSRF；只有固定的官方域名和文章路径。
- [cloud-reader](https://github.com/acviana/cloud-reader) · Cloudflare Worker/D1/Cron 参考架构。当前未确认项目根目录有独立 LICENSE，不复制其实现；后续做 D1/定时采集前再核对许可。

上述适配代码为校序重新编写；若后续直接复制任何 MIT 上游源码，应保留版权与许可文本；GPL/自定义许可另行隔离。

## 实现

- `lib/notice-feed.mjs`：固定来源列表、原文域名/路径检查、文本规范化、稳定 ID、去重，拒绝异常结构和不足两篇的伪成功。
- `functions/api/notices.js`：Cloudflare Pages Function `GET /api/notices`；仅两个官方源、18 秒超时、限响应体大小、编码嗅探（UTF-8/GBK）、独立来源失败处理；成功结果边缘缓存约半小时。
- `public/index.html`：「教务 → 通知」真实列表、通知/考试分类、原文链接、刷新；首页展示最多三条。无网络时显示明确状态及上次成功缓存。
- `scripts/academic-notices-tests.mjs` / `tests/e2e/official-notices.spec.mjs`：离线模拟两类华水官方文章；覆盖去重、伪造源、局部失败、过期缓存和无数据不显示假通知。
- `public/sw.js`：只缓存静态 UI；**不把 `/api/notices` 当静态资产**。
- 部署后务必确认 `/api/notices` 返回 JSON 而非 Pages SPA 回退 HTML，并检查真实华水官网访问权限；**本地 mock E2E 通过不等于已验收云端真源抓取**。

## 下一步
1. 如 Cloudflare 到学校不稳定，优先采用 notice-radar 的**GitHub Actions 定时采集 + 静态 JSON 发布**作为可维护备选，而不是添加绕过学校访问限制的手段。
2. 有真实通知数据后逐步增加已读/收藏、通知分类、自动刷新健康状态，之后再决定是否需要 D1 持久化。
3. 华水青果/学习通/二课**私人数据连接器**必须使用本人的合法授权与真实接口验证，不能从公告公开接口推断已经完成。
