# 校序青果账号连接：先课表后成绩、考试

本次修改把既有 `lib/academic-ncwu-verified-sync.mjs` 中已经通过测试的 `kinds:['schedule']` 模式接到 **服务端**`academic-school-login.mjs` 的登录及会话刷新入口。学校账号登录没有重新建系统。

## 连接逻辑
- 学校适配器**必须已经由可信 Pages 中间件在服务端注册**，且验证校序 owner 与学校身份同属本人。
- 登录时需要完整、可归属的课表，才能加密保存有效学校会话；仅解析出部分 HTML 课块会失败。
- 只调用服务端 `readSection('schedule')`；成绩、考试不请求、不写库，也不伪造已有数据。
- 本次连接 `status='partial'`，仅表示课表领域通过可信同步门禁；通过 `trustedKinds:['schedule']` 和活动学校会话共同显示「课表已授权 · 可同步；成绩、考试待接入」。
- 每次刷新仍只读取课表。待成绩和考试适配真正验收后，未来可用服务端已有全三类 `runVerifiedNcwuSync` 升级状态。
- 当会话失效、服务端 adapter 不存在或来源记录不可信，页面不显示已经授权，也不能自动收集学校密码。

## 合成验收
新增 `scripts/xiaoxu-ncwu-login-schedule-first-tests.mjs`，验证首次登录仅查询一次课表、会话 AES-GCM 加密、数据库只存 schedule batch、连接状态 partial、会话刷新不触碰 grades/exams、没有有效来源与过期会话不能宣称授权，以及本机测试不含实际学校 Cookie 或账号。

## 尚未完成
- 学校真实统一身份认证适配器的注册与真人会话校验。
- 本人身份与教务主体的可靠映射和真实课程唯一 ID、周次、节次、教室的全量核验。
- 真实课表动态同步上线验收。此改动**不将任何模拟数据冒充真实数据**。
