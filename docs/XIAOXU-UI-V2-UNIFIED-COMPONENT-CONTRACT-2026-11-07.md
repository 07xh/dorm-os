# 校序 Native UI V2｜唯一 Compose 组件接口契约（设计锁定草案）

日期：2026-11-07。本文件只定义**现有组件的单一所有者与可演进接口**；本 PR 不制造空壳组件、不改变运行时行为。适用基线：main cc8c35e + 待整合 PR #351。

## 1. 必须保留的架构所有权

| 层 | 唯一归属 | 禁止事项 |
|---|---|---|
| App 根主题与颜色 | ui/XiaoxuDesignSystem.kt 的 XiaoxuTheme、Palette、Radius、Spacing | 每页自建主题、独立壁纸 Host 或第二套 CSS/网页设计系统 |
| 根 Scaffold、边到边 Insets、主路由 | MainActivity.NativeShell + AcademicNavigation.kt | 某个业务页另造全局 Shell、二层固定底栏或替换导航目的地 |
| 悬浮导航 | ui/FloatingBottomBar.kt + ui/nav 动画工具 | 复制北化 CapsuleNavigationBar 再同时保留旧栏 |
| 顶部栏 | XiaoxuTopBar（只有根 Shell 或真正独立全屏二级页负责） | 一级页自行叠加同级品牌/标题工具栏 |
| 卡片、颜色、状态 | XiaoxuCard、MetricCard、StatusChip、XiaoxuLoading/Empty/Error/Unverified/Disconnected | 用多个私有 GlassCard 改色独立演化 |
| 通用弹层 | XiaoxuSheet | 页面分别创建不同容器和不同遮罩策略 |
| 业务详情 | ScheduleScreen / RealDataWorkspace / MessageCenter / SecondClassPage 等 feature 文件 | 把业务数据字段/云端真实状态搬进 DesignSystem |
| 官方数据与保存 | CloudClient / LocalScheduleStore / Room / SchoolAuthReturn | UI 对未知官方数据构造成功状态、重复连接器 |

## 2. 现有公开接口（审查于 main；优先保持兼容）

~~~kotlin
@Composable
fun XiaoxuTheme(content: @Composable () -> Unit)

@Composable
fun XiaoxuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(XiaoxuRadius.card),
    container: Color = XiaoxuPalette.Surface,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = XiaoxuSpacing.card,
    content: @Composable ColumnScope.() -> Unit
)

@Composable
fun XiaoxuTopBar(
    title: String,
    subtitle: String? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onMessages: (() -> Unit)? = null
)

@Composable
fun XiaoxuSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
)

@Composable
fun FloatingBottomBar(
    items: List<FloatingBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = XiaoxuPalette.Surface,
    indicatorColor: Color = XiaoxuPalette.BrandSoft,
    unselectedContentColor: Color = XiaoxuPalette.Ink3,
    selectedContentColor: Color = XiaoxuPalette.BrandDeep,
    showLabels: Boolean = true,
    applyNavigationBarInset: Boolean = true,
    draggable: Boolean = true
)
~~~

现有接口在内部迁移前不得破坏；如果最终需要参数变更，先加向后兼容可选参数，逐个更新调用者，最后根据静态引用和 UI 测试删除旧参数。此处只是接口摘录，**不是要求创建一份新的 Kotlin 同名实现**。

## 3. V2 扩展接口（目标草案，不是已实现源码）

### 3.1 全局背景和材质

统一的主题配置通过同一所有者读取，不允许各页面提供不同渐变：

~~~kotlin
// 规划：同一个 DesignSystem 文件/模块中扩展，勿另建独立主题。
enum class XiaoxuSurfaceStyle { Solid, SoftGlass, InteractiveLens }
data class XiaoxuVisualSpec(
    val style: XiaoxuSurfaceStyle = XiaoxuSurfaceStyle.SoftGlass,
    val reducedMotion: Boolean = false
)

// 卡片仍叫 XiaoxuCard，增加 style 之类的可选参数。
// 默认 SoftGlass；如果高对比度、GPU 不支持或降级，应保持可读的 Solid。
~~~

材质渲染优先级：普通列表/内容卡片使用半透明合成；互动指示器是否使用真实折射要由设备能力和真机帧耗时决定。不允许把折射捕获直接塞进所有卡片。

### 3.2 唯一布局指标与 Insets

~~~kotlin
// 规划：仅由根 Shell 和 FloatingBottomBar 共用，不由页面各自写死。
data class XiaoxuChromeMetrics(
    val horizontalMargin: Dp,
    val barTrackHeight: Dp,
    val systemNavigationInset: Dp,
    val contentBottomPadding: Dp
)
~~~

- 系统状态栏由 Shell/topBar 消费一次；底部系统导航栏由 Shell/bar 协同消费一次。
- 列表 contentPadding 底部来自真实 bar 尺寸 + system inset，不再独立硬编码 96dp。
- 页面切换保留原 rememberSaveableStateHolder；周次/筛选/滚动/详情选择状态不因背景更新重置。
- 大屏仍保持现有 NavigationRail/双栏分支，不强迫手机浮动栏成为平板的底部栏。

### 3.3 新增通用控件的唯一接口

仅在功能页面迁移确实需要时才补代码，先指定这些名字与所有者：

~~~kotlin
// 计划在 XiaoxuDesignSystem 中实现，与现有 XiaoxuCard 共用 token。
data class XiaoxuOption(val key: String, val title: String)

