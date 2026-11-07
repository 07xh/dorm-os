# 四个参考仓库的源码研究（2026-11-06）

> 校序 Android 全局流畅度与 UI 整合专项 · 第二阶段参考材料
>
> 全部结论来自本地克隆的真实源码，未凭记忆推断。克隆 HEAD：
> - `SleepDown-Schedule` → `sleepdown-src/`
> - `android/nowinandroid` → `ref-nowinandroid/`
> - `android/performance-samples` → `ref-performance-samples/`
> - `android/compose-samples` → `ref-compose-samples/`
>
> 行号以各仓库克隆时 HEAD 为准，路径均可直接打开核对。

---

## 0. 结论先行

1. **四个仓库不能混着抄**。nowinandroid 与 compose-samples 已迁到 **Navigation 3**（`NavDisplay`），校序是 `Scaffold + AnimatedContent`（Nav2 风格）。它们的**导航代码不能直接搬**，只有"状态持有 / 动画规范 / insets 处理"这套思路可用。
2. **真正能直接复用的是性能工程那一半**：`performance-samples` 的 `MacrobenchmarkSample` 用 **AGP 8.13.1 / compileSdk 36 / Kotlin 2.2.21**，与校序（AGP 8.13.0 / compileSdk 36 / Kotlin 2.2.20）几乎是同一代工具链。Baseline Profile 与 Macrobenchmark 的配置可以**整块照搬**。
3. **SleepDown 的可复用面比预期窄**：只有 `DampedDragAnimation` + `DragGestureInspector` 两个文件零项目依赖（已在 PR #328 复用）。它的 Dock 渲染层依赖 `glass` 41 文件 / 6559 行，不可抽取。
4. **本轮唯一被证实有价值的"上游补丁"是 `DragGestureInspector`**：它修的是标准 `detectDragGestures` 的 pointer 泄漏（长按后快速点击失效）。这是真问题，不是风格差异。
5. 三个官方仓库均 **Apache-2.0**，可商用；SleepDown 是 **署名非商业 1.1**，**不可商用**。这个差异决定了哪些代码能进校序的主干。

---

## 1. 四个仓库的定位与适用场景

| 仓库 | 定位 | 对校序的价值 | 许可 |
| --- | --- | --- | --- |
| **SleepDown-Schedule** | 单一开发者的课程表 App，自研液态玻璃 Dock | 导航手势与阻尼动画（已复用 2 文件） | 署名非商业 1.1（**不可商用**） |
| **nowinandroid** | Google 官方 modularized 参考 App | 自适应导航结构、insets 处理链、JankStats 挂载、Baseline Profile 工程化 | Apache-2.0 |
| **performance-samples** | Google 官方性能样例集 | **Macrobenchmark / Baseline Profile 的权威写法**，工具链与校序同代 | Apache-2.0 |
| **compose-samples** | Google 官方 Compose UI 样例集 | 自定义 indicator 滑动、转场动画规范、重组优化具体手法 | Apache-2.0 |

---

## 2. nowinandroid：结构参考，代码不可直接搬

### 2.1 导航（**Nav3，不能直接用于校序**）

- `app/src/main/kotlin/.../ui/NiaApp.kt:268-272` — `NavDisplay(entries = navigationState.toEntries(entryProvider), sceneStrategies = listOf(listDetailStrategy), onBack = …)`，**没有 transition 参数**，转场由 NavDisplay 内部完成。
- 全仓库 **grep `AnimatedContent` / `NavHost` / `SizeTransform` 零命中**。
- `core/navigation/src/main/kotlin/.../NavigationState.kt:70-89` — `currentTopLevelKey by derivedStateOf { topLevelStack.last() }`、`isAtTopLevel`。**思路可用**：用 `derivedStateOf` 收敛栈顶判断，避免每次重组重算。
- `core/navigation/src/main/kotlin/.../NavigationState.kt:94-113` — 每个 top-level 一条 `rememberNavBackStack(key)`，`toEntries` 用 `rememberSaveableStateHolderNavEntryDecorator()` 装饰。**思路对应校序现有 `pageStateHolder.SaveableStateProvider(page.name)`**，校序已经做对了。

### 2.2 insets 处理链（**可直接照抄到校序 Scaffold**）

