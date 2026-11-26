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

## R23 通知中心恢复
第五轮 CI 45573748668 已全部通过。复用旧版公告筛选、来源文案、本机提醒计划和状态契约，接入 HOME 的 MESSAGES 子路由。手机公告详情用统一对话框，宽屏列表和详情并列；本机计划跳转现有任务和课程页面。微信候选计数与官方公告分离，当前仅保留既有云端审核入口，未恢复原生审核面板。不生成公告正文、不推断实际送达。新提交仍需编译和运行验收。

## R24 导航光学及拖动移植
第六轮 CI 45574185666（通知中心）全部通过。直接移植上游 DampedDragAnimation、DragGestureInspector、GlassMaterial，以及 PhysicalLensSurface 与 GlassCompat 的平台折射解析和安全形状检查；适配原五路由、共享材质档位和单一页面采样，加入滑动选中胶囊、速度形变、按压折射、高光及阴影，保留低性能、减少动态效果及高对比度回退。未移植 API31/32 离屏 GL、隐藏 tint 文字采样层或最小化导航，不能声称完整上游 1:1 光学。需重新编译、真机验证手势与帧时间。

## R25 统一表单和筛选胶囊
第七轮 CI 45574549658（导航光学）全部通过。移植上游 GlassFilterCapsule、GlassFormField 及 GlassKit 中 GlassTextField，使用本项目主题、按压交互源与无障碍动效策略，暂不移植嵌套实时光学读取层。课表日周切换、课程编辑、通知来源/类别筛选与搜索、任务搜索与新建共享这套组件。保留原业务校验及数据来源；新提交需重新编译及交互验证。

## R26 上游滚轮与开关复用
从上游 GlassWheelPicker.kt 提取单列滚轮、时间选择弹窗与解析辅助函数，保留吸附滚动、选中行、确认取消和初始分钟保留；减少动态效果时禁用透视形变及点击滚动动画。复用 LiquidComponents.kt 中 LiquidSwitch 的阻尼手势、语义和非光学回退轨道/滑块，适配异步权限/持久化受控状态，暂不启用嵌套光学采样。提醒设置使用统一滚轮和开关，保留原调度逻辑。R25 主程序与 JVM 单测通过，仪器测试源码编译尚在运行；新提交待验收。

## R27 日期及节次选择统一
继续复用同一上游 GlassWheelPicker.kt 的日期、起止时间和单列选项选择器；课程设置的第一周周一、教学周数、每日节数、节次时间以及本机任务截止日期接入统一滚轮。日期回调改为 LocalDate，避免 UTC/本地午夜转换误差；节次保留分钟精度、清除入口及原有整表验证，不自动填写未配置的时间；无效日期或时间范围不写入草稿。弹窗仍由 Material AlertDialog 承载，未进行完整光学弹层移植。R26 CI 正在运行，R27 待编译与交互验证。

## R28 批量组件迁移
R27 CI 45575569306 全部通过。移植上游 LiquidSelectionComponents.kt 的分段控件回退布局、拖动选择、速度形变和受控状态校验，复用 R24 阻尼引擎。提取 SystemUi.kt SystemDialogContent 的自适应间距、正文与按钮布局，保留独立原生窗口的实色回退；不跨窗口采样。课表、通知、任务、提醒、编辑及滚轮等八个功能文件切换到同一公共弹窗，并将课表日周和通知来源切换合并为分段控件。尚未接入完整上游同窗 portal、低版本离屏 GL 与标签光学采样。后续按组件组批量提交，等待关键 CI 完成后再推进，避免取消验收。

## R29 离屏光学引擎整组移植
R28 CI 45576163019 全部通过。直接移植上游 EGL/ES2 引擎、渲染器、着色器、捕获、帧队列、区域和锚点生命周期、几何及帧身份模块，补齐 PhysicalLensSurface 参数与 ChipAppearance。保留上游五组纯 JVM 测试。主题变化适配系统主题，启用 BuildConfig 供调试诊断。当前仅加入源码和测试，不连接任何页面采样路径；不放宽现有设备性能档位，不启用 API31/32 折射。上游 Robolectric 测试依赖当前项目不存在，暂不移植，后续用现有仪器测试架构验证原生路径。待本批编译通过后再统一接入导航、弹层、开关与分段控件，避免未经验证的捕获自循环。

