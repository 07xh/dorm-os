# 校园跑 · Android 本机嵌入 Python 可行性验证

关联 [#302](https://github.com/07xh/dorm-os/issues/302)。已合并 #372 的 Python 开源读取桥与 #374 的 Kotlin 映射边界。本目录仅验证**运行环境**，不需要再次抓包、不产生第二套模型、不接写接口。

## 运行方式选择

优先验证 **Chaquopy 17.2 + Python 3.13，按需在校序 Android 进程内启动**。项目使用 Android Gradle Plugin 8.13、minSdk 26，均在 Chaquopy 的受支持范围内。对用户的一加 Ace 6 优先采用 `arm64-v8a`，CI 使用单独的 `x86_64` 模拟器包确认 Python 确能启动并导入固定 MIT 开源项目的只读查询类。

这与在局域网运行 Python 服务相比，能避免把上游登录凭据或个人成绩跨设备传输，也不需要 Windows 常开或另外购买服务器。其代价是 APK 更大，且 Python 首次冷启动存在性能开销，所以**先在独立 Android smoke 工程中测量与验证，不把插件引入正式校序 App**。

## 本次 CI 的明确边界

- 只从 MIT 项目 `Inverx/com.zjwh.android_wh_physicalfitness` 固定 commit `412a2e28bed209201057bf8b943fcd0a0e2211c1` 拷贝 `core/profile/policy/history`。
- `readonly_runtime_smoke.py` 仅验证各类和只读方法能被 Android Python 运行时导入；**不实例化网络客户端，不登录，不读取任何账号数据，不执行跑步提交**。
- 独立 `app`（包名 `io.github.x07xh.campusrun.smoke`）不参与正式 `apps/xiaoxu-android` 构建；不可标成校序正式 APK。
- 测量 ARM64 APK 字节数，作为最终集成前的体积/冷启动取舍依据。CI 的模拟器导入检查属于运行时兼容，不是运动世界校园真实数据的证据。

## 真实账号闭环仍需实测

要推进下一阶段，首先必须得到**用户本人正常登录授权、合法可用的会话**。安卓应用不能直接读取另一个 App 的私有会话。没有明确合法的 App 授权回跳/复用接口时，应由用户在校序可验证的官方授权流程中完成独立授权，不能默默复制 Cookie、令牌、绕过验证码或用旧 HAR 重放认证。对于 Python 原项目包含的自动验证码、轨迹生成、上传结算和设备欺骗实现，一律不引入。

必须以真实账号的 `get_semester_summary/get_semester_info/get_run_records` **只读**结果与官方 App 同期比对字段含义、单位和分页，再通过 #372 → #374 → #368 → #367 接到现有的 `CampusRunPanel`。当前仍没有官方个人数据实测，`verifiedFieldSemantics` 不可设置为 true。

待独立测试、体积/性能和合法授权方式都通过后，才考虑将运行时作为非首页懒加载组件，防止影响校序底部导航、课表和任务页面流畅度。

## 一加 Ace 6 的独立 CPU / 内存实测 APK

本分支额外提供一个**可安装、无互联网权限的独立调试 APK**（包名 `io.github.x07xh.campusrun.smoke`，**不是正式校序 App**）：

1. 从 PR 对应的 GitHub Actions `Xiaoxu Campus Run Embedded Python Runtime Smoke` 运行页面下载 `campus-run-cpu-smoke-arm64-NOT-XIAOXU` 测试构建产物，解压后安装 `app-debug.apk`。
2. 在 Ace 6 的应用列表中打开 **校园跑 Python 性能测试**，点击「测试 Python 启动与 CPU 开销」。
3. 屏幕会显示首次启动 Python、首次导入 MIT 项目只读模块、再次调用的耗时（wall ms）、**本进程使用的 CPU 时间**（process CPU ms）与 PSS（进程分摊内存、KB）。
4. 保持同一进程时再点一次测暖启动；强制停止应用并重开后测冷启动。采集对比结果即可，不要提交设备身份信息或账号凭据。
5. CI 的 Android 模拟器测试还测量 Python 首次启动、首次导入、暖调用以及两秒短暂空闲时的进程 CPU 时间与 PSS，输出为 `CAMPUS_RUN_PERF` 日志和 7 天保留的 `campus-run-embedded-python-perf` 工件。

这个 APK 不声明 `INTERNET` 权限，源码也不初始化上游登录、请求或跑步提交；仅检验 Python 运行时能否加载。**进程 CPU 时间不是 CPU 使用率，不能由一次冷启动结果推断全天耗电。** CI 模拟器结果不是 Ace 6 的真实性能结论，独立测试包体积也不是正式校序最终安装包增量。

通过条件：实机无崩溃、Python 类加载成功；耗时和内存开销被记录，且校序正式首页/课表/任务无需启动 Python。之后才评估是否将该运行时接入现有 `CampusRunPanel`。即使此步骤通过，本人官方授权只读查询仍需单独验收。