`app/src/main/kotlin/.../ui/NiaApp.kt:192-256` 这段是完整的正确写法：

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),   // 关键：不让 Scaffold 自己塞 inset
) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)              // 关键：消费掉，避免子级重复加
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) { … }
}
```
`:248-256` 还有一处带注释的 workaround（`issuetracker.google.com/338478720`）：只在显示 topBar 时 `consumeWindowInsets(safeDrawing.only(Top))`。

**校序现状**：`Scaffold(containerColor = XiaoxuPalette.Bg)`，没有设 `contentWindowInsets`，靠 `enableEdgeToEdge()` + 各页面自己 `useStatusBarInset` 参数处理。切换 tab 时不同页面的顶部留白不一致就是这个原因。**这是 PR B 要改的点。**

### 2.3 JankStats 挂载（**P1，用于建立基线**）

- `core/ui/src/main/kotlin/.../JankStatsExtensions.kt:80-93` — `TrackScrollJank`：`snapshotFlow { scrollableState.isScrollInProgress }` 把 "Scrolling=true" 写进 JankStats 状态，卡顿可归因到滚动。
- `core/ui/src/main/kotlin/.../JankStatsExtensions.kt:67-75` — `TrackDisposableJank(vararg keys)`：`DisposableEffect` 挂/卸状态标签。
- `app/src/main/kotlin/.../ui/NiaAppState.kt:119-124` — 导航时 `putState("Navigation", currentKey)`。

**价值**：校序目前完全没有卡顿归因能力。接了 JankStats 才能知道「卡在切换动画」还是「卡在页面首次布局」。这是 PR D 的前置。

### 2.4 Baseline Profile 工程化（**PR D 直接照搬**）

- `benchmarks/build.gradle.kts:18-78` — 用 `com.android.test` 模块 + `alias(libs.plugins.baselineprofile)`，`minSdk = 28`，`targetProjectPath = ":app"`，`experimentalProperties["android.experimental.self-instrumenting"] = true`。
- `:60-68` — `baselineProfile { managedDevices.clear(); managedDevices += "pixel6Api33"; useConnectedDevices = false }`，强制 GMD 保证本地/CI 一致。
- `app/build.gradle.kts:140-147` — `baselineProfile { automaticGenerationDuringBuild = false; dexLayoutOptimization = true }`。**`dexLayoutOptimization = true` 是关键**，校序现在没有。
- `benchmarks/.../baselineprofile/StartupBaselineProfile.kt:30-38` — `BaselineProfileRule().collect(PACKAGE_NAME, includeInStartupProfile = true, profileBlock = MacrobenchmarkScope::startActivityAndAllowNotifications)`。
- `app/src/main/kotlin/.../util/ProfileVerifierLogger.kt:51-69` — 运行时校验 profile 是否真的生效：`ProfileVerifier.getCompilationStatusAsync().await()`，并给出 `adb shell cmd package compile -f -m speed-profile <pkg>` 的验证命令。**这个必须抄**，否则无法证明 profile 装上了。

---

## 3. performance-samples：与校序同代，**配置可整块复用**

### 3.1 Macrobenchmark 模块（**PR D 主参考**）

`MacrobenchmarkSample/macrobenchmark/build.gradle.kts`：

```kotlin
plugins {
    id("kotlin-android")
    alias(libs.plugins.test)           // com.android.test
    alias(libs.plugins.kotlin)
    alias(libs.plugins.baselineprofile)
}
android {
    compileSdk = 36                     // 与校序一致
    minSdk = 28                         // baseline profile 的实用下限
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    testOptions.managedDevices.allDevices {
        create<ManagedVirtualDevice>("pixel6Api31") {
            device = "Pixel 6"; apiLevel = 31; systemImageSource = "aosp-atd"
        }
    }
}
baselineProfile { managedDevices += "pixel6Api31"; useConnectedDevices = false }
dependencies {
    implementation(libs.benchmark.junit)
    implementation(libs.androidx.junit)
    implementation(libs.espresso)
    implementation(libs.ui.automator)
    implementation(libs.tracing.perfetto)          // Composition Tracing
    implementation(libs.tracing.perfetto.binary)
}
```

`minSdk = 28` 的原因源码里有注释：**API 28 以下应用本来就被 AOT 全编译，Baseline Profile 没有意义**。独立 `com.android.test` 模块的 minSdk 不受 app 的 minSdk 26 影响，可共存。

### 3.2 StartupTimingMetric（**覆盖冷/温/热三态**）

`MacrobenchmarkSample/.../benchmark/startup/StartupBenchmark.kt`：

- `:36-52` — 用抽象基类参数化 `StartupMode`：`ColdStartupBenchmark(COLD)` / `WarmStartupBenchmark(WARM)` / `HotStartupBenchmark(HOT)`。**HOT 的 `@RunWith` 被官方注释掉了**，注释原文：`Hot startups tend to be flaky in some test environments. It's disabled here for your convenience.` —— 这是官方承认的 flake，校序的 HOT 用例应独立 job 且允许重试。
- `:62-78` — 四个 `@Test` 覆盖四种编译模式：`None()` / `Partial(BaselineProfileMode.Disable, warmupIterations = 3)` / `Partial(BaselineProfileMode.Require)` / `Full()`。**这四组就是"profile 有没有用"的对照实验设计。**
- `:80-90` — `measureRepeated(packageName, metrics = listOf(StartupTimingMetric()), compilationMode, iterations, startupMode) { uiAutomator { startApp(TARGET_PACKAGE) } }`。

