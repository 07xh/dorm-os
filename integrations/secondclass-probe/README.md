# 第二课堂：手机本机业务协议验证

独立验证工具：不接 App、Cloudflare、D1 或 Room，不编译 APK。

## 验证范围

在本人已经正常登录的 https://dekt.ncwu.edu.cn 官方业务页，以该页面自身会话发起一次已确认的只读业务 POST：`/sys/stu/score/list`。
仅核对返回账号与本机输入是否一致，并投影一笔积分 JSON；优先选择 `school_term_index=100` 的学校汇总行。零分有效；空数据、登录 HTML、身份不一致、字段类型不符均失败。
**这是浏览器同源协议验证，不是 Android 原生 HTTP 会话交接验收。** 不登录、不读取 Cookie 或其他 App 会话、不保存上传学号、不读取密码，不改变学校数据。没有本人执行结果前，真实读取仍未验收。

## 手机执行

1. 在手机浏览器正常登录第二课堂官网，进入本人活动或积分业务页。不要在校序网站或学校认证页执行。
2. 新建一个浏览器书签，名称填“二课协议验证”；将 `probe-bookmarklet.txt` 完整一行（以 `javascript:` 开头）设为书签网址。若浏览器禁止脚本书签，停止使用此方式，不修改安全设置；可在本人已登录官网的开发者控制台运行同一代码。
3. 停留在 dekt.ncwu.edu.cn，从书签列表点击该书签。只在本机核对框输入自己的学号，不输入密码。
4. 结果窗显示 JSON。可复制结果排障；只有账号匹配布尔量、固定失败阶段和一笔积分字段，不含学号、密码、Cookie、票据或完整响应。

本人账号若无法正常进入官网业务页，本工具不能解决首次认证，应记为“尚未执行”，不要反复提交登录。
首次只运行一次；失败保留结果并先定位，不要重复点击猜测登录状态。

## 结果含义

- `ok=true`：本次实际响应的账号匹配，且一笔积分字段有效；仍需本人对照官网数值。
- `fullSyncVerified=false`：没有验收活动全量、分页或完整积分同步。
- `nativeSessionHandoffVerified=false`：没有验收 WebView 到原生 HTTP 的会话交接。
- `ok=false`：按 `stage` 和 `reason` 定位；HTTP 200 和页面可打开均不等于成功。
- 无结果窗：可能是脚本未执行或浏览器限制，应记为“未执行”。

## 开发验证

运行 `node --test integrations/secondclass-probe/probe.test.mjs`；生成书签运行 `node integrations/secondclass-probe/build-bookmarklet.mjs`。
测试全部使用合成响应，只证明校验逻辑，不能证明本人读取成功。路径与字段复用 `lib/academic-ncwu-second-class.mjs` 契约。学校认证、活动解析、原 App 和云端连接不在本次改动范围。