## R30 光学引擎离线运行验收
R29 CI 45576797300 已全部通过（主程序编译、JVM 测试、仪器测试源码编译）。直接迁移上游 GlassLensRendererTest、GlassLensGeometryDeviceTest、GlassLensCaptureDeviceTest，保留原断言并仅适配包名和 MaterialTheme。复用现有 xiaoxuRuntimeTest 离线隔离构建，在 API32 模拟器上运行三组光学测试；不使用学校账户、真实业务数据或签名发布。编译门禁不再主动取消运行中的测试。离屏渲染目前仍未接入正式页面，须在实际运行验证后再接入；本批 CI 尚未验收。

## R31 同窗口光学弹层整组接入
R30 编译门禁 45577309278 全部通过，API32 离线 GPU 测试 45577309613 仍运行。直接移植 DialogHost、ScheduleBottomSheetState、GlassSheetSurface 和 DialogHostStateTest，适配已复用的 MotionProfile、材质及现有页面采样。仅性能档位允许的 Android 13+ 手机启用同窗滚轮和公告详情；其他设备、编辑表单及嵌套原生窗口继续使用原生 Dialog。页面采样与弹层为兄弟节点，关闭时停止额外采样，阻挡底层触摸及无障碍访问；未经设备验证，不启用 Android12 离屏折射或嵌套控件捕获。离线引擎工作流限定引擎、测试及构建相关路径，不主动取消运行中的测试。新提交待编译、交互和性能验证。

## R32 开关及分段控件光学接入、APK 构建阻塞修复
R30 离线 GPU 验收 45577309613 在 :app:dexBuilderDebug 失败，D8 明确定位 AcademicSnapshotListKt.class 的 NON_LOCAL_RETURN 标记；尚未运行渲染测试。将 Compose key 内 return@key 改为等价条件分支，保留 owner、快照状态和真实数据逻辑；常规 CI 增加 assembleDebug，不发布 APK。移植上游 ThumbLens.kt 及开关、分段选中透镜的折射、高光、阴影、形变参数；仅性能档位允许的 API33+、非高对比度和非减少动效状态启用，采样源为各自独立轨道，不捕获页面自身或标签层，保留静态文字。开关合成表面使用主题色作为背景，不是上游完整环境采样。API31/32 路径仍关闭，待实际 GPU 测试和新构建通过后再启用。R31 Kotlin/JVM 已通过，仪器源码门禁继续执行。本次修复与新光学控件待验收。

## R33 五大主页面布局收敛
复用既有 InsetGroupedSection、SettingsRow、SystemStatStrip、LiquidSegmentedControl、ScreenMetrics 与上游 ScheduleScreen 节次时间列；总览改为课程、任务、公告三个分组，宽屏双栏，真实学期配置存在时按当前教学周和中国时区筛选今日课程，否则明确展示本机预览，不推断日期。课表主操作收敛为日/周切换、新增及更多菜单，恢复已配置节次时间。教务采用与我的页面相同的分组入口；任务状态直接分段切换，通知打开任务时定位并打开详情；二级页面从实际来路返回，系统返回和标题返回共享处理。未改数据层、授权或自动同步策略。R32 CI 正在运行，本轮待编译及设备验收。

## R34 原任务归档与删除功能恢复
R33 CI 45578766609 全部通过，包括 assembleDebug APK 打包。复用原 LocalTaskArchiveStore 与 visibleWorkspaceTasks，将已完成的学习通云端任务本机归档、恢复及归档计数接回统一任务页；不写入平台或云端完成状态。通过原 LocalTaskDao.remove 事务恢复本机任务删除入口，显示删除确认及关联关系提示。新提交待编译与运行验收，GPU 实际运行尚需核验。

## R35 原生账户、关于与第二课堂设置恢复
R34 CI 45580208201 全部通过，包括 APK 打包。恢复原 CloudClient.signIn/signOut/refresh 调用入口，个人口令仅短暂保存在非 saveable 状态；退出前确认，不删除本机任务或课表。复用旧 CloudPanels 的真实会话状态映射、AboutPage 安装版本查询、SecondClassAutoReadControls 的原设置订阅及 configure 调用，统一分组和 LiquidSwitch。新增 ACCOUNT、ABOUT、SECOND_CLASS_SETTINGS 二级路由，保留手动读取入口。未调整既有第二课堂后台检查及读取间隔（约30分钟检查、10分钟快照有效期），不宣称已经满足12小时同步要求。未修改学校授权或数据协议。新提交待 CI 和设备验证。

