# 校序第二课堂 M23｜服务端原生 CAS 登录连接器

## 代码实现

- `lib/academic-ncwu-cas-server.mjs`：服务端只在
  `dekt.ncwu.edu.cn`、`authserver.ncwu.edu.cn` 和
  `sec.ncwu.edu.cn` 之间跟随 HTTPS 跳转。先识别校方 CAS
  的常规密码登录表单与隐藏字段，遇到滑块、图形码、短信验证、
  页面脚本加密或无法核实的入口时拒绝自动提交，不绕过验证。
- `functions/api/v1/_middleware.js` 在**现有**
  `/api/v1/connections` 范围内注册可信服务端适配器，
  不再让用户安装 ScriptCat，也不新建登录账户表。
- `GET /api/v1/connections/second_class/preflight`：
  需要校序本人账号登录且服务端 `SCHOOL_SESSION_KEY` 可用，
  **先执行无密码的学校页面检查**；没有通过前不会展示密码输入框。
- `POST /api/v1/connections/second_class/login` 通过原有登录
  限流、安全校序会话和 HTTPS 后端验证，只有成功拿到本人身份、
  正常读取活动与积分后，才复用 AES-GCM D1 保险库保存有效会话。
  既有 `/sync` 和 `/disconnect` 复用相同会话及业务适配器。
- 浏览器界面只在 `preflight.loginSupported===true` 后展示
  学校账号/密码输入框，不保存输入密码，不生成模拟绑定结果。
  仅在 D1 有真实可信同步和可解密的有效会话时显示“已连接”。

## 必须明确的实际限制

这套代码是一条已经实现并经过**合成学校请求验证**的原生
服务端连接路径，不需要学校官方开放第三方 API；但
**尚未经过你的本人学校账号真实登录验收**。
官方 CAS 当前的表单方式和 Cloudflare Pages 生产网络是否
允许完整访问仍未经过真实测试，可能返回
`interactive_login_required` 或 `upstream_unavailable`。
校序后端还要求 `SCHOOL_SESSION_KEY` 独立的高强度 Secret，
否则 preflight 只返回 `server_session_vault_unavailable`
并拒绝接收学校密码。
不能把“代码已接入”写作“学校帐号已连接成功”。

此阶段只读取本人官方页面的已核实活动和积分接口。
不操作报名、签到、审核或他人数据，不处理和绕过校方验证码、
风控、短信或其他身份检查。旧 ScriptCat 路线仍保持删除状态。

## 验收

`node scripts/xiaoxu-ncwu-cas-native-tests.mjs` 使用完整
合成 CAS 请求 transcript 验证：安全域名、同源 Cookie、表单、
本人身份、既有 POST 读接口、跨域拒绝及验证码失败关闭。
与项目现有 M18 AES-GCM 会话和 D1 数据测试共同运行。
上线前必须通过 `npm run check` 与 Playwright 桌面/移动回归。

以后补学校本人登录的验证只通过校序 HTTPS 页面进行，
不得向 GitHub、ChatGPT 或日志提交学校密码和 Cookie。
