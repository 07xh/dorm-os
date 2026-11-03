# 校序 · 学习通消息收件箱同步（真实 HAR 接口分析）
 
## 已确认的服务端消息结构（2026-11-03）
 
- 官方页面域名：`notice.chaoxing.com`。
- `POST /pc/notice/getNoticeList` 的成功响应包含 `status:true`、`notices.list`、`notices.lastPage` 和 `notices.lastGetId`。
- 消息包含 `idCode`、`uuid`、`title`、`content`、`sendTime`、`isread`，部分课程消息包含 `tag=courseId...`。
- 消息正文中的字段可包含课程名称、作业名称、开始/结束时间、互评开始/结束时间、试卷名称、考试时间。
- 有的 `getNoticeList` 响应只有 `status:true`，**绝不能**当作已认证的空消息收件箱。
- `POST /pc/notice/getNewNotices` 不直接返回消息列表，不得当成任务明细。
- 捕获只是本人网络记录，不证明可以在 Cloudflare Workers 直接复用浏览器登录态。不得提交 HAR、Cookie、令牌、学生标识。
 
## 本轮接入设计
 
1. 复用现有 `academic_provider_sessions` 和统一数据连接中心；只允许已验证用户身份的**私有可信**学习通服务端适配器提供 `verifySession()` 和 `readNoticePage()`。
2. `lib/academic-chaoxing-notices.mjs` 使用真实已观察字段解析消息，并进行分页、消息去重、任务类型与截止时间提取。
3. 可执行的作业、互评和考试形成独立来源任务。作业结束提醒会关联已有作业而非重复创建。签到、活动、测验和无明确动作的通知保留在收件箱，不冒充已确认待办。
4. 通过现有 `runTrustedConnector` 写入 `academic_assignments` 和 `academic_assignment_sources`；消息元数据写入 `academic_chaoxing_notices`。
5. 通过现有网站本人会话访问 `GET /api/v1/chaoxing-inbox`。不存消息原文、附件、发送人、原始 URLs、原始 JSON。
6. 来自收件箱的提交状态一律 `unknown`，不可凭未读、发布提醒或页面时间变更标记 `todo/submitted`。
 
## 真正上线前的验收门槛
 
- 对学习通账号完成本人登录和学校身份校验，证明 `readNoticePage()` 来自可信服务端适配器，官方允许的只读访问方式及必要的分页参数必须实际核验。
- 页面开启真实同步前先迁移 `0015_academic_chaoxing_notices.sql`，并完成既有 D1/身份权限/端到端测试；尚未接入真实适配器时 UI 必须显示“未连接”。
- 开发人员不得从 HAR 中拷贝 Cookie 或尝试凭已上传 HAR 冒充当前学校会话。
- 分类器是通知发现层，任务真正的完成状态需业务接口单独校验；通知并非所有任务的完整枚举。
 
合成测试使用匿名虚构消息，非用户原始 HAR。
