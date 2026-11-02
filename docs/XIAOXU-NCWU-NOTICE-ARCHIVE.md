# 校序：华水公开通知历史归档与 notice-radar 适配

## 与已有 /api/notices 的关系

主分支现有的 Cloudflare Pages Function `/api/notices` 已负责教务处公开「通知/考试」的实时读取和校序 UI。**本功能不再增加第二套通知页面，也不会覆盖现有 API。**

这次补充的是：

1. 以 notice-radar 的 HTML 列表适配、标题规范化、链接去重、低频采集和失败保护方案作为参考，配置教务处/学校通知/学生工作处三个公开源。
2. 将真实官网已核实原文的 5 条记录作为 `public/data/notices.json` 初始档案；`mode: seed`、`lastSuccessAt: null`，避免将人工核实说成已自动同步。
3. Actions 在合并后首次运行以及每日北京时间 10:17、18:17、次日 00:17 低频采集；只有至少一个官方列表真实返回有效条目才更新 JSON，失败保留原档案。
4. 后续可将该档案接入 API 作为跨设备公开历史来源/故障备用，但本 PR 不改变既有 API 的实时成功判定和 UI。

**存储形式：** GitHub 中版本化 JSON 文件，由后端 Actions 维护，不是 Cloudflare D1 或 SQL 数据库，也不包含个人成绩、课表、学号、账号会话。

## 验收

- `npm run notices:test`：合成 HTML 测试，包括去重、恶意域名、日期规范化、失败保护。
- 合并后检查 Collect NCWU official notices 的实际运行记录：成功源数量、有效通知数、档案的 `mode=auto` 和 `lastSuccessAt`。
- 检查数据提交引发的现有 Cloudflare Pages 部署，以及公开数据文件能被访问。
- 任何自动化测试使用的合成 HTML **不能**冒充真正学校网页抓取；现阶段尚缺生产环境首轮核验。

## 第三方许可

notice-radar：<https://github.com/Yang-Yin734/notice-radar>，MIT。

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


## 海外 GitHub Runner 的备用出口

主采集器优先直接访问三条华水公开栏目。若本轮全部失败，则调用已部署的 `https://dorm-os-dk9.pages.dev/api/notices`。该 Cloudflare Pages Function 自行读取华水教务处公开「通知/考试」栏目。

只有 API 响应带 `x-xiaoxu-public-notices: 1`、`success: true`、每条链接通过 `www2.ncwu.edu.cn/jiaowu/info/...` 官方白名单，才允许归档；其余全部失败保留旧数据。只有 MM-DD 的发布日期不推断年份，`date` 保持 null。

兜底源 `jwc-api` 与教务、校级、学工处三个直连源独立记录状态。备用源只覆盖教务/考试，不等于其他栏目已抓取成功，也不涉及需登录的青果个人教务。
