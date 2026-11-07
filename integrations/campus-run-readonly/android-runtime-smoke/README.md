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
