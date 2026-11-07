# 校序更新后残留审查与清理 R2｜2026-11-07

基线：#351 `16a8b050`，已确认 #370 合入，合并后的综合 CI、Kotlin/JVM、WebView Runtime、APK 构建四项成功。#351 仍 Draft，不进入 main。本轮只清退有源码引用证据的残留，保留当前原生 UI 与学校读取行为。

## 实际清理

| 范围 | 引用及替代证据 | 处理 |
| --- | --- | --- |
| 旧首页渐变主卡 | `XiaoxuHero` 在 main 源码只有定义，无调用；新版首页使用 RealAcademicHome/HomeCourseList。旧静态门禁却要求该函数存在 | 删除组件及只为它服务的阴影/绘制导入；保留全局背景、主题形状、课程色值、现用卡片和浮层。修改旧门禁并验证先失败后通过 |
| 旧导航状态副本 | NativeShell 实际用 rememberSaveable 管 `messagesOpen`，AcademicNavigation 的 selectMain/openMessages/closeMessages 只被测试调用 | 移除测试专用消息状态与转移方法。保留生产实际调用的 openSecondClass/selectAcademic、两个路由枚举；测试转向所有主页面/教务子页进入二课的实际 helper。没有把 helper 单测说成真实返回栈验收 |
| 未使用的动画 API | animateToValueAndThen 在所有生产/测试源码无调用；FloatingBottomBar 使用 animateToValue、updateValue 等 | 删除无人调用的方法及 coroutineScope 导入；保留按压、拖动、释放、速度与实际回弹逻辑，并修正源码及两份署名声明中“完全未修改”的描述 |
| 更新后的会话分支残留 | refreshSchoolAccount 的学习通分支提前 return，后半段仍判断 provider===chaoxing | 删除后半段无法进入的学习通分支/返回码/多余变量；原专用 Cookie 轮换与 CAS 防旧请求覆盖路径保留，青果/二课分支行为不变 |
| 二课通用字段映射 | academic-second-class-source-map.mjs 只有旧合成测试引用；真实学校字段由 academic-ncwu-second-class.mjs 处理 | 删除通用映射与其测试/包脚本入口；M8 文档明确标记历史停止执行，保留原文追溯 |
| 二课通用目录模型 | academic-ncwu-second-class-catalog.mjs 只有旧合成测试引用；实际目录读取/空结果验证在官方读取 JS、LocalSecondClassCatalogEmpty 与学校活动来源契约 | 删除未接线的通用目录模型与其测试/脚本入口，不删除实际已确认的 `/sys/stu/activity` 空目录证据或猜测非空活动 |
| 未使用导入 | 对 main Kotlin 文件按符号引用逐项扫描，排除 getValue/setValue 等 Kotlin 隐式运算符/委托导入 | 删除 35 条无引用导入，交由 Kotlin 编译验证；不改变页面布局、权限、路由或数据 |
| 过时当前版本说明 | Android README 当前章节仍说测试包只包含 main 源码，已与 #351 开发方式冲突 | 修正为唯一整合分支及精确 APK SHA；历史版本仍保留并区分 |

## 全面检查后的保留项

- **Room 与 D1**：保留实际任务、本人学校快照、备份/恢复、迁移和历史表。已应用的 0013/0014 迁移不可因旧登录路径退役而删除。
- **课表**：保持本地导入/持久化、周次单双周、调课、作息与提醒；既有服务端兼容记录与成绩/考试接口仍有用途，不因 App 不读云端课表而批量删除。
- **学习通与二课读取**：保留 owner 鉴权、加密学校会话、官方本人解析、去重、完整分页、失败保留旧数据、后台 opt-in、取消与失效保护。旧 CAS/Sudy 退役路径未发现运行时引用重新出现。
- **任务/消息**：LocalTaskEditor 仍承担 Room 管理，不是重复任务系统；微信审核必须人工确认；官方状态与个人完成不互相覆盖。
- **动态模块待验收**：#367 CampusRunReadOnlyQuery 是用户指定待接线查询层，虽然当前主要由单测使用，必须保留；不按“只有测试调用”机械删除。本人 r/s/v 载荷映射是另一任务，不重造模型。
- **诊断/证据契约**：依赖图还列出 ncwu-schedule-audit 与 second-class-activity-source。前者有独立官网参考对照的只读诊断用途，后者是已确认学校目录的拒绝伪造契约；本轮保留，不把它们称为已接入运行时。
- **构建请求/其他项目**：18 份 pending APK 请求中含已审核历史和 #351 本次 QA 申请。仓库规则明确删除也会命中构建触发，本轮不批量删除或归档；NFC/TapFlow、DORM OS 兼容路径不属于校序死代码。
- **并行设置工作**：本轮对 MainActivity、SemesterSettingsDialog 等只删除无引用导入，没有改分类、通知权限和二级页，避免覆盖设置专项实现。

## 检查与边界

- 通过 Kotlin main 源码符号扫描和服务端/运维入口依赖图定位候选，再复核真实调用、测试、文档、Manifest、现有 PR 归属；“没有运行时调用”不是唯一删除标准。
- 已跑：Android 静态检查、学校登录/会话轮换/二课只读传输、真实字段契约、活动空目录与同步原子性检查。
- 提交前跑完整 npm check；提交后核对 GitHub Kotlin/JVM 与综合 E2E。合并子 PR 进入 #351，不解除 main 真机门禁。
- 本轮代码清理不代表三平台本人数据、Android16 后台提醒或玻璃视觉/性能已验收。大文件按职责拆分、旧 APK 请求消费机制属于后续可独立验证的工作，不借本轮扩大重构。

- 独立只读复核未发现阻断性功能问题；指出的两份署名描述已更正，未更改许可证条款。
