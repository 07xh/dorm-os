# 校序 M18｜第二课堂并入统一学校会话通路（2026-11-03）

## 已提交（代码完成，不代表学校账号真实联通）

使用现有「我的 → 数据连接」和现有校序授权机制，不新建第二套账号系统或表单。
第二课堂 provider `second_class` 已纳入 `lib/academic-school-login.mjs` 的
会话加密、重连、单来源撤销；成功的只读查询分发到已有
`runVerifiedNcwuSecondClassSync`，活动与官方素质分/志愿服务时长
先全部读完，再按用户原子写入 D1。首次登录成功返回实际同步时间
(`capturedAt`)，不会把历史导入误判为有效登录。

`GET /api/v1/connections` 对第二课堂采用「会话有效 + 受信任活动批次 +
当前服务端授权适配器可用」的组合判据，展示已授权与手动同步。
原始 `academic_connections` 的 provider CHECK 不支持 `second_class`，
因此不向它插入伪造的连接记录；继续复用已存在的
`academic_verified_batches` 和 `academic_provider_sessions`。

## D1 前向迁移

新增 `0012_academic_second_class_sessions.sql`，只扩展
`academic_provider_sessions` 的 provider CHECK，保留原来的
NCWU/Chaoxing AES-GCM 密文和其他学业表。生产使用原
`upgrade-owner-auth-d1.yml`，先核对绑定及既有 0010/0011，再检查
`sqlite_master` 中的实际约束，未升级时执行一次 0012；
未知约束立即停止，不覆盖个人数据。

自动测试通过合成学校会话验证：密码不落库、来源和本人隔离、
真实 HAR 已验证路径的活动/官方分数、续读、撤销、旧密文迁移与
SQL 外键完整性。没有提交任何真实 HAR、Cookie、Token 或姓名。

## 仍须完成的学校实时连接

学校的 `dekt.ncwu.edu.cn` 只读 POST **业务接口**已经由本人 HAR 确认，
但该 HAR 不能证明该站允许第三方网站直接使用账号密码登录。
目前仓库**没有经过真实学校登录验证的** `second_class` 服务器
`login({username,password})` 实现，**不能声称已接入本人学校账号**。
只有校方允许的本人授权登录方式可用，且服务端可信 Pages 适配器
注册到 `context.data.academicSchoolAdapters.second_class` 后，
现有登录卡片才会显示学校密码输入项；否则不收集凭据。
适配器 `forSession({session,userId})` 必须返回
`makeNcwuSecondClassAdapter({verifySession,readPost})`，
其 `verifySession` 必须把真实学校本人身份绑定到校序 userId，
`readPost` 仅请求先前已验证的四项白名单只读路径。

生产使用前：执行 0012、配置独立 `SCHOOL_SESSION_KEY`，
完成本人官方登录授权方式验证、真机同步对照、失效重连与匿名隔离；
后台 Cron 仍关闭。禁止自动抓取登录验证码、绕过 SSO 或把学校密码、
Cookie、HAR 上传到 GitHub。