## R36 上游优先视觉基底
R35 QA 的实际观感未达到用户要求，废弃简化自制主题。以固定上游提交 bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed 的原包名引入完整主题、色板、字型、壁纸渲染/色调图/图片存储/可读性源码，恢复原分组控件的壁纸区域及动画。现有入口仅桥接 CourseSelectorTheme，主容器直接绘制原壁纸。未移植 AppCompat 跨进程主题应用，保留校序 Activity/数据与原有设备性能降级；不宣称完整页面或导航已替换。后续以整页源码及真实截图比较验收，不以组件数量估计还原度。R35 原始备份分支 backup/android-r35-before-upstream-rebase。
## R37 统一上游窗口与页面基础设施
R36 完整上游主题及壁纸源码迁移的编译门禁 45585128446 已全部通过。停止并行维护简化组件：以完整 GlassKit.kt、AnimatedCount.kt、PageLoadingIcon.kt、ModuleMotion.kt、GlassOverlayHost.kt、GlassWindowHost.kt、GlassPageScaffold.kt 为统一公共基础，删除 UpstreamGlassKit.kt 和 UpstreamGlassTextField.kt，保留原组件签名。根窗口统一独立壁纸源、页面源、模态合成源；不在采样层中绘制自己的光学结果。五个根页面及二级路由共享该窗口；普通页面经 GlassPageScaffold，设置页直接采用上游 SettingsScreen 的完整页面 Scaffold、滚动折叠、分组与账号头布局，替换不可用的上游业务入口为校序真实路由，并复用现有上游滚轮及 AppearanceSettingsManager 提供主题、预设背景和玻璃开关。原版独立设备采样门禁保留，API31/32 离屏路径继续禁用。未完成原版完整导航、课表及成绩页面业务映射，未声称视觉完全一致。此批需编译、运行和截图验收；未发布。


## 2026-11-26 文件级源码复用清点（基线 218af2741486d60499cc5e2a4b94439c26b5c32c）

以 Git tree 实际存在的 Kotlin 文件为范围：两个包的 ui、manager、Activity 与 SecondClassPage，共 120 个。固定上游 bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed。按 Git blob SHA 完全相同、同名但不同 SHA、无同名分三类。此分类是完整文件清点而非逐行等价证明；别名说明是已知映射或业务职责，不能用名称代替来源证据。13 个完全一致；52 个同名但差异需复核；55 个无同名（含已知别名和校序独有业务）。

### 19 个路由的实际容器检查

五个一级路由与 14 个子路由由 AppRoute/AppFeaturePage 分发。总览、教务、任务、通知、数据连接、账户、关于、学习进度、校园跑、第二课堂及其同步设置、提醒、备份、许可、课表设置使用 AppPageFrame；课表及个人中心走 GlassPageScaffold；成绩、考试使用移植的专用 GradesFeature 透明 Scaffold。已检查这些入口不存在自绘全屏 PageGradient；专用课表/成绩布局与局部分组颜色不是重复根背景。第二课堂兼容 Activity 已改共享 GlassWindowHost / AppPageFrame。两类官方授权/导入 WebView 是必要独立窗口，保留既有授权、回执和安全逻辑，不把学校网页替换成仿造登录。

本批实际修复：提醒开关行委托 InsetGroupedRow + LiquidSwitch，保留现有分隔和回调；修正 CI 中第二课堂缓存筛选的旧字符串断言，仍要求同一批次和记录类型，不削弱身份/数据安全约束。旧分支停止新增 UI。剩余差异必须逐个核实，尚不能声称 120 文件全部逐行复核或全部适配完成。

### 文件清单（相对 app/src/main/java）

