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

## SleepDown课程表（历史复用记录，署名非商业、源码可见许可 1.1）

历史来源：[xiaomanjun233/SleepDown-Schedule](https://github.com/xiaomanjun233/SleepDown-Schedule)，Copyright © 2026 SleepDown课程表项目作者。原先适配的 `ui/nav/DampedDragAnimation.kt`、`ui/nav/DragGestureInspector.kt` 和 `ui/PrimaryPageMotion.kt` 已从当前源码树删除。历史声明与来源保留于 Git 历史、相关研究文档，不将非商业条款附加给 GPL 组件。尚不能仅凭文件删除证明所有衍生代码已排除。

## 北化课表 / CourseSelector v1.0.83（GPL-3.0）

- 固定来源：[e3f2833a2fb1e184968724ee4c7f71ebc816a2dd](https://github.com/znjhahaha/zhengfang-apk/tree/e3f2833a2fb1e184968724ee4c7f71ebc816a2dd)。
- 原作者及其他贡献者依原项目 Git 历史；87 个原始文件由 snapshot manifests 和 Git blob hash 校验。
- 路径：`vendor/north-ui-v1.0.83/app/` 与原始 `LICENSE`；`validation/` 存放校序适配及构建脚本，不计入原版哈希数。
- APK 中 GPL 全文：`app/src/main/assets/LICENSE-GPL-3.0.txt`；Phosphor Icons MIT 声明见 `assets/licenses/phosphor-icons.txt`。
- Backdrop 2.0.0、Shapes 1.2.0 均声明 Apache-2.0，原则上可与 GPLv3 组合，仍须保留适用的许可/版权声明。
- 正式分发尚未获准；整体 Android 代码的权利人许可、所有传递依赖/资源声明与对应源码义务尚待完成。详见 [审查记录](../../docs/XIAOXU-NORTH-UI-RELEASE-REVIEW.md)。

---

## NextTodo 任务依赖模型（MIT）

- 项目：[JunieXD/NextTodo](https://github.com/JunieXD/NextTodo)
- 原作者：JunieXD
- 版权：Copyright (c) 2026 JunieXD
- 许可证：MIT
- 校序适配：参考 NextTodo `crates/core/src/dag.rs` / `board.rs` 的 DAG 循环检测与“前置完成才解锁”规则，在现有 Android Room / Compose 内以 Kotlin 实现；并未引入 NextTodo Rust/Axum 后端或新增学校任务库。
- 作用范围：只有用户明确设置的本机任务关系；学校作业和微信任务的来源、身份与官方状态仍使用校序已有实现。

MIT License

Copyright (c) 2026 JunieXD

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
