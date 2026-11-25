# 校序 Android UI 重构：统一扩展契约

R4 采用一个 Shell、一个页面框架和一个共享读模型，不另建后端或本地数据库。

- AppRoute：五个一级入口唯一；子页归属一级入口，返回统一交给 Shell。新增子页只添加路由与内容分发，不新建 Scaffold 或导航栏。
- AppPageFrame / AppSpacing / XiaoxuTheme：所有业务页共享顶栏、留白、字体、状态提示。底部安全区归 Shell。
- AppWorkspaceState：复用 Activity 持有的 CloudClient、Room DAO、课表快照；Room 生命周期订阅统一处理。Loading、Ready（含空数据/旧快照）、Unavailable 不混用。
- AppPageActions：页面调用已注入的动作，不自行实例化网络客户端或改变官方数据。
- WorkspaceTaskLogic：继续使用现有来源核验及去重；本机完成操作使用 Room 前置依赖事务。非本机条目仅展示。
- 手机与平板共享同一路由及状态；子页不重复显示手机胶囊。页面 saveable 状态按路由保存。
- 当前是开发分支；成绩、考试、学习进度等尚未完成页面迁移，不使用假数据。第二课堂仍有自己的现存读取入口，后续需接入共享读模型。课表仍为基础列表，后续替换内容而非框架。
- 不删除历史测试、不更改发布流程；全量编译、旧测试迁移、许可证和真机验收通过前不得发布。

新增功能时优先扩展 AppFeaturePage 的对应业务文件与 AppPageActions；若必须新增状态或仓储接口，复用现有业务适配器，不在每个页面复制登录、刷新或持久化逻辑。

## R5 源码复用优先（固定开发原则）
上游存在成熟组件时直接移植，仅修改包名、依赖、主题/数据绑定和必要兼容层。不得重新实现同等组件或新增另一套架构。保持 AppRoute、AppPageFrame、AppWorkspaceState、AppPageActions 不变。
固定来源：znjhahaha/zhengfang-apk@bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed。已移植 GlassKit 四个公共组件及完整 GlassPerformancePolicy，现有 AppEntry 与主题分组统一委托给上游组件。后续课表、成绩、设置继续按上游组件依赖迁移，保留测试及发布门禁。


## R6 现有自研组件替换原则
R3 自研底栏的选中颜色、静态图标和文字单元已替换为上游 NavTab、PhosphorNavigationIcon、NavigationIconPlayback、NavigationIconMotion 原实现，并移植上游动作测试。仅保留路由映射、主题绑定及外层 backdrop 与安全区；完整物理折射、拖拽和最小化依赖待继续移植。禁止并存两套同功能 UI。校序独有业务保留原业务逻辑，成熟上游同功能 UI 优先替换。

## R7 公共页面替换
直接移植上游 ScreenMetrics、CollapsingGlassHeader 插值函数、SystemTopBar、SystemEmptyState、SystemSectionHeader、SystemListItem；替换 AppPageFrame 顶栏和课表列表行、空状态，底栏尺寸与内容留白统一使用上游响应式尺寸策略。仍保留唯一 Shell 与数据契约。完整周课表网格、弹窗系统、物理折射尚未移植，本提交未完成编译与运行验证。

## R8 周课表源码移植
移植上游 TimetableLayout、TimetableBackground、CourseCard、文本测量、ScheduleOverlap、ScheduleLocation 与 ScheduleDomain 相关实现。原临时列表替换为周网格；ScheduleFeature 仅做现有本机课程及学期配置绑定、周次选择和课程详情。保留导入导出和提醒打开课程入口。周次上限适配既有 35 周约束，不猜测开学日期；未配置学期时仅展示真实周次。上游完整头部、日视图、滑动分页和玻璃详情弹层尚未迁移。编译及真机验收仍待完成。

## R9 周次滑动与日期动画
直接移植上游 MotionTokens、MotionProfile（不引入无关全局导航依赖）、ScheduleDateText、ScheduleWeekLabel、ScheduleWeekPagerSync 及原测试。使用 HorizontalPager 绑定已有周课表，将稀疏导入周次映射到连续页索引；按钮、周次下拉、滑动共用状态，程序跳页不重复回写。修正 R8 文本测量列宽参数误传像素整数，恢复 Dp 参数。课程详情仍为现有 AlertDialog；日视图、完整液态日期栏、详情弹层待移植。未完成编译和设备验证，继续保留发布门禁。

## R10 日课表与课程详情
移植 ScheduleAgendaContent 的日课程卡片及空状态、CourseDetailContent 的信息区和信息卡、ScheduleGlass 的高光材质。日/周视图共享原有周分页、真实课程、学期配置及本机视图偏好；点击星期打开日视图。详情统一用现有 Material 底部面板承载移植内容，恢复原 LocalCourseReminders 单课程开关、通知授权和调度，不复制上游提醒数据库。真实节次时间缺失时明确显示未配置，不生成假时间。修复文本测量 Dp 与像素混算。完整上游光学弹层、日自习计划、课程编辑、课表设置仍待接入；未完成全量编译及真机验证。

## R11 课表设置
直接复用上游基础设置与节次列表的源码布局，新增 SCHEDULE_SETTINGS 子路由，复用 AppPageFrame、现有分组组件、LocalSemesterConfigStore 和 SemesterCalendar 校验。设置第一周周一、1–35 教学周、1–24 显示节次及实际起止时间；空节次不编造时间，减少显示行不删除已有设置。保存通过 AppPageActions 更新共享配置并在 IO 调度现有课程提醒，不自动请求教务接口。完整上游滚轮选择器、课程编辑及其他设置尚待移植；未完成编译和真机验收。