| 文件 | SHA/同名/别名分类 | 上游候选或职责 |
|---|---|---|
| `com/tyust/course/manager/AppThemeCoordinator.kt` | 同名有差异 | `com/tyust/course/manager/AppThemeCoordinator.kt` |
| `com/tyust/course/manager/AppThemeMode.kt` | 原样一致 | `com/tyust/course/manager/AppThemeMode.kt` |
| `com/tyust/course/manager/AppearanceSettingsManager.kt` | 原样一致 | `com/tyust/course/manager/AppearanceSettingsManager.kt` |
| `com/tyust/course/manager/WallpaperImageStore.kt` | 原样一致 | `com/tyust/course/manager/WallpaperImageStore.kt` |
| `com/tyust/course/manager/WallpaperToneMap.kt` | 原样一致 | `com/tyust/course/manager/WallpaperToneMap.kt` |
| `com/tyust/course/ui/system/GlassCompat.kt` | 原样一致 | `com/tyust/course/ui/system/GlassCompat.kt` |
| `com/tyust/course/ui/system/GlassIndication.kt` | 原样一致 | `com/tyust/course/ui/system/GlassIndication.kt` |
| `com/tyust/course/ui/system/GlassRuntimeGuard.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassRuntimeGuard.kt` |
| `com/tyust/course/ui/system/WallpaperAppearance.kt` | 原样一致 | `com/tyust/course/ui/system/WallpaperAppearance.kt` |
| `com/tyust/course/ui/system/WallpaperReadability.kt` | 原样一致 | `com/tyust/course/ui/system/WallpaperReadability.kt` |
| `com/tyust/course/ui/system/WallpaperRenderer.kt` | 原样一致 | `com/tyust/course/ui/system/WallpaperRenderer.kt` |
| `com/tyust/course/ui/theme/AppAppearance.kt` | 原样一致 | `com/tyust/course/ui/theme/AppAppearance.kt` |
| `com/tyust/course/ui/theme/AppSystemBars.kt` | 原样一致 | `com/tyust/course/ui/theme/AppSystemBars.kt` |
| `com/tyust/course/ui/theme/Color.kt` | 原样一致 | `com/tyust/course/ui/theme/Color.kt` |
| `com/tyust/course/ui/theme/Theme.kt` | 同名有差异 | `com/tyust/course/ui/theme/Theme.kt` |
| `com/tyust/course/ui/theme/Type.kt` | 原样一致 | `com/tyust/course/ui/theme/Type.kt` |
| `io/github/x07xh/xiaoxu/LocalQingguoImportActivity.kt` | 无同名 | 校序原课表导入 WebView |
| `io/github/x07xh/xiaoxu/MainActivity.kt` | 同名有差异 | `com/tyust/course/MainActivity.kt` |
| `io/github/x07xh/xiaoxu/NativeSchoolConnectActivity.kt` | 无同名 | 校序原授权 WebView |
| `io/github/x07xh/xiaoxu/SecondClassHomeActivity.kt` | 无同名 | 校序兼容入口 / GlassWindowHost + AppPageFrame |
| `io/github/x07xh/xiaoxu/SecondClassPage.kt` | 无同名 | 校序业务 / 上游分组与弹窗 |
| `io/github/x07xh/xiaoxu/ui/AboutFeature.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/AcademicSnapshotList.kt` | 无同名 | GradesScreen.kt（学期与读取操作） |
| `io/github/x07xh/xiaoxu/ui/AnimatedCount.kt` | 同名有差异 | `com/tyust/course/ui/system/AnimatedCount.kt` |
| `io/github/x07xh/xiaoxu/ui/AnimatedIconSpec.kt` | 同名有差异 | `com/tyust/course/ui/system/AnimatedIconSpec.kt` |
| `io/github/x07xh/xiaoxu/ui/AnimatedStateIcon.kt` | 同名有差异 | `com/tyust/course/ui/system/AnimatedStateIcon.kt` |
| `io/github/x07xh/xiaoxu/ui/AppDialog.kt` | 无同名 | SystemUi.kt（SystemDialogContent） |
| `io/github/x07xh/xiaoxu/ui/AppFeaturePages.kt` | 无同名 | 校序业务路由绑定 |
| `io/github/x07xh/xiaoxu/ui/AppPageContract.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/AppPageFrame.kt` | 无同名 | GlassPageScaffold / SystemTopBar 的业务适配 |
| `io/github/x07xh/xiaoxu/ui/AppSymbolSpec.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/AppWorkspaceState.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/CampusRunFeature.kt` | 无同名 | 校序原校园跑逻辑 / 上游分组 |
| `io/github/x07xh/xiaoxu/ui/CollapsingGlassHeader.kt` | 同名有差异 | `com/tyust/course/ui/system/CollapsingGlassHeader.kt` |
| `io/github/x07xh/xiaoxu/ui/CourseEditorDialog.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/DampedDragAnimation.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/DampedDragAnimation.kt` |
| `io/github/x07xh/xiaoxu/ui/DialogHost.kt` | 同名有差异 | `com/tyust/course/ui/system/DialogHost.kt` |
| `io/github/x07xh/xiaoxu/ui/DragGestureInspector.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/DragGestureInspector.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassChipAppearance.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassChipAppearance.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassFilterCapsule.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassFilterCapsule.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassFormField.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassFormField.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassFrameQueue.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassFrameQueue.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassKit.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassKit.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLens.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLens.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLensCapture.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLensCapture.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLensEngine.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLensEngine.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLensFrameIdentity.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLensFrameIdentity.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLensFreshness.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLensFreshness.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLensGeometry.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLensGeometry.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLensRegion.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLensRegion.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLensRenderer.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLensRenderer.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassLensShader.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassLensShader.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassMaterial.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassMaterial.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassOverlayHost.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassOverlayHost.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassPageScaffold.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassPageScaffold.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassPerformancePolicy.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassPerformancePolicy.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassSheetSurface.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/GlassSheetSurface.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassWheelPicker.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassWheelPicker.kt` |
| `io/github/x07xh/xiaoxu/ui/GlassWindowHost.kt` | 同名有差异 | `com/tyust/course/ui/system/GlassWindowHost.kt` |
| `io/github/x07xh/xiaoxu/ui/GradesFeature.kt` | 无同名 | GradesScreen.kt / MeasuredGradesHeader.kt |
| `io/github/x07xh/xiaoxu/ui/ImmediateRouteTransition.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/InitialPageLoad.kt` | 同名有差异 | `com/tyust/course/ui/system/InitialPageLoad.kt` |
| `io/github/x07xh/xiaoxu/ui/LocalRowsState.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/LocalTaskBackupPanel.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/LocalTaskDependencyGraph.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/LocalTaskEditSheet.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/MessageCenterFeature.kt` | 无同名 | 校序原通知逻辑 / 上游分组、弹窗 |
| `io/github/x07xh/xiaoxu/ui/ModuleMotion.kt` | 同名有差异 | `com/tyust/course/ui/theme/ModuleMotion.kt` |
| `io/github/x07xh/xiaoxu/ui/MotionTokens.kt` | 同名有差异 | `com/tyust/course/ui/theme/MotionTokens.kt` |
| `io/github/x07xh/xiaoxu/ui/NavScrollIntent.kt` | 同名有差异 | `com/tyust/course/ui/system/NavScrollIntent.kt` |
| `io/github/x07xh/xiaoxu/ui/NavigationIconMotion.kt` | 同名有差异 | `com/tyust/course/ui/system/NavigationIconMotion.kt` |
| `io/github/x07xh/xiaoxu/ui/NavigationIconPlayback.kt` | 同名有差异 | `com/tyust/course/ui/system/NavigationIconPlayback.kt` |
| `io/github/x07xh/xiaoxu/ui/NavigationMaterialPolicy.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/OpenSourceCredits.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/PageDataState.kt` | 同名有差异 | `com/tyust/course/ui/system/PageDataState.kt` |
| `io/github/x07xh/xiaoxu/ui/PageLoadingIcon.kt` | 同名有差异 | `com/tyust/course/ui/system/PageLoadingIcon.kt` |
| `io/github/x07xh/xiaoxu/ui/PhosphorNavigationIcon.kt` | 同名有差异 | `com/tyust/course/ui/system/PhosphorNavigationIcon.kt` |
| `io/github/x07xh/xiaoxu/ui/ProfileFeature.kt` | 无同名 | SettingsScreen.kt（SettingsRow） |
| `io/github/x07xh/xiaoxu/ui/RebuildCapsuleDock.kt` | 无同名 | CapsuleNavigationBar.kt |
| `io/github/x07xh/xiaoxu/ui/RebuildReadContracts.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/RebuildShell.kt` | 无同名 | MainActivity.kt（路由、窗口与导航集成适配） |
| `io/github/x07xh/xiaoxu/ui/RebuildTheme.kt` | 无同名 | CourseSelectorTheme（Theme.kt 桥接） |
| `io/github/x07xh/xiaoxu/ui/ReminderSettingsFeature.kt` | 无同名 | 校序原提醒业务 / InsetGroupedRow + LiquidSwitch |
| `io/github/x07xh/xiaoxu/ui/ScheduleBottomSheetState.kt` | 同名有差异 | `com/tyust/course/ui/system/ScheduleBottomSheetState.kt` |
| `io/github/x07xh/xiaoxu/ui/ScheduleDateText.kt` | 同名有差异 | `com/tyust/course/ui/screen/ScheduleDateText.kt` |
| `io/github/x07xh/xiaoxu/ui/ScheduleDetail.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/ScheduleDomain.kt` | 同名有差异 | `com/tyust/course/schedule/ScheduleDomain.kt` |
| `io/github/x07xh/xiaoxu/ui/ScheduleFeature.kt` | 无同名 | 共享课表源码的数据绑定 |
| `io/github/x07xh/xiaoxu/ui/ScheduleFocus.kt` | 同名有差异 | `com/tyust/course/ui/screen/ScheduleFocus.kt` |
| `io/github/x07xh/xiaoxu/ui/ScheduleHeader.kt` | 同名有差异 | `com/tyust/course/ui/screen/ScheduleHeader.kt` |
| `io/github/x07xh/xiaoxu/ui/ScheduleLocation.kt` | 同名有差异 | `com/tyust/course/schedule/ScheduleLocation.kt` |
| `io/github/x07xh/xiaoxu/ui/ScheduleMotionProfile.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/ScheduleOverlap.kt` | 同名有差异 | `com/tyust/course/schedule/ScheduleOverlap.kt` |
| `io/github/x07xh/xiaoxu/ui/ScheduleSettingsFeature.kt` | 无同名 | ScheduleSettingsScreen.kt |
| `io/github/x07xh/xiaoxu/ui/ScheduleWeekLabel.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/ScheduleWeekPagerSync.kt` | 同名有差异 | `com/tyust/course/schedule/ScheduleWeekPagerSync.kt` |
| `io/github/x07xh/xiaoxu/ui/ScreenMetrics.kt` | 同名有差异 | `com/tyust/course/ui/system/ScreenMetrics.kt` |
| `io/github/x07xh/xiaoxu/ui/SecondClassAutoReadControls.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/SettingsScreen.kt` | 同名有差异 | `com/tyust/course/ui/screen/SettingsScreen.kt` |
| `io/github/x07xh/xiaoxu/ui/StudyProgressFeature.kt` | 无同名 | 校序业务 / 上游分组与空状态 |
| `io/github/x07xh/xiaoxu/ui/TaskExecutionPanel.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/TaskExecutionRows.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/TasksFeature.kt` | 无同名 | 校序原任务数据逻辑 / 上游分组、弹窗 |
| `io/github/x07xh/xiaoxu/ui/ThumbLens.kt` | 同名有差异 | `com/tyust/course/ui/system/glass/ThumbLens.kt` |
| `io/github/x07xh/xiaoxu/ui/TimetableImportPreviewSheet.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/UpstreamCourseDetailSheet.kt` | 无同名 | CourseDetailContent |
| `io/github/x07xh/xiaoxu/ui/UpstreamDayCourse.kt` | 无同名 | ScheduleAgendaContent |
| `io/github/x07xh/xiaoxu/ui/UpstreamExamCard.kt` | 无同名 | 考试卡片 |
| `io/github/x07xh/xiaoxu/ui/UpstreamGradeCard.kt` | 无同名 | GradeItemRow |
| `io/github/x07xh/xiaoxu/ui/UpstreamHeaderInterpolation.kt` | 无同名 | CollapsingGlassHeader.kt |
| `io/github/x07xh/xiaoxu/ui/UpstreamLiquidSegmentedControl.kt` | 无同名 | LiquidSelectionComponents.kt |
| `io/github/x07xh/xiaoxu/ui/UpstreamLiquidSwitch.kt` | 无同名 | LiquidComponents.kt |
| `io/github/x07xh/xiaoxu/ui/UpstreamNavTab.kt` | 无同名 | CapsuleNavigationBar.kt |
| `io/github/x07xh/xiaoxu/ui/UpstreamPhysicalLens.kt` | 无同名 | PhysicalLensSurface |
| `io/github/x07xh/xiaoxu/ui/UpstreamScheduleGlass.kt` | 无同名 | ScheduleGlass |
| `io/github/x07xh/xiaoxu/ui/UpstreamStatStrip.kt` | 无同名 | SystemStatStrip |
| `io/github/x07xh/xiaoxu/ui/UpstreamSystemUi.kt` | 无同名 | SystemUi.kt |
| `io/github/x07xh/xiaoxu/ui/UpstreamTimetable.kt` | 无同名 | 课表 TimetableLayout / TimetableBackground / CourseCard |
| `io/github/x07xh/xiaoxu/ui/WechatTaskLinkActions.kt` | 无同名 | 待复核来源或校序专用适配 |
| `io/github/x07xh/xiaoxu/ui/WorkspaceTaskLogic.kt` | 无同名 | 待复核来源或校序专用适配 |