### 3.3 FrameTimingMetric + TraceSection（**覆盖切换与滚动**）

`MacrobenchmarkSample/.../benchmark/frames/FrameTimingBenchmark.kt`：

- `:43-70` — `FrameTimingMetric()` + `CompilationMode.None()` + `StartupMode.WARM`；`setupBlock { uiAutomator { startIntent(...) } }`；测量块 `repeat(3) { fling(Direction.DOWN) }`。
- `:72-108` — 追加 `TraceSectionMetric("EntryRowCustomTrace", Mode.Sum)` 统计某 composable 的总耗时；通配写法 `TraceSectionMetric("%EntryRow (%", Mode.Sum)` **只在开启 composition tracing 时才有结果**（源码注释原话）。

`MacrobenchmarkSample/.../benchmark/scroll/ScrollBenchmark.kt:51-83` —— **非启动类基准必须 `startupMode = null`**，且 `setupBlock` 里用 `var firstStart` 只启动一次，避免每轮重复进页面。这个细节很容易踩坑。

### 3.4 JankStats 在 Compose 里的唯一推荐写法

`JankStatsSample/app/src/main/java/com/example/jankstats/compose/rememberMetricsStateHolder.kt:12-16`：

```kotlin
val view = LocalView.current
return remember(view) { PerformanceMetricsState.getHolderForHierarchy(view) }
```

`JankStatsSample/.../tools/simulateJank.kt:29-50` —— 主动注入卡顿（30% 概率 32–82ms、2% 概率 500–700ms）来验证监控链路是否真的在采集。**这是自测工具，很实用**。

---

## 4. compose-samples：UI 手法的唯一真实来源

### 4.1 自定义 indicator 滑动（**唯一实现**）

`Jetsnack/app/src/main/java/com/example/jetsnack/ui/home/Home.kt:233-308` —— `JetsnackBottomNavLayout`：

1. `remember(itemCount) { List(itemCount) { Animatable(if (i == selectedIndex) 1f else 0f) } }` 记录每项选中度；
2. `LaunchedEffect(target) { selectionFraction.animateTo(target, animSpec) }`；
3. `val indicatorIndex = remember { Animatable(0f) }` 让 indicator 位置连续动画；
4. 自定义 `Layout` 里 `unselectedWidth = maxWidth / (itemCount + 1)`、`selectedWidth = 2 * unselectedWidth`（选中项占 2 槽），用 `lerp` 逐项测宽；
5. place 阶段 `indicatorPlaceable.placeRelative(x = (indicatorIndex.value * unselectedWidth).toInt(), y = 0)`。

`Home.kt:400-412` —— indicator 本体只是 `Spacer(Modifier.fillMaxSize().then(BottomNavigationItemPadding).border(strokeWidth, color, RoundedCornerShape(percent = 50)))`，用**描边**画胶囊，不用自定义 draw。

