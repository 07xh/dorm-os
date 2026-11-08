# 学习通 App 单一本机读取链替换

用户明确要求停止在自研旧读取链上补兼容，按已提供参考 App 的完整读取流程实现。沿用 PR #419 原生主线和现有 Room/任务中心，不新增后端或凭据存储。

## 已核实的参考读取流程

`MobileEngine.scan` 使用本机 WebView CookieManager 会话，POST 通知 getNoticeList（lastGetId 分页），通过 TaskDiscovery 递归通知附件 JSON 与 content/rtf_content HTML 链接；读取学习计划页面及其 iframeUrl 中 eTaskUserId，调用 getGroupData、getPlanDataByGroupId，将 planType=4 的 getToStudyUrl 目标关联作业；最后 POST courselistdata，经 CourseEntry 真实入口补扫普通课程。状态取平台明确文字和 isFinish。反编译存在错误的方法体仅作为控制流证据，未复制源码。

## 单一实现

- `ChaoxingLocalReader.kt`：可测试的只读流程与 Jsoup HTML 解析，通知→计划→课程按顺序执行，跨源单条失败保留其它结果。
- `ChaoxingWebSession.kt`：仅复用本 App 官方 WebView CookieManager，逐跳校验官方主机和 HTTP 方法、逐目标域取会话、保存 Set-Cookie 轮换。数据不发到校序服务器，URL/token 不进入读取结果或 Room。
- `NativeSchoolConnectActivity`：只负责官方登录、发起/取消本机读取、展示进度以及一次 Room 提交。删除两份旧 JS 扫描器和旧网页阶段状态机。
- `CloudClient`：删除 App 旧学习通 QR/云端课程和作业读取方法；原任务中心删除重复云端核验分组与面板，数据连接只保留本机学习通入口。网站的兼容 API 和云端历史记录未删除，App 不再调用旧学习通读取 API。
- 已完成/未完成作业计划都可解析只读目标，同一 work 官方 ID 只记一份；确证 plan→work 的旧身份在同一 Room 事务中清理。完整来源批次替换本机快照，部分失败批次保留其它旧快照的原采集时间；切换本人账号不混入前账号记录。
- 首页和任务页均使用本机学习通结果，过滤旧云端学习通历史任务作为读取替代路径；其它云端/微信/个人任务不受影响。

## 验证与边界

本地完整 Node check、静态原生门禁及真实 Kotlin/JUnit 模拟 transport 已验证通知分页/附件字符串/iframe/分组/目标/课程入口、官方状态、同作业归一、失败恢复、无引号 UID、前后账号校验、跨轮合并和换账号隔离。独立代码审查发现的失败恢复、UID、403分类及重复统计问题已修正。新增 Jsoup 1.21.2（MIT）是成熟 HTML 解析依赖。

不把 fixture 或 CI 当作用户账号全量验收；最后仍须 Ace 6 核对本人课程、通知学习计划作业和平台状态，关闭重开后再刷新。此改造没有作答、提交或阅读进度操作，也没有擅自开启后台扫描。
