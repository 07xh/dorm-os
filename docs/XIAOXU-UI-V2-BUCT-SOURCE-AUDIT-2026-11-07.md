# 校序 Native UI V2｜北化源码映射、许可和依赖审查（第一阶段）

日期：2026-11-07。状态：**审查与架构锁定，不含运行时代码迁移**。

## 0. 审查基线与重要限制

- 校序：07xh/dorm-os，审查基线 main `cc8c35e3b12ba7809334ff7482ee5e7d676749ad`；Android 模块 apps/xiaoxu-android，当前 main 的版本 0.8.10。
- 参考源码：[znjhahaha/zhengfang-apk](https://github.com/znjhahaha/zhengfang-apk)，审查了 README、LICENSE、app/build.gradle 以及下表对应实际源文件。该项目当前 main 不保证与用户持有的 APK 1.0.8.3 完全同版，**禁止未经版本对应核验就声称某个 Kotlin 文件是该 APK 的原始实现**。
- 参考 APK：用户 Library /北化课表/北化课表_1.0.8.3.apk，39,578,493 bytes，SHA-256 `e6d91e1cd81459d9f6467086820ab1cd216f4e740958e3d90ecb519ddbe50dd4`；已检查 ZIP 结构，尚未完成逐控件资源或 dex 反编译映射。
- 校序当前 [PR #351](https://github.com/07xh/dorm-os/pull/351) 为 Draft，覆盖 MainActivity、FloatingBottomBar、XiaoxuDesignSystem、RealDataWorkspace、ScheduleScreen、WeeklyTimetableGrid、TaskBoard、MessageCenter 等关键文件，且引入 CourseEditorDialog、SemesterSettingsDialog 和课表本机数据类。**不得与 #351 并行改写同一批文件**，待集成基线确定后重新核对符号。
- 本文不是“可以任意复制源码”的批准书；实现与分发前需完成许可证审查及设备对照。

## 1. 逐模块映射（上游相对 app/src/main/java/com/tyust/course/；校序相对 apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/）

| 北化源文件 | 对应校序当前实际文件/函数 | 工作决定 | 原因 / 明确禁区 |
|---|---|---|---|
| ui/system/GlassKit.kt | ui/XiaoxuDesignSystem.kt：XiaoxuCard、MetricCard、SectionHeader、StatusChip | **改现有**；统一玻璃卡、分组和统计样式 | GlassKit 依赖北化自己的 WallpaperAppearance、MotionSpring 和多项 ui/system 类型，不是独立复制件；禁止再造 GlassCard 类并行使用 |
| ui/system/GlassMaterial.kt、ContentSurface.kt | ui/XiaoxuDesignSystem.kt：XiaoxuTheme、XiaoxuPalette、XiaoxuRadius | **抽象视觉规则**，保留唯一主题 | 全局背景和玻璃材质由一个来源决定，普通卡片先用低成本 alpha 混合 |
| ui/system/CapsuleNavigationBar.kt | ui/FloatingBottomBar.kt、MainActivity.NativeShell、ui/nav/* | **改现有导航** | 校序已复用 SleepDown 动画/手势驱动；北化底栏又引用 com.kyant.backdrop、shapes 与内部 glass 体系；不允许直接覆盖或叠加第二个底栏 |
| ui/system/ScreenMetrics.kt、CapsuleNavigationBar.NavBarMetrics | ui/FloatingBottomBar.kt、MainActivity.NativeShell | **统一尺寸/Insets 契约** | nav 高度和内容底部避让必须从同一份指标算，不得每页硬编码 96dp |
| ui/system/GlassPageScaffold.kt、SystemUi.kt | MainActivity.NativeShell、ui/XiaoxuDesignSystem.kt：XiaoxuTopBar | **维持唯一 Scaffold** | 根 Shell 管理安全区、全局顶/底栏；二级页不得再制造全局重复标题与顶栏 |
| ui/system/GlassWindowHost.kt、WallpaperRenderer.kt | XiaoxuTheme + NativeShell 全局背景（待从 PR #351 确认最终归属） | **仅参考设计** | 禁止叠第二套捕获窗口/壁纸宿主 |
| ui/system/glass/GlassLensEngine.kt、GlassLensCapture.kt | 无直接对应；当前 FloatingBottomBar 为轻量 Compose 渲染 | **本阶段拒绝导入** | EGL/OpenGL 环境、纹理捕获、线程/降级生命周期是新依赖和高复杂度；先做真机帧耗时基准 |
| ui/system/GlassPerformancePolicy.kt | 未来统一 MaterialPolicy，可由 XiaoxuDesignSystem 同一所有者管理 | **只记录候选** | 不要为审查新建运行时枚举；性能测量前不引入多套参数系统 |
| ui/screen/ScheduleScreen.kt | ui/ScheduleScreen.kt：CloudTimetablePanel、ScheduleVerifiedContent、DayLessonList、WeekSelector | **保留业务，调整视图** | 校序 VerifiedLesson、LocalScheduleStore 与本机来源门禁不变 |
| ui/screen/ScheduleCourseSheet.kt、CourseDetailContent.kt | ui/ScheduleScreen.kt：HomeCourseDetailSheet、CourseDetailBottomSheet + XiaoxuSheet | **合并交互，保留唯一 Sheet 宿主** | 底部拖动、回弹、返回值得借鉴；不要把官方 course id 或假教学周作为真实数据 |
| ui/system/ScheduleBottomSheetState.kt | ui/XiaoxuDesignSystem.kt：XiaoxuSheet（未来独立内部 motion helper） | **候选改写** | 单个状态对象管理 drag/settle/cancel；GPL 复制前须许可证放行 |
| ui/screen/ScheduleSettingsScreen.kt | **PR #351**：ui/SemesterSettingsDialog.kt、ui/CourseEditorDialog.kt；main 当前课表实现 | **待 #351 基线合并再映射** | 使用已有教学周、节次、调课、备份和提醒，不重复造存储 |
| ui/screen/GradesScreen.kt、ui/route/GradesRoute.kt、ui/screen/GradeRowIdentity.kt | MainActivity.AcademicPage(Grades)；现有 CloudClient 只读接口 | **外观与稳定 key 策略参考** | 成绩尚未真实验收，禁止引入 DemoData 或复用北化的账号/教务接口 |
| ui/system/TaskModeBar.kt、GlassSearchFilterPanel.kt | ui/RealDataWorkspace.kt：RealAcademicTaskHub、WorkspaceTaskDetailSheet；ui/MessageCenter.kt | **共享选择控件，不共享数据** | 任务中心与消息中心各自保留真实来源、筛选/审核逻辑 |
| ui/screen/SettingsScreen.kt、ui/system/InsetGroupedSection | MainActivity.ConnectionsPage；ui/CloudPanels.kt：ConnectionsPanel | **采用分组设置样式** | “我的 → 学校数据连接”是唯一学校授权入口，不再建独立登录器 |
| ui/system/CollapsingGlassHeader.kt | ui/XiaoxuDesignSystem.kt：XiaoxuTopBar，及各页滚动状态 | **只借交互** | 顶栏背景/文字图层分离，避免标题随着玻璃透明度一起变淡 |
| demo/DemoData.kt | **无映射** | **禁止引入** | 任何北化演示课程、成绩、选课都不能落入校序生产数据/代码路径 |

### 校序现成能力（不重造）

- ui/XiaoxuDesignSystem.kt 约 877 行，拥有 Palette / Radius / Spacing / Theme / Card / Hero / MetricCard / CourseTile / SectionHeader / Badge / Sheet / 四类状态 / TopBar。
- ui/FloatingBottomBar.kt 约 418 行，已实现悬浮导航、触控阈值、拖拽、取消回弹和局部重组；ui/nav 中有来源声明。
- MainActivity.NativeShell 拥有五主目的地、消息二级页、rememberSaveableStateHolder、CloudClient 生命周期同步、平板 NavigationRail。
- ui/ScheduleScreen.kt、ui/WeeklyTimetableGrid.kt、ui/RealDataWorkspace.kt、ui/MessageCenter.kt、ui/CloudPanels.kt、SecondClassPage.kt 为业务归属。
- 数据层（Room / LocalScheduleStore / CloudClient / Cloudflare 服务端）独立于 UI 重构，**不可因为页面复刻而删除或重命名业务来源接口**。

## 2. 许可证审查

| 来源 | 已核验声明 | 处理结果 |
|---|---|---|
| 北化 zhengfang-apk | 根 LICENSE 为 GNU GPLv3 | **禁止直接复制到校序分发树，直到相容性方案审查通过**。直接集成代码可能触发 GPLv3 对组合/衍生作品的许可与源码分发义务 |
| SleepDown-Schedule | 校序 THIRD_PARTY_NOTICES.md 已披露署名非商业、源码可见许可 1.1；非 OSI 开源许可 | 当前校序 ui/nav/DampedDragAnimation.kt、DragGestureInspector.kt 是已有派生文件；其非商业限制可能与 GPLv3 的“不得增加附加限制”要求不兼容，不能简单把两者代码合到一个应用后宣称合法 |
| Kyant AndroidLiquidGlass 上游 | 校序现有来源文件头声明 Apache-2.0；北化引用 io.github.kyant0:backdrop/shapes | 逐文件确认版权/许可及依赖发布许可，不能推断整个北化 glass 目录都能以 Apache-2.0 复用 |
| 拾光青果适配 | 校序 THIRD_PARTY_NOTICES.md 所载 MIT | 原学校解析代码保留，署名与许可条件不变 |

**许可证决策：**当前仅允许借鉴交互方案、参考布局比例、独立制作校序视觉资产及自有实现。若要直接复用北化的 GPLv3 源文件，必须先决定兼容的整体许可/替换 SleepDown 限制性代码/取得必要授权，并保存复制文件的来源 SHA、原作者、修改清单及许可。个人非商用不自动消除全部复制和再分发义务。最终具体法律判断须以许可证文本和实际组合方式为准。

禁止复制原应用图标、学校标识、个人资料、演示数据或无来源资产。

## 3. 构建与依赖审查

| 项目 | 校序 main | 北化源码当前 main | 结论 |
|---|---|---|---|
| Android Gradle Plugin | 8.13.0 | 9.1.0 | **不升级校序工具链** |
| Kotlin 插件 | 2.2.20 | 2.4.20 | 源文件不能未经适配直接复制 |
| compileSdk | 36 | 37.0 | 不为了玻璃特效升级 compileSdk |
| minSdk | 26 | 24 | 当前最低版本无需改变 |
| Compose BOM | 2026.03.01 | 2026.01.00 | 具体 API 兼容逐项编译确认，禁止整包替换 |
| 玻璃依赖 | 当前无 Kyant backdrop/shapes 声明 | backdrop 2.0.0 / shapes 1.2.0 | 本阶段 **零新增依赖** |
| Baseline Profile | 已配置插件、profileinstaller 和 :benchmark | 独立性能基础设施 | 校序已有基础设施，不能按旧研究文档再重复引入 |
| 本机数据 | Room / 本机课表 / CloudClient | 北化自有学校协议与管理器 | 不迁移北化业务或账号层 |

参考项目还引入 GeckoView、地图、ONNX、脚本运行时及多种遗留视图库，这些均不是 UI 复刻所需依赖，严禁随 UI 打包引入。

## 4. 与当前 PR 冲突清单

- [PR #351](https://github.com/07xh/dorm-os/pull/351)：**唯一整合主线，当前 Draft**；现阶段先审核，不另起同名 UI 代码实现。
- [PR #335](https://github.com/07xh/dorm-os/pull/335)、#338、#327、#329：存在 UI/课表文件重叠，须先与 #351 收敛，以免后合并覆盖新设计。
- #351 已包含部分课表设置、编辑、提醒与旧代码清理；不能因 main 未出现某新文件就断言其未实现。
- 真机/官方数据验收不因架构文档或 PR 可合并而自动变为通过。

## 5. 实施门禁

1. 先把参考 APK 截图按页面/控件编号，标出准确尺寸、圆角、色值、层级、状态和手势；允许后续设计稿接近原版，但品牌素材独立制作。
2. 从当前 main 与 #351 选定**唯一真实整合基线**，仅之后修改现有 DS / Nav / Schedule / Message / Task 文件。
3. 先落实通用组件接口契约，再按课程详情→课表→成绩→任务→消息→首页→我的逐步迁移，注意数据真实来源与异常状态。
4. 每一批保留旧功能测试，编译验证、真机视觉对照、滚动与启动 Macrobenchmark，检查底栏与系统手势安全区。
5. 新版本实际验证完成后再删除无引用旧 UI；没有复刻验收不得清理尚有业务回调的旧文件。

**结论：不直接拷贝北化源码；先在已有 XiaoxuDesignSystem 和 FloatingBottomBar 上收敛唯一接口，并在许可与性能门禁通过后按最小批次改造。**
