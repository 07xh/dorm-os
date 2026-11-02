# 校序 · 华水通知源（notice-radar 适配）

## 当前能力

- 真实原文：`public/data/notices.json` 首次包含经学校公开网页核验的文章链接、标题与发布日期，`mode: seed` 不代表已自动同步。
- 官方采集：`scripts/ncwu-notices.mjs` 按 `config/ncwu-notices.json` 的三条公开来源逐一抓取，解析符合官网文章路由的链接；源站失败、挑战页、空列表都不覆盖既有历史。
- 版本化持久化：采集任务保存 `public/data/notices.json`，Actions 只提交该公共数据文件；前台使用 `fetch('/data/notices.json', {cache:'no-store'})` 读取。**这是带后台定时采集的 JSON 档案，不是 D1/SQL 数据库，更不是个人教务账户数据同步。**
- UI：原校序教务二级导航下的通知、首页摘要、来源过滤、搜索、刷新；记录全部指向学校官方 HTTPS 原文；显示上次成功采集时间，超过 72 小时提示陈旧。
- 自动更新：`.github/workflows/collect-ncwu-notices.yml` 在首次合并后触发一次、之后每天北京时间 10:17、18:17、次日 00:17 执行；有效采集后更新数据并触发现有 Pages 部署。
- 自动验证：`npm run notices:test` 离线对合成网页 fixture 检查解析、去重、合法 URL、保存历史及失败保护；Playwright 在手机/桌面视口检查通知筛选、搜索、无网络回退与 HTML 转义。

## 尚须现场验收

1. Actions 首次运行必须确认至少一个官网真实列表抓到两条以上合法文章记录，且 `mode=auto`、`lastSuccessAt` 非空。
2. 打开 GitHub Actions 的 Collect NCWU official notices 与 Cloudflare Pages 部署记录，检查新数据的提交号及上线版本。
3. 无痕浏览器打开正式网站的教务/通知，确认标题链接、日期、响应状态和更新时间与官网一致；手机 UI 与 PWA 离线状态同样验证。
4. 若远端返回挑战页/乱码/空列表，按官网结构补正确的 fixture 与适配器，不绕过站点访问保护；当前合成 fixture 不能替代真实网页快照。

## 数据隐私与下一步

此采集不登录、不保存 Cookie/密码、学号、成绩或用户会话。页面使用官方链接白名单，定时任务低频运行；部署前需要遵守学校网站相关条款及 robots 规则。

未来如需真正的云数据库，可将 JSON 归档迁移到 Cloudflare D1，并由同一采集器通过写入 API 更新；不得把版本化 JSON 档案误称已经连接 D1。

## 第三方许可

此适配器借鉴 notice-radar 的 CSS 选择器采集、标题规范化、稳定 ID 和历史保护机制：

- 来源：https://github.com/Yang-Yin734/notice-radar
- 许可：MIT License
- Copyright (c) 2026 notice-radar contributors

MIT License

Copyright (c) 2026 notice-radar contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
