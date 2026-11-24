# 校序 UI V2

目标：独立重构原生表现层，不修改经验证的接口、业务、数据库、登录会话与同步。V1 正式版保留至 V2 完成功能及性能验收。禁止在五个旧页面继续堆叠临时样式；迁移期间旧组件只通过兼容入口接入。

## 当前切片

- `ui/v2/UiV2Shell.kt`：导航外壳与页面状态分离，底栏仍复用北化玻璃实现，页面状态由原 NativeShell 持有。
- `UiV2Window.kt`：统一手机、平板及短横屏窗口分级；600/840dp 宽度、480dp 高度边界。
- `UiV2Motion.kt`：统一触控反馈，工具栏、可点击卡与选择器已接入。优先图层更新，点击回调不等待动画。
- 原主导航即时切换、每页 saveable 状态和业务回调原样保留。
- 当前不代表五页已全部换新。

## 上游及许可

DSH Go 固定参考提交： https://github.com/ccch1mneyyy/dsh-Go/tree/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0

- AppMotion: https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/theme/motion.dart （MIT 标注，Copyright 2026 CikeSeven；当前 Compose 实现参考其参数及原则，非复制 Dart 文件）。
- AdaptiveLayout: https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/shell/adaptive/adaptive_layout.dart （窗口尺寸规则参考；独立 Compose 实现）。
- 第三方声明： https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/THIRD_PARTY_NOTICES.md
- 优先源码级移植明确 MIT 授权的通用组件；如实际移植，随代码和 APK 保留完整许可、版权、来源与修改声明。对 AGPL 文件须先处理校序整体许可及分发义务；不复用不在授权范围内的品牌、美术或专有资产。

## 迁移与验收

1. V2 shell 与 motion：构建、窗口边界单测、快速连续切页、深链接与返回栈、无障碍点击及系统动画比例。
2. V2 通用弹层、主题、输入及列表：统一关闭语义、输入法安全区、未保存内容保护，避免多层模糊。
3. 总览、课表、校园、任务、我的五页逐页迁移；保留旧版真实记录与业务回调的功能对照。
4. 平板、横屏和动态窗口；详情面板与返回手势在单页测量基线之上引入，禁止同时重组两个重型页面。
5. 使用已有 NavigationFrameBenchmark 和 StartupBenchmark 比较 V1/V2，记录设备、刷新率、构建类型、P50/P95/P99 帧耗时及掉帧。高刷为目标而非无实测保证。
6. 通过同包升级、本地数据库、通知、会话、课表和任务回归后，才清除已无引用的 V1 UI、发布 APK。

本分支尚未构建或真机验收。

## 第二批：通用短内容弹层

- `UiV2SheetPolicy` 统一短内容浮层几何与下拉判定；`UiV2SheetDrag` 实现把手跟手平移、释放后回位、位置/速度关闭判定以及点击关闭。参考 DSH Go 的 MIT 标注 [app_sheet.dart](https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/common/app_sheet.dart)，为独立 Compose 实现，非 Dart 文件复制。
- 兼容入口 `XiaoxuBottomSheet` 保持既有签名；耗时操作的关闭保护覆盖遮罩、返回键与把手，内容滚动不抢手势。不接入长表单及课程专用 ModalBottomSheet，暂不加入实时模糊。
- 静态回归检查迁移至真实 V2 Shell 位置，保留唯一底栏、侧栏、Scaffold 和显隐规则；增加弹层判定边界单测。仍需构建、无障碍、真机与性能验收。

## 第三批：主题与分段控件

- `UiV2Theme` 提供统一系统无衬线字阶、400/600 字重及 6/10/14/20/28dp Material 形状阶梯；原业务页标题保持 20sp/26sp，北化导航和课程专用圆角仍保留以供后续逐页迁移。中性浅色背景替代原多色渐变，校序品牌蓝和课程色板不变。未声明全局暗色支持。
- `UiV2SegmentedControl` 采用单一绘制层滑动选中胶囊，保持等宽、48dp触控区、RTL 与可访问选中语义。保留调用者的真实筛选状态；本阶段仅点击切换，未实现 DSH Go 的水平拖拽与液态形变。
- 上游参考（固定提交）：[typography.dart](https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/theme/typography.dart)、[shapes.dart](https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/theme/shapes.dart)（MIT 标注）；[segmented_pill.dart](https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/common/segmented_pill.dart)（交互参考，独立 Compose 绘制实现）。未直接复制 Dart 代码或受限素材。

