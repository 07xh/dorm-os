# SleepDown 源码研究与校序导航/流畅度优化建议

日期：2026-11-06 ｜ Issue #287 ｜ 参考仓库：`xiaomanjun233/SleepDown-Schedule`（本地克隆于 `sleepdown-src/`）

> 本文所有结论均来自实际阅读源码，文件路径与行号可对照。凡未经测量的性能结论一律标注为"未验证"。

---

## 0. 许可证前提（先看这个）

SleepDown 根目录 `LICENSE.md`：**「署名非商业、源码可见许可 1.1」**，非 OSI 开源许可。允许「查看、下载、克隆」「为个人、非商业的学习、研究和技术评估，编译、运行和修改」。

其 `com/kyant/backdrop/catalog/**` 下若干文件另有头部声明：

```
// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
```

因此本次复用的文件同时受 **Apache-2.0（上游原件）** 与 **SleepDown 1.1（修改部分）** 约束。已在该文件头部保留双署名 + 上游路径 + 许可说明。

---

## 1. SleepDown 关键实现（附源码路径）

### 1.1 底部 Dock 入口

`app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/HomeDockTabs.kt`（68 行）

- `HomeDockTabs(selectedIndex, parallel, config, backdrop, lightGlass, ...)`
- tab 定义为资源 id + 文案：`R.drawable.ic_day_view to "今日"`、`ic_week_view to "课程表"`、`ic_settings to "设置"`（并排模式多一个）
- 三级降级：`LocalHomeMaterialControls.current.Dock(...)` 可整体替换 → `backdrop == null` 时用 `GlassPill` + `DockItem` → 否则 `LiquidBottomTabs`
- 容器 54dp、指示器 46dp、`blurRadius = homeChromeBlur(1.3.dp)`、`lensHeight = 10.dp`、`lensAmount = 40.dp`、`indicatorWidthOverflow = 8.dp`、`chromaticAberrationEnabled = true`

### 1.2 悬浮胶囊 + 选中态滑动（核心）

`app/src/main/java/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt`（489 行）

| 关注点 | 实现位置 | 做法 |
| --- | --- | --- |
| 单元宽度 | `BoxWithConstraints` 第 222–229 行 | `(maxWidth - horizontalPadding*2 - leadingWidth) / tabsCount` |
| 选中位置 | 第 230、248–281 行 | `DampedDragAnimation(initialValue = selectedTabIndex(), valueRange = 0f..(n-1))`，浮点位而非整数下标 |
| 拖拽切页 | 第 271–279 行 | `onDrag` 里 `updateValue(targetValue + dragAmount.x / tabWidth)`，带 LTR/RTL 方向 |
| 落点吸附 | 第 257–270 行 | `targetValue.fastRoundToInt()` → `animateToValue` → 回调 `onSelect` |
| 整条 dock 位移 | 第 231–238、303–307 行 | `Animatable` + `derivedStateOf`，`4.dp * fraction.sign * EaseOut(abs(fraction))` |
| 外部选中同步 | 第 282–286 行 | `LaunchedEffect(selectedIndex, dragging)`，拖拽中不打断手势 |
| 触控高光 | 第 288–301 行 | `InteractiveHighlight`，位置由 `leadingInset + (value + 0.5f) * tabWidth` 推出 |
| **低端机降级** | 第 338–352 行 | `superPerformance` 时不画液态玻璃，改用 `drawBehind` 画静态圆角矩形 + 描边 |
| 主题切换 | 第 116–132 行 | `animateFloatAsState(tween(220))` 交叉淡入，避免重建 surface 造成一帧闪白 |

### 1.3 手势补丁（真正有价值的修复）

`app/src/main/java/com/kyant/backdrop/catalog/utils/DragGestureInspector.kt`（45 行）

`inspectDragGestures` 直接在 **Initial pass** 用 `awaitFirstDown(false, PointerEventPass.Initial)` 捕获 DOWN 并**固定该 pointer id** 直到抬起。源码注释说明原实现的问题：

> 「The former implementation awaited a second DOWN at Main pass; if a child consumed the first event, that await leaked into the next gesture and made a quick tap after long-press disappear.」

即：标准 `detectDragGestures` 在子元素消费首个事件后，等待会泄漏到下一次手势，导致"长按后快速轻点失效"。

### 1.4 动画驱动

`app/src/main/java/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt`（172 行）

- 5 个 `Animatable`：`value`（位置）、`velocity`、`pressProgress`、`scaleX`、`scaleY`
- spring 参数：位移 `spring(1f, 1000f, visibilityThreshold)`、压感 `spring(1f, 1000f, 0.001f)`、`scaleX spring(0.6f, 250f)`、`scaleY spring(0.7f, 250f)`
- `MutatorMutex` 保证动画互斥，`VelocityTracker` 算速度
- `release()` 用 `snapshotFlow { value }.filter { abs(it - target) < threshold }.first()` **等位移收敛后再撤压感**，避免两个动画同时结束造成视觉突兀

