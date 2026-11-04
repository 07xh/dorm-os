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

校序本轮**没有**复制 SleepDown 自身受限制的应用源码、界面资源或专有资产，只参考其本机教务导入流程。院校真实课表能否成功解析仍需本人正常授权登录后验证；CI 构建通过不代表本人实测通过。