**与校序的关系**：校序已在 PR #328 用 SleepDown 的阻尼驱动实现了滑动，**不需要再抄 Jetsnack**。Jetsnack 的价值在于两点可借鉴：
- `placeRelative` 而非 `place` —— 这正是 PR #328 里 RTL 双重镜像 bug 的根因（`Modifier.offset {}` 内部也是 `placeRelative`）。
- `Modifier.graphicsLayer { alpha = progress }` 做文字淡入，不触发重组。

### 4.2 转场动画规范（**PR B 直接用**）

`Jetsnack/.../ui/snackdetail/SnackDetail.kt:130-142`：

```kotlin
fun <T> spatialExpressiveSpring() = spring(dampingRatio = 0.8f, stiffness = 380f)   // 位移/尺寸
fun <T> nonSpatialExpressiveSpring() = spring(1f, 1600f)                            // 淡入淡出
```

**规范含义：位移用欠阻尼（有弹性），透明度用临界阻尼（不弹）。** 校序现在两处都用 `tween`，没有弹性也没有收敛速度的区分。

`Jetsnack/.../ui/home/Home.kt:87-126` —— `composableWithCompositionLocal()` 统一包装 `enterTransition = fadeIn(nonSpatialExpressiveSpring())` / `exitTransition = fadeOut(nonSpatialExpressiveSpring())`，并通过 `CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable)` 把 `AnimatedContentScope` 暴露给子级。

`JetNews/app/src/main/java/com/example/jetnews/ui/navigation/ListDetailScene.kt:57-72` —— **`AnimatedContent` 加 `contentKey` 的唯一真实用例**：

```kotlin
AnimatedContent(
    targetState = detailEntry,
    contentKey = { entry -> entry?.contentKey ?: "detailPlaceholder" },
    transitionSpec = { fadeIn() togetherWith fadeOut() },
)
```
`contentKey` 的作用：只有"内容真的变了"才重放动画，占位符出现时不播。校序的 `AnimatedContent(targetState = current)` 是 enum，本身稳定，加 `contentKey` 收益不大，但**切页时旧页面持有资源的问题需要另外解决**（见 §6）。

`ListDetailScene.kt:74-77` —— `put(NavDisplay.TransitionKey) { fadeIn() togetherWith ExitTransition.KeepUntilTransitionsFinished }`。**`KeepUntilTransitionsFinished` 是防"淡出期间闪白"的关键**：旧页面在新页面完全覆盖前不被移除。校序现在 `fadeOut(tween(120))` 比 `fadeIn(tween(180))` 早 60ms 结束，中间会出现半透明空档 —— **这是可观察到的真缺陷**。

### 4.3 重组优化（**逐条可落地**）

| 手法 | 位置 | 校序能否直接用 |
| --- | --- | --- |
| `derivedStateOf` 包滚动阈值 | `Jetchat/.../conversation/Conversation.kt:505-512` | ✅ 课表/消息列表可直接用 |
| `rememberUpdatedState` 包回调给 `pointerInput` | `Conversation.kt:747-750` | ✅ PR #328 已用 |
| `graphicsLayer { }` lambda 读动画值 | `Conversation.kt:748-768`（点赞缩放） | ✅ PR #328 已用 |
| 浮点动画转布尔再进组合 | `Conversation.kt:251`（`isGlowVisible by derivedStateOf { glowAlpha > 0f }`） | ✅ |
| `drawWithCache` 缓存渐变 Brush | `SnackDetail.kt:228-247` | ✅ 首页 Hero 渐变可用 |
| `key(navItem.navKey) { }` 包列表项 | `JetNews/.../components/AppNavRail.kt:61-76` | ✅ |
| `Modifier.offset { }` lambda | `Jetchat/.../JumpToBottom.kt:80-90` | ✅ PR #328 已用（并踩出 placeRelative 的坑） |

### 4.4 模糊的正确用法（老板点名"不用昂贵的全屏模糊"）

- `Jetchat/.../components/MeshUtils.kt:35-44` 注释原文：柔和发光靠 `MeshGradientPainter` + 外圈顶点 alpha=0 实现，**"That means no `.blur()` and no linear gradient are needed"** —— 用网格渐变替代模糊。
- `Jetchat/.../conversation/SurfaceViewBlur.kt:56-113` —— 局部模糊走 **API 37 平台能力** `SurfaceView.setBlurRegions(...)`，且用 `WeakHashMap` 做"同参数不重复下发"去重。**校序 minSdk 26 / compileSdk 36，这条用不了**，只能作为"不要用 RenderEffect 全屏糊"的佐证。

