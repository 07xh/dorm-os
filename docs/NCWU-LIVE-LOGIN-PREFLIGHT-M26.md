# 青果官网登录可达性探测 M26（本人登录前置诊断）

## 复用而非重复开发
直接复用原「我的 → 数据连接」和已有 Owner 身份校验、私有 API。此次不新增学校密码收集入口，也不新增登录状态库。青果课表 HTML 读取器、分节次解析器与逐项对照仍保留。

新增：服务端无凭据 GET \`https://jwmis.ncwu.edu.cn/hsjw/caslogin\`，仅允许跳转到已知本校 \`authserver.ncwu.edu.cn/authserver/login\`，并严格核对 CAS \`service\` 仍是教务官方入口。检查当前页面是否存在普通密码表单；遇到互动验证、页面跳转变化、网络问题时安全停止。最多六次请求，绝不携带学校 Cookie、用户名或密码。接口返回固定状态码，不返回官方页面 HTML 或敏感参数。

- 服务端逻辑：\`lib/academic-ncwu-login-probe.mjs\`
- 原有本人受保护端点：\`GET /api/v1/connections/ncwu/preflight\`
- 原有 UI 的青果卡片：检测按钮与原因提示
- 回归：\`scripts/xiaoxu-ncwu-login-preflight-tests.mjs\`，已列入 \`npm run check\`

## 明确验收边界

这不是学校账号接入成功。探测仅确认**当前公开登录页面类型和 Cloudflare 服务端可达性**。成功结果 \`ordinary_form_detected_not_authenticated\` 只表示表单可被识别，不能代表登录方式一定可自动完成，尤其不能证明本人身份或课程归属。

**目前青果缺少可使用的真实学校登录适配器**。因此任何探测状态均不能直接显示账号输入框；现有 \`/connections/ncwu/login\` 应继续拒绝未注册适配器的学校密码，\`trusted_readonly\` 快照仍须遵守本人身份、来源 ID 和课程完整性门禁。

后续唯一优先交付：在受支持的校方正常登录方式下，打通服务端会话 → 本人身份核验 → 同学期课表完整读取 → 网格/明细与官网对照 → 加密保存会话 → 真实课表显示与重复刷新。成绩和考试暂不抢占课表优先级，且不可把测试/导入内容标记为已同步。

不提交任何真实 Cookie、学校密码、登录令牌或未脱敏 HAR。
