# 第三方源代码声明

## 拾光课程表教务适配器（MIT）

本项目的 `app/src/main/assets/ncwu_qingguo_local.js` 参考并适配以下来源的青果教务课表提取策略：

- 项目：[ShiGuangSchedule/shiguang_warehouse](https://github.com/ShiGuangSchedule/shiguang_warehouse)
- 文件：`resources/qingguo_jiaowu/qingguo_01.js`
- 原作者：星河欲转
- 版权：Copyright (c) 2025 星河欲转
- 许可证：MIT
- 适配变更：增加本机 HTML 网格的严格拓扑检查、无法解析时拒绝覆盖旧数据、仅导出必要课程字段；不复用上游的 JS Bridge 或自动保存逻辑。

MIT License

Copyright (c) 2025 星河欲转

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

校序的本机教务导入流程参考了上述 MIT 许可实现。院校真实课表能否成功解析仍需本人正常授权登录后验证；CI 构建通过不代表本人实测通过。

---

## SleepDown课程表（署名非商业、源码可见许可 1.1）

PR #328 起，校序的悬浮底部导航复用以下两个文件（**逻辑未改动，仅替换 package 并补充来源头注释**）：

- 项目：[xiaomanjun233/SleepDown-Schedule](https://github.com/xiaomanjun233/SleepDown-Schedule)
- 原作者：xiaomanjun233
- 版权：Copyright © 2026 SleepDown课程表项目作者
- 许可证：署名非商业、源码可见许可 1.1（**非 OSI 开源许可**）

| 校序文件 | 上游文件 | 变更 |
| --- | --- | --- |
| `app/src/main/java/io/github/x07xh/xiaoxu/ui/nav/DampedDragAnimation.kt` | `app/src/main/java/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt` | 仅改 package 与文件头注释 |
| `app/src/main/java/io/github/x07xh/xiaoxu/ui/nav/DragGestureInspector.kt` | `app/src/main/java/com/kyant/backdrop/catalog/utils/DragGestureInspector.kt` | 仅改 package 与文件头注释 |

这两个上游文件自身基于 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的 Apache-2.0 代码并由 SleepDown 修改，因此同时受 Apache-2.0 与本 1.1 许可约束；校序按更严格的一方（1.1）履行义务。

**未复用的部分**：校序**没有**复制 SleepDown 的 `LiquidBottomTabs`、`glass` 模块、界面资源或应用包结构。`ui/FloatingBottomBar.kt` 保留校序自己的 Compose 手势驱动与旧系统回退，Android 13+ 的玻璃轨道、透镜和图标另外适配自下述北化课表 GPL-3.0 项目。

### 许可 1.1 的履行情况

| 1.1 要求 | 校序履行方式 |
| --- | --- |
| 非商业使用 | 校序为个人学习/自用项目，无收费、无广告、无商业分发 |
| 修改版源码可见 | 校序仓库公开于 [07xh/dorm-os](https://github.com/07xh/dorm-os)，含全部构建脚本 |
| 发布页/仓库首页署名 | 见仓库根 `README.md` 的「致谢与许可」一节 |
| App 内「关于」页署名与链接 | 见「我的」页面底部「开源组件与致谢」区块 |
| 明确标注为修改版并列出修改内容 | 上表 + 仓库内 `docs/SLEEPDOWN-NAV-RESEARCH-2026-11-06.md` |
| 不使用近似名称/包名/图标 | 校序应用名「校序」、包名 `io.github.x07xh.xiaoxu`、图标独立设计，与上游无关联 |
| 保留本许可与原始版权声明 | 本文件 + 两个源文件的文件头 |

> 若校序未来改为商业分发（应用商店付费、广告、收费服务），**必须先移除**这两个文件并换用自研动画驱动。此约束同时记录在 `docs/SLEEPDOWN-NAV-RESEARCH-2026-11-06.md` 的「§0 许可证前提」。

---

## 北化课表 / CourseSelector v1.0.83 玻璃导航（GPL-3.0）

- 来源：[znjhahaha/zhengfang-apk v1.0.83](https://github.com/znjhahaha/zhengfang-apk/tree/v1.0.83)，原作者及其他贡献者依原项目 Git 历史。
- 许可证：GNU General Public License version 3（GPL-3.0）。
- 本次适配：保留校序单一 Compose Shell、导航手势和页面状态；移植轨道玻璃配方、物理折射透镜、Phosphor 原版导航图标路径及播放逻辑，调整 Android SDK 36 与校序五项路由的兼容边界。
- 对应文件：`ui/NorthGlassNavChrome.kt`、`ui/PrimaryBottomGeometry.kt`、`ui/northglass/NorthAppSymbolSpec.kt`、`ui/northglass/NorthNavigationIconMotion.kt`、`ui/northglass/NorthNavigationIconPlayback.kt`、`ui/northglass/NorthPhosphorNavigationIcon.kt`，以及 `ui/FloatingBottomBar.kt` 与 `ui/PrimaryNavigation.kt` 中的相关适配。
- 上游可复核：`app/src/main/java/com/tyust/course/ui/system/CapsuleNavigationBar.kt` 和上游玻璃材质相关文件。
- 许可全文：`app/src/main/assets/LICENSE-GPL-3.0.txt`，与上游 v1.0.83 的 `LICENSE` 一致；随测试 APK 一起打包。
- 依赖：`io.github.kyant0:backdrop:2.0.0-alpha03`、`io.github.kyant0:shapes:1.2.0`，对应依赖项目的许可证也需在正式发布前完成清点。

**许可证发布门禁**：GPL-3.0 对分发和相应源码有要求；现有 SleepDown 修改版采用非商业限制许可，不能直接假设其条款与 GPL-3.0 的整体组合发布义务兼容。当前只作为研发与设备 QA 验证，未经专门的许可兼容审查和相应源码/许可证准备，不发布对外正式 APK。构建成功不构成法律兼容性证明。