---

## 5. 校序现状对照

| 维度 | 校序现状（`5fec79b`） | 上游更好的做法 | 差距定性 |
| --- | --- | --- | --- |
| 底部导航 | MD3 `NavigationBar`（PR #328 提供可替换组件，未接入） | Jetsnack 自定义 Layout / SleepDown 阻尼驱动 | 组件已就绪，缺集成 |
| 页面切换 | `AnimatedContent` + `fadeIn(180)+slideIn(width/18)` togetherWith `fadeOut(120)` | 淡入淡出用 `spring(1f, 1600f)`；exit 保持到 transition 结束 | **有真缺陷**：exit 比 enter 早 60ms，出现半透明空档 |
| insets | `enableEdgeToEdge()` + 各页面自己判 `useStatusBarInset` | `contentWindowInsets(0)` + `consumeWindowInsets(padding)` | **有真缺陷**：不同 tab 顶部留白不一致 |
| 状态保存 | `pageStateHolder.SaveableStateProvider(page.name)` | 同 nowinandroid 的 NavEntryDecorator 思路 | ✅ 已正确 |
| 平板/横屏 | `maxWidth >= 700.dp` → `NavigationRail`，**不可滚动** | `NavigationSuiteScaffold` + `calculateFromAdaptiveInfo` | **有真缺陷**：横屏 5 项超一屏，「我的」不可达 |
| Baseline Profile | 无 | nowinandroid 完整工程化 + `dexLayoutOptimization` | 缺失 |
| R8 / 资源压缩 | `minifyEnabled` / `shrinkResources` 均未设 | performance-samples 标准配置 | 缺失 |
| Macrobenchmark | 无 | performance-samples 四模式对照 | 缺失 |
| 卡顿归因 | 无 JankStats | nowinandroid `TrackDisposableJank("Navigation", key)` | 缺失 |
| 课表网格 | `remember(lessons)` 缓存 + `horizontalScroll` | SleepDown 周级 `gridCache.getOrPut(week)` | 已有基础缓存 |
| Compose 稳定性配置 | 无 `composeCompiler { stabilityConfigurationFiles }` | nowinandroid / Jetcaster 都有 | 缺失（P2） |

---

## 6. PR B 必须解决的三个真缺陷（研究得出的，不是猜的）

**① 切换动画的半透明空档**
`fadeOut(tween(120))` 在 `fadeIn(tween(180)) + slide(tween(200))` 完成前就把旧页移除了，剩下 60–80ms 只有半透明的新页，透出 `Scaffold` 背景色 = 闪白。
修法：exit 与 enter 同长（或让 exit 保持到 transition 结束），并给 `AnimatedContent` 的 `ContentTransform` 指定合理的 `targetContentZIndex`。

**② 双页同时绘制的成本**
`slideIn` 让新页面从头绘制整页（含 LazyColumn 全部可见 item），旧页面同时绘制。对课表（`weeklyGrid` 全量布局）这种重页，这是切页掉帧的主因。
修法：位移距离足够小（现在 width/18 ≈ 60px），**取消横向滑入、只保留淡入淡出**，或对重页用 `Modifier.graphicsLayer { alpha }` 单独控制而不参与 layout 动画。

**③ 切页无条件重新请求云端**
`DisposableEffect(appLifecycle)` 只管前后台。页面内部的 `LaunchedEffect` 每次进页都会跑。需要确认各页面是否有"已加载则不重复请求"的守卫，没有就补。

---

## 7. P0 / P1 / P2（按"可度量收益 ÷ 风险"排序）

### P0（不碰 UI 结构，收益可测）

1. **Baseline Profile + `dexLayoutOptimization`**
   参考 `ref-nowinandroid/benchmarks/build.gradle.kts` + `app/build.gradle.kts:140-147`。
   验收：`CompilationMode.Partial(baselineProfileMode = Require)` vs `Disable` 的 `StartupTimingMetric` 中位数差。
2. **release 开 R8 + `shrinkResources`**
   校序 `app/build.gradle` 两项都没有。开 `proguard-android-optimize.txt`，注意 `androidbrowserhelper` 与 Room 的 keep 规则。
   验收：APK 体积 + 冷启动中位数。