## 第四批：设置页面与返回规则

参考 DSH Go MIT 标注 [settings_widgets.dart](https://github.com/ccch1mneyyy/dsh-Go/blob/a99b5e1906e6084d4d0876fad1cc1f35bb296ff0/app/lib/ui/settings/settings_widgets.dart) 的 SettingsMetrics，独立 Compose 实现统一的 52/64dp 行、32dp 图标槽、16dp 内边距、20dp 分组面和共享标题栏。迁移设置入口及通知开关展示，保留权限检查、备份、连接等原业务回调。系统返回和标题栏共享父路由映射，修正通知子页返回设置首页的问题；不引入两棵重型页面同时渲染的转场。新增父路由单测。未完成真机视觉与 APK 验收。

## 第五批：按实机参考图重构设置首页

- 用户提供的 DSH Go 设置页截图作为布局基准：独立大标题、圆形工具键、胶囊搜索、账号状态卡、按组配色的圆形图标、24dp 连续分组及 24dp 区块间距。校序设置为一级标签，不添加无意义的返回按钮；二级页保留返回键。
- 账号卡使用 CloudClient 的真实 authenticated/busy 状态，不显示虚构 AI 引擎或登录状态；入口仍打开真实数据连接页面。
- 设置索引与搜索接通现有八条路由，支持空白、全半角、大小写、同义词匹配和标题优先；未实现的设置不进入目录。参考 DSH Go settings_search.dart 的交互思路，自行实现 Kotlin 索引，不复制其源码或受限素材。
- 新增搜索规则测试，并保留原提醒、备份、连接、课表功能入口。仍需实际 APK 和截图验收。

## 第六批：总览首页

- 新建 UiV2Home 组件：独立日期/问候层级、单层 24dp 轻量白色分组、分类圆形图标与共享按压反馈；不复制聊天应用功能、不新增真实背景采样。
- 首页接入新组件，删除课程列表内重复卡片，任务、公告、学习通快照、指标等沿用原数据计算及详情回调。新增稳定的 LazyColumn 项 key，使用 V2 窗口分级并缓存本机星期过滤、任务计数、作业记录过滤。未知教学周仍称“课程记录”。
- 修复设置开关静态检查目标路径，保持唯一共享 switch 断言；更新明确被替换的首页视觉断言。等待 CI 和实际 APK、真机帧时间验收。

## 第七批：校园教务及任务中心组件复用

- 校园总览直接复用 V2 SettingsGroup / SettingsEntry 已有成熟实现，同一套图标几何、分类色和行语义；保留成绩、考试、第二课堂、校园服务导航及真实未读取状态。原教务行的独立绘制退役。
- 抽取已接入的胶囊搜索为 UiV2SearchField，设置和任务中心直接共享同一个源码文件；任务搜索输入仍由既有筛选状态持有，保留100字符限制及清除行为。任务状态槽采用 V2 调色，待完成与完成计数按真实列表缓存。
- 不重写本机任务、云端任务、前置依赖、官方状态、任务编辑、权限或数据接入。任务列表整体迁移尚未完成，不声明真机流畅度。

## 第八批：共享滑块与课表详情交互

- 提取 V2 分段控件的绘制层为 uiV2SelectionThumb，任务四段统计直接复用，保留实际计数及选中语义；移除每段重复背景。折叠箭头读取图层状态而非逐帧重组。
- 课程详情继续使用北化课表专用 Material3 ModalBottomSheet，不替换课程网格、手势或详情业务；复用 V2 弹层把手几何，提醒提前量改为本机真实配置。
- 修复静态检查对缓存前表达式的重复硬编码，保留本机课表唯一来源、真实任务计数等约束。尚未完成 APK 和真机性能验收。

## 第九批：跟手分段滑块

- 参考 DSH Go segmented_pill.dart 的拖动预览/释放吸附模式，自行实现 Compose 横向手势；通用分段与任务四段筛选共用原有 uiV2SelectionThumb。拖动期间仅更新图层预览，释放时提交最近索引，取消时回到业务选中态，支持 RTL、边界钳制、未知选项禁用拖动；原点击与无障碍路径保留。新增纯逻辑单测。
- 已检查 DSH Go page_transitions.dart：其快照机制及预测返回依赖 Flutter 路由生命周期。校序现有即时路由合同明确禁止恢复双页面重型动画；暂保留原路由实现，后续单独验证单页面变换与预测返回，不宣称已移植 Flutter 转场或实测性能。