@Composable
fun XiaoxuSegmentedControl(
    options: List<XiaoxuOption>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun XiaoxuGroupedSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
)
~~~

分段控件服务成绩（学期/总体/考试）、课表（周/日；最终位置按用户设定）、二课（活动/我的活动/积分）、任务/消息的合理筛选；**其内容选项由业务页提供，不允许共用一个固定导航模板**。分组控件服务我的/设置；不应再给各业务页引入第二个 SettingsHost。

### 3.4 详情、弹层与返回

- 继续以 XiaoxuSheet 为唯一通用容器；优先强化它的手势与预测性返回，而不是将北化 ScheduleBottomSheetState 原文件直接复制。
- 课程详情和必要的只读预览使用该容器；任务详情采用独立内容页（用户已确认），不是强制所有详情都展开卡片。
- 成绩列表可原地展开子项；其他列表默认跳详情。组件层只管动画/布局，不决定业务模式。
- Sheet 拥有单一 dismiss 回调；取消/恢复不能触发保存。点击区以 >=48dp 为原则，保留无障碍 semantics。

## 4. 每个页面的组件接入协议

| 页 | 根内容所有者 | 应使用的共用控件 | 数据来源不变 |
|---|---|---|---|
| 今日 | RealAcademicHome | XiaoxuCard / CourseTile / MetricCard | LocalScheduleStore + 任务 + 官方通知 |
| 课表 | CloudTimetablePanel / ScheduleVerifiedContent / WeeklyTimetableGrid | SegmentedControl / XiaoxuSheet / CourseTile | 本人确认的 VerifiedLesson |
| 教务总览 | AcademicPage（以 PR #351 集成为准） | QuickEntry / GroupedSection | CloudClient |
| 成绩/考试 | AcademicPage.GRADES / EXAMS，后续可拆业务内容文件 | MetricCard / GroupedSection / 可展开 GradeRow | 官方可信成绩/考试；未验证保持空态 |
| 学习通 | ChaoxingHomeworkPanel / 既有连接流程 | GroupedSection / 课程作业列表 / 详情页 | CloudClient + 官方本人状态 |
| 第二课堂 | SecondClassPage | SegmentedControl / GroupedSection | 官方本人活动和积分 / 已保存本机快照 |
| 任务 | RealAcademicTaskHub / WorkspaceTaskDetailSheet | SegmentedControl / XiaoxuCard / 状态组件 | Room + cloud tasks + 学习通映射 + 微信已审核 |
| 消息 | MessageCenterPanel / NoticeDetailSheet | 分段/搜索 / XiaoxuCard / Sheet | 官方通知 + 微信候选审核 |
| 我的 | ConnectionsPage / ConnectionsPanel | GroupedSection / XiaoxuCard / StatusChip | 云端和本机身份/连接状态 |

状态语义：未连接 ≠ 无数据；读取失败 ≠ 空记录；本机快照 ≠ 本轮可信刷新；历史成绩/任务不自动升级成官方已核验结果。

## 5. 迁移与代码清退协议

1. **检查 PR #351 之后最终文件**：先落地唯一的 NativeShell、XiaoxuDesignSystem 和 FloatingBottomBar；确认 #335/#338/#327/#329 不反向覆盖这些文件。
2. 创建北化 1.0.8.3 的截图规格表（截图编号、目标 dp、颜色、动态状态、按压/滚动/弹层录像）；源代码当前 main 与 APK 版本差异须标注。
3. 只在现有 DesignSystem 内新增必要 token/组件；先迁移一张真实课程详情和一张真实成绩空态，验证一致性后扩散。
4. 每次替换强制搜索旧组件引用、测试绑定、AndroidManifest 入口与数据存储调用；只有不可达、没有引用且通过回归时才删除旧类。
5. 旧代码清退以删除“重复 UI 实现”为目标，不删 Room DAO、授权回调、学校读取入口、微信审核、课表迁移和本人记录。
6. 新依赖必须逐项提交许可、API/AGP/Compose 兼容矩阵、包体积增加及真机性能数据；本阶段不新增。
7. 性能质量门：同一 Android 16 真机同场景比较冷启动、五页切换、周课表滑动和 Sheet 展开，Macrobenchmark/JankStats 报告采用可复现数据；减动效及软件降级保证可用。

## 6. 审查门禁与责任边界

- UI 代码实现前检查当前 main、最新 PR、CI 和真机已验收项；必要时先合并/关闭冲突的 UI 主线，再编辑代码。
- 只有在上游 GPLv3 与当前限制性代码许可兼容方案有结论后才能直接复用相应文件。否则只参考可观察的交互和自制视觉资产。
- DeepSeek/Claude 的 UI 补丁只能针对被明确分配的现有文件，不得自行增加第二套主题、导航、登录器或后端。
- 该接口契约**不表示已经实现**；每一项标注为 existing/planned/blocked，不把文档完成标记为正式视觉或功能验收。

## 7. 本阶段实际交付

- [x] main 当前模块归属审查及北化文件映射
- [x] 源码许可证/主要依赖风险清单
- [x] 单一主题、Shell、导航、组件、状态和业务责任界面
- [x] 标识 PR #351 等交叉变更和合并顺序
- [ ] 上游截图/源码逐控件尺寸与原 APK 版本关联测试
- [ ] GPLv3 / SleepDown 许可兼容方案落实
- [ ] Kotlin 源文件调整、CI/编译与真机性能对照