3. **接入 JankStats 并打导航标签**
   参考 `ref-nowinandroid/core/ui/.../JankStatsExtensions.kt:67-93`。
   没有基线就无法证明任何优化，这条是其余所有工作的前提。

### P1（改 Shell 与页面，需逐项测量）

4. **切页动画修正**（§6 ①②）—— 先测再改，改完必须给出前后帧数据，否则回退。
5. **insets 统一**（§5）—— 消除 tab 间顶部留白跳变。
6. **NavigationRail 可滚动**（§5）—— 横屏可达性缺陷。
7. **课表按周缓存布局**—— 参考 SleepDown `WeekScheduleScreen.kt` 的 `gridCache.getOrPut(week)`；但 `WeeklyTimetableGrid.kt` 正被 **#327 / #329** 修改，**必须等它们合并或改为在其之上叠加**，否则冲突。

### P2（收益不明确，先研究）

8. Compose 编译器稳定性配置 + `reportsDestination`（Jetcaster 写法）。
9. 设备分级降级（SleepDown `AppMaterialPreferences` 三档）。
10. AGSL 触控高光（SleepDown `InteractiveHighlight`，需 API 33+）。
11. 分层入场容器 —— **SleepDown 自己已退化成空 `Box`，不建议做**。

---

## 8. 测量方案（拒绝"未测量就宣称提升"）

| 指标 | 工具 | 条件 | 样本 |
| --- | --- | --- | --- |
| 冷/温启动 | `StartupTimingMetric` | release + `profileable`，`CompilationMode.None/Partial/Full` 四组 | `iterations = 20` |
| 首页→任务/消息/课表/我的切换 | `FrameTimingMetric` | `startupMode = null`，`setupBlock` 只启动一次 | `iterations = 10` |
| 连续快速切换 | `FrameTimingMetric` | 5 tab × 10 轮无间隔 | `iterations = 10` |
| 课表滚动 / 切周 | `FrameTimingMetric` + `TraceSectionMetric` | 滚动用 `setGestureMargin(displayWidth/5)` 防系统手势误触 | `iterations = 10` |
| 卡顿归因 | JankStats | 导航时 `putState("Navigation", key)` | 全程 |

**硬约束**：
- 测试必须在 **release + `debuggable=false` + `profileable`** 构建上跑。Debug 构建的所有帧数据不具参考价值（这是 v1 报告出错的根因）。
- 真机（一加 Ace 6）与模拟器分别出数，**不混算**。
- 每个结论必须给出「改前 / 改后 / 差值 / 样本量与离散度」四元组；离散度覆盖差值时写"未证明改善"。

---

## 9. 与并行 PR 的冲突规避

| 文件 | 被谁改 | 本轮策略 |
| --- | --- | --- |
| `MainActivity.kt` | #327（+7/-1）、#329（+87/-12） | PR B 只改 `bottomBar` / `Scaffold` / `AnimatedContent` 三块，改动区域与它们不重叠；合并前先 rebase |
| `ScheduleScreen.kt` | #327（+186/-14）、#329（+239/-14） | **PR C 暂不碰**，等合并后再做 |
| `WeeklyTimetableGrid.kt` | #327、#329（各 +18/-7） | **PR C 暂不碰** |
| `MessageCenter.kt` / `TaskBoard.kt` | 无开放 PR | PR C 可改 |
| `FloatingBottomBar.kt` / `ui/nav/` | 仅 PR #328 | 本 PR 独占 |

---

## 10. 许可与合规

| 仓库 | 许可 | 能否进主干 | 义务 |
| --- | --- | --- | --- |
| nowinandroid / performance-samples / compose-samples | Apache-2.0 | ✅ 可商用 | 保留版权与许可声明；修改文件需注明 |
| SleepDown-Schedule | 署名非商业 1.1 | ⚠️ **仅非商业** | 源码可见 + 仓库首页署名 + **App 内「关于」页署名** + 不得用近似名称 |

已履行的 SleepDown 义务见 `apps/xiaoxu-android/THIRD_PARTY_NOTICES.md`。
**未履行项**：SleepDown 1.1 第 2 条第 3 项要求「App 内『关于』或设置页面保留同等清晰的原作者署名与原项目链接」—— 校序目前**没有**这个区块，需在 PR B 补（「我的」页面底部）。
