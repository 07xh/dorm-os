package com.tyust.course

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyust.course.ui.system.*
import com.tyust.course.ui.system.glass.glassLensAnchor
import com.tyust.course.ui.system.glass.drawBackdropSource
import com.tyust.course.ui.system.glass.drawBlurred
import com.tyust.course.ui.theme.NavigationPages
import com.tyust.course.ui.theme.rememberNavigationMotionState
import com.tyust.course.ui.theme.rememberAppWallpaperStyle

/**
 * UI-only extraction from upstream MainActivity.kt, e3f2833a2fb1e184968724ee4c7f71ebc816a2dd.
 * Original capture order, optical anchors, scrolling and navigation motion are retained.
 * The caller supplies business pages and account state; no upstream school/session logic.
 * This adapter is shared by the validation project and the app module.
 */
@Composable
fun NorthPrimaryHost(
    items: List<BottomNavItem>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    accountKey: String,
    pageData: PageDataState,
    showNavigation: Boolean = true,
    notice: FloatingNotice? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    contextContent: (@Composable () -> Unit)? = null,
    pageContent: @Composable (Int) -> Unit
) {
    val appWallpaper = rememberAppWallpaperStyle()
    val accessibility = rememberGlassAccessibilityMode()
    val navigationMotion = rememberNavigationMotionState(selectedTab, accountKey, accessibility.reduceMotion)
    val dialogHostState = key(accountKey) { rememberDialogHostState() }
    val density = LocalDensity.current
    var navBarMinimized by remember { mutableStateOf(false) }
    // API31/32 折射底图的新鲜度。页面内容随滚动移动，底图必须跟着重拍，
    // 否则折射里是启动那一刻的画面。见 GlassLensFreshness 的注释。
    //
    // 其它 API 上是 null：onScroll() 会写一个 mutableIntState，写在滚动的热路径上。
    // 33+ 没有底图要重拍，那份写入没有任何消费者，白烧。
    val lensFreshness = remember {
        if (com.tyust.course.ui.system.glass.isGlassLensApplicable()) {
            com.tyust.course.ui.system.glass.GlassLensFreshness()
        } else {
            null
        }
    }
    val navScrollIntent = remember { NavScrollIntent() }
    val navBarScrollConnection = remember(density, dialogHostState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && dialogHostState.currentDialog == null) {
                    if (consumed.y == 0f && available.y > 0f) {
                        navBarMinimized = false
                        navScrollIntent.reset()
                    } else if (kotlin.math.abs(consumed.y) > kotlin.math.abs(consumed.x)) {
                        navScrollIntent.scroll(consumed.y / density.density)?.let { navBarMinimized = it }
                    }
                }
                // 惯性滑行（source == SideEffect）也要算：手指离开后页面还在动
                if (consumed.y != 0f) {
                    lensFreshness?.onScroll()
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(selectedTab) {
        navBarMinimized = false
        navScrollIntent.reset()
    }

    GlassOverlayHost(modifier = Modifier.fillMaxSize()) {
        val useGlass = isBackdropSupported()
        val wallpaperBackdrop = if (useGlass) {
            rememberLayerBackdrop()
        } else {
            null
        }

        // 底栏位于该层外，只能单向采样壁纸与页面内容。
        val navBarBackdrop = if (useGlass) {
            rememberLayerBackdrop()
        } else {
            null
        }

        // 顶栏把底边写进这里，通知覆盖层据此落位，避免压住顶栏按钮
        val noticeAnchorState = remember { NoticeAnchorState() }
        // 内容要避开的底栏高度【由底栏自己算】。原先这里写死 96dp，是照手势条量的；
        // 三键导航下底栏实际 136dp 高，各页列表的末项就有一截藏在栏后面。
        val navBarContentInset = if (showNavigation) com.tyust.course.ui.system.NavBarMetrics.contentInset() else 0.dp

        // ## API 31/32 的全局折射区域
        //
        // 绝大多数控件（按钮、圆钮、开关、选择器）采样的是 `LocalControlBackdrop`，
        // 而它在这里就是 `wallpaperBackdrop` —— **一层静态壁纸**。所以整个 App
        // 只需要一张底图，一次快照，之后除了换壁纸/换主题都不必重拍。
        //
        // 为什么是全屏一张、而不是每个控件一张：折射会把边缘附近的背景**位移**进来，
        // 采样点会落到控件轮廓之外。底图只有控件那么大时，那些采样点会被 CLAMP 成
        // 边缘像素，屏幕上是一圈拉长的涂抹而不是真实背景。区域必须比控件大。
        //
        // 页面若用自己的 backdrop 覆盖了 LocalControlBackdrop（例如成绩页顶栏的
        // `combined(壁纸, 顶栏玻璃层)`），那一处就必须自己再建一个区域 ——
        // 底图要复现的是**那个控件实际采样的东西**，不是这一层。
        val appLensDensity = LocalDensity.current
        val appLensAnchor = if (wallpaperBackdrop != null) {
            com.tyust.course.ui.system.glass.rememberGlassLensRegion(
                tag = "app",
                // 壁纸不必写进 keys：rememberGlassLensAnchor 自己盯着
                // AppearanceSettingsManager.style（见那边的注释）。写在这里的
                // 后果是组合期读 style，整棵子树跟着订阅壁纸。
                drawSource = { coords ->
                    drawBackdropSource(wallpaperBackdrop, appLensDensity, coords)
                }
            )
        } else {
            null
        }

        // ## 第二张底图：模糊过的
        //
        // 上面那张是**锐利**的，因为按钮/圆钮/选择器在 33+ 上都是 `enableBlur = false`
        // —— 它们的 lens 采的就是没模糊过的背景。
        //
        // 模态面板（弹窗、下拉菜单）不一样，33+ 的管线是 vibrancy → blur → lens，
        // lens 采的是**已经模糊过的**像素。而模糊必须先烤进底图：屏幕上那层 blur
        // 是加在 drawBackdrop 的图层上的，折射已经在它上游画完了，那层 blur 拿不到
        // 任何输入。所以这里单独存一张 6dp 模糊版。
        //
        // 半径取 Modal 档的 6dp。弹窗与下拉菜单读的是同一个 `modal.blurDp`，
        // 所以一张就够；将来若有别的档位要 blur→lens，它得自己再建一张 ——
        // 差一档模糊，折射里的内容就和屏幕上的对不上。
        val modalLensBlurPx = with(appLensDensity) { GlassRecipe.DialogBlurDp.dp.toPx() }
        val modalLensAnchor = if (navBarBackdrop != null) {
            com.tyust.course.ui.system.glass.rememberGlassLensRegion(
                tag = "app-modal",
                keys = arrayOf(selectedTab, accountKey, dialogHostState.currentDialog),
                freshness = lensFreshness,
                // 壁纸同上，由锚点自己盯
                drawSource = { coords ->
                    drawBlurred(modalLensBlurPx) {
                        drawBackdropSource(navBarBackdrop, appLensDensity, coords)
                    }
                }
            )
        } else {
            null
        }

        CompositionLocalProvider(
            LocalAppBackdrop provides wallpaperBackdrop,
            LocalControlBackdrop provides wallpaperBackdrop,
            LocalModalBackdrop provides navBarBackdrop,
            LocalAppOverlayBottomInset provides navBarContentInset,
            LocalDialogHost provides dialogHostState,
            LocalPageDataState provides pageData,
            LocalFloatingNotice provides notice,
            LocalNoticeAnchor provides noticeAnchorState,
            com.tyust.course.ui.system.glass.LocalPageGlassFreshness provides lensFreshness,
            com.tyust.course.ui.system.glass.LocalGlassLensAnchor provides appLensAnchor,
            com.tyust.course.ui.system.glass.LocalGlassLensModalAnchor provides modalLensAnchor
        ) {
            Box(
                modifier = Modifier.fillMaxSize().then(
                    if (useGlass && navBarBackdrop != null) Modifier.layerBackdrop(navBarBackdrop)
                    else Modifier
                ).then(
                    // 全局折射区域的取景框：全屏。控件的采样点会跑到自己轮廓之外，
                    // 底图必须比控件大，否则边缘会被 CLAMP 成一圈涂抹。
                    //
                    // **两张底图都要挂**。只挂一张时，另一张的 coordinates 永远是
                    // null，`ensureSource()` 直接返回 null，折射一个像素都不画；
                    // 而调用方的 `onDrawBackdrop` 已经因为"锚点非 null"把正常的背景
                    // 绘制让掉了 —— 屏幕上是**整块面板消失**，只剩文字和按钮浮在
                    // 页面上。弹窗上实拍过一次，就是这个原因。
                    Modifier
                        .glassLensAnchor(appLensAnchor)
                        .glassLensAnchor(modalLensAnchor)
                )
            ) {
            if (useGlass && wallpaperBackdrop != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(wallpaperBackdrop)
                ) {
                    // 在绘制 lambda 内部再读一次 state：图片壁纸的位图是异步解码的，
                    // 只读外面那份快照的话，位图到位时这一层不会重绘。
                    drawWallpaperPattern(appWallpaper)
                }
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 无玻璃分支也画完整壁纸。这里原先只填 baseColor，于是关掉液态玻璃
                    // （或在 API 31 以下）用户自己传的图片壁纸会整张消失，只剩一块纯色。
                    // drawWallpaperPattern 是纯 Canvas 绘制，不依赖 backdrop。
                    // 微纹理关掉：它唯一的作用是给折射提供可弯曲的高频内容，这条路径没有折射。
                    drawWallpaperPattern(
                        appWallpaper,
                        microTexture = false
                    )
                }
            }


            Row(modifier = Modifier.fillMaxSize()) {
                leadingContent?.invoke()
                Box(Modifier.weight(1f).fillMaxHeight().nestedScroll(navBarScrollConnection)) {
                    key(accountKey) {
                        val savedPages = rememberSaveableStateHolder()
                        if (contextContent != null) {
                            contextContent()
                        } else {
                            NavigationPages(navigationMotion, modifier = Modifier.fillMaxSize().graphicsLayer {
                                translationX = -6.dp.toPx() * dialogHostState.pageProgress
                                alpha = 1f - 0.03f * dialogHostState.pageProgress
                            }) { page ->
                                savedPages.SaveableStateProvider(items[page].route) { pageContent(page) }
                            }
                        }
                    }
                }
            }
            } // original navBarBackdrop capture boundary

            // 底栏位于捕获层外，避免采样源包含底栏自身。
            if (showNavigation) CompositionLocalProvider(com.tyust.course.ui.theme.LocalNavigationMotion provides navigationMotion) {
            CapsuleNavigationBar(
                items = items,
                selectedTab = selectedTab,
                onTabSelect = onTabSelect,
                minimized = navBarMinimized,
                onExpandRequest = { navBarMinimized = false },
                backdrop = navBarBackdrop,
                lensFreshness = lensFreshness,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            }


            GlassToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = navBarContentInset + 12.dp))
            DialogHost(dialogHostState, Modifier.fillMaxSize())
            FloatingNoticeHost(modifier = Modifier.fillMaxSize())
        }
    }
}