### 1.5 触控高光

`app/src/main/java/com/kyant/backdrop/catalog/utils/InteractiveHighlight.kt`（269 行）

基于 `android.graphics.RuntimeShader`（AGSL，**API 33+**）绘制跟随位置的高光；`Build` 版本判断做降级。

### 1.6 启动与流畅度

`app/src/main/java/com/xiaomanjun/sleepdownschedule/core/performance/Performance.kt`（527 行）

| 机制 | 位置 | 说明 |
| --- | --- | --- |
| `StartupPhase` 六阶段 | 第 42–49 行 | Loading / Prewarm / Reveal / Entrance / Settle / FullQuality |
| 玻璃质量渐进 | 第 54–61 行 | `animatedGlassQuality`：0.45 → 0.55 → 0.70 → 0.85 → 1.0，启动期先降质保帧率 |
| JankStats 埋点 | 第 66–141 行 | `JankStats.createAndTrack` + `PerformanceMetricsState.putState("startup_phase"/"screen"/"animation"/"thermal_status")`，仅在 benchmark build 开启 |
| 刷新率检测 | 第 146–230 行 | `RefreshCadenceTracker`：9 帧中位数 + 3 次滞后确认，避免每帧抖动污染 Trace |
| 分层入场容器 | 第 355–380 行 | `TopBarEntranceContainer` / `ContentEntranceContainer` / `DockEntranceContainer` —— **当前实现已退化为空 `Box`**，历史包袱，不建议照搬 |
| 壁纸缓存 | 第 399–412 行 | 两级 `LinkedHashMap` LRU（3 / 4 条）+ `shouldClearHomeWallpaperCaches` 响应内存压力 |

`app/src/main/java/com/xiaomanjun/sleepdownschedule/core/performance/AppMaterialPreferences.kt`（49 行）

`AppMaterialLevel { QUALITY, PERFORMANCE, SUPER_PERFORMANCE }` + `EffectiveMaterialPolicy`，把"设备能力"和"外观选择"分开，`SUPER_PERFORMANCE` 一旦生效不会被外观恢复覆盖。

### 1.7 Baseline Profile / Macrobenchmark

| 文件 | 内容 |
| --- | --- |
| `benchmark/build.gradle.kts` | `com.android.test` + `androidx.baselineprofile` 插件；`targetProjectPath = ":app"`；`experimentalProperties["android.experimental.self-instrumenting"] = true`；`baselineProfile { useConnectedDevices = true }`；依赖 `benchmark-macro-junit4:1.4.1` + `uiautomator:2.3.0` |
| `benchmark/src/main/java/.../BaselineProfileGenerator.kt` | `BaselineProfileRule.collect(packageName, includeInStartupProfile = true)`，路径覆盖：`startActivityAndWait` → 课表 → 打开/关闭课程编辑器 ×2 |
| `benchmark/src/main/java/.../BenchmarkHomeJourney.kt` 等 5 个文件 | 首页旅程、课程编辑器动画、弹窗与页面转场、导入历史，均用 `MacrobenchmarkScope` |
| `app/build.gradle.kts` 第 224–225、253 行 | `metrics-performance:1.0.0`、`profileinstaller:1.4.1`、`baselineProfile(project(":benchmark"))` |
| `app/build.gradle.kts` 第 125–143 行 | release 开 `isShrinkResources` + `proguard-android-optimize.txt`；另有独立 `benchmark` buildType 保持 unshrunk |

### 1.8 课表滚动与布局计算

`app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/week/WeekScheduleScreen.kt`（765 行）

- **单一滚动所有者**：`rememberScrollState()` + `Modifier.verticalScroll(scrollState)`（第 127、450 行）
- **周级缓存**：`weekBucketCache.getOrPut(week) { ... }`（第 147 行）、`gridCache.getOrPut(week) { ... }`（第 177 行）——按周缓存布局计算结果，避免每帧重算
- **横向周分页**：`HorizontalPager`（第 258 行 `pagerState.pageCount`）而不是一次性铺全部周
- **滚动状态派生**：`derivedStateOf { scrollState.value > 0 }`（第 309 行）、`derivedStateOf { homeSwitching.value || scrollState.isScrollInProgress || weekTail.moving }`（第 411 行）——把多状态合成单 Boolean，减少无效重组

---

## 2. 校序与 SleepDown 架构对照

