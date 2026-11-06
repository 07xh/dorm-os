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

**未复用的部分**：校序没有复制 SleepDown 的 `LiquidBottomTabs`、`glass` 模块或整套应用资源；`FloatingBottomBar.kt` 保留校序原有 Compose 回退与手势架构，Android 13+ 玻璃光学材质则按下方注明的北化课表源代码单独适配。

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

## 北化课表 / CourseSelector 液态玻璃导航（GPL-3.0）

本分支开始将[znjhahaha/zhengfang-apk](https://github.com/znjhahaha/zhengfang-apk) 的 `v1.0.83` 版本 `app/src/main/java/com/tyust/course/ui/system/CapsuleNavigationBar.kt` 中玻璃轨道与选中透镜的材质配方适配至 `ui/NorthGlassNavChrome.kt`。

- 原项目：CourseSelector / 教务助手，原作者及贡献者见其 Git 历史，许可证为 GPL-3.0。
- 修改：将材质绘制抽为不持有路由状态的 Compose Modifier；图标路径和播放动画源自原版 `PhosphorNavigationIcon.kt`、`NavigationIconMotion.kt`、`NavigationIconPlayback.kt` 并复制到 `ui/northglass/`；手势仍由校序唯一底栏管理；仅在 API 33+ 开启真实 Backdrop，低版本沿用已验证的回退渲染。
- 依赖：`io.github.kyant0:backdrop:2.0.0-alpha03`（为保留校序现有 compileSdk 36 选择的兼容构建）、`io.github.kyant0:shapes:1.2.0`，见 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)。
- 本修改分支为公开源码；复刻视觉及性能仍须与原版 APK 真机比对，不能以编译成功代替视觉验收。