## R12 成绩页面
直接复用上游 GradeItemUi 和 GradeItemRow 的布局，在原 GRADES 路由中接入。按学期过滤、展示课程成绩、学分、绩点和实际备注。通过现有 CloudClient 和 AppPageActions 手动读取 /api/v1/verified/grades，校验来源、状态、记录格式及账号绑定，区分可信只读快照、个人导入、过期、未连接与读取失败。退出或账号变更清除成绩，不自动发起校方查询，不把学习通作业成绩合并为正式教务成绩。尚未接入学习通课程成绩、完整统计和上游成绩分项图表；无已验证分项数据不从备注推算。未完成编译及真机验证。

## R13 考试页面
直接移植上游考试卡片及信息行，接入 EXAMS 路由；通过现有 CloudClient 手动读取 /api/v1/verified/exams，复用成绩的账号隔离、来源、状态、时间戳及稳定记录身份校验。两种业务共享 loadAcademicSection，不新增客户端，不自动同步学校。按学期筛选及日期排序；缺失座位等字段不推测。真实学校考试查询、完整上游考试管理和统计仍待接入，未完成编译及真机验证。

## R14 统一教务观感及交互
成绩和考试合并重复的页面控制逻辑至 AcademicSnapshotList；共用 AcademicTermPicker、AcademicReadAction、上游 SystemStatStrip、现有 SystemSectionHeader 与 AppSnapshotContent。业务页只负责数据映射和业务卡片。第二课堂主路由直接接收 AppWorkspaceState.nativeRecords，不重复订阅数据库；独立 Activity 保留兼容入口并复用同一内容。积分概览同行学期筛选、学校合计、本人活动及目录同页展示，身份绑定和批次不一致时不展示为确认数据；详情可展开查看原始已保存文本，旧目录单独提示。保持现有读取入口和同步策略，不添加自动查询。学习通课程进度目前无已核验专用数据，仅统一空状态及数据连接入口，不从作业完成推算。编译和真机验收未执行。

## R15 任务与个人中心统一
将任务、个人中心和连接管理拆成共享 Shell 下的独立业务组件。任务复用原 WorkspaceTask 合并、搜索、状态筛选、完成校验及 Room DAO；复用上游统计栏和分组行，恢复新建本机任务及详情入口，平台记录只读。个人中心直接移植上游 SettingsRow，按账号与教务、学习设置、应用与支持分组；连接页统一读取按钮及实际授权状态。没有新增数据层或网络客户端。依赖编辑、任务备份、归档管理、更多设置及完整上游导航仍待恢复。未执行编译和真机验证。

## R16 编译收尾门禁
仅重构分支 push 触发独立 Android UI rebuild compile gate；沿用现有 JDK17、Gradle8.13、API36 配置。编译生产与仪器测试源码、运行全部 JVM 单测，不签名、不上传 APK、不发布、不更改 main。修复周课表列宽 Dp 类型契约；恢复备份中原有首页课程截取、课表来源标签、通知状态判定和即时转场契约，保留既有测试而非删除测试以取得通过。首页使用原有真实课程顺序与通知空状态。实际 CI 结论需读取对应提交的运行记录；真机与许可验收仍单独进行。

## R19 恢复任务执行面板
R17 提交 e60d2ae 的 CI 45572257841 主程序 Kotlin 编译与 JVM 测试通过；仪器测试源码因删除 TaskExecutionPanel 而编译失败。直接从重构前备份恢复原面板业务逻辑，改用现有 Material 主题、复选框和删除确认组件，保留已有测试和辅助功能描述。主任务详情接回本机个人步骤编辑，复用现有 Room DAO、共享依赖状态及完成校验，不改变学校任务状态。提醒与读取回执计数只在当前 Activity 组合生命周期内记忆，不跨重建保留旧计数。新提交须重新验收，真机、完整任务入口与许可审查仍待完成。

## R21 恢复任务成熟组件
上一提交 4dda75f 的 CI 45572748305 已全部通过（Kotlin、JVM 单测、Android 仪器测试源码编译）。直接恢复旧版 LocalTaskBackupPanel、LocalTaskDependencyGraph 和 TaskBoard 本机编辑器，保留数据模型、事务、依赖环检查、SAF 导入导出与合并规则；替换旧色板和弹层依赖为统一主题及 Material 对话框。任务详情进入原编辑器，关系图支持搜索、追踪和打开编辑器，个人中心接入任务备份页。新增提交需要重新执行编译门禁，模拟器实际执行尚未通过验收。

## R22 课程编辑及提醒设置恢复
直接恢复旧版 CourseEditorDialog 与 ProfileSettingsPage 通知提醒内容，统一主题、分组和 Material 选择弹层。课程新增、编辑、整门及单周删除沿用 LocalScheduleEditor 校验、LocalScheduleStore 存储、ID 重映射与提醒调度；通过统一 AppPageActions 回写 Activity 的真实课程快照，保存前核对本机记录，避免覆盖已经变化的课表。提醒页保留课程和个人截止提醒独立开关、提前时间、任务提醒时刻、本机免打扰及系统通知权限入口。未引入第二套存储或调度器；均为本机操作，不提交学校。第四轮主程序及 JVM 单测已通过；新提交仍需编译和实际运行测试。