| 维度 | SleepDown 实现 | 校序现状 | 是否值得借鉴 | 改造风险 |
| --- | --- | --- | --- | --- |
| 底部导航 | 悬浮胶囊 + 液态玻璃 `LiquidBottomTabs`，浮点位置驱动 | `MainActivity.kt:466` Material3 `NavigationBar` + `NavigationBarItem`，整数下标 | **交互值得**，渲染不值得（见 §3） | 低（组件独立，不改状态） |
| 选中态动画 | `DampedDragAnimation`（5 Animatable + MutatorMutex + 速度） | MD3 默认 indicator 过渡，无自定义 | **值得**，已移植 | 低 |
| 触控反馈 | 压感缩放 1.2x（`pressedScale = 78/56`）+ 位置高光 | 标准 ripple | 部分值得（缩放已实现，高光需 AGSL） | 中（AGSL 需 API 33+） |
| 手势健壮性 | `inspectDragGestures` Initial pass 固定 pointer id | 无拖拽，不存在该问题 | **接入拖拽时必须**，已移植 | 低 |
| 页面切换转场 | `HorizontalPager` 周分页 + 入场容器（已退化空壳） | `AnimatedContent` fade + slide 1/18 宽，180ms | 校序现状已够用 | 高（动导航结构） |
| 状态保存 | ViewModel + `rememberSaveable` + 周级 `getOrPut` 缓存 | `rememberSaveable` + `rememberSaveableStateHolder` | 校序已达标 | — |
| 课表滚动 | 单一 `verticalScroll` + 周级缓存 + Pager | 单一 `verticalScroll`（`ScheduleScreen.kt:305` 注释记录了从内层 LazyColumn 改过来的原因） | 校序已改对；**周级缓存值得补** | 低 |
| 启动优化 | Baseline Profile + `StartupPhase` 渐进降质 | **无** baselineProfile / profileinstaller / minify | **最值得**（P0） | 低（只加构建配置） |
| 设备分级降级 | `AppMaterialLevel` 三档 + 策略对象 | 无 | 值得（P1） | 中（需产品决策） |
| 性能埋点 | JankStats + PerformanceMetricsState + 刷新率检测 | 无 | 值得（P1，先做度量再做优化） | 低 |
| 导航状态建模 | 未单独抽离（UI 内） | `AcademicNavigation.kt` 16 行纯数据类 | **校序更好**，不必改 | — |

---

## 3. 可复用性判定（为什么只复用了 2 个文件）

| 候选 | 依赖 | 结论 |
| --- | --- | --- |
| `DampedDragAnimation.kt` | 仅 `androidx.compose.*` + `kotlinx.coroutines.*` + `kotlin.math.abs` | **✅ 已直接复用**（逻辑零改动，仅换包名 + 署名） |
| `DragGestureInspector.kt` | 仅 `androidx.compose.foundation.gestures.*` + `ui.input.pointer.*` | **✅ 已直接复用**（同上） |
| `InteractiveHighlight.kt` | `android.graphics.RuntimeShader`（AGSL / API 33+） | ⚠️ 可移植但校序 `minSdk 26`，需完整降级分支，本轮未做 |
| `LiquidBottomTabs.kt` | `glass` 模块 **41 文件 / 6559 行** + `com.kyant.backdrop` 第三方库 + `com.kyant.shapes.Capsule` | ❌ **不可独立抽取**。强行移植 = 把整套液态玻璃基础设施搬进校序，与"避免新一轮架构重构"直接冲突 |
| Miuix（`miuix-ui-android:0.9.3`） | SleepDown 的 Dock **不是** Miuix 官方组件，是自研 glass + `LocalHomeMaterialControls` 可替换钩子 | ❌ 导航这块用不上 Miuix；引入 Miuix 会与 `XiaoxuDesignSystem` 整套冲突 |

因此本轮的策略是：**复用其"驱动层"（动画 + 手势），自建"渲染层"（Compose 原生胶囊）**。

---

## 4. 性能实测（模拟器，结论：未证明改善）

### 4.1 方法

- 设备：Android 16 / API 36 模拟器（`swiftshader_indirect` 软渲染），1080×2400
- 指标：`adb shell dumpsys gfxinfo <pkg>` → `Total frames` / `Janky frames` / `90th percentile`
- 场景：`gfxinfo reset` → 依次点击 5 个 tab × 6 轮 = 30 次 → 读统计
- 变体：A = `FloatingBottomBar`（临时接入），B = 原 MD3 `NavigationBar`
- 每个变体跑 3 轮取中位

### 4.2 结果

| 变体 | Total frames | Janky | 90th percentile |
| --- | --- | --- | --- |
| A（FloatingBottomBar） | 595 / 497 / 479 | 29.41% / **45.27%** / 53.65% | 77 / **101** / 113 ms |
| B（MD3 NavigationBar） | 820 / 743 / 770 | 46.46% / **59.62%** / 54.03% | 93 / **121** / 101 ms |

