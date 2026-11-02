# 校序 · 个人教务接入前的官方入口连通性检测 V8.4

**范围：只测公开入口的网络响应，不读取个人课程、成绩、考试、学习通作业或第二课堂积分。**

## 数据源与开源复用位置

| 连接器 | 固定官方入口 | 正式个人数据实现依赖 |
| --- | --- | --- |
| 华水青果 KINGO | `https://jwmis.ncwu.edu.cn/` | 正方 `znjhahaha/zhengfang-apk` 的 KINGO 协议（GPL-3.0）、拾光 `qingguo_01.js`（MIT）验证与正式授权 |
| 华水学习通 | `https://ncwu.fanya.chaoxing.com/portal` | MIT `DanMo661/xuexitong-cli` 的作业只读查询；校序已有 `xiaoxu-chaoxing-cli.js` 标准化，不重复开发 |
| 第二课堂 | `https://dekt.ncwu.edu.cn/` | 需本人合法登录后确认可用的只读字段/授权机制；不能使用 demo 生长记录冒充积分 |

## 新增实现（Cloudflare Pages Function）

`GET /api/academic/status`：Cloudflare 从固定、无用户参数的 HTTPS 入口发出匿名 `HEAD` 请求；仅在 405/501 时对同一固定入口尝试一次 `GET`；不发送 Cookie、密码、原始会话、身份信息，不跟随登录重定向。每源 4.5 秒超时，约 15 分钟边缘缓存，失败只标明未响应，不能推断学校系统“已停用”。

响应同时包含 `accountConnected:false`、`personalData:false`、每源 `accountStatus:'not-connected'`。**即使 HTTP 200/302/403，也只证明服务器可能有响应，不证明账号已认证、Cookie 有效、课程可读取或作业已同步。**

现有「我的 → 设置 → 教务数据连接」内联展示三源的实际检测结果与检测时间，可以手动复检。仍保留原有学习通 CLI 导入在折叠的“数据迁移与手动导入”中，不把它宣传成自动同步。未新增侧栏/第三方嵌套 UI。

## 真实接入下一门禁

1. 在华水**官方网页**完成本人授权，去敏后验证青果实际登录和课表响应。把真实字段映射到已存在的 `lib/academic-upstream-bridge.mjs`，核对学期、周次、节次、节次开始时间；状态从 `unverified` 变为真实数据之前必须完成校内验证。
2. 学习通沿用 `xuexitong-cli` 对作业 ID、提交状态、截止时间的只读抽取。需要稳定合法会话；无法授权时继续显示“未连接”，不伪造作业。
3. 第二课堂只在能够合法读取本人记录的情况下实现积分/活动状态。严禁把开源 Demo 测试数据和活动报名接口当成实测。
4. 后台持久化和受控凭据托管需要明确的单用户认证安全方案；不能为了开发方便暴露 Cookie 或公开设置敏感 API。

验证：`node scripts/academic-source-readiness-tests.mjs`、`npm run check`、Playwright 手机/桌面 E2E 和部署后的 `scripts/verify-release.mjs`。

仓库路线和许可审查另见 `docs/XIAOXU-ACADEMIC-REUSE-AUDIT.md`。