### 4.3 结论

- **90th percentile 中位数持平（101ms vs 101ms）**，janky 比例 A 略低但两变体自身波动（A 29–54%，B 46–60%）已覆盖该差异。
- 该模拟器为软件渲染，且同一变体重复测量波动达 ±15 个百分点 / ±30ms，**不具备判定优劣的精度**。
- 因此：**不宣称性能提升；按约定不接入 `MainActivity`**，组件以独立文件形式交付，等真机 Macrobenchmark 验证后再决定。

### 4.4 过程中修掉的一个真实缺陷

首次接入时 A 的帧数仅为 B 的 1/4（179–276 vs 743–820）。定位到：父级手势的 `onDragStopped` 无条件用 `targetValue` 回调 `onSelect`，把"轻点第 3 个 tab"变成"落回当前指示器位置"，导致大部分点击不生效。

修复：引入 `dragMoved` 标记，只有 `|dragAmount.x| > 0.5f` 才进入拖拽分支，轻点完全交给 cell 的 `clickable`。修复后帧数与 janky 恢复正常区间。

**若后续有人接入该组件，这个分支不可回退。**

---

## 5. 性能优化清单

### P0（低风险、可度量、建议先做）

1. **接入 Baseline Profile**
   `app/build.gradle` 加 `androidx.profileinstaller:profileinstaller:1.4.1` + `baselineProfile(project(":benchmark"))`，新建 `:benchmark` 模块（参考 `benchmark/build.gradle.kts`）。
   验收方式：`am start -W` 冷启动 + Macrobenchmark `StartupTimingMetric`，对比前/后中位数，**改善才合并**。

2. **release 开启 R8 与资源压缩**
   校序当前 `app/build.gradle` 无 `minifyEnabled` / `shrinkResources`。开 `proguard-android-optimize.txt` 后可减少启动期类加载与 dex 体积。
   验收：APK 体积 + 冷启动时长。

3. **先建立性能度量基线**
   在 App 内接入 `androidx.metrics:metrics-performance`（JankStats），仅在 debug 采样；配合 `PerformanceMetricsState.putState("screen", ...)` 标注页面。
   没有基线就没有"优化"，这条必须排在最前。

### P1（需要改动业务代码之外的结构）

4. **课表按周缓存布局结果**
   参考 `WeekScheduleScreen.kt` 的 `gridCache.getOrPut(week)`。校序 `ScheduleScreen.kt` 当前每次重组重算周网格，缓存后切周更稳。
   验收：连续切周 10 次，`gfxinfo` 90th 与 janky%。

5. **把"滚动中"状态合成单一 Boolean**
   参考 `derivedStateOf { homeSwitching.value || scrollState.isScrollInProgress || ... }`，避免多处各自判断导致重复重组。

6. **设备分级降级（`AppMaterialLevel` 思路）**
   不是照搬玻璃，而是：低端机关闭阴影/大圆角/渐变，改用纯色块。校序 `XiaoxuHero` 目前固定 6dp shadow + 渐变 + `drawBehind` 两个圆，低端机上可整段跳过。
   验收：同一设备 `dumpsys gfxinfo` 对比，或直接用 `isLowRamDevice` 分流。

### P2（研究性质，收益不明确）

7. **`InteractiveHighlight` 触控高光**：需 AGSL（API 33+）+ 降级分支，收益主观，暂不做。
8. **`RefreshCadenceTracker` 刷新率检测**：仅对诊断有价值，不影响用户可感知流畅度。
9. **分层入场容器**：SleepDown 自己的实现已退化成空 `Box`，说明这条路收益存疑，**不建议做**。

---

## 6. 本轮交付物

| 文件 | 性质 |
| --- | --- |
| `apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/nav/DampedDragAnimation.kt` | 直接复用（SleepDown，Apache-2.0 + 1.1，署名完整） |
| `apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/nav/DragGestureInspector.kt` | 直接复用（同上） |
| `apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/FloatingBottomBar.kt` | 自建渲染层 + 复用驱动层，**未接入 MainActivity** |
| 本文 | 研究报告 |

**未改动**：`MainActivity.kt`、`AcademicNavigation.kt`、`XiaoxuDesignSystem.kt`、`ScheduleScreen.kt`、`CloudClient.kt`、`app/build.gradle`、Room / 认证 / 数据接口 —— 零改动。

接入方式见 `FloatingBottomBar.kt` 文末 KDoc 示例；接入只需替换 `bottomBar` 内容，`current` / `rememberSaveable` / `SaveableStateHolder` 均不动。
